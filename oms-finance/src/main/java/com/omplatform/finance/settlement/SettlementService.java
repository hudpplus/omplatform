package com.omplatform.finance.settlement;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.finance.entity.PaymentOrder;
import com.omplatform.finance.entity.SettlementOrder;
import com.omplatform.finance.mapper.PaymentOrderMapper;
import com.omplatform.finance.mapper.SettlementOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结算服务（ADR-043 §3）。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>按日生成结算报告 — 对已完成支付的订单计算佣金、生成结算单</li>
 *   <li>结算确认 — 将 PENDING 状态的结算单标记为 SETTLED</li>
 *   <li>查询结算数据 — 按日期/店铺/状态维度</li>
 * </ol>
 * <p>
 * 结算模型：结算金额 = 订单实付金额 − 平台佣金
 * 佣金 = 实付金额 × 佣金比例（默认 0.6%，可通过 {@code SETTLEMENT_COMMISSION_RATE} 环境变量覆盖）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final SettlementOrderMapper settlementOrderMapper;

    /** 默认佣金比例 0.6% */
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.006");

    /**
     * 生成指定日期的结算报告。
     * <p>
     * 流程：
     * <ol>
     *   <li>查询该日所有 SUCCESS 状态的支付单</li>
     *   <li>按订单粒度计算佣金，创建 settlement_order 记录</li>
     *   <li>汇总统计返回</li>
     * </ol>
     * <p>
     * 幂等：同一日期重复调用不会重复生成（已存在的 order_no 跳过）。
     *
     * @param date 结算日期 yyyy-MM-dd
     * @return 结算报表
     */
    @Transactional
    public SettlementReport generateDailyReport(String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDateTime dayStart = localDate.atStartOfDay();
        LocalDateTime dayEnd = localDate.atTime(LocalTime.MAX);

        // 1. 查询该日 SUCCESS 支付单
        List<PaymentOrder> payments = paymentOrderMapper.selectList(
                Wrappers.<PaymentOrder>lambdaQuery()
                        .eq(PaymentOrder::getStatus, "SUCCESS")
                        .between(PaymentOrder::getGmtCreate, dayStart, dayEnd));

        if (payments.isEmpty()) {
            log.info("[结算] {} 无 SUCCESS 支付单", date);
            return new SettlementReport(date, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        }

        // 2. 查询已有结算单（去重）
        Set<String> existingOrderNos = settlementOrderMapper.selectList(
                Wrappers.<SettlementOrder>lambdaQuery()
                        .select(SettlementOrder::getOrderNo)
                        .eq(SettlementOrder::getDeleted, 0)
        ).stream().map(SettlementOrder::getOrderNo).collect(Collectors.toSet());

        // 3. 按店铺分组
        Map<String, List<PaymentOrder>> byShop = payments.stream()
                .collect(Collectors.groupingBy(this::resolveShopId));

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        List<SettlementOrder> newOrders = new ArrayList<>();

        for (Map.Entry<String, List<PaymentOrder>> entry : byShop.entrySet()) {
            String shopId = entry.getKey();
            for (PaymentOrder payment : entry.getValue()) {
                // 跳过已生成结算单的订单（幂等）
                if (existingOrderNos.contains(payment.getOrderNo())) {
                    log.debug("[结算] 订单已结算，跳过: orderNo={}", payment.getOrderNo());
                    continue;
                }

                BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
                BigDecimal commissionRate = getCommissionRate();
                BigDecimal commission = amount.multiply(commissionRate)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal settleAmount = amount.subtract(commission)
                        .setScale(2, RoundingMode.HALF_UP);

                SettlementOrder settle = new SettlementOrder();
                settle.setSettleNo(generateSettleNo(payment.getOrderNo()));
                settle.setOrderNo(payment.getOrderNo());
                settle.setShopId(shopId);
                settle.setAmount(settleAmount);
                settle.setCommission(commission);
                settle.setCommissionRate(commissionRate);
                settle.setStatus("PENDING");
                settle.setGmtCreate(LocalDateTime.now());
                settle.setGmtModified(LocalDateTime.now());

                settlementOrderMapper.insert(settle);
                newOrders.add(settle);

                totalAmount = totalAmount.add(amount);
                totalCommission = totalCommission.add(commission);

                log.info("[结算] 生成结算单: orderNo={}, shop={}, amount={}, commission={}, settle={}",
                        payment.getOrderNo(), shopId, amount, commission, settleAmount);
            }
        }

        int newCount = newOrders.size();
        log.info("[结算] {} 结算完成: 总订单={}笔, 新增结算={}笔, 总金额={}, 总佣金={}",
                date, payments.size(), newCount, totalAmount, totalCommission);

        return new SettlementReport(
                date, payments.size(), totalAmount, totalCommission,
                totalAmount.subtract(totalCommission), newCount, newOrders);
    }

    /**
     * 确认结算（单笔 PENDING → SETTLED）。
     */
    @Transactional
    public boolean confirmSettlement(String settleNo) {
        SettlementOrder order = settlementOrderMapper.selectById(settleNo);
        if (order == null) {
            log.warn("[结算] 结算单不存在: settleNo={}", settleNo);
            return false;
        }
        if (!"PENDING".equals(order.getStatus())) {
            log.warn("[结算] 结算单状态非 PENDING，无法确认: settleNo={}, status={}",
                    settleNo, order.getStatus());
            return false;
        }

        order.setStatus("SETTLED");
        order.setSettleAt(LocalDateTime.now());
        order.setGmtModified(LocalDateTime.now());
        settlementOrderMapper.updateById(order);

        log.info("[结算] 结算确认完成: settleNo={}, orderNo={}, amount={}",
                settleNo, order.getOrderNo(), order.getAmount());
        return true;
    }

    /**
     * 批量确认结算（日期维度，确认所有 PENDING 结算单）。
     *
     * @param date 结算日期
     * @return 确认的结算单数量
     */
    @Transactional
    public int batchConfirmSettlement(String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDateTime dayStart = localDate.atStartOfDay();
        LocalDateTime dayEnd = localDate.atTime(LocalTime.MAX);

        List<SettlementOrder> pendingOrders = settlementOrderMapper.selectList(
                Wrappers.<SettlementOrder>lambdaQuery()
                        .eq(SettlementOrder::getStatus, "PENDING")
                        .eq(SettlementOrder::getDeleted, 0)
                        .between(SettlementOrder::getGmtCreate, dayStart, dayEnd));

        int count = 0;
        for (SettlementOrder order : pendingOrders) {
            order.setStatus("SETTLED");
            order.setSettleAt(LocalDateTime.now());
            order.setGmtModified(LocalDateTime.now());
            settlementOrderMapper.updateById(order);
            count++;
        }

        log.info("[结算] 批量确认完成: date={}, count={}", date, count);
        return count;
    }

    // ========== 查询方法 ==========

    /**
     * 查询指定日期的结算单明细。
     */
    public List<SettlementOrder> getSettlementOrders(String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        return settlementOrderMapper.selectList(
                Wrappers.<SettlementOrder>lambdaQuery()
                        .eq(SettlementOrder::getDeleted, 0)
                        .between(SettlementOrder::getGmtCreate,
                                localDate.atStartOfDay(), localDate.atTime(LocalTime.MAX))
                        .orderByAsc(SettlementOrder::getGmtCreate));
    }

    /**
     * 查询指定店铺的结算单。
     */
    public List<SettlementOrder> getShopSettlements(String shopId, String date) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        return settlementOrderMapper.selectList(
                Wrappers.<SettlementOrder>lambdaQuery()
                        .eq(SettlementOrder::getShopId, shopId)
                        .eq(SettlementOrder::getDeleted, 0)
                        .between(SettlementOrder::getGmtCreate,
                                localDate.atStartOfDay(), localDate.atTime(LocalTime.MAX))
                        .orderByAsc(SettlementOrder::getGmtCreate));
    }

    /**
     * 获取结算汇总统计。
     */
    public SettlementSummary getSummary(String date) {
        List<SettlementOrder> orders = getSettlementOrders(date);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        long pendingCount = 0;
        long settledCount = 0;

        for (SettlementOrder o : orders) {
            totalAmount = totalAmount.add(o.getAmount());
            totalCommission = totalCommission.add(o.getCommission());
            if ("PENDING".equals(o.getStatus())) pendingCount++;
            else if ("SETTLED".equals(o.getStatus())) settledCount++;
        }

        return new SettlementSummary(
                date, orders.size(), pendingCount, settledCount,
                totalAmount, totalCommission, totalAmount.subtract(totalCommission));
    }

    // ========== 配置 ==========

    /**
     * 获取佣金比例，优先从环境变量读取。
     */
    private BigDecimal getCommissionRate() {
        try {
            String env = System.getenv("SETTLEMENT_COMMISSION_RATE");
            if (env != null) {
                return new BigDecimal(env);
            }
        } catch (Exception e) {
            log.warn("[结算] 环境变量 SETTLEMENT_COMMISSION_RATE 解析失败，使用默认值: {}", e.getMessage());
        }
        return DEFAULT_COMMISSION_RATE;
    }

    // ========== 辅助方法 ==========

    /**
     * 解析店铺 ID。
     * <p>
     * TODO: 当前返回默认值 "DEFAULT"。
     * 生产环境应从 {@code PaymentOrder.orderNo} 调用 OrderService Dubbo 接口获取 shopId。
     * 或从支付回调的 notify_raw 中解析店铺信息。
     */
    private String resolveShopId(PaymentOrder payment) {
        return "DEFAULT";
    }

    /**
     * 生成结算单号：S + yyyyMMdd + 6位序列。
     */
    private String generateSettleNo(String orderNo) {
        return "S" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + orderNo.hashCode() % 1000000;
    }

    // ========== DTO ==========

    /**
     * 结算报告（日期级）。
     */
    public record SettlementReport(
            String date,
            int totalOrders,
            BigDecimal totalAmount,
            BigDecimal totalCommission,
            BigDecimal netSettlement,
            int newSettlementCount,
            List<SettlementOrder> settlementOrders
    ) {}

    /**
     * 结算汇总。
     */
    public record SettlementSummary(
            String date,
            int totalOrders,
            long pendingCount,
            long settledCount,
            BigDecimal totalAmount,
            BigDecimal totalCommission,
            BigDecimal netSettlement
    ) {}
}
