package com.omplatform.fulfillment.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.omplatform.fulfillment.inventory.lua.InventoryLuaEngine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存查询服务 — 多级缓存（ADR-043 §13.3）。
 * <p>
 * 缓存层级：
 * <ol>
 *   <li><b>L1</b>: Caffeine 本地缓存，5s TTL，消除热点 SKU 的重复 Redis 查询</li>
 *   <li><b>L2</b>: Redis 实时库存（通过 {@link InventoryLuaEngine}）</li>
 * </ol>
 * <p>
 * 批量查询优先查 L1 未命中再回源 L2。
 */
@Slf4j
@Service
public class StockQueryService {

    @Autowired
    private InventoryLuaEngine luaEngine;

    /** L1 Caffeine 缓存：SKU → 可用库存 */
    private Cache<String, Integer> l1Cache;

    /** 缓存 TTL（毫秒），可通过环境变量覆盖 */
    private static final long L1_TTL_MS = Long.getLong("STOCK_L1_TTL_MS", 5000);

    /** 缓存最大条目数 */
    private static final long L1_MAX_SIZE = 10_000;

    @PostConstruct
    public void init() {
        l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(L1_TTL_MS))
                .maximumSize(L1_MAX_SIZE)
                .recordStats()
                .build();
        log.info("库存 L1 Caffeine 缓存初始化完成: ttl={}ms, maxSize={}", L1_TTL_MS, L1_MAX_SIZE);
    }

    /**
     * 查询单个 SKU 可用库存（L1 → L2）。
     */
    public int getAvailable(String skuId) {
        // 1. 查 L1
        Integer cached = l1Cache.getIfPresent(skuId);
        if (cached != null) {
            return cached;
        }

        // 2. L1 未命中 → 查 L2 (Redis)
        int available = luaEngine.getAvailable(skuId);

        // 3. 回填 L1
        l1Cache.put(skuId, available);

        return available;
    }

    /**
     * 批量查询可用库存（L1 + L2 混合）。
     * <p>
     * 先查 L1 命中，剩余未命中的批量走 Redis MGET。
     */
    public Map<String, Integer> batchGetAvailable(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }

        // 1. 查 L1
        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> missList = skuIds.stream()
                .filter(id -> {
                    Integer v = l1Cache.getIfPresent(id);
                    if (v != null) {
                        result.put(id, v);
                        return false;
                    }
                    return true;
                })
                .toList();

        if (missList.isEmpty()) {
            return result;
        }

        // 2. 未命中批量查 L2 (Redis MGET)
        Map<String, Integer> l2Result = luaEngine.batchGetAvailable(missList);

        // 3. 回填 L1 + 合并结果
        for (String skuId : missList) {
            int val = l2Result.getOrDefault(skuId, 0);
            l1Cache.put(skuId, val);
            result.put(skuId, val);
        }

        return result;
    }

    /**
     * 使 L1 缓存失效（库存变动后调用）。
     */
    public void invalidate(String skuId) {
        l1Cache.invalidate(skuId);
    }

    /**
     * 批量使 L1 缓存失效。
     */
    public void invalidateAll(Set<String> skuIds) {
        l1Cache.invalidateAll(skuIds);
    }

    /**
     * 获取缓存统计信息。
     */
    public CacheStats getStats() {
        var stats = l1Cache.stats();
        return new CacheStats(
                stats.hitCount(), stats.missCount(),
                stats.hitRate(), stats.evictionCount());
    }

    /**
     * 缓存统计记录。
     */
    public record CacheStats(
            long hitCount,
            long missCount,
            double hitRate,
            long evictionCount
    ) {}
}
