package com.omplatform.trade.migration.service;

import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.OrderMapper;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.omplatform.trade.sharding.BusinessContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据迁移服务 — 将旧 {@code order} 表数据迁移到 ADR-017 新分片表 {@code order_ecommerce_X}。
 * <p>
 * <h3>迁移策略</h3>
 * <ol>
 *   <li>使用 {@code legacyJdbcTemplate}（非 ShardingSphere 数据源）逐页读取旧 {@code oms_trade.order}</li>
 *   <li>通过 {@link OrderMapper} 写入新表（ShardingSphere 自动按 buyer_id 路由到正确分片）</li>
 *   <li>写入前注入 {@link BusinessContext} 确保路由正确</li>
 * </ol>
 * <p>
 * <h3>幂等性</h3>
 * 按 order_no 逐条插入，重复主键时跳过。支持重复执行。
 */
@Slf4j
@Service
public class DataMigrationService {

    @Autowired
    @Qualifier("legacyJdbcTemplate")
    private JdbcTemplate legacyJdbcTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /**
     * 迁移指定业务线的订单到新表。
     *
     * @param businessType 业务线（默认 ecommerce）
     * @param batchSize    每批读取行数
     * @param maxPages     最大页数（null=无限制）
     * @return 迁移统计
     */
    public MigrationResult migrateOrders(String businessType, int batchSize, Integer maxPages) {
        String bt = businessType != null ? businessType : "ecommerce";
        MigrationResult result = new MigrationResult();
        result.setBusinessType(bt);
        result.setStartTime(LocalDateTime.now());

        long offset = 0;
        int pages = 0;

        while (maxPages == null || pages < maxPages) {
            // 1. 从旧表读取一批数据
            List<OrderEntity> oldOrders = readLegacyOrders(bt, offset, batchSize);
            if (oldOrders.isEmpty()) break;

            // 2. 写入新表（ShardingSphere 路由）
            for (OrderEntity oldOrder : oldOrders) {
                try {
                    // 注入 BusinessContext → 路由到正确的 order_ecommerce_X
                    BusinessContext.setAll(bt, oldOrder.getBuyerId(), oldOrder.getOrderNo());
                    orderMapper.insert(oldOrder);
                    result.setMigratedOrders(result.getMigratedOrders() + 1);

                    // 迁移 order_items
                    String orderNo = oldOrder.getOrderNo();
                    List<OrderItemEntity> oldItems = readLegacyItems(orderNo);
                    if (!oldItems.isEmpty()) {
                        for (OrderItemEntity item : oldItems) {
                            item.setBusinessType(bt);
                            orderItemMapper.insert(item);
                        }
                        result.setMigratedItems(result.getMigratedItems() + oldItems.size());
                    }
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 幂等：已迁移的记录跳过
                    log.debug("跳过已存在的订单: orderNo={}", oldOrder.getOrderNo());
                    result.setSkippedOrders(result.getSkippedOrders() + 1);
                } catch (Exception e) {
                    log.error("迁移失败: orderNo={}, err={}", oldOrder.getOrderNo(), e.getMessage());
                    result.setFailedOrders(result.getFailedOrders() + 1);
                } finally {
                    BusinessContext.clear();
                }
            }

            offset += batchSize;
            pages++;

            log.info("迁移进度: businessType={}, 已完成 {} 条（页 {}/{}）",
                    bt, result.getMigratedOrders(), pages,
                    maxPages != null ? maxPages : "∞");
        }

        result.setEndTime(LocalDateTime.now());
        log.info("迁移完成: businessType={}, 成功={}, 跳过={}, 失败={}, 商品行={}, 耗时={}ms",
                bt, result.getMigratedOrders(), result.getSkippedOrders(),
                result.getFailedOrders(), result.getMigratedItems(),
                java.time.Duration.between(result.getStartTime(), result.getEndTime()).toMillis());
        return result;
    }

