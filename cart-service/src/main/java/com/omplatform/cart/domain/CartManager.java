package com.omplatform.cart.domain;

import com.omplatform.cart.config.CartIdBloomFilter;
import com.omplatform.cart.event.CartEventPublisher;
import com.omplatform.cart.repository.CartSyncOutboxRepository;
import com.omplatform.cart.repository.entity.CartEntity;
import com.omplatform.cart.repository.entity.CartItemEntity;
import com.omplatform.cart.repository.entity.CartSyncOutboxEntity;
import com.omplatform.cart.repository.mapper.CartItemMapper;
import com.omplatform.cart.repository.mapper.CartMapper;
import com.omplatform.cart.repository.redis.CartRedisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 购物车领域管理器（ADR-044）。
 * <p>
 * 封装购物车的核心领域逻辑：加购、改量、合并、TTL 管理。
 */
@Slf4j
@Component
public class CartManager {

    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private CartItemMapper cartItemMapper;
    @Autowired
    private CartRedisRepository cartRedis;
    @Autowired
    private CartEventPublisher eventPublisher;
    @Autowired
    private CartSyncOutboxRepository syncOutboxRepository;
    @Autowired
    private CartIdBloomFilter cartIdBloomFilter;

    public CartManager(CartMapper cartMapper, CartItemMapper cartItemMapper,
                       CartRedisRepository cartRedis, CartEventPublisher eventPublisher,
                       CartSyncOutboxRepository syncOutboxRepository,
                       CartIdBloomFilter cartIdBloomFilter) {
        this.cartMapper = cartMapper;
        this.cartItemMapper = cartItemMapper;
        this.cartRedis = cartRedis;
        this.eventPublisher = eventPublisher;
        this.syncOutboxRepository = syncOutboxRepository;
        this.cartIdBloomFilter = cartIdBloomFilter;
    }

    // ========== 获取/创建购物车 ==========

