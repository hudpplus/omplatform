package com.omplatform.fulfillment.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.fulfillment.inventory.lua.InventoryLuaEngine;
import com.omplatform.fulfillment.inventory.lua.LuaResult;
import com.omplatform.fulfillment.inventory.lua.ReserveResult;
import com.omplatform.fulfillment.repository.InventoryHoldMapper;
import com.omplatform.fulfillment.repository.InventoryMapper;
import com.omplatform.fulfillment.repository.InventoryTransactionMapper;
import com.omplatform.fulfillment.repository.entity.InventoryEntity;
import com.omplatform.fulfillment.repository.entity.InventoryHoldEntity;
import com.omplatform.fulfillment.repository.entity.InventoryTransactionEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存服务（ADR-043）。
 * <p>
 * 基于 Redis Lua 原子脚本的两阶段预占协议：
 * <ol>
 *   <li>reserve（预占）— 下单时，available → reserved</li>
 *   <li>confirmDeduct（确认扣减）— 支付后，reserved → deducted</li>
 *   <li>releaseHold（释放预占）— 取消订单，reserved → available</li>
 *   <li>undoDeduct（撤销扣减）— 退款，deducted → available</li>
 * </ol>
 * <p>
 * 预占记录同时持久化到 DB（inventory_hold），用于对账和 HoldReleaseJob 扫描。
 * 每笔操作写入 inventory_transaction 流水，并发布 RocketMQ 事件。
 */
@Slf4j
@Service
public class InventoryService {

    @Autowired
    private InventoryLuaEngine luaEngine;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryHoldMapper holdMapper;

    @Autowired(required = false)
    private InventoryTransactionMapper transactionMapper;

    @Autowired(required = false)
    private InventoryEventPublisher eventPublisher;

    // ========== 初始化 ==========

    @PostConstruct
    public void initFromDb() {
        // 启动时从 DB 加载库存数据到 Redis，确保 Redis 与 DB 一致
        List<InventoryEntity> all = inventoryMapper.selectList(null);
        if (all != null && !all.isEmpty()) {
            for (InventoryEntity e : all) {
                luaEngine.setStock(e.getSkuId(),
                        e.getAvailableQuantity() != null ? e.getAvailableQuantity() : 0,
                        e.getHoldQuantity() != null ? e.getHoldQuantity() : 0,
                        e.getDeductedQuantity() != null ? e.getDeductedQuantity() : 0);
            }
            log.info("启动加载库存数据到 Redis: {} 条", all.size());
        } else {
            log.info("DB 无库存数据，跳过 Redis 初始化");
        }
    }

    // ========== 核心库存操作 ==========

    /**
     * 预占库存（下单时）。
     * <p>
     * 先检查冻结标记，再通过 Redis Lua 原子预占，最后持久化预占记录。
     *
     * @return true=预占成功
     */
    @Transactional
    public boolean hold(String skuId, int quantity, String orderNo) {
        // 1. 冻结检查
        if (luaEngine.isFrozen(skuId)) {
            log.warn("商品已冻结，预占拒绝: skuId={}, orderNo={}", skuId, orderNo);
            return false;
        }

        int beforeAvailable = luaEngine.getAvailable(skuId);

        // 2. 生成幂等键并执行 Lua 预占
        String requestId = requestId(orderNo, skuId);
        ReserveResult result = luaEngine.reserve(skuId, quantity, requestId);

        if (!result.isSuccess()) {
            log.warn("库存预占失败: skuId={}, qty={}, order={}, code={}, msg={}",
                    skuId, quantity, orderNo, result.code(), result.message());
            writeTransaction(skuId, orderNo, null, requestId, "RESERVE",
                    quantity, beforeAvailable, beforeAvailable, "FAILED",
                    String.valueOf(result.code()), result.message());
            return false;
        }

        // 3. 持久化预占记录
        persistHold(result.holdId(), requestId, skuId, quantity, orderNo, "RESERVE");

        int afterAvailable = luaEngine.getAvailable(skuId);
        writeTransaction(skuId, orderNo, result.holdId(), requestId, "RESERVE",
                quantity, beforeAvailable, afterAvailable, "SUCCESS", null, null);

        // 4. 发布事件
        if (eventPublisher != null) {
            eventPublisher.reserved(skuId, result.holdId(), quantity, orderNo);
        }

        log.info("库存预占成功: skuId={}, qty={}, order={}, holdId={}, 幂等={}",
                skuId, quantity, orderNo, result.holdId(), result.isIdempotent());
        return true;
    }

