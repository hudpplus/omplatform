package com.omplatform.trade.migration.job;

import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据一致性对账 XXL-Job — 比较旧 {@code oms_trade.order} 与新 {@code order_ecommerce_X} 的数据一致性。
 * <p>
 * <h3>检查内容</h3>
 * <ol>
 *   <li><b>数量一致性</b>：旧表记录数 vs 新表记录数</li>
 *   <li><b>字段一致性</b>：对每一笔订单逐字段比较（状态、金额等）</li>
 *   <li><b>差异报告</b>：列出不一致的记录</li>
 * </ol>
 * <p>
 * xxl-job-admin 配置：
 * <ul>
 *   <li>执行器：oms-trade</li>
 *   <li>任务描述：数据一致性对账</li>
 *   <li>Java 类型：{@code com.omplatform.trade.migration.job.ReconciliationJob.execute}</li>
 * </ul>
 */
@Slf4j
@Component
public class ReconciliationJob {

    @Autowired
    @Qualifier("legacyJdbcTemplate")
    private JdbcTemplate legacyJdbcTemplate;

    @Autowired
    private com.omplatform.trade.repository.OrderRepository orderRepository;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @XxlJob("reconciliation")
    public void execute() {
        StringBuilder report = new StringBuilder();
        report.append("===== 数据一致性对账报告 =====\n");

        // 1. 数量对比
        long oldCount = countLegacyOrders();
        long newCount = orderRepository.count();
        report.append(String.format("旧表订单数: %d\n", oldCount));
        report.append(String.format("新表订单数: %d\n", newCount));

        if (oldCount != newCount) {
            report.append(String.format("⚠ 数量不一致！差异: %d\n", oldCount - newCount));
        } else {
            report.append("✓ 数量一致\n");
        }

        // 2. 抽样逐字段对比（取前 1000 条）
        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger diffCount = new AtomicInteger(0);
        List<String> diffs = new ArrayList<>();

        legacyJdbcTemplate.query(
                "SELECT * FROM `order` ORDER BY gmt_create ASC LIMIT 1000",
                rs -> {
                    String orderNo = rs.getString("order_no");
                    OrderEntity newEntity = orderRepository.getById(orderNo);

                    if (newEntity == null) {
                        diffs.add(orderNo + ": 新表中不存在");
                        diffCount.incrementAndGet();
                        return;
                    }

                    // 逐字段比较
                    List<String> fieldDiffs = compareFields(rs, newEntity);
                    if (!fieldDiffs.isEmpty()) {
                        diffs.add(orderNo + ": " + String.join(", ", fieldDiffs));
                        diffCount.incrementAndGet();
                    } else {
                        matched.incrementAndGet();
                    }
                });

        report.append(String.format("对账抽样: 一致 %d 条, 差异 %d 条\n", matched.get(), diffCount.get()));

        if (!diffs.isEmpty()) {
            report.append("\n--- 差异明细（前 50 条）---\n");
            diffs.stream().limit(50).forEach(d -> report.append("  ").append(d).append("\n"));
        }

        log.info(report.toString());
        XxlJobHelper.handleSuccess(report.toString());
    }

    private long countLegacyOrders() {
        Long count = legacyJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order`", Long.class);
        return count != null ? count : 0;
    }

    private List<String> compareFields(ResultSet oldRs, OrderEntity newEntity) {
        List<String> diffs = new ArrayList<>();
        try {
            // 状态
            String oldStatus = oldRs.getString("status");
            if (newEntity.getStatus() != null && !newEntity.getStatus().name().equals(oldStatus)) {
                diffs.add("status: old=" + oldStatus + ", new=" + newEntity.getStatus());
            }

            // 金额
            compareDecimal(diffs, "total_amount", oldRs.getBigDecimal("total_amount"),
                    newEntity.getTotalAmount());
            compareDecimal(diffs, "pay_amount", oldRs.getBigDecimal("pay_amount"),
                    newEntity.getPayAmount());
            compareDecimal(diffs, "freight_amount", oldRs.getBigDecimal("freight_amount"),
                    newEntity.getFreightAmount());
            compareDecimal(diffs, "discount_amount", oldRs.getBigDecimal("discount_amount"),
                    newEntity.getDiscountAmount());

            // buyer_id / shop_id
            compareString(diffs, "buyer_id", oldRs.getString("buyer_id"),
                    newEntity.getBuyerId());
            compareString(diffs, "shop_id", oldRs.getString("shop_id"),
                    newEntity.getShopId());

            // businessType 应 = ecommerce
            if (!"ecommerce".equals(newEntity.getBusinessType())) {
                diffs.add("businessType: expected=ecommerce, actual=" + newEntity.getBusinessType());
            }

            // deleted
            int oldDeleted = oldRs.getInt("deleted");
            if ((newEntity.getDeleted() == null ? 0 : newEntity.getDeleted()) != oldDeleted) {
                diffs.add("deleted: old=" + oldDeleted + ", new=" + newEntity.getDeleted());
            }

        } catch (SQLException e) {
            diffs.add("读取旧表字段异常: " + e.getMessage());
        }
        return diffs;
    }

    private void compareDecimal(List<String> diffs, String field,
                                java.math.BigDecimal oldVal, java.math.BigDecimal newVal) {
        if (oldVal == null && newVal == null) return;
        if (oldVal != null && newVal != null && oldVal.compareTo(newVal) == 0) return;
        diffs.add(field + ": old=" + oldVal + ", new=" + newVal);
    }

    private void compareString(List<String> diffs, String field,
                               String oldVal, String newVal) {
        if (oldVal == null && newVal == null) return;
        if (oldVal != null && oldVal.equals(newVal)) return;
        diffs.add(field + ": old=" + oldVal + ", new=" + newVal);
    }
}