    /**
     * 获取或创建购物车。
     *
     * @param userId   登录用户 ID（可为空）
     * @param deviceId 设备指纹（匿名时必填）
     * @return cartId
     */
    public String getOrCreateCart(String userId, String deviceId) {
        if (userId != null) {
            // 已登录：查 DB 获取 cartId
            CartEntity cart = cartMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartEntity>()
                            .eq(CartEntity::getUserId, userId)
                            .eq(CartEntity::getStatus, "ACTIVE")
                            .last("LIMIT 1"));
            if (cart != null) return cart.getCartId();
            return createCart(userId, null);
        } else {
            // 匿名：查 Redis 索引
            String cartId = cartRedis.getAnonCartId(deviceId);
            if (cartId != null) return cartId;
            cartId = createCart(null, deviceId);
            cartRedis.bindAnonCart(deviceId, cartId);
            return cartId;
        }
    }

    private String createCart(String userId, String deviceId) {
        String cartId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiredAt = userId != null ? null : LocalDateTime.now().plusDays(30);
        CartEntity entity = new CartEntity(cartId, userId, deviceId, "ACTIVE", 0, expiredAt);
        cartMapper.insert(entity);
        // 新 cartId 加入布隆过滤器，后续读取可穿透防护
        cartIdBloomFilter.put(cartId);
        log.info("创建购物车: cartId={}, userId={}, deviceId={}", cartId, userId, deviceId);
        return cartId;
    }

    // ========== 购物车项操作 ==========

    /** 加购（同 SKU 合数量）。 */
    @Transactional
    public void addItem(String cartId, String skuId, String skuName, String imageUrl,
                        int quantity, BigDecimal unitPrice) {
        // 先查 Redis 中是否已有同 SKU
        List<CartItemEntity> existing = cartRedis.getAllItems(cartId);
        for (CartItemEntity item : existing) {
            if (item.getSkuId().equals(skuId)) {
                // 合并数量 — DB 先于 Redis
                int newQty = item.getQuantity() + quantity;
                syncItemQuantityToDb(cartId, item.getItemId(), newQty);
                writeSyncOutbox(cartId);
                cartRedis.updateQuantity(cartId, item.getItemId(), newQty);
                eventPublisher.itemAdded(cartId, skuId, quantity);
                return;
            }
        }

        // 新增
        String itemId = UUID.randomUUID().toString().replace("-", "");
        int sortOrder = existing.size();
        CartItemEntity item = new CartItemEntity(itemId, cartId, skuId, skuName,
                imageUrl, quantity, unitPrice, 1, null, sortOrder);

        cartItemMapper.insert(item);
        writeSyncOutbox(cartId);
        cartRedis.addItem(cartId, item);
        updateItemCount(cartId);

        eventPublisher.itemAdded(cartId, skuId, quantity);
    }

    /** 修改数量。 */
    @Transactional
    public void updateQuantity(String cartId, String itemId, int quantity) {
        if (quantity <= 0) {
            removeItem(cartId, itemId);
            return;
        }
        String cid = resolveCartId(cartId, itemId);
        // DB 先于 Redis
        syncItemQuantityToDb(cartId, itemId, quantity);
        writeSyncOutbox(cid);
        cartRedis.updateQuantity(cid, itemId, quantity);
    }

    /** 移除商品。 */
    @Transactional
    public void removeItem(String cartId, String itemId) {
        CartItemEntity item = cartItemMapper.selectById(itemId);
        String cid = resolveCartId(cartId, itemId);
        if (item != null) {
            // DB 先于 Redis
            cartItemMapper.deleteById(itemId);
            writeSyncOutbox(cid);
            updateItemCount(cid);
            eventPublisher.itemRemoved(cid, item.getSkuId());
        }
        cartRedis.removeItem(cid, itemId);
    }

    /** 勾选/取消勾选。 */
    public void selectItem(String cartId, String itemId, boolean selected) {
        cartRedis.selectItem(resolveCartId(cartId, itemId), itemId, selected);
    }

    /** 全选/全不选。 */
    public void selectAll(String cartId, boolean selected) {
        cartRedis.selectAll(cartId, selected);
    }

    /** 获取购物车列表（读修复：Redis 空时从 DB 恢复）。 */
    public List<CartItemEntity> listItems(String cartId) {
        // 布隆过滤器：一定不存在的 cartId 直接拦截（缓存穿透防护）
        if (!cartIdBloomFilter.mightExist(cartId)) {
            return List.of();
        }

        // 缓存穿透防护：空值标记直接返回空
        if (cartRedis.hasNullMarker(cartId)) {
            return List.of();
        }

        List<CartItemEntity> items = cartRedis.getAllItems(cartId);
        if (items.isEmpty()) {
            // 缓存雪崩防护：随机延迟 0-300ms，避免大量重建同时打 DB
            randomDelay();
            // 双重检查：延迟期间可能已被其他请求恢复
            items = cartRedis.getAllItems(cartId);
            if (items.isEmpty()) {
                repairFromDb(cartId);
                items = cartRedis.getAllItems(cartId);
            }
        }
        return items;
    }

    /** 获取勾选商品（读修复：Redis 空时从 DB 恢复）。 */
    public List<CartItemEntity> getCheckedItems(String cartId) {
        // 布隆过滤器拦截
        if (!cartIdBloomFilter.mightExist(cartId)) {
            return List.of();
        }

        // 缓存穿透防护
        if (cartRedis.hasNullMarker(cartId)) {
            return List.of();
        }

        List<CartItemEntity> items = cartRedis.getCheckedItems(cartId);
        if (items.isEmpty()) {
            randomDelay();
            items = cartRedis.getCheckedItems(cartId);
            if (items.isEmpty()) {
                repairFromDb(cartId);
                items = cartRedis.getCheckedItems(cartId);
            }
        }
        return items;
    }

    /** 获取商品数量。 */
    public int getItemCount(String cartId) {
        return (int) cartRedis.getItemCount(cartId);
    }

    /** 清理勾选商品（下单后调用）。 */
    @Transactional
    public void clearCheckedItems(String cartId) {
        List<CartItemEntity> checked = cartRedis.getCheckedItems(cartId);
        for (CartItemEntity item : checked) {
            cartItemMapper.deleteById(item.getItemId());
        }
        writeSyncOutbox(cartId);
        cartRedis.clearCheckedItems(cartId);
        updateItemCount(cartId);
    }

    /** 清空购物车。 */
    @Transactional
    public void clearCart(String cartId) {
        cartItemMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getCartId, cartId));
        writeSyncOutbox(cartId);
        cartRedis.clearCart(cartId);
        CartEntity cart = cartMapper.selectById(cartId);
        if (cart != null) {
            cart.setItemCount(0);
            cartMapper.updateById(cart);
        }
    }

    // ========== 匿名→登录合并 ==========

    /**
     * 合并匿名购物车到登录用户（ADR-044 §2.2 智能合并）。
     * <p>
     * 同 SKU+活动合并数量，不同 SKU 追加。
     */
    @Transactional
    public String mergeCart(String anonymousCartId, String userId) {
        // 获取或创建用户购物车
        String userCartId = getOrCreateCart(userId, null);
        // 使用带 read-repair 的读路径
        List<CartItemEntity> anonItems = listItems(anonymousCartId);

        for (CartItemEntity anonItem : anonItems) {
            // 查用户购物车中是否已有同 SKU
            List<CartItemEntity> userItems = listItems(userCartId);
            boolean found = false;
            for (CartItemEntity userItem : userItems) {
                if (userItem.getSkuId().equals(anonItem.getSkuId())) {
                    // 合并数量 — DB 先于 Redis
                    int newQty = userItem.getQuantity() + anonItem.getQuantity();
                    syncItemQuantityToDb(userCartId, userItem.getItemId(), newQty);
                    writeSyncOutbox(userCartId);
                    cartRedis.updateQuantity(userCartId, userItem.getItemId(), newQty);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 追加到用户购物车 — DB 先于 Redis
                String newItemId = UUID.randomUUID().toString().replace("-", "");
                CartItemEntity newItem = new CartItemEntity(
                        newItemId, userCartId, anonItem.getSkuId(), anonItem.getSkuName(),
                        anonItem.getImageUrl(), anonItem.getQuantity(), anonItem.getUnitPrice(),
                        anonItem.getSelected(), anonItem.getPromotionInfo(), userItems.size()
                );
                cartItemMapper.insert(newItem);
                writeSyncOutbox(userCartId);
                cartRedis.addItem(userCartId, newItem);
            }
        }

        // 清理匿名购物车
        clearCart(anonymousCartId);
        // 标记匿名购物车为已合并
        CartEntity anonCart = cartMapper.selectById(anonymousCartId);
        if (anonCart != null) {
            anonCart.setStatus("MERGED");
            cartMapper.updateById(anonCart);
            writeSyncOutbox(anonymousCartId);
        }
        updateItemCount(userCartId);
        eventPublisher.cartMerged(anonymousCartId, userId, userCartId);

        log.info("购物车合并: anonymous={}, userId={}, target={}", anonymousCartId, userId, userCartId);
        return userCartId;
    }

    // ========== 刷新价签 ==========

    /**
     * 刷新购物车所有商品的价签（促销变更后调用）。
     * 由外部促销系统通过 MQ 触发。
     */
    public void refreshPrice(String cartId) {
        // 实际实现：遍历商品，调用 price-service 获取最新价
        // 此处仅发送事件，由 listener 异步处理
        eventPublisher.priceRefreshRequested(cartId);
    }

    // ========== 过期清理 ==========

    /** 清理过期匿名购物车（由 XXL-Job 定时调用）。 */
    @Transactional
    public void expireAnonCarts() {
        List<CartEntity> expiredCarts = cartMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartEntity>()
                        .eq(CartEntity::getStatus, "ACTIVE")
                        .isNull(CartEntity::getUserId)
                        .isNotNull(CartEntity::getExpiredAt)
                        .le(CartEntity::getExpiredAt, LocalDateTime.now()));

        for (CartEntity cart : expiredCarts) {
            cartItemMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItemEntity>()
                            .eq(CartItemEntity::getCartId, cart.getCartId()));
            cart.setStatus("EXPIRED");
            cartMapper.updateById(cart);
            writeSyncOutbox(cart.getCartId());
            // DB 操作成功后，再清理 Redis
            cartRedis.deleteCart(cart.getCartId());
            eventPublisher.cartExpired(cart.getCartId());
            log.info("匿名购物车过期清理: cartId={}", cart.getCartId());
        }
    }

    // ========== 一致性修复 ==========

    /**
     * 从 DB 恢复 Redis 购物车数据（读修复）。
     * <p>
     * 当 Redis 中某购物车数据为空但 DB 中有对应数据时，
     * 重新从 DB 加载并写入 Redis，保证最终一致性。
     */
    public void repairFromDb(String cartId) {
        // 先检查购物车是否存在且有效（穿透防护：不存在的 cartId 或非 ACTIVE 状态设空值标记）
        CartEntity cart = cartMapper.selectById(cartId);
        if (cart == null || !"ACTIVE".equals(cart.getStatus())) {
            cartRedis.setNullMarker(cartId, 15);
            log.warn("Read-repair: cartId={} 不存在或状态非 ACTIVE(status={})，设置空值标记 15s",
                    cartId, cart != null ? cart.getStatus() : "null");
            return;
        }
        cartRedis.removeNullMarker(cartId);

        // 清空 Redis 当前数据，避免残留旧数据（如已删除的商品）
        cartRedis.clearCart(cartId);
        // 从 DB 全量读取（@TableLogic 自动过滤已删除记录）
        List<CartItemEntity> dbItems = cartItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItemEntity>()
                        .eq(CartItemEntity::getCartId, cartId));
        if (dbItems.isEmpty()) {
            return;
        }
        for (CartItemEntity item : dbItems) {
            cartRedis.addItem(cartId, item);
        }
        log.info("Read-repair: 从 DB 恢复 {} 条商品到 Redis, cartId={}", dbItems.size(), cartId);
    }

    // ========== 辅助方法 ==========

    /**
     * 缓存雪崩防护：随机休眠 0-300ms，避免大量读修复同时打 DB。
     */
    private void randomDelay() {
        long millis = ThreadLocalRandom.current().nextLong(0, 300);
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 写入 Redis 同步发件箱（与业务数据在同一 DB 事务中）。
     * 同时清除空值标记，确保后续读取能拿到最新数据。
     */
    private void writeSyncOutbox(String cartId) {
        // 缓存穿透防护：有数据写入时清除空值标记
        cartRedis.removeNullMarker(cartId);

        CartSyncOutboxEntity record = new CartSyncOutboxEntity(
                UUID.randomUUID().toString().replace("-", ""),
                cartId,
                "PENDING",
                0,
                LocalDateTime.now()
        );
        syncOutboxRepository.save(record);
    }

    private void updateItemCount(String cartId) {
        int count = (int) cartRedis.getItemCount(cartId);
        CartEntity cart = cartMapper.selectById(cartId);
        if (cart != null) {
            cart.setItemCount(count);
            cartMapper.updateById(cart);
        }
    }

    private void syncItemQuantityToDb(String cartId, String itemId, int quantity) {
        CartItemEntity dbItem = cartItemMapper.selectById(itemId);
        if (dbItem != null) {
            dbItem.setQuantity(quantity);
            cartItemMapper.updateById(dbItem);
        }
    }

    /**
     * 解析 cartId：若传入非空则直接使用，否则从 DB 中查询 item 所属的 cartId。
     */
    private String resolveCartId(String cartId, String itemId) {
        if (cartId != null) return cartId;
        CartItemEntity item = cartItemMapper.selectById(itemId);
        return item != null ? item.getCartId() : null;
    }
}