    /**
     * 确认扣减（支付成功后，按订单批量确认）。
     * <p>
     * 查询订单的所有 RESERVED 预占记录，逐个转为 CONFIRMED。
     *
     * @return true=全部成功
     */
    @Transactional
    public boolean deduct(String orderNo) {
        List<InventoryHoldEntity> holds = findHoldsByOrder(orderNo, "RESERVED");
        if (holds.isEmpty()) {
            log.warn("扣减失败：订单 {} 无 RESERVED 预占记录", orderNo);
            return false;
        }

        boolean allSuccess = true;
        for (InventoryHoldEntity hold : holds) {
            int beforeReserved = luaEngine.getReserved(hold.getSkuId());

            LuaResult r = luaEngine.confirmDeduct(
                    hold.getSkuId(), hold.getHoldId(),
                    hold.getQuantity(), hold.getRequestId());

            int afterReserved = luaEngine.getReserved(hold.getSkuId());

            if (r.isSuccess()) {
                hold.setStatus("CONFIRMED");
                hold.setConfirmedAt(LocalDateTime.now());
                holdMapper.updateById(hold);

                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "CONFIRM", hold.getQuantity(), beforeReserved, afterReserved,
                        "SUCCESS", null, null);

                if (eventPublisher != null) {
                    eventPublisher.confirmed(hold.getSkuId(), hold.getHoldId(),
                            hold.getQuantity(), orderNo);
                }

                log.info("确认扣减: skuId={}, holdId={}, order={}", hold.getSkuId(), hold.getHoldId(), orderNo);
            } else {
                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "CONFIRM", hold.getQuantity(), beforeReserved, afterReserved,
                        "FAILED", String.valueOf(r.code()), r.message());

                log.error("确认扣减失败: skuId={}, holdId={}, code={}, msg={}",
                        hold.getSkuId(), hold.getHoldId(), r.code(), r.message());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 释放指定订单的全部预占（取消订单 / 超时关单）。
     */
    @Transactional
    public boolean releaseOrderHolds(String orderNo) {
        List<InventoryHoldEntity> holds = findHoldsByOrder(orderNo, "RESERVED");
        if (holds.isEmpty()) {
            log.info("释放订单预占：无 RESERVED 记录，可能是已确认或已释放: orderNo={}", orderNo);
            return true;
        }

        boolean allSuccess = true;
        for (InventoryHoldEntity hold : holds) {
            int beforeAvailable = luaEngine.getAvailable(hold.getSkuId());

            LuaResult r = luaEngine.releaseHold(
                    hold.getSkuId(), hold.getHoldId(),
                    hold.getQuantity(), hold.getRequestId());

            int afterAvailable = luaEngine.getAvailable(hold.getSkuId());

            if (r.isSuccess()) {
                hold.setStatus("RELEASED");
                hold.setReleasedAt(LocalDateTime.now());
                holdMapper.updateById(hold);

                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "RELEASE", hold.getQuantity(), beforeAvailable, afterAvailable,
                        "SUCCESS", null, null);

                if (eventPublisher != null) {
                    eventPublisher.released(hold.getSkuId(), hold.getHoldId(),
                            hold.getQuantity(), orderNo);
                }

                log.info("释放预占: skuId={}, holdId={}, order={}", hold.getSkuId(), hold.getHoldId(), orderNo);
            } else {
                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "RELEASE", hold.getQuantity(), beforeAvailable, afterAvailable,
                        "FAILED", String.valueOf(r.code()), r.message());

                log.error("释放预占失败: skuId={}, holdId={}, code={}, msg={}",
                        hold.getSkuId(), hold.getHoldId(), r.code(), r.message());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 回滚库存（售后退款，按订单批量撤销扣减）。
     */
    @Transactional
    public boolean restore(String orderNo) {
        List<InventoryHoldEntity> holds = findHoldsByOrder(orderNo, "CONFIRMED");
        if (holds.isEmpty()) {
            log.warn("撤销扣减：订单 {} 无 CONFIRMED 预占记录", orderNo);
            return false;
        }

        boolean allSuccess = true;
        for (InventoryHoldEntity hold : holds) {
            int beforeAvailable = luaEngine.getAvailable(hold.getSkuId());

            LuaResult r = luaEngine.undoDeduct(
                    hold.getSkuId(), hold.getHoldId(),
                    hold.getQuantity(), hold.getRequestId());

            int afterAvailable = luaEngine.getAvailable(hold.getSkuId());

            if (r.isSuccess()) {
                hold.setStatus("UNDONE");
                holdMapper.updateById(hold);

                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "UNDO_DEDUCT", hold.getQuantity(), beforeAvailable, afterAvailable,
                        "SUCCESS", null, null);

                if (eventPublisher != null) {
                    eventPublisher.undone(hold.getSkuId(), hold.getHoldId(),
                            hold.getQuantity(), orderNo);
                }

                log.info("撤销扣减: skuId={}, holdId={}, order={}", hold.getSkuId(), hold.getHoldId(), orderNo);
            } else {
                writeTransaction(hold.getSkuId(), orderNo, hold.getHoldId(), hold.getRequestId(),
                        "UNDO_DEDUCT", hold.getQuantity(), beforeAvailable, afterAvailable,
                        "FAILED", String.valueOf(r.code()), r.message());

                log.error("撤销扣减失败: skuId={}, holdId={}, code={}, msg={}",
                        hold.getSkuId(), hold.getHoldId(), r.code(), r.message());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    // ========== 故障修复 ==========

    /**
     * 从 MySQL 重载单 SKU 库存到 Redis（修复 Redis 不一致）。
     * <p>
     * 当 Redis 因故障或异常落后于 MySQL 时调用。
     * 不需要重启服务。
     *
     * @param skuId SKU
     * @return 加载后的可用库存
     */
    public int reloadFromDb(String skuId) {
        InventoryEntity entity = inventoryMapper.selectById(skuId);
        if (entity == null) {
            throw new IllegalArgumentException("SKU 不存在: " + skuId);
        }
        int available = entity.getAvailableQuantity() != null ? entity.getAvailableQuantity() : 0;
        int hold = entity.getHoldQuantity() != null ? entity.getHoldQuantity() : 0;
        int deducted = entity.getDeductedQuantity() != null ? entity.getDeductedQuantity() : 0;
        luaEngine.setStock(skuId, available, hold, deducted);
        log.info("Redis 库存已从 MySQL 重载: skuId={}, available={}, hold={}, deducted={}",
                skuId, available, hold, deducted);
        return available;
    }

    // ========== 库存调整（管理后台） ==========

    /**
     * 调整库存（管理后台 / 运营工具）。
     * <p>
     * 先写 MySQL（事务内），再写 Redis。
     * Redis 更新失败可通过 {@link #reloadFromDb(String)} 修复。
     *
     * @param skuId       SKU
     * @param delta       调整量（正数=增加，负数=减少）
     * @param reason      调整原因
     * @return 调整后的可用库存
     */
    @Transactional
    public int adjust(String skuId, int delta, String reason) {
        int before = luaEngine.getAvailable(skuId);
        int after = before + delta;
        if (after < 0) {
            throw new IllegalArgumentException("调整后可用库存不能为负: skuId=" + skuId
                    + ", current=" + before + ", delta=" + delta);
        }

        int reserved = luaEngine.getReserved(skuId);
        int deducted = luaEngine.getDeducted(skuId);
        int total = after + reserved + deducted;

        // 先写 MySQL（事务保护）
        int updated = inventoryMapper.update(null,
                Wrappers.<InventoryEntity>lambdaUpdate()
                        .eq(InventoryEntity::getSkuId, skuId)
                        .set(InventoryEntity::getTotalQuantity, total)
                        .set(InventoryEntity::getAvailableQuantity, after)
                        .set(InventoryEntity::getHoldQuantity, reserved)
                        .set(InventoryEntity::getDeductedQuantity, deducted));
        if (updated == 0) {
            InventoryEntity e = new InventoryEntity();
            e.setSkuId(skuId);
            e.setTotalQuantity(total);
            e.setAvailableQuantity(after);
            e.setHoldQuantity(reserved);
            e.setDeductedQuantity(deducted);
            inventoryMapper.insert(e);
        }

        // 再写 Redis
        try {
            luaEngine.setStock(skuId, after, reserved, deducted);
        } catch (Exception e) {
            log.error("库存调整后 Redis 更新失败（MySQL 已更新）: skuId={}, err={}", skuId, e.getMessage());
        }

        writeTransaction(skuId, null, null, null, "ADJUST",
                delta, before, after, "SUCCESS", null, reason);

        log.info("库存调整: skuId={}, {} → {} (delta={}), reason={}", skuId, before, after, delta, reason);
        return after;
    }

    // ========== 查询 ==========

    /**
     * 查询单 SKU 可用库存（Redis L2）。
     */
    public int getAvailable(String skuId) {
        return luaEngine.getAvailable(skuId);
    }

    /**
     * 批量查询可用库存（Redis MGET 管道）。
     */
    public Map<String, Integer> batchGetAvailable(List<String> skuIds) {
        return luaEngine.batchGetAvailable(skuIds);
    }

    /**
     * 查询订单的预占记录。
     */
    public List<InventoryHoldEntity> getOrderHolds(String orderNo) {
        LambdaQueryWrapper<InventoryHoldEntity> w = new LambdaQueryWrapper<>();
        w.eq(InventoryHoldEntity::getOrderNo, orderNo);
        w.orderByDesc(InventoryHoldEntity::getGmtCreate);
        return holdMapper.selectList(w);
    }

    /**
     * 查询 SKU 的所有预占记录。
     */
    public List<InventoryHoldEntity> getSkuHolds(String skuId) {
        LambdaQueryWrapper<InventoryHoldEntity> w = new LambdaQueryWrapper<>();
        w.eq(InventoryHoldEntity::getSkuId, skuId);
        w.orderByDesc(InventoryHoldEntity::getGmtCreate);
        return holdMapper.selectList(w);
    }

    /**
     * 查询 DB 中所有库存记录（管理后台列表）。
     */
    public List<InventoryEntity> listAllStock() {
        return inventoryMapper.selectList(null);
    }

    /**
     * 查询库存流水（按 SKU 或订单）。
     */
    public List<InventoryTransactionEntity> queryTransactions(String skuId, String orderNo,
                                                                String operationType, int limit) {
        if (transactionMapper == null) return List.of();
        LambdaQueryWrapper<InventoryTransactionEntity> w = new LambdaQueryWrapper<>();
        if (skuId != null) w.eq(InventoryTransactionEntity::getSkuId, skuId);
        if (orderNo != null) w.eq(InventoryTransactionEntity::getOrderNo, orderNo);
        if (operationType != null) w.eq(InventoryTransactionEntity::getOperationType, operationType);
        w.orderByDesc(InventoryTransactionEntity::getGmtCreate);
        w.last("LIMIT " + limit);
        return transactionMapper.selectList(w);
    }

    // ========== 内部 ==========

    private String requestId(String orderNo, String skuId) {
        return orderNo + ":" + skuId + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<InventoryHoldEntity> findHoldsByOrder(String orderNo, String status) {
        LambdaQueryWrapper<InventoryHoldEntity> w = new LambdaQueryWrapper<>();
        w.eq(InventoryHoldEntity::getOrderNo, orderNo);
        w.eq(InventoryHoldEntity::getStatus, status);
        return holdMapper.selectList(w);
    }

    private List<InventoryHoldEntity> findExpiredHolds(int batchSize) {
        LambdaQueryWrapper<InventoryHoldEntity> w = new LambdaQueryWrapper<>();
        w.eq(InventoryHoldEntity::getStatus, "RESERVED")
                .lt(InventoryHoldEntity::getExpireAt, LocalDateTime.now())
                .last("LIMIT " + batchSize);
        return holdMapper.selectList(w);
    }

    private void persistHold(String holdId, String requestId, String skuId,
                              int quantity, String orderNo, String holdType) {
        try {
            InventoryHoldEntity entity = new InventoryHoldEntity();
            entity.setHoldId(holdId);
            entity.setRequestId(requestId);
            entity.setSkuId(skuId);
            entity.setQuantity(quantity);
            entity.setOrderNo(orderNo);
            entity.setHoldType(holdType);
            entity.setStatus("RESERVED");
            entity.setExpireAt(LocalDateTime.now().plusSeconds(900)); // 15min
            holdMapper.insert(entity);
        } catch (Exception e) {
            log.error("持久化预占记录失败: holdId={}, skuId={}, order={}, err={}",
                    holdId, skuId, orderNo, e.getMessage());
        }
    }

    /**
     * 写入库存流水（异步容忍失败，不影响主流程）。
     */
    private void writeTransaction(String skuId, String orderNo, String holdId,
                                   String requestId, String operationType,
                                   int quantity, int beforeQty, int afterQty,
                                   String status, String errorCode, String errorMsg) {
        if (transactionMapper == null) return;
        try {
            InventoryTransactionEntity txn = new InventoryTransactionEntity();
            txn.setTransactionNo("TXN_" + UUID.randomUUID().toString().substring(0, 16).toUpperCase());
            txn.setSkuId(skuId);
            txn.setOrderNo(orderNo);
            txn.setHoldId(holdId);
            txn.setRequestId(requestId);
            txn.setOperationType(operationType);
            txn.setQuantity(quantity);
            txn.setBeforeQty(beforeQty);
            txn.setAfterQty(afterQty);
            txn.setStatus(status);
            txn.setErrorCode(errorCode);
            txn.setErrorMsg(errorMsg);
            txn.setGmtCreate(LocalDateTime.now());
            transactionMapper.insert(txn);
        } catch (Exception e) {
            log.warn("写入库存流水失败（不影响主流程）: skuId={}, op={}, err={}",
                    skuId, operationType, e.getMessage());
        }
    }
}
