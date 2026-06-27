package com.omplatform.fulfillment.inventory;

import com.omplatform.fulfillment.inventory.lua.InventoryLuaEngine;
import com.omplatform.fulfillment.inventory.lua.LuaResult;
import com.omplatform.fulfillment.repository.InventoryHoldMapper;
import com.omplatform.fulfillment.repository.entity.InventoryHoldEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时预占自动释放任务（ADR-043 §14.1）。
 * <p>
 * 每 5 分钟扫描 {@code inventory_hold} 中 {@code status='RESERVED' AND expire_at < NOW()} 的记录，
 * 调用 Redis Lua release_hold 将库存归还，并更新 DB 状态为 TIMEOUT。
 * <p>
 * 兜底机制：预占 Lua 中 hold_key 和 hold_detail_key 都有 TTL，
 * 但 Redis TTL 过期不会自动将 reserved → available，所以需要此 Job 做有损恢复。
 * <p>
 * RocketMQ 15min 延迟消息作为辅助兜底（当前未实现，仅依赖此 Job）。
 */
@Slf4j
@Component
public class HoldReleaseJob {

    @Autowired
    private InventoryLuaEngine luaEngine;

    @Autowired
    private InventoryHoldMapper holdMapper;

    @Autowired(required = false)
    private InventoryEventPublisher eventPublisher;

    /** 每批处理数 */
    private static final int BATCH_SIZE = 100;

    /**
     * Scan expired holds every 5 minutes.
     */
    @Scheduled(fixedRate = 300_000)
    public void releaseExpiredHolds() {
        log.debug("[HoldRelease] 开始扫描超时预占记录");

        try {
            List<InventoryHoldEntity> expiredHolds = findExpiredHolds(BATCH_SIZE);
            if (expiredHolds.isEmpty()) {
                return;
            }

            log.info("[HoldRelease] 发现 {} 笔超时预占待释放", expiredHolds.size());

            int released = 0;
            int failed = 0;

            for (InventoryHoldEntity hold : expiredHolds) {
                try {
                    // 调用 Redis Lua 释放
                    LuaResult r = luaEngine.releaseHold(
                            hold.getSkuId(), hold.getHoldId(),
                            hold.getQuantity(), hold.getRequestId());

                    if (r.isSuccess()) {
                        // 更新 DB 状态为 TIMEOUT
                        hold.setStatus("TIMEOUT");
                        hold.setReleasedAt(LocalDateTime.now());
                        holdMapper.updateById(hold);
                        released++;

                        // 发布超时事件
                        if (eventPublisher != null) {
                            eventPublisher.holdTimeout(hold.getSkuId(), hold.getHoldId(),
                                    hold.getQuantity(), hold.getOrderNo());
                        }

                        log.info("[HoldRelease] 释放超时预占: holdId={}, skuId={}, qty={}, order={}",
                                hold.getHoldId(), hold.getSkuId(), hold.getQuantity(), hold.getOrderNo());
                    } else {
                        failed++;
                        log.warn("[HoldRelease] Lua 释放失败: holdId={}, code={}, msg={}",
                                hold.getHoldId(), r.code(), r.message());
                        // 更新重试计数
                        hold.setRetryCount(hold.getRetryCount() != null ? hold.getRetryCount() + 1 : 1);
                        hold.setLastError(r.message());
                        holdMapper.updateById(hold);
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("[HoldRelease] 释放异常: holdId={}, err={}", hold.getHoldId(), e.getMessage());
                    hold.setRetryCount(hold.getRetryCount() != null ? hold.getRetryCount() + 1 : 1);
                    hold.setLastError(e.getMessage());
                    holdMapper.updateById(hold);
                }
            }

            log.info("[HoldRelease] 扫描完成: 总超时={}, 已释放={}, 失败={}",
                    expiredHolds.size(), released, failed);
        } catch (Exception e) {
            log.error("[HoldRelease] 扫描异常: {}", e.getMessage(), e);
        }
    }

    private List<InventoryHoldEntity> findExpiredHolds(int batchSize) {
        return holdMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryHoldEntity>()
                        .eq(InventoryHoldEntity::getStatus, "RESERVED")
                        .lt(InventoryHoldEntity::getExpireAt, LocalDateTime.now())
                        .last("LIMIT " + batchSize));
    }
}
