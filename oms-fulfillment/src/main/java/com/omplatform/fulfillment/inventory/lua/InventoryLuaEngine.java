package com.omplatform.fulfillment.inventory.lua;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Redis Lua 脚本引擎（ADR-043 §5）。
 * <p>
 * 加载 resources/lua/*.lua 脚本文件，提供原子库存操作方法。
 * 所有操作通过 Lua 脚本在 Redis 单线程中原子执行，无需分布式锁。
 */
@Slf4j
@Component
public class InventoryLuaEngine {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 脚本 SHA 缓存 */
    private DefaultRedisScript<List> reserveScript;
    private DefaultRedisScript<List> confirmScript;
    private DefaultRedisScript<List> releaseScript;
    private DefaultRedisScript<List> undoScript;
    private DefaultRedisScript<List> freezeScript;
    private DefaultRedisScript<List> unfreezeScript;

    /** 预占过期时间（秒），Apollo 可配，默认 15min */
    private static final long HOLD_EXPIRE_SECONDS = 900;

    // ========== Key 模式 ==========

    private static String availableKey(String skuId) {
        return "stock:" + skuId + ":available";
    }

    private static String reservedKey(String skuId) {
        return "stock:" + skuId + ":reserved";
    }

    private static String deductedKey(String skuId) {
        return "stock:" + skuId + ":deducted";
    }

    private static String frozenKey(String skuId) {
        return "stock:" + skuId + ":frozen";
    }

    // ========== 初始化 ==========

