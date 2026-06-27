package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.*;
import com.omplatform.wms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 出库主流程编排服务。
 * <p>
 * 整合 {@link WmsAllocationStrategy}、{@link WmsWaveService}、
 * {@link WmsPickingService}、{@link WmsPackingService}、{@link WmsShippingService}，
 * 对外提供完整的出库流程 API。
 * <p>
 * 标准流程:
 * <pre>
 * 创建出库单 → 分配库存 → 创建波次 → 释放波次(生成拣货任务) → 拣货确认 → 打包 → 发货
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsOutboundService {

    private final WmsOutboundOrderMapper outboundOrderMapper;
    private final WmsOutboundItemMapper outboundItemMapper;
    private final WmsAllocationMapper allocationMapper;
    private final WmsPickingTaskMapper pickingTaskMapper;
    private final WmsPickingItemMapper pickingItemMapper;
    private final WmsWaveMapper waveMapper;
    private final WmsWaveOrderMapper waveOrderMapper;
    private final WmsPackingMapper packingMapper;

    private final WmsAllocationStrategy allocationStrategy;
    private final WmsWaveService waveService;
    private final WmsPickingService pickingService;
    private final WmsPackingService packingService;
    private final WmsShippingService shippingService;

    // ========================================================================
    // 出库单管理
    // ========================================================================

    /**
     * 创建出库单。
     *
     * @param order 出库单主表信息
     * @param items 商品明细
     * @return 出库单号
     */
    @Transactional
    public String createOutboundOrder(WmsOutboundOrderEntity order, List<WmsOutboundItemEntity> items) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String outboundNo = "OB" + today + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        order.setOutboundNo(outboundNo);
        order.setStatus("NEW");
        if (order.getPriority() == null) order.setPriority(5);
        outboundOrderMapper.insert(order);

        for (WmsOutboundItemEntity item : items) {
            item.setOutboundNo(outboundNo);
            item.setAllocatedQty(0);
            item.setPickedQty(0);
            item.setShippedQty(0);
            outboundItemMapper.insert(item);
        }

        // 汇总统计
        order.setTotalSkuCount((int) items.stream().map(WmsOutboundItemEntity::getSkuId).distinct().count());
        order.setTotalQuantity(items.stream().mapToInt(WmsOutboundItemEntity::getExpectedQty).sum());
        outboundOrderMapper.updateById(order);

        log.info("出库单创建: outbound={}, items={}, totalQty={}", outboundNo, items.size(), order.getTotalQuantity());
        return outboundNo;
    }

    /**
     * 获取出库单。
     */
    public WmsOutboundOrderEntity getOutboundOrder(String outboundNo) {
        return outboundOrderMapper.selectById(outboundNo);
    }

    /**
     * 获取出库单明细。
     */
    public List<WmsOutboundItemEntity> getOutboundItems(String outboundNo) {
        return outboundItemMapper.selectList(
                Wrappers.<WmsOutboundItemEntity>lambdaQuery()
                        .eq(WmsOutboundItemEntity::getOutboundNo, outboundNo));
    }

    // ========================================================================
    // 分配库存
    // ========================================================================

    /**
     * 为出库单分配库存（FEFO 策略）。
     * <p>
     * 遍历出库单的所有商品明细，逐个调用 {@link WmsAllocationStrategy#allocate}。
     *
     * @param outboundNo 出库单号
     * @param opBy       操作人
     */
    @Transactional
    public void allocate(String outboundNo, String opBy) {
        WmsOutboundOrderEntity order = outboundOrderMapper.selectById(outboundNo);
        if (order == null) {
            throw new IllegalArgumentException("出库单不存在: " + outboundNo);
        }

        // 更新状态
        order.setStatus("ALLOCATING");
        outboundOrderMapper.updateById(order);

        // 逐个 SKU 分配
        List<WmsOutboundItemEntity> items = getOutboundItems(outboundNo);
        int totalAllocated = 0;
        boolean allFullMatch = true;

        for (WmsOutboundItemEntity item : items) {
            List<WmsAllocationEntity> allocs = allocationStrategy.allocate(
                    outboundNo, item.getId(), item.getSkuId(),
                    order.getWarehouseCode(), item.getExpectedQty(), opBy);
            int allocated = allocs.stream().mapToInt(WmsAllocationEntity::getAllocatedQty).sum();
            totalAllocated += allocated;
            if (allocated < item.getExpectedQty()) {
                allFullMatch = false;
            }
        }

        // 更新出库单状态
        order.setStatus(allFullMatch ? "ALLOCATED" : "ALLOCATED");
        order.setTotalQuantity(totalAllocated);
        outboundOrderMapper.updateById(order);

        log.info("出库分配完成: outbound={}, allFullMatch={}", outboundNo, allFullMatch);
    }

    // ========================================================================
    // 波次管理
    // ========================================================================

    /**
     * 创建波次并关联出库单。
     */
    @Transactional
    public WmsWaveEntity createWave(String warehouseCode, List<String> outboundNos, String type) {
        return waveService.createWave(warehouseCode, outboundNos, type);
    }

    /**
     * 释放波次（生成拣货任务）。
     */
    @Transactional
    public List<WmsPickingTaskEntity> releaseWave(String waveNo, String opBy) {
        return waveService.releaseWave(waveNo, opBy);
    }

    // ========================================================================
    // 拣货
    // ========================================================================

    /**
     * 拣货确认。
     */
    @Transactional
    public boolean confirmPick(Long pickingItemId, int quantity, String picker) {
        return pickingService.confirmPick(pickingItemId, quantity, picker);
    }

    /**
     * 获取拣货任务。
     */
    public WmsPickingTaskEntity getPickingTask(String taskNo) {
        return pickingService.getTask(taskNo);
    }

    /**
     * 获取拣货任务明细。
     */
    public List<WmsPickingItemEntity> getPickingItems(String taskNo) {
        return pickingService.getTaskItems(taskNo);
    }

    // ========================================================================
    // 打包
    // ========================================================================

    /**
     * 打包确认。
     */
    @Transactional
    public WmsPackingEntity pack(String outboundNo, String taskNo, String packageNo,
                                  String skuId, String skuName, int quantity,
                                  java.math.BigDecimal weight, java.math.BigDecimal length,
                                  java.math.BigDecimal width, java.math.BigDecimal height,
                                  String operator) {
        return packingService.pack(outboundNo, taskNo, packageNo, skuId, skuName,
                quantity, weight, length, width, height, "BOX", operator);
    }

    // ========================================================================
    // 发货
    // ========================================================================

    /**
     * 发货确认。
     */
    @Transactional
    public boolean ship(String outboundNo, String logisticsCompany, String logisticsNo, String opBy) {
        return shippingService.ship(outboundNo, logisticsCompany, logisticsNo, opBy);
    }

    // ========================================================================
    // 查询
    // ========================================================================

    /**
     * 查询出库单的所有分配记录。
     */
    public List<WmsAllocationEntity> getAllocations(String outboundNo) {
        return allocationMapper.findByOutboundNo(outboundNo);
    }

    /**
     * 查询波次详情。
     */
    public WmsWaveEntity getWave(String waveNo) {
        return waveMapper.selectById(waveNo);
    }

    /**
     * 查询波次关联的出库单号。
     */
    public List<String> getWaveOutboundNos(String waveNo) {
        return waveOrderMapper.findOutboundNosByWaveNo(waveNo);
    }

    /**
     * 查询出库单的包裹信息。
     */
    public List<WmsPackingEntity> getPackings(String outboundNo) {
        return packingService.getPackingByOutbound(outboundNo);
    }

    /**
     * 查询待分配的出库单。
     */
    public List<WmsOutboundOrderEntity> findPendingAllocate(String warehouseCode) {
        return outboundOrderMapper.findPendingAllocate(warehouseCode);
    }

    /**
     * 取消出库单（释放已分配库存）。
     */
    @Transactional
    public void cancelOutbound(String outboundNo, String reason) {
        allocationStrategy.cancelAllocate(outboundNo);
        outboundOrderMapper.update(null,
                Wrappers.<WmsOutboundOrderEntity>lambdaUpdate()
                        .eq(WmsOutboundOrderEntity::getOutboundNo, outboundNo)
                        .set(WmsOutboundOrderEntity::getStatus, "CANCELLED")
                        .set(WmsOutboundOrderEntity::getCancelReason, reason));
        log.info("出库单取消: outbound={}, reason={}", outboundNo, reason);
    }
}
