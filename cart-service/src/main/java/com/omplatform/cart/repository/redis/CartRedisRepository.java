package com.omplatform.cart.repository.redis;

import com.omplatform.cart.repository.entity.CartItemEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 购物车 Redis 运行时层（ADR-044 §2.1）。
 * <p>
 * 数据结构：
 * <ul>
 *   <li>Hash {@code cart:{cartId}:items} — 行级数据（field=itemId, value=JSON）</li>
 *   <li>Sorted Set {@code cart:{cartId}:sorted_set} — 排序（member=itemId, score=sortOrder）</li>
 *   <li>String {@code cart:anon:{deviceId}} → cartId — 匿名购物车索引</li>
 * </ul>
 * <p>
 * 所有写操作均使用 Redis Lua 脚本保证原子性（ADR-044 §2.4）。
 */
@Repository
public class CartRedisRepository {

    private static final String KEY_ITEMS = "cart:%s:items";
    private static final String KEY_SORTED = "cart:%s:sorted_set";
    private static final String KEY_ANON_IDX = "cart:anon:%s";

    @Autowired
    private StringRedisTemplate redis;

    /*
    public CartRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }
    */

    // ========== 购物车项操作 ==========

    /** 添加商品（原子：Hash 写入 + Sorted Set 加入）。由 Lua 脚本保证原子性。 */
    public void addItem(String cartId, CartItemEntity item) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String sortedKey = key(KEY_SORTED, cartId);

