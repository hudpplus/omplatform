package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsAllocationEntity;
import com.omplatform.wms.entity.WmsInventoryEntity;
import com.omplatform.wms.entity.WmsOutboundOrderEntity;
import com.omplatform.wms.entity.WmsOutboundItemEntity;
import com.omplatform.wms.mapper.WmsAllocationMapper;
import com.omplatform.wms.mapper.WmsInventoryMapper;
import com.omplatform.wms.mapper.WmsOutboundItemMapper;
import com.omplatform.wms.mapper.WmsOutboundOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货管理服务。
 * <p>
 * 出库流程的最终环节：确认发货、扣减库存、更新聚合库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsShippingService {

    private final WmsOutboundOrderMapper outboundOrderMapper;
    private final WmsOutboundItemMapper outboundItemMapper;
    private final WmsAllocationMapper allocationMapper;
    private final WmsInventoryMapper inventoryMapper;
    private final WmsSkuAggregator skuAggregator;

    /**
     * 发货确认。
     * <p>
     * 对整个出库单执行发货操作：
     * <ol>
     *   <li>扣减每个分配记录对应的锁定库存（deductAndUnlock）</li>
     *   <li>更新分配记录状态为 SHIPPED</li>
     *   <li>更新出库单明细的发货数量</li>
     *   <li>更新出库单状态为 SHIPPED + 物流信息</li>
     *   <li>同步聚合库存</li>
     * </ol>
     *
     * @param outboundNo     出库单号
     * @param logisticsCompany 物流公司
     * @param logisticsNo    物流单号
     * @param opBy           操作人
     * @return true=发货成功
     */
    @Transactional
    public boolean ship(String outboundNo, String logisticsCompany, String logisticsNo, String opBy) {
        // 1. 查询所有分配记录
        List<WmsAllocationEntity> allocations = allocationMapper.findByOutboundNo(outboundNo);
        if (allocations.isEmpty()) {
            log.warn("发货失败：出库单无分配记录 outbound={}", outboundNo);
            return false;
        }

        // 2. 逐条扣减库存
        for (WmsAllocationEntity alloc : allocations) {
            if (!"ALLOCATED".equals(alloc.getStatus()) && !"PICKED".equals(alloc.getStatus())) {
                continue;
            }
            int shipQty = alloc.getAllocatedQty() - (alloc.getPickedQty() != null ? alloc.getPickedQty() : 0);
            if (shipQty <= 0) continue;

            // 调用 inventory.confirmShip 语义：deductAndUnlock
            int updated = inventoryMapper.deductAndUnlock(alloc.getInventoryId(), shipQty);
            if (updated == 0) {
                log.warn("发货扣减失败: allocId={}, inventoryId={}, qty={}",
                        alloc.getId(), alloc.getInventoryId(), shipQty);
            }

            // 更新分配记录状态
            alloc.setStatus("SHIPPED");
            allocationMapper.updateById(alloc);

            // 同步每个涉及 SKU 的聚合库存
            skuAggregator.syncAggregate(alloc.getSkuId());
        }

        // 3. 更新出库单明细的发货数量
        List<WmsOutboundItemEntity> items = outboundItemMapper.selectList(
                Wrappers.<WmsOutboundItemEntity>lambdaQuery()
                        .eq(WmsOutboundItemEntity::getOutboundNo, outboundNo));
        for (WmsOutboundItemEntity item : items) {
            int shippedQty = allocations.stream()
                    .filter(a -> item.getId().equals(a.getItemId()))
                    .mapToInt(a -> a.getAllocatedQty())
                    .sum();
            item.setShippedQty(shippedQty);
            outboundItemMapper.updateById(item);
        }

        // 4. 更新出库单状态
        LocalDateTime now = LocalDateTime.now();
        outboundOrderMapper.updateShipStatus(outboundNo, "SHIPPED", now);

        // 更新物流信息
        outboundOrderMapper.update(null,
                Wrappers.<WmsOutboundOrderEntity>lambdaUpdate()
                        .eq(WmsOutboundOrderEntity::getOutboundNo, outboundNo)
                        .set(WmsOutboundOrderEntity::getLogisticsCompany, logisticsCompany)
                        .set(WmsOutboundOrderEntity::getLogisticsNo, logisticsNo)
                        .set(WmsOutboundOrderEntity::getShippedAt, now));

        log.info("发货完成: outbound={}, logistics={}/{}, allocations={}",
                outboundNo, logisticsCompany, logisticsNo, allocations.size());
        return true;
    }

    /**
     * 查询出库单的物流状态。
     */
    public WmsOutboundOrderEntity getShippingStatus(String outboundNo) {
        return outboundOrderMapper.selectById(outboundNo);
    }
}
