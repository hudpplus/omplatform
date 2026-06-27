package com.omplatform.wms.service;

import com.omplatform.wms.mapper.WmsInventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * WMS → 聚合库存 双向同步桥梁（关键组件）。
 * <p>
 * 职责：
 * <ol>
 *   <li><b>实时同步</b>：WMS 每次写操作后调用 {@link #syncAggregate(String)}，将 WMS 细粒度库存聚合为 {@code stock:{sku}:available}</li>
 *   <li><b>定时兜底</b>：每 5 分钟全量扫描，确保聚合值与 WMS 一致</li>
 * </ol>
 * <p>
 * 这样订单系统的 {@code InventoryService}（oms-fulfillment）无需任何修改，
 * 继续使用 {@code stock:{sku}:available} 做预占/确认/释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsSkuAggregator {

    private final WmsInventoryMapper inventoryMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 同步单个 SKU 的聚合库存。
     * <p>
     * WMS 每次入库/出库/移动/调整操作后必须调用此方法。
     *
     * @param skuId SKU
     */
    public void syncAggregate(String skuId) {
        try {
            // 1. 从 wms_inventory 聚合合格品可用量（quantity - lock_quantity）
            int available = inventoryMapper.aggregateAvailable(skuId);

            // 2. 同步到 Redis（供订单系统 InventoryService 使用）
            redisTemplate.opsForValue().set(
                    "stock:" + skuId + ":available",
                    String.valueOf(available));

            log.debug("聚合库存已同步: sku={}, available={}", skuId, available);
        } catch (Exception e) {
            log.error("聚合库存同步失败: sku={}, err={}", skuId, e.getMessage());
        }
    }

    /**
     * 查询单个 SKU 的聚合可用量（直接读 Redis）。
     */
    public int getAggregatedAvailable(String skuId) {
        String val = redisTemplate.opsForValue().get("stock:" + skuId + ":available");
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 定时兜底：每 5 分钟扫描所有有库存的 SKU，同步聚合值。
     * <p>
     * 防止实时同步因异常遗漏导致聚合层与 WMS 不一致。
     */
    @Scheduled(fixedRate = 300_000)
    public void scheduledSync() {
        log.debug("[聚合同步] 开始定时同步");

        try {
            // 全量扫描所有有库存的 SKU（去重）
            Set<String> skuIds = inventoryMapper.selectList(null).stream()
                    .map(com.omplatform.wms.entity.WmsInventoryEntity::getSkuId)
                    .collect(java.util.stream.Collectors.toSet());

            if (skuIds.isEmpty()) {
                return;
            }

            log.info("[聚合同步] 同步 {} 个 SKU 的聚合库存", skuIds.size());
            for (String skuId : skuIds) {
                syncAggregate(skuId);
            }

            log.info("[聚合同步] 完成: count={}", skuIds.size());
        } catch (Exception e) {
            log.error("[聚合同步] 异常: {}", e.getMessage(), e);
        }
    }
}
