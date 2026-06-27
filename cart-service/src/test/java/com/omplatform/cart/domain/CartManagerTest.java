package com.omplatform.cart.domain;

import com.omplatform.cart.event.CartEventPublisher;
import com.omplatform.cart.repository.CartSyncOutboxRepository;
import com.omplatform.cart.repository.entity.CartEntity;
import com.omplatform.cart.repository.entity.CartItemEntity;
import com.omplatform.cart.repository.mapper.CartItemMapper;
import com.omplatform.cart.repository.mapper.CartMapper;
import com.omplatform.cart.repository.redis.CartRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 购物车领域管理器单元测试（ADR-044 §2）。
 */
@ExtendWith(MockitoExtension.class)
class CartManagerTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private CartItemMapper cartItemMapper;
    @Mock
    private CartRedisRepository cartRedis;
    @Mock
    private CartEventPublisher eventPublisher;
    @Mock
    private CartSyncOutboxRepository syncOutboxRepository;

    private CartManager cartManager;

    @BeforeEach
    void setUp() {
        cartManager = new CartManager(cartMapper, cartItemMapper, cartRedis, eventPublisher,
                syncOutboxRepository);
    }

    @Test
    @DisplayName("加购同 SKU 应合并数量而非新增行")
    void addItem_sameSku_shouldMergeQuantity() {
        CartItemEntity existingItem = new CartItemEntity("item001", "cart001", "SKU001", "商品A",
                null, 2, new BigDecimal("50.00"), 1, null, 0);
        when(cartRedis.getAllItems("cart001")).thenReturn(List.of(existingItem));
        when(cartItemMapper.selectById("item001")).thenReturn(existingItem);
        lenient().when(cartItemMapper.updateById(any(CartItemEntity.class))).thenReturn(1);

        cartManager.addItem("cart001", "SKU001", "商品A", null, 3, new BigDecimal("50.00"));

        verify(cartRedis).updateQuantity("cart001", "item001", 5);
        verify(cartItemMapper).updateById(any(CartItemEntity.class));
        verify(cartRedis, never()).addItem(anyString(), any());
        verify(cartItemMapper, never()).insert(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("加购不同 SKU 应新增行")
    void addItem_differentSku_shouldCreateNewItem() {
        CartItemEntity existingItem = new CartItemEntity("item001", "cart001", "SKU001", "商品A",
                null, 2, new BigDecimal("50.00"), 1, null, 0);
        when(cartRedis.getAllItems("cart001")).thenReturn(List.of(existingItem));
        lenient().when(cartItemMapper.insert(any(CartItemEntity.class))).thenReturn(1);
        lenient().when(cartMapper.selectById(anyString())).thenReturn(
                new CartEntity("cart001", "user1", null, "ACTIVE", 1, null));

        cartManager.addItem("cart001", "SKU002", "商品B", null, 1, new BigDecimal("100.00"));

        verify(cartRedis).addItem(anyString(), any(CartItemEntity.class));
        verify(cartItemMapper).insert(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("数量修改为 0 应移除商品")
    void updateQuantity_zero_shouldRemove() {
        CartItemEntity dbItem = new CartItemEntity("item001", "cart001", "SKU001", "商品A",
                null, 5, new BigDecimal("50.00"), 1, null, 0);
        when(cartItemMapper.selectById("item001")).thenReturn(dbItem);

        cartManager.updateQuantity("cart001", "item001", 0);

        verify(cartRedis).removeItem("cart001", "item001");
        verify(cartItemMapper).deleteById("item001");
    }

    @Test
    @DisplayName("匿名购物车合并应智能合并同 SKU")
    void mergeCart_sameSku_shouldMergeQuantity() {
        CartItemEntity anonItem = new CartItemEntity("a001", "anon001", "SKU001", "商品A",
                null, 2, new BigDecimal("50.00"), 1, null, 0);
        CartItemEntity userItem = new CartItemEntity("u001", "user001", "SKU001", "商品A",
                null, 3, new BigDecimal("50.00"), 1, null, 0);

        when(cartMapper.selectOne(any())).thenReturn(
                new CartEntity("user001", "uid001", null, "ACTIVE", 1, null));
        when(cartRedis.getAllItems("anon001")).thenReturn(List.of(anonItem));
        when(cartRedis.getAllItems("user001")).thenReturn(List.of(userItem));

        cartManager.mergeCart("anon001", "uid001");

        verify(cartRedis).updateQuantity("user001", "u001", 5);
        verify(eventPublisher).cartMerged("anon001", "uid001", "user001");
    }

    @Test
    @DisplayName("匿名购物车合并应追加新 SKU")
    void mergeCart_newSku_shouldAddToUserCart() {
        CartItemEntity anonItem = new CartItemEntity("a001", "anon001", "SKU002", "商品B",
                null, 1, new BigDecimal("100.00"), 1, null, 0);

        when(cartMapper.selectOne(any())).thenReturn(
                new CartEntity("user001", "uid001", null, "ACTIVE", 0, null));
        when(cartRedis.getAllItems("anon001")).thenReturn(List.of(anonItem));
        when(cartRedis.getAllItems("user001")).thenReturn(List.of());

        cartManager.mergeCart("anon001", "uid001");

        verify(cartRedis).addItem(anyString(), any(CartItemEntity.class));
        verify(cartItemMapper).insert(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("获取购物车列表应返回 Redis 中的商品")
    void listItems_shouldReturnFromRedis() {
        List<CartItemEntity> items = List.of(
                new CartItemEntity("item001", "cart001", "SKU001", "商品A", null, 2,
                        new BigDecimal("50.00"), 1, null, 0));
        when(cartRedis.getAllItems("cart001")).thenReturn(items);

        List<CartItemEntity> result = cartManager.listItems("cart001");

        assertEquals(1, result.size());
        assertEquals("SKU001", result.get(0).getSkuId());
    }
}