    /** 获取旧表总记录数 */
    public long countLegacyOrders(String businessType) {
        Long count = legacyJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order`", Long.class);
        return count != null ? count : 0;
    }

    // ========== 内部 ==========

    private List<OrderEntity> readLegacyOrders(String businessType, long offset, int limit) {
        return legacyJdbcTemplate.query(
                "SELECT * FROM `order` ORDER BY gmt_create ASC LIMIT ? OFFSET ?",
                new LegacyOrderRowMapper(businessType), limit, offset);
    }

    private List<OrderItemEntity> readLegacyItems(String orderNo) {
        return legacyJdbcTemplate.query(
                "SELECT * FROM order_items WHERE order_no = ?",
                new LegacyItemRowMapper(), orderNo);
    }

    // ===== RowMapper =====

    /**
     * 旧表 row → OrderEntity 映射。
     * 旧表 {@code oms_trade.order} 字段与 {@link OrderEntity} 基本一致，补充 businessType。
     */
    private static class LegacyOrderRowMapper implements RowMapper<OrderEntity> {
        private final String businessType;

        LegacyOrderRowMapper(String businessType) {
            this.businessType = businessType;
        }

        @Override
        public OrderEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            OrderEntity e = new OrderEntity();
            e.setOrderNo(rs.getString("order_no"));
            e.setParentOrderNo(rs.getString("parent_order_no"));
            e.setBuyerId(rs.getString("buyer_id"));
            e.setShopId(rs.getString("shop_id"));
            e.setBusinessType(businessType);
            e.setStatus(OrderStatus.valueOf(rs.getString("status")));

            String prevStatus = rs.getString("previous_status");
            if (prevStatus != null) {
                e.setPreviousStatus(OrderStatus.valueOf(prevStatus));
            }

            e.setTotalAmount(rs.getBigDecimal("total_amount"));
            e.setPayAmount(rs.getBigDecimal("pay_amount"));
            e.setFreightAmount(rs.getBigDecimal("freight_amount"));
            e.setDiscountAmount(rs.getBigDecimal("discount_amount"));
            e.setAddressId(rs.getString("address_id"));
            e.setRemark(rs.getString("remark"));
            e.setChannelSource(rs.getString("channel_source"));
            e.setCouponInstanceId(rs.getString("coupon_instance_id"));
            e.setPayChannel(rs.getString("pay_channel"));
            e.setTransactionId(rs.getString("transaction_id"));
            e.setHoldReason(rs.getString("hold_reason"));
            e.setFrozenReason(rs.getString("frozen_reason"));

            java.sql.Timestamp sca = rs.getTimestamp("status_changed_at");
            if (sca != null) e.setStatusChangedAt(sca.toLocalDateTime());
            java.sql.Timestamp sea = rs.getTimestamp("status_expires_at");
            if (sea != null) e.setStatusExpiresAt(sea.toLocalDateTime());

            e.setVersion(rs.getLong("version"));

            java.sql.Timestamp gmtc = rs.getTimestamp("gmt_create");
            if (gmtc != null) e.setGmtCreate(gmtc.toLocalDateTime());
            java.sql.Timestamp gmtm = rs.getTimestamp("gmt_modified");
            if (gmtm != null) e.setGmtModified(gmtm.toLocalDateTime());

            e.setDeleted(rs.getInt("deleted"));
            return e;
        }
    }

    /**
     * 旧 order_items row → OrderItemEntity 映射。
     */
    private static class LegacyItemRowMapper implements RowMapper<OrderItemEntity> {
        @Override
        public OrderItemEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            OrderItemEntity item = new OrderItemEntity();
            item.setItemId(rs.getLong("item_id"));
            item.setOrderNo(rs.getString("order_no"));
            item.setSkuId(rs.getString("sku_id"));
            item.setSkuName(rs.getString("sku_name"));
            item.setSkuSpec(rs.getString("sku_spec"));
            item.setImageUrl(rs.getString("image_url"));
            item.setQuantity(rs.getInt("quantity"));
            item.setUnitPrice(rs.getBigDecimal("unit_price"));
            item.setTotalAmount(rs.getBigDecimal("total_amount"));
            item.setDiscountAmount(rs.getBigDecimal("discount_amount"));
            item.setPayAmount(rs.getBigDecimal("pay_amount"));
            item.setCategoryId(rs.getString("category_id"));
            item.setLineType(rs.getString("line_type"));
            item.setStatus(rs.getString("status"));

            item.setVersion(rs.getLong("version"));

            java.sql.Timestamp gmtc = rs.getTimestamp("gmt_create");
            if (gmtc != null) item.setGmtCreate(gmtc.toLocalDateTime());
            java.sql.Timestamp gmtm = rs.getTimestamp("gmt_modified");
            if (gmtm != null) item.setGmtModified(gmtm.toLocalDateTime());

            item.setDeleted(rs.getInt("deleted"));
            return item;
        }
    }

    // ===== Result DTO =====

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MigrationResult {
        private String businessType;
        private int migratedOrders;
        private int migratedItems;
        private int skippedOrders;
        private int failedOrders;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public String summary() {
            return String.format(
                    "迁移统计 [%s]: 订单 %d 条 / 商品行 %d 条 | 跳过 %d | 失败 %d | 耗时 %dms",
                    businessType, migratedOrders, migratedItems, skippedOrders, failedOrders,
                    startTime != null && endTime != null
                            ? java.time.Duration.between(startTime, endTime).toMillis() : 0);
        }
    }

}
