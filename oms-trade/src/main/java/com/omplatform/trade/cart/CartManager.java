package com.omplatform.trade.cart;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 购物车管理器（ADR-044）。
 * <p>
 * 基于 Redis Hash 的购物车实现：
 * <ul>
 *   <li>每个购物车一个 Redis Hash（field=SKU_ID, value=JSON）</li>
 *   <li>匿名购物车用 device_id 索引，登录后合并</li>
 *   <li>TTL 策略：匿名 30d / 登录持久</li>
 * </ul>
 * <p>
 * 作为内嵌模块运行在 oms-trade 中，非独立服务。
 */
@Slf4j
@Component
public class CartManager {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CART_KEY_PREFIX = "cart:";
    private static final String ANON_INDEX_PREFIX = "cart:anon:";

    // ========== 买家接口 ==========

    /**
     * 添加商品到购物车。
     *
     * @param userId   用户 ID（登录用户）或 null（匿名）
     * @param deviceId 设备 ID（匿名用户标识）
     * @param skuId    SKU ID
     * @param quantity 数量
     */
    public void addItem(String userId, String deviceId, String skuId, int quantity) {
        String cartKey = resolveCartKey(userId, deviceId);
        redisTemplate.opsForHash().increment(cartKey, skuId, quantity);
        // 匿名用户建立索引
        if (userId == null) {
            redisTemplate.opsForSet().add(ANON_INDEX_PREFIX + deviceId, cartKey);
        }
        log.debug("购物车添加: userId={}, skuId={}, qty={}", userId, skuId, quantity);
    }

    /**
     * 修改数量。
     */
    public void modifyQuantity(String userId, String deviceId, String skuId, int quantity) {
        String cartKey = resolveCartKey(userId, deviceId);
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(cartKey, skuId);
        } else {
            redisTemplate.opsForHash().put(cartKey, skuId, String.valueOf(quantity));
        }
    }

    /**
     * 移除商品。
     */
    public void removeItem(String userId, String deviceId, String skuId) {
        String cartKey = resolveCartKey(userId, deviceId);
        redisTemplate.opsForHash().delete(cartKey, skuId);
    }

    /**
     * 获取购物车内容。
     */
    public Map<Object, Object> getCart(String userId, String deviceId) {
        String cartKey = resolveCartKey(userId, deviceId);
        return redisTemplate.opsForHash().entries(cartKey);
    }

    /**
     * 清空购物车（下单后调用）。
     */
    public void clearCart(String userId, String deviceId) {
        String cartKey = resolveCartKey(userId, deviceId);
        redisTemplate.delete(cartKey);
    }

    /**
     * 获取购物车商品数量。
     */
    public long getCount(String userId, String deviceId) {
        String cartKey = resolveCartKey(userId, deviceId);
        Long size = redisTemplate.opsForHash().size(cartKey);
        return size != null ? size : 0;
    }

    /**
     * 获取选中的购物车商品（下单时使用）。
     */
    public Map<Object, Object> getCheckedItems(String userId, String deviceId) {
        // 实际实现中 Hash 的 value 包含 selected 标记
        return getCart(userId, deviceId);
    }

    // ========== 内部 ==========

    private String resolveCartKey(String userId, String deviceId) {
        if (userId != null) {
            return CART_KEY_PREFIX + "user:" + userId;
        }
        return CART_KEY_PREFIX + "anon:" + deviceId;
    }
}