    @PostConstruct
    public void init() {
        reserveScript = loadScript("lua/reserve_stock.lua");
        confirmScript = loadScript("lua/confirm_deduct.lua");
        releaseScript = loadScript("lua/release_hold.lua");
        undoScript = loadScript("lua/undo_deduct.lua");
        freezeScript = loadScript("lua/freeze_stock.lua");
        unfreezeScript = loadScript("lua/unfreeze_stock.lua");
        log.info("库存 Lua 脚本加载完成: reserve/confirm/release/undo/freeze/unfreeze");
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<List> loadScript(String classpath) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        try {
            String text = new String(
                    new ClassPathResource(classpath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            script.setScriptText(text);
        } catch (IOException e) {
            throw new RuntimeException("加载 Lua 脚本失败: " + classpath, e);
        }
        return script;
    }

    // ========== 公共方法 ==========

    /**
     * 预占库存（reserve_stock.lua）。
     * <p>
     * 下单时调用，将 available → reserved。
     *
     * @return ReserveResult
     */
    public ReserveResult reserve(String skuId, int quantity, String requestId) {
        List<Object> result = redisTemplate.execute(
                reserveScript,
                Arrays.asList(availableKey(skuId), reservedKey(skuId)),
                skuId, String.valueOf(quantity), requestId, String.valueOf(HOLD_EXPIRE_SECONDS)
        );
        return parseReserveResult(result);
    }

    /**
     * 确认扣减（confirm_deduct.lua）。
     * <p>
     * 支付成功后调用，将 reserved → deducted。
     */
    public LuaResult confirmDeduct(String skuId, String holdId, int quantity, String requestId) {
        List<Object> result = redisTemplate.execute(
                confirmScript,
                Arrays.asList(reservedKey(skuId), deductedKey(skuId)),
                skuId, holdId, String.valueOf(quantity), requestId
        );
        return parseLuaResult(result);
    }

    /**
     * 释放预占（release_hold.lua）。
     * <p>
     * 取消订单 / 超时关单时调用，将 reserved → available。
     */
    public LuaResult releaseHold(String skuId, String holdId, int quantity, String requestId) {
        List<Object> result = redisTemplate.execute(
                releaseScript,
                Arrays.asList(availableKey(skuId), reservedKey(skuId)),
                skuId, holdId, String.valueOf(quantity), requestId
        );
        return parseLuaResult(result);
    }

    /**
     * 撤销扣减（undo_deduct.lua）。
     * <p>
     * Saga 补偿 / 退款时调用，将 deducted → available。
     */
    public LuaResult undoDeduct(String skuId, String holdId, int quantity, String requestId) {
        List<Object> result = redisTemplate.execute(
                undoScript,
                Arrays.asList(availableKey(skuId), deductedKey(skuId)),
                skuId, holdId, String.valueOf(quantity), requestId
        );
        return parseLuaResult(result);
    }

    /**
     * 冻结库存（freeze_stock.lua）。
     * <p>
     * 设置 Redis 冻结标记，阻止新预占。
     *
     * @param ttlSeconds -1=永久，>0=指定秒数后自动解冻
     */
    public LuaResult freeze(String skuId, long ttlSeconds) {
        List<Object> result = redisTemplate.execute(
                freezeScript,
                Arrays.asList(frozenKey(skuId)),
                skuId, String.valueOf(ttlSeconds)
        );
        return parseLuaResult(result);
    }

    /**
     * 解冻库存（unfreeze_stock.lua）。
     */
    public LuaResult unfreeze(String skuId) {
        List<Object> result = redisTemplate.execute(
                unfreezeScript,
                Arrays.asList(frozenKey(skuId)),
                skuId
        );
        return parseLuaResult(result);
    }

    /**
     * 检查是否已冻结。
     */
    public boolean isFrozen(String skuId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(frozenKey(skuId)));
    }

    /**
     * 查询可用库存（从 Redis GET）。
     */
    public int getAvailable(String skuId) {
        String val = redisTemplate.opsForValue().get(availableKey(skuId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 查询预占库存。
     */
    public int getReserved(String skuId) {
        String val = redisTemplate.opsForValue().get(reservedKey(skuId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 查询已扣减库存。
     */
    public int getDeducted(String skuId) {
        String val = redisTemplate.opsForValue().get(deductedKey(skuId));
        return val != null ? Integer.parseInt(val) : 0;
    }

    /**
     * 批量查询可用库存（通过 Redis MGET 管道）。
     */
    public java.util.Map<String, Integer> batchGetAvailable(java.util.List<String> skuIds) {
        java.util.List<String> keys = skuIds.stream()
                .map(InventoryLuaEngine::availableKey)
                .toList();
        java.util.List<String> values = redisTemplate.opsForValue().multiGet(keys);
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (values != null) {
            for (int i = 0; i < skuIds.size(); i++) {
                String v = values.get(i);
                result.put(skuIds.get(i), v != null ? Integer.parseInt(v) : 0);
            }
        } else {
            skuIds.forEach(id -> result.put(id, 0));
        }
        return result;
    }

    /**
     * 设置库存（管理 API / 初始化时从 DB 加载）。
     */
    public void setStock(String skuId, int available, int reserved, int deducted) {
        redisTemplate.opsForValue().set(availableKey(skuId), String.valueOf(available));
        if (reserved > 0) {
            redisTemplate.opsForValue().set(reservedKey(skuId), String.valueOf(reserved));
        }
        if (deducted > 0) {
            redisTemplate.opsForValue().set(deductedKey(skuId), String.valueOf(deducted));
        }
    }

    // ========== 结果解析 ==========

    /**
     * 解析 reserve_stock.lua 返回值。
     * Lua 返回 {code(int), message(string), holdId(string), quantity(string)}
     */
    private ReserveResult parseReserveResult(List<Object> result) {
        if (result == null || result.isEmpty()) {
            return new ReserveResult(-1, "Lua 返回空", null, 0);
        }
        int code = toInt(result.get(0));
        String msg = result.size() > 1 ? toString(result.get(1)) : "";
        String holdId = result.size() > 2 ? toString(result.get(2)) : null;
        int qty = result.size() > 3 ? toInt(result.get(3)) : 0;
        return new ReserveResult(code, msg, holdId, qty);
    }

    /**
     * 解析通用 Lua 脚本返回值。
     * Lua 返回 {code(int), message(string), ...}
     */
    private LuaResult parseLuaResult(List<Object> result) {
        if (result == null || result.isEmpty()) {
            return new LuaResult(-1, "Lua 返回空");
        }
        int code = toInt(result.get(0));
        String msg = result.size() > 1 ? toString(result.get(1)) : "";
        return new LuaResult(code, msg);
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static String toString(Object o) {
        return o != null ? o.toString() : "";
    }
}
