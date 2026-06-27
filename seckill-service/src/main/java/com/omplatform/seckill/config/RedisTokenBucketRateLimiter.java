package com.omplatform.seckill.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Redis 令牌桶限流器 — 秒杀活动级（应用层限流，Sentinel 之外的第二道防线）。
 * <p>
 * 每个秒杀活动独立一个令牌桶 Redis Key (seckill:token_bucket:{activityId})。
 * 使用 Lua 脚本保证：补水 / 扣减 / 判断 三合一原子操作。
 * <p>
 * 配置（环境变量）：
 * <ul>
 *   <li>{@code SECKILL_TOKEN_BUCKET_CAPACITY} — 桶容量（默认 500）</li>
 *   <li>{@code SECKILL_TOKEN_BUCKET_REFILL_RATE} — 补水速率，个/秒（默认 200）</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisTokenBucketRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private DefaultRedisScript<List> script;

    /** 桶容量（最大瞬时并发） */
    private static final long DEFAULT_CAPACITY = 500;
    /** 每秒补充令牌数 */
    private static final long DEFAULT_REFILL_RATE = 200;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        script = loadScript("lua/seckill_token_bucket.lua");
        log.info("[令牌桶] Lua 脚本加载完成: capacity={}, refillRate={}/s",
                getCapacity(), getRefillRate());
    }

    /**
     * 尝试获取 1 个令牌（活动级别）。
     *
     * @param activityId 秒杀活动 ID
     * @return 限流结果
     */
    public TokenBucketResult tryAcquire(Long activityId) {
        return tryAcquire(activityId, 1);
    }

    /**
     * 尝试获取 N 个令牌。
     *
     * @param activityId       秒杀活动 ID
     * @param requestedTokens  请求的令牌数
     * @return 限流结果
     */
    public TokenBucketResult tryAcquire(Long activityId, int requestedTokens) {
        String key = "seckill:token_bucket:" + activityId;

        List<Object> result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(getCapacity()),
                String.valueOf(getRefillRate()),
                String.valueOf(requestedTokens),
                String.valueOf(System.currentTimeMillis()));

        if (result == null || result.isEmpty()) {
            // Redis 异常时放行（降级打开）
            log.warn("[令牌桶] Redis 返回空，降级放行: activityId={}", activityId);
            return new TokenBucketResult(true, 0);
        }

        int code = ((Number) result.get(0)).intValue();
        long remaining = result.size() > 1 ? ((Number) result.get(1)).longValue() : 0;
        return new TokenBucketResult(code == 1, remaining);
    }

    // ========== 配置 ==========

    private static long getCapacity() {
        return getEnvLong("SECKILL_TOKEN_BUCKET_CAPACITY", DEFAULT_CAPACITY);
    }

    private static long getRefillRate() {
        return getEnvLong("SECKILL_TOKEN_BUCKET_REFILL_RATE", DEFAULT_REFILL_RATE);
    }

    // ========== 辅助 ==========

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<List> loadScript(String classpath) {
        DefaultRedisScript<List> s = new DefaultRedisScript<>();
        s.setResultType(List.class);
        try {
            String text = new String(
                    new ClassPathResource(classpath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            s.setScriptText(text);
        } catch (IOException e) {
            throw new RuntimeException("加载 Lua 脚本失败: " + classpath, e);
        }
        return s;
    }

    private static long getEnvLong(String key, long defaultValue) {
        try {
            String val = System.getenv(key);
            return val != null ? Long.parseLong(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ========== 结果 ==========

    /**
     * 令牌桶判定结果。
     */
    public record TokenBucketResult(boolean allowed, long remainingTokens) {}
}