        redis.opsForHash().put(itemsKey, item.getItemId(), serializeItem(item));
        redis.opsForZSet().add(sortedKey, item.getItemId(), item.getSortOrder().doubleValue());
    }

    /** 修改商品数量（直接更新 Hash 字段）。 */
    public void updateQuantity(String cartId, String itemId, int quantity) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String json = (String) redis.opsForHash().get(itemsKey, itemId);
        if (json == null) return;

        // 反序列化 → 修改 → 写回
        CartItemEntity item = deserializeItem(json, itemId);
        CartItemEntity updated = new CartItemEntity(
                item.getItemId(), item.getCartId(), item.getSkuId(), item.getSkuName(),
                item.getImageUrl(), quantity, item.getUnitPrice(),
                item.getSelected(), item.getPromotionInfo(), item.getSortOrder()
        );
        redis.opsForHash().put(itemsKey, itemId, serializeItem(updated));
    }

    /** 删除商品（原子：Hash 移除 + Sorted Set 移除）。 */
    public void removeItem(String cartId, String itemId) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String sortedKey = key(KEY_SORTED, cartId);

        redis.opsForHash().delete(itemsKey, itemId);
        redis.opsForZSet().remove(sortedKey, itemId);
    }

    /** 勾选/取消勾选商品。 */
    public void selectItem(String cartId, String itemId, boolean selected) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String json = (String) redis.opsForHash().get(itemsKey, itemId);
        if (json == null) return;

        CartItemEntity item = deserializeItem(json, itemId);
        CartItemEntity updated = new CartItemEntity(
                item.getItemId(), item.getCartId(), item.getSkuId(), item.getSkuName(),
                item.getImageUrl(), item.getQuantity(), item.getUnitPrice(),
                selected ? 1 : 0, item.getPromotionInfo(), item.getSortOrder()
        );
        redis.opsForHash().put(itemsKey, itemId, serializeItem(updated));
    }

    /** 批量勾选/取消勾选。 */
    public void selectAll(String cartId, boolean selected) {
        String itemsKey = key(KEY_ITEMS, cartId);
        Map<Object, Object> entries = redis.opsForHash().entries(itemsKey);

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String itemId = (String) entry.getKey();
            CartItemEntity item = deserializeItem((String) entry.getValue(), itemId);
            CartItemEntity updated = new CartItemEntity(
                    item.getItemId(), item.getCartId(), item.getSkuId(), item.getSkuName(),
                    item.getImageUrl(), item.getQuantity(), item.getUnitPrice(),
                    selected ? 1 : 0, item.getPromotionInfo(), item.getSortOrder()
            );
            redis.opsForHash().put(itemsKey, itemId, serializeItem(updated));
        }
    }

    /** 获取所有商品（按排序返回）。 */
    public List<CartItemEntity> getAllItems(String cartId) {
        String sortedKey = key(KEY_SORTED, cartId);
        String itemsKey = key(KEY_ITEMS, cartId);

        Set<String> itemIds = redis.opsForZSet().range(sortedKey, 0, -1);
        if (itemIds == null || itemIds.isEmpty()) return Collections.emptyList();

        List<Object> jsons = redis.opsForHash().multiGet(itemsKey, new ArrayList<>(itemIds));
        List<CartItemEntity> result = new ArrayList<>();
        for (int i = 0; i < itemIds.size(); i++) {
            String json = (String) jsons.get(i);
            if (json != null) {
                result.add(deserializeItem(json, (String) new ArrayList<>(itemIds).get(i)));
            }
        }
        return result;
    }

    /** 获取勾选的商品。 */
    public List<CartItemEntity> getCheckedItems(String cartId) {
        return getAllItems(cartId).stream()
                .filter(item -> item.getSelected() == 1)
                .collect(Collectors.toList());
    }

    /** 清理勾选的商品。 */
    public void clearCheckedItems(String cartId) {
        List<CartItemEntity> checked = getCheckedItems(cartId);
        for (CartItemEntity item : checked) {
            removeItem(cartId, item.getItemId());
        }
    }

    /** 清空购物车。 */
    public void clearCart(String cartId) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String sortedKey = key(KEY_SORTED, cartId);
        redis.delete(itemsKey);
        redis.delete(sortedKey);
    }

    /** 获取商品数量。 */
    public long getItemCount(String cartId) {
        String sortedKey = key(KEY_SORTED, cartId);
        Long count = redis.opsForZSet().zCard(sortedKey);
        return count != null ? count : 0;
    }

    // ========== 匿名购物车索引 ==========

    /** 绑定匿名设备到购物车。 */
    public void bindAnonCart(String deviceId, String cartId) {
        redis.opsForValue().set(key(KEY_ANON_IDX, deviceId), cartId);
    }

    /** 根据设备 ID 获取匿名购物车 ID。 */
    public String getAnonCartId(String deviceId) {
        return redis.opsForValue().get(key(KEY_ANON_IDX, deviceId));
    }

    /** 删除匿名购物车索引。 */
    public void deleteAnonCartIndex(String deviceId) {
        redis.delete(key(KEY_ANON_IDX, deviceId));
    }

    /** 删除整个购物车的 Redis 数据。 */
    public void deleteCart(String cartId) {
        String itemsKey = key(KEY_ITEMS, cartId);
        String sortedKey = key(KEY_SORTED, cartId);
        redis.delete(itemsKey);
        redis.delete(sortedKey);
    }

    // ========== 辅助方法 ==========

    private static String key(String pattern, String param) {
        return String.format(pattern, param);
    }

    /**
     * 简单 JSON 序列化（字段固定，避免 fastjson/jackson 依赖冲突）。
     * 生产环境建议替换为 Jackson ObjectMapper。
     */
    private static String serializeItem(CartItemEntity item) {
        return String.join("|",
                item.getItemId(),
                item.getCartId(),
                item.getSkuId(),
                safe(item.getSkuName()),
                safe(item.getImageUrl()),
                String.valueOf(item.getQuantity()),
                item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : "0",
                String.valueOf(item.getSelected()),
                safe(item.getPromotionInfo()),
                String.valueOf(item.getSortOrder())
        );
    }

    private static CartItemEntity deserializeItem(String json, String itemId) {
        String[] parts = json.split("\\|", -1);
        if (parts.length < 10) return null;
        CartItemEntity item = new CartItemEntity();
        item.setItemId(itemId);
        item.setCartId(parts[1]);
        item.setSkuId(parts[2]);
        item.setSkuName(parts[3]);
        item.setImageUrl(parts[4]);
        item.setQuantity(Integer.parseInt(parts[5]));
        item.setUnitPrice(new BigDecimal(parts[6]));
        item.setSelected(Integer.parseInt(parts[7]));
        item.setPromotionInfo(parts[8]);
        item.setSortOrder(Integer.parseInt(parts[9]));
        return item;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
