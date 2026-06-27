package com.omplatform.risk.repository.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 风控黑白名单 Redis 缓存（ADR-047 §2.3）。
 * <p>
 * 每 60s 由 XXL-Job 刷新，P99 10ms 本地缓存级延迟。
 * 缓存键格式：
 * <ul>
 *   <li>{@code whitelist:user:{userId}} — 用户白名单</li>
 *   <li>{@code blacklist:user:{userId}} — 用户黑名单</li>
 *   <li>{@code blacklist:device:{deviceId}} — 设备黑名单</li>
 * </ul>
 */
@Slf4j
@Repository
public class RiskCacheRepository {

    private static final String KEY_WHITELIST_USER = "whitelist:user:%s";
    private static final String KEY_BLACKLIST_USER = "blacklist:user:%s";
    private static final String KEY_BLACKLIST_DEVICE = "blacklist:device:%s";

    @Autowired
    private StringRedisTemplate redis;

    /*public RiskCacheRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }*/

    // ========== 白名单 ==========

    public boolean isUserInWhitelist(String userId) {
        Boolean exists = redis.hasKey(String.format(KEY_WHITELIST_USER, userId));
        return Boolean.TRUE.equals(exists);
    }

    public void addWhitelistUser(String userId) {
        redis.opsForValue().set(String.format(KEY_WHITELIST_USER, userId), "1", 1, TimeUnit.HOURS);
    }

    public void removeWhitelistUser(String userId) {
        redis.delete(String.format(KEY_WHITELIST_USER, userId));
    }

    // ========== 黑名单 ==========

    public boolean isUserInBlacklist(String userId) {
        Boolean exists = redis.hasKey(String.format(KEY_BLACKLIST_USER, userId));
        return Boolean.TRUE.equals(exists);
    }

    public boolean isDeviceInBlacklist(String deviceId) {
        Boolean exists = redis.hasKey(String.format(KEY_BLACKLIST_DEVICE, deviceId));
        return Boolean.TRUE.equals(exists);
    }

    public void addBlacklistUser(String userId, String reason) {
        redis.opsForValue().set(String.format(KEY_BLACKLIST_USER, userId), reason, 1, TimeUnit.HOURS);
    }

    public void addBlacklistDevice(String deviceId, String reason) {
        redis.opsForValue().set(String.format(KEY_BLACKLIST_DEVICE, deviceId), reason, 1, TimeUnit.HOURS);
    }

    public void removeBlacklistUser(String userId) {
        redis.delete(String.format(KEY_BLACKLIST_USER, userId));
    }

    public void removeBlacklistDevice(String deviceId) {
        redis.delete(String.format(KEY_BLACKLIST_DEVICE, deviceId));
    }

    /** 清理全部黑白名单缓存（XXL-Job 刷新时调用）。 */
    public void clearAll() {
        Set<String> keys = redis.keys("whitelist:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        keys = redis.keys("blacklist:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        log.info("风控黑白名单缓存已刷新");
    }
}
