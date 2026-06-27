package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsAllocationEntity;
import com.omplatform.wms.entity.WmsInventoryEntity;
import com.omplatform.wms.entity.WmsOutboundItemEntity;
import com.omplatform.wms.mapper.WmsAllocationMapper;
import com.omplatform.wms.mapper.WmsInventoryMapper;
import com.omplatform.wms.mapper.WmsOutboundItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 出库分配策略（FEFO + 按库位序）。
 * <p>
 * 核心职责：
 * <ol>
 *   <li>按 FEFO（First Expire First Out）扫描可用库存</li>
 *   <li>调用 {@link WmsInventoryMapper#addLock} 锁定库存</li>
 *   <li>创建 {@link WmsAllocationEntity} 分配记录</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsAllocationStrategy {

    private final WmsInventoryMapper inventoryMapper;
    private final WmsAllocationMapper allocationMapper;
    private final WmsOutboundItemMapper outboundItemMapper;

    /**
     * 为一个 SKU 分配库存（FEFO）。
     *
     * @param outboundNo   出库单号
     * @param itemId       出库单明细 ID
     * @param skuId        SKU
     * @param warehouseCode 仓库
     * @param requiredQty  所需数量
     * @param opBy         操作人
     * @return 分配记录列表（可能不足）
     */
    @Transactional
    public List<WmsAllocationEntity> allocate(String outboundNo, Long itemId, String skuId,
                                               String warehouseCode, int requiredQty, String opBy) {
        // 1. FEFO 扫描可用库存
        List<WmsInventoryEntity> available = inventoryMapper.findAvailableByFefo(skuId, warehouseCode, 50);
        if (available.isEmpty()) {
            log.warn("分配库存不足: sku={}, warehouse={}, required={}", skuId, warehouseCode, requiredQty);
            return List.of();
        }

        // 2. 逐个库位分配
        int allocated = 0;
        List<WmsAllocationEntity> results = new ArrayList<>();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (WmsInventoryEntity inv : available) {
            int availQty = inv.getQuantity() - (inv.getLockQuantity() != null ? inv.getLockQuantity() : 0);
            if (availQty <= 0) continue;

            int toAllocate = Math.min(availQty, requiredQty - allocated);

            // 乐观锁更新库存锁定
            int updated = inventoryMapper.addLock(inv.getId(), toAllocate);
            if (updated == 0) continue; // 并发冲突，跳过

            // 创建分配记录
            WmsAllocationEntity alloc = new WmsAllocationEntity();
            alloc.setAllocationNo("ALC" + today + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            alloc.setOutboundNo(outboundNo);
            alloc.setItemId(itemId);
            alloc.setSkuId(skuId);
            alloc.setWarehouseCode(warehouseCode);
            alloc.setLocationCode(inv.getLocationCode());
            alloc.setBatchNo(inv.getBatchNo());
            alloc.setOwnerCode(inv.getOwnerCode());
            alloc.setInventoryId(inv.getId());
            alloc.setAllocatedQty(toAllocate);
            alloc.setPickedQty(0);
            alloc.setStatus("ALLOCATED");
            allocationMapper.insert(alloc);
            results.add(alloc);

            allocated += toAllocate;
            if (allocated >= requiredQty) break;
        }

        // 3. 更新出库单明细的已分配数量
        outboundItemMapper.update(null,
                Wrappers.<WmsOutboundItemEntity>lambdaUpdate()
                        .eq(WmsOutboundItemEntity::getId, itemId)
                        .set(WmsOutboundItemEntity::getAllocatedQty, allocated));

        log.info("分配完成: sku={}, outbound={}, required={}, allocated={}",
                skuId, outboundNo, requiredQty, allocated);
        return results;
    }

    /**
     * 取消分配：释放所有已锁定库存。
     *
     * @param outboundNo 出库单号
     */
    @Transactional
    public void cancelAllocate(String outboundNo) {
        List<WmsAllocationEntity> allocations = allocationMapper.findByOutboundNo(outboundNo);
        for (WmsAllocationEntity alloc : allocations) {
            if (!"ALLOCATED".equals(alloc.getStatus()) && !"CANCELLED".equals(alloc.getStatus())) {
                continue;
            }
            // 释放库存锁定量
            inventoryMapper.addLock(alloc.getInventoryId(), -alloc.getAllocatedQty());
            alloc.setStatus("CANCELLED");
            allocationMapper.updateById(alloc);
        }
        log.info("分配已取消: outbound={}, count={}", outboundNo, allocations.size());
    }
}
