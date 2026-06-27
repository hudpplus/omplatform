package com.omplatform.api.cart;

import com.omplatform.common.api.ApiResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车服务接口（Dubbo，由 cart-service 实现，ADR-044）。
 */
public interface CartService {

    /**
     * 获取购物车商品列表（下单时调用）。
     *
     * @param cartId     购物车 ID
     * @param onlyChecked 是否只返回勾选的商品
     * @return 商品行列表
     */
    ApiResult<List<CartItemDTO>> getCartItems(String cartId, boolean onlyChecked);

    /**
     * 合并匿名购物车到登录用户。
     *
     * @param anonymousCartId 匿名购物车 ID（deviceId）
     * @param userId          登录用户 ID
     * @return 合并后的购物车 ID
     */
    ApiResult<String> mergeCart(String anonymousCartId, String userId);

    /**
     * 刷新购物车价签（促销变更后调用）。
     */
    ApiResult<Void> refreshCartPrice(String cartId);

    /**
     * 清理已下单商品。
     *
     * @param cartId 购物车 ID
     */
    ApiResult<Void> clearCheckedItems(String cartId);

    /**
     * 获取购物车商品数量。
     */
    ApiResult<Integer> getCartCount(String cartId);

    // ========== DTO ==========

    record CartItemDTO(
            String itemId,
            String skuId,
            String skuName,
            String imageUrl,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            boolean selected,
            String promotionInfo
    ) {}
}
