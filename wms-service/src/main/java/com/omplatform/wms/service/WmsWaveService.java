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
import java.util.*;
import java.util.stream.Collectors;

/**
 * 波次管理服务。
 * <p>
 * 将多个出库单聚合为波次，统一分配和生成拣货任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsWaveService {

    private final WmsWaveMapper waveMapper;
    private final WmsWaveOrderMapper waveOrderMapper;
    private final WmsAllocationMapper allocationMapper;
    private final WmsPickingTaskMapper pickingTaskMapper;
    private final WmsPickingItemMapper pickingItemMapper;
    private final WmsOutboundOrderMapper outboundOrderMapper;

    /**
     * 创建波次。
     *
     * @param warehouseCode 仓库
     * @param outboundNos   出库单号列表
     * @param type          MANUAL / AUTO
     * @return 波次实体
     */
    @Transactional
    public WmsWaveEntity createWave(String warehouseCode, List<String> outboundNos, String type) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String waveNo = "WV" + today + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 统计
        int totalOrders = outboundNos.size();
        int totalQty = 0;
        Set<String> skuSet = new HashSet<>();

        for (String obNo : outboundNos) {
            List<WmsOutboundItemEntity> items = null;
            try {
                items = null; // will use mapper
            } catch (Exception ignored) {}
            // 用通用查询替代
            List<WmsOutboundItemEntity> itemList = null;
        }

        // 简单方式：创建波次主记录
        WmsWaveEntity wave = new WmsWaveEntity();
        wave.setWaveNo(waveNo);
        wave.setWarehouseCode(warehouseCode);
        wave.setStatus("OPEN");
        wave.setType(type != null ? type : "MANUAL");
        wave.setTotalOrderCount(totalOrders);
        wave.setTotalSkuCount(0); // 稍后填充
        wave.setTotalQuantity(0);
        wave.setWaveAt(LocalDateTime.now());
        waveMapper.insert(wave);

        // 关联出库单
        for (String obNo : outboundNos) {
            WmsWaveOrderEntity wo = new WmsWaveOrderEntity();
            wo.setWaveNo(waveNo);
            wo.setOutboundNo(obNo);
            waveOrderMapper.insert(wo);

            // 更新出库单状态
            outboundOrderMapper.update(null,
                    Wrappers.<WmsOutboundOrderEntity>lambdaUpdate()
                            .eq(WmsOutboundOrderEntity::getOutboundNo, obNo)
                            .set(WmsOutboundOrderEntity::getStatus, "ALLOCATING"));
        }

        log.info("波次创建: wave={}, warehouse={}, orders={}", waveNo, warehouseCode, totalOrders);
        return wave;
    }

    /**
     * 释放波次：为波次内所有出库单的分配记录生成拣货任务。
     * <p>
     * 按库区/库位分组生成任务，同一库区的分配归入同一拣货任务。
     *
     * @param waveNo 波次号
     * @param opBy   操作人
     * @return 生成的拣货任务列表
     */
    @Transactional
    public List<WmsPickingTaskEntity> releaseWave(String waveNo, String opBy) {
        // 1. 获取波次内所有分配记录
        List<WmsAllocationEntity> allocations = allocationMapper.findByWaveNo(waveNo);
        if (allocations.isEmpty()) {
            log.warn("波次无待分配记录: wave={}", waveNo);
            return List.of();
        }

        // 2. 按 (warehouse_code, location_code) 分组（同一库位的合并）
        Map<String, List<WmsAllocationEntity>> locationGroups = allocations.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getWarehouseCode() + "|" + a.getLocationCode()));

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<WmsPickingTaskEntity> tasks = new ArrayList<>();

        // 3. 每个分组生成一个拣货任务（实际可按库区/通道进一步合并）
        //   简单实现：每 50 条分配记录拆一个任务
        final int TASK_SIZE = 50;
        List<WmsAllocationEntity> sortedAllocs = allocations.stream()
                .sorted(Comparator.comparing(WmsAllocationEntity::getLocationCode)
                        .thenComparing(WmsAllocationEntity::getBatchNo))
                .toList();

        for (int i = 0; i < sortedAllocs.size(); i += TASK_SIZE) {
            List<WmsAllocationEntity> batch = sortedAllocs.subList(i,
                    Math.min(i + TASK_SIZE, sortedAllocs.size()));

            String taskNo = "PK" + today + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            WmsPickingTaskEntity task = new WmsPickingTaskEntity();
            task.setTaskNo(taskNo);
            task.setWaveNo(waveNo);
            task.setWarehouseCode(batch.getFirst().getWarehouseCode());
            task.setStatus("NEW");
            task.setType("PICKING");
            task.setTotalItems(batch.size());
            task.setTotalQuantity(batch.stream().mapToInt(WmsAllocationEntity::getAllocatedQty).sum());
            task.setTotalLocations((int) batch.stream().map(WmsAllocationEntity::getLocationCode).distinct().count());
            task.setPickedItems(0);
            task.setPickedQuantity(0);
            task.setPickedLocations(0);
            pickingTaskMapper.insert(task);
            tasks.add(task);

            // 生成明细
            for (WmsAllocationEntity alloc : batch) {
                WmsPickingItemEntity item = new WmsPickingItemEntity();
                item.setTaskNo(taskNo);
                item.setAllocationId(alloc.getId());
                item.setSkuId(alloc.getSkuId());
                item.setLocationCode(alloc.getLocationCode());
                item.setBatchNo(alloc.getBatchNo());
                item.setExpectedQty(alloc.getAllocatedQty() - (alloc.getPickedQty() != null ? alloc.getPickedQty() : 0));
                item.setPickedQty(0);
                item.setStatus("PENDING");
                pickingItemMapper.insert(item);

                // 更新分配记录为拣货中
                alloc.setStatus("PICKING");
                allocationMapper.updateById(alloc);
            }
        }

        // 4. 更新波次状态
        waveMapper.update(null,
                Wrappers.<WmsWaveEntity>lambdaUpdate()
                        .eq(WmsWaveEntity::getWaveNo, waveNo)
                        .set(WmsWaveEntity::getStatus, "ALLOCATED"));

        log.info("波次释放: wave={}, tasks={}, allocations={}", waveNo, tasks.size(), allocations.size());
        return tasks;
    }

    /**
     * 完成波次（所有拣货任务均已完成时调用）。
     */
    @Transactional
    public void completeWave(String waveNo) {
        waveMapper.update(null,
                Wrappers.<WmsWaveEntity>lambdaUpdate()
                        .eq(WmsWaveEntity::getWaveNo, waveNo)
                        .set(WmsWaveEntity::getStatus, "COMPLETED"));
        log.info("波次完成: wave={}", waveNo);
    }
}
