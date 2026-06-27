package com.omplatform.cart.service;

import com.omplatform.cart.domain.CartManager;
import com.omplatform.cart.event.CartEventPublisher;
import com.omplatform.cart.repository.entity.CartItemEntity;
import com.omplatform.cart.repository.redis.CartRedisRepository;
import com.omplatform.common.api.ApiResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车 Buyer REST API（ADR-044 §6）。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/cart")
public class CartController {

    @Autowired
    private CartManager cartManager;
    @Autowired
    private CartRedisRepository cartRedis;
    @Autowired
    private CartEventPublisher eventPublisher;

    /*
    public CartController(CartManager cartManager, CartRedisRepository cartRedis,
                          CartEventPublisher eventPublisher) {
        this.cartManager = cartManager;
        this.cartRedis = cartRedis;
        this.eventPublisher = eventPublisher;
    }
    */

    /**
     * 获取或创建购物车 ID。
     */
    @GetMapping("/id")
    public ApiResult<String> getCartId(@RequestParam(required = false) String userId,
                                        @RequestParam(required = false) String deviceId) {
        String cartId = cartManager.getOrCreateCart(userId, deviceId);
        return ApiResult.success(cartId);
    }

    /**
     * 加购。
     */
    @PostMapping("/items")
    public ApiResult<Void> addItem(@RequestParam @NotBlank String cartId,
                                    @RequestParam @NotBlank String skuId,
                                    @RequestParam String skuName,
                                    @RequestParam(required = false) String imageUrl,
                                    @RequestParam @Min(1) int quantity,
                                    @RequestParam BigDecimal unitPrice) {
        cartManager.addItem(cartId, skuId, skuName, imageUrl, quantity, unitPrice);
        return ApiResult.success();
    }

    /**
     * 修改数量。
     */
    @PutMapping("/items/{itemId}/quantity")
    public ApiResult<Void> updateQuantity(@PathVariable String itemId,
                                           @RequestParam @Min(1) int quantity) {
        // itemId 需要 cartId，此处由前端传递；简化处理：通过 itemId 查 cartId
        cartManager.updateQuantity(null, itemId, quantity);
        return ApiResult.success();
    }

    /**
     * 移除商品。
     */
    @DeleteMapping("/items/{itemId}")
    public ApiResult<Void> removeItem(@PathVariable String itemId) {
        cartManager.removeItem(null, itemId);
        return ApiResult.success();
    }

    /**
     * 勾选/取消勾选。
     */
    @PutMapping("/items/{itemId}/select")
    public ApiResult<Void> selectItem(@PathVariable String itemId,
                                       @RequestParam boolean selected) {
        cartManager.selectItem(null, itemId, selected);
        return ApiResult.success();
    }

    /**
     * 全选/全不选。
     */
    @PutMapping("/select-all")
    public ApiResult<Void> selectAll(@RequestParam @NotBlank String cartId,
                                      @RequestParam boolean selected) {
        cartManager.selectAll(cartId, selected);
        return ApiResult.success();
    }

    /**
     * 获取购物车列表。
     */
    @GetMapping("/{cartId}/items")
    public ApiResult<List<CartItemVO>> listItems(@PathVariable String cartId) {
        List<CartItemEntity> items = cartManager.listItems(cartId);
        List<CartItemVO> vos = items.stream()
                .map(item -> new CartItemVO(
                        item.getItemId(), item.getSkuId(), item.getSkuName(),
                        item.getImageUrl(), item.getQuantity(), item.getUnitPrice(),
                        item.getSelected() == 1))
                .toList();
        return ApiResult.success(vos);
    }

    /**
     * 获取购物车商品数量。
     */
    @GetMapping("/{cartId}/count")
    public ApiResult<Integer> getCount(@PathVariable String cartId) {
        return ApiResult.success(cartManager.getItemCount(cartId));
    }

    /**
     * 清空购物车。
     */
    @DeleteMapping("/{cartId}/clear")
    public ApiResult<Void> clearCart(@PathVariable String cartId) {
        cartManager.clearCart(cartId);
        return ApiResult.success();
    }

    /**
     * 合并匿名购物车。
     */
    @PostMapping("/merge")
    public ApiResult<String> mergeCart(@RequestParam @NotBlank String anonymousCartId,
                                        @RequestParam @NotBlank String userId) {
        String targetCartId = cartManager.mergeCart(anonymousCartId, userId);
        return ApiResult.success(targetCartId);
    }

    // ========== View Object ==========

    public record CartItemVO(
            String itemId,
            String skuId,
            String skuName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            boolean selected
    ) {}
}
