package com.omplatform.wms.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.wms.entity.WmsAllocationEntity;
import com.omplatform.wms.entity.WmsPickingItemEntity;
import com.omplatform.wms.entity.WmsPickingTaskEntity;
import com.omplatform.wms.mapper.WmsAllocationMapper;
import com.omplatform.wms.mapper.WmsPickingItemMapper;
import com.omplatform.wms.mapper.WmsPickingTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拣货管理服务。
 * <p>
 * 负责拣货任务的执行与确认。支持逐行拣货、跳过（缺货）等操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsPickingService {

    private final WmsPickingTaskMapper pickingTaskMapper;
    private final WmsPickingItemMapper pickingItemMapper;
    private final WmsAllocationMapper allocationMapper;

    /**
     * 拣货确认：拣取指定数量的商品。
     * <p>
     * 同时更新：
     * <ol>
     *   <li>{@link WmsPickingItemEntity} — 已拣数量 + 状态</li>
     *   <li>{@link WmsAllocationEntity} — 已拣数量 + 状态</li>
     *   <li>{@link WmsPickingTaskEntity} — 进度统计</li>
     * </ol>
     *
     * @param pickingItemId 拣货明细 ID
     * @param quantity      本次拣取数量
     * @param picker        拣货员
     * @return true=确认成功
     */
    @Transactional
    public boolean confirmPick(Long pickingItemId, int quantity, String picker) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 更新拣货明细
        int updated = pickingItemMapper.confirmPick(pickingItemId, quantity, picker, now);
        if (updated == 0) {
            log.warn("拣货确认失败: itemId={}, qty={}", pickingItemId, quantity);
            return false;
        }

        WmsPickingItemEntity item = pickingItemMapper.selectById(pickingItemId);
        if (item == null) return false;

        // 2. 更新分配记录
        allocationMapper.addPickedQty(item.getAllocationId(), quantity);

        // 3. 更新拣货任务进度
        boolean itemCompleted = item.getPickedQty() + quantity >= item.getExpectedQty();
        pickingTaskMapper.updatePickProgress(
                item.getTaskNo(),
                itemCompleted ? 1 : 0,
                quantity,
                now);

        log.info("拣货确认: itemId={}, sku={}, loc={}, qty={}, picker={}",
                pickingItemId, item.getSkuId(), item.getLocationCode(), quantity, picker);
        return true;
    }

    /**
     * 跳过拣货（缺货标记）。
     */
    @Transactional
    public boolean skipItem(Long pickingItemId, String reason) {
        WmsPickingItemEntity item = pickingItemMapper.selectById(pickingItemId);
        if (item == null) return false;

        item.setStatus("SKIPPED");
        pickingItemMapper.updateById(item);
        log.warn("拣货跳过: itemId={}, sku={}, reason={}", pickingItemId, item.getSkuId(), reason);
        return true;
    }

    /**
     * 获取拣货任务详情。
     */
    public WmsPickingTaskEntity getTask(String taskNo) {
        return pickingTaskMapper.selectById(taskNo);
    }

    /**
     * 获取拣货任务明细列表。
     */
    public List<WmsPickingItemEntity> getTaskItems(String taskNo) {
        return pickingItemMapper.findByTaskNo(taskNo);
    }

    /**
     * 查询拣货员的待处理任务。
     */
    public List<WmsPickingTaskEntity> getAssigneeTasks(String assignee) {
        return pickingTaskMapper.selectList(
                Wrappers.<WmsPickingTaskEntity>lambdaQuery()
                        .eq(WmsPickingTaskEntity::getAssignee, assignee)
                        .in(WmsPickingTaskEntity::getStatus, "NEW", "PICKING", "PARTIALLY_PICKED")
                        .orderByAsc(WmsPickingTaskEntity::getGmtCreate));
    }
}
