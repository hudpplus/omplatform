package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsInventoryEntity;
import com.omplatform.wms.entity.WmsInventoryTransactionEntity;
import com.omplatform.wms.entity.WmsLocationEntity;
import com.omplatform.wms.mapper.WmsInventoryMapper;
import com.omplatform.wms.mapper.WmsInventoryTransactionMapper;
import com.omplatform.wms.mapper.WmsLocationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 多维库存操作服务（WMS 核心）。
 * <p>
 * 提供按 (sku + 库位 + 批次) 维度的库存操作：入库上架、出库分配、库内移动、调整。
 * 每步操作写入流水并触发 {@link WmsSkuAggregator} 同步聚合库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsInventoryService {

    private final WmsInventoryMapper inventoryMapper;
    private final WmsInventoryTransactionMapper transactionMapper;
    private final WmsLocationMapper locationMapper;
    private final WmsSkuAggregator skuAggregator;

    // ========== 入库上架 ==========

    /**
     * 入库上架：在指定库位增加批次库存。
     * <p>
     * 如果该 (sku+库位+批次+状态) 已存在，则累加数量；
     * 否则新增记录。
     *
     * @return 库存记录 ID
     */
    @Transactional
    public Long receiveStock(String skuId, String warehouseCode, String locationCode,
                              String batchNo, int quantity, LocalDate expireDate,
                              String ownerCode, String opBy) {
        // 查找是否已存在该维度记录
        WmsInventoryEntity existing = inventoryMapper.selectOne(
                Wrappers.<WmsInventoryEntity>lambdaQuery()
                        .eq(WmsInventoryEntity::getSkuId, skuId)
                        .eq(WmsInventoryEntity::getWarehouseCode, warehouseCode)
                        .eq(WmsInventoryEntity::getLocationCode, locationCode)
                        .eq(WmsInventoryEntity::getBatchNo, batchNo != null ? batchNo : "")
                        .eq(WmsInventoryEntity::getInventoryStatus, "QUALIFIED")
                        .eq(WmsInventoryEntity::getOwnerCode, ownerCode != null ? ownerCode : "DEFAULT")
                        .last("LIMIT 1"));

        int beforeQty;
        if (existing != null) {
            beforeQty = existing.getQuantity();
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setLockQuantity(existing.getLockQuantity() != null ? existing.getLockQuantity() : 0);
            inventoryMapper.updateById(existing);
        } else {
            beforeQty = 0;
            existing = new WmsInventoryEntity();
            existing.setSkuId(skuId);
            existing.setWarehouseCode(warehouseCode);
            existing.setLocationCode(locationCode);
            existing.setBatchNo(batchNo);
            existing.setOwnerCode(ownerCode != null ? ownerCode : "DEFAULT");
            existing.setInventoryStatus("QUALIFIED");
            existing.setQuantity(quantity);
            existing.setLockQuantity(0);
            existing.setInboundDate(LocalDate.now());
            existing.setExpireDate(expireDate);
            inventoryMapper.insert(existing);
        }

        // 更新库位状态
        locationMapper.update(null,
                Wrappers.<WmsLocationEntity>lambdaUpdate()
                        .eq(WmsLocationEntity::getLocationCode, locationCode)
                        .set(WmsLocationEntity::getStatus, "OCCUPIED"));

        // 写流水
        writeTransaction(skuId, warehouseCode, locationCode, batchNo,
                ownerCode, null, "RECEIVE", quantity, beforeQty,
                existing.getQuantity(), opBy);

        // 同步聚合库存
        skuAggregator.syncAggregate(skuId);

        log.info("入库上架: sku={}, loc={}, batch={}, qty={}, warehouse={}",
                skuId, locationCode, batchNo, quantity, warehouseCode);
        return existing.getId();
    }

    // ========== 出库分配（锁定库存） ==========

    /**
     * 按 FEFO 策略分配库存并锁定。
     * <p>
     * 从多个库位分配所需数量，优先分配最早到期日批次。
     *
     * @param skuId         SKU
     * @param warehouseCode 仓库
     * @param requiredQty   所需数量
     * @param refNo         关联单号（出库单号）
     * @param opBy          操作人
     * @return 实际可分配数量（不足时返回小于 requiredQty）
     */
    @Transactional
    public int allocateStock(String skuId, String warehouseCode, int requiredQty,
                              String refNo, String opBy) {
        List<WmsInventoryEntity> available = inventoryMapper.findAvailableByFefo(
                skuId, warehouseCode, 50);

        int allocated = 0;
        for (WmsInventoryEntity inv : available) {
            int availQty = inv.getQuantity() - (inv.getLockQuantity() != null ? inv.getLockQuantity() : 0);
            if (availQty <= 0) continue;

            int toAllocate = Math.min(availQty, requiredQty - allocated);
            int beforeLock = inv.getLockQuantity() != null ? inv.getLockQuantity() : 0;

            // 更新锁定量
            int updated = inventoryMapper.addLock(inv.getId(), toAllocate);
            if (updated == 0) continue; // 并发冲突，跳过

            allocated += toAllocate;

            writeTransaction(skuId, warehouseCode, inv.getLocationCode(), inv.getBatchNo(),
                    inv.getOwnerCode(), refNo, "ALLOCATE", toAllocate,
                    beforeLock, beforeLock + toAllocate, opBy);

            if (allocated >= requiredQty) break;
        }

        log.info("出库分配: sku={}, warehouse={}, required={}, allocated={}",
                skuId, warehouseCode, requiredQty, allocated);
        return allocated;
    }

    // ========== 出库确认扣减 ==========

    /**
     * 确认出库扣减（发货后调用）。
     * <p>
     * 将已锁定库存实际扣减，释放锁定量。
     */
    @Transactional
    public boolean confirmShip(Long inventoryId, int quantity, String refNo, String opBy) {
        WmsInventoryEntity inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) {
            log.warn("出库确认失败: 库存记录不存在 id={}", inventoryId);
            return false;
        }

        int beforeQty = inv.getQuantity();
        int beforeLock = inv.getLockQuantity() != null ? inv.getLockQuantity() : 0;

        int updated = inventoryMapper.deductAndUnlock(inventoryId, quantity);
        if (updated == 0) {
            log.warn("出库确认失败: 锁定不足 id={}, lock={}, qty={}",
                    inventoryId, beforeLock, quantity);
            return false;
        }

        writeTransaction(inv.getSkuId(), inv.getWarehouseCode(), inv.getLocationCode(),
                inv.getBatchNo(), inv.getOwnerCode(), refNo, "SHIP", quantity,
                beforeQty, beforeQty - quantity, opBy);

        // 如果库存归零，更新库位状态为空
        int remaining = beforeQty - quantity;
        if (remaining <= 0) {
            locationMapper.update(null,
                    Wrappers.<WmsLocationEntity>lambdaUpdate()
                            .eq(WmsLocationEntity::getLocationCode, inv.getLocationCode())
                            .set(WmsLocationEntity::getStatus, "EMPTY"));
        }

        // 同步聚合库存
        skuAggregator.syncAggregate(inv.getSkuId());

        log.info("出库确认: id={}, sku={}, loc={}, qty={}", inventoryId, inv.getSkuId(),
                inv.getLocationCode(), quantity);
        return true;
    }

    // ========== 库内移动 ==========

    /**
     * 库内移动：从一个库位移到另一个库位。
     */
    @Transactional
    public boolean moveStock(Long inventoryId, String toLocationCode, int quantity, String opBy) {
        WmsInventoryEntity inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) return false;

        String fromLocation = inv.getLocationCode();
        int beforeQty = inv.getQuantity();

        // 源库位扣减
        inv.setQuantity(beforeQty - quantity);
        if (inv.getQuantity() <= 0) {
            inventoryMapper.deleteById(inventoryId);
            locationMapper.update(null,
                    Wrappers.<WmsLocationEntity>lambdaUpdate()
                            .eq(WmsLocationEntity::getLocationCode, fromLocation)
                            .set(WmsLocationEntity::getStatus, "EMPTY"));
        } else {
            inventoryMapper.updateById(inv);
        }

        // 目标库位增加（复用 receiveStock 的逻辑）
        receiveStock(inv.getSkuId(), inv.getWarehouseCode(), toLocationCode,
                inv.getBatchNo(), quantity, inv.getExpireDate(),
                inv.getOwnerCode(), opBy);

        writeTransaction(inv.getSkuId(), inv.getWarehouseCode(), fromLocation,
                inv.getBatchNo(), inv.getOwnerCode(), null, "MOVE_OUT",
                -quantity, beforeQty, inv.getQuantity(), opBy);

        log.info("库内移动: sku={}, from={}, to={}, qty={}", inv.getSkuId(),
                fromLocation, toLocationCode, quantity);
        return true;
    }

    // ========== 库存调整 ==========

    /**
     * 库存调整（盘点差异 / 运营调账）。
     *
     * @param inventoryId 库存记录 ID
     * @param newQty      调整后的数量
     * @param reason      调整原因
     */
    @Transactional
    public void adjustStock(Long inventoryId, int newQty, String reason, String opBy) {
        WmsInventoryEntity inv = inventoryMapper.selectById(inventoryId);
        if (inv == null) {
            throw new IllegalArgumentException("库存记录不存在: " + inventoryId);
        }

        int beforeQty = inv.getQuantity();
        int delta = newQty - beforeQty;
        inv.setQuantity(newQty);
        inventoryMapper.updateById(inv);

        writeTransaction(inv.getSkuId(), inv.getWarehouseCode(), inv.getLocationCode(),
                inv.getBatchNo(), inv.getOwnerCode(), null, "ADJUST",
                delta, beforeQty, newQty, opBy);

        if (newQty <= 0) {
            locationMapper.update(null,
                    Wrappers.<WmsLocationEntity>lambdaUpdate()
                            .eq(WmsLocationEntity::getLocationCode, inv.getLocationCode())
                            .set(WmsLocationEntity::getStatus, "EMPTY"));
        }

        skuAggregator.syncAggregate(inv.getSkuId());
        log.info("库存调整: id={}, {}→{}, reason={}", inventoryId, beforeQty, newQty, reason);
    }

    // ========== 查询 ==========

    /**
     * 查询 SKU 在各库位的库存明细。
     */
    public List<WmsInventoryEntity> getSkuInventory(String skuId) {
        return inventoryMapper.selectList(
                Wrappers.<WmsInventoryEntity>lambdaQuery()
                        .eq(WmsInventoryEntity::getSkuId, skuId)
                        .gt(WmsInventoryEntity::getQuantity, 0)
                        .orderByAsc(WmsInventoryEntity::getWarehouseCode,
                                WmsInventoryEntity::getLocationCode,
                                WmsInventoryEntity::getBatchNo));
    }

    /**
     * 查询库位中的库存。
     */
    public List<WmsInventoryEntity> getLocationInventory(String locationCode) {
        return inventoryMapper.findByLocation(locationCode);
    }

    /**
     * 查询 SKU 总可用量。
     */
    public int getAvailableQuantity(String skuId) {
        return inventoryMapper.aggregateAvailable(skuId);
    }

    /**
     * 查询流水。
     */
    public List<WmsInventoryTransactionEntity> queryTransactions(
            String skuId, String refNo, int limit) {
        var wrapper = Wrappers.<WmsInventoryTransactionEntity>lambdaQuery();
        if (skuId != null) wrapper.eq(WmsInventoryTransactionEntity::getSkuId, skuId);
        if (refNo != null) wrapper.eq(WmsInventoryTransactionEntity::getRefNo, refNo);
        wrapper.orderByDesc(WmsInventoryTransactionEntity::getGmtCreate)
                .last("LIMIT " + limit);
        return transactionMapper.selectList(wrapper);
    }

    // ========== 内部 ==========

    private void writeTransaction(String skuId, String warehouseCode, String locationCode,
                                   String batchNo, String ownerCode, String refNo,
                                   String opType, int quantity, int beforeQty, int afterQty,
                                   String opBy) {
        try {
            WmsInventoryTransactionEntity txn = new WmsInventoryTransactionEntity();
            txn.setTransactionNo("WMS_" + UUID.randomUUID().toString().substring(0, 16).toUpperCase());
            txn.setSkuId(skuId);
            txn.setWarehouseCode(warehouseCode);
            txn.setLocationCode(locationCode);
            txn.setBatchNo(batchNo);
            txn.setOwnerCode(ownerCode);
            txn.setRefNo(refNo);
            txn.setRefType(refNo != null ? deriveRefType(refNo) : null);
            txn.setOpType(opType);
            txn.setQuantity(quantity);
            txn.setBeforeQty(beforeQty);
            txn.setAfterQty(afterQty);
            txn.setOpBy(opBy);
            txn.setGmtCreate(LocalDateTime.now());
            transactionMapper.insert(txn);
        } catch (Exception e) {
            log.warn("写入 WMS 流水失败（不影响主流程）: sku={}, op={}, err={}",
                    skuId, opType, e.getMessage());
        }
    }

    private String deriveRefType(String refNo) {
        if (refNo == null) return null;
        if (refNo.startsWith("ASN")) return "ASN";
        if (refNo.startsWith("OB")) return "OUTBOUND";
        if (refNo.startsWith("CNT")) return "COUNT";
        if (refNo.startsWith("MV")) return "MOVE";
        return "ADJUST";
    }
}
