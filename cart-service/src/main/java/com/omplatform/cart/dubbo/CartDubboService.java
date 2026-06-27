package com.omplatform.cart.dubbo;

import com.omplatform.api.cart.CartService;
import com.omplatform.cart.domain.CartManager;
import com.omplatform.cart.repository.entity.CartItemEntity;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车 Dubbo 服务实现（ADR-044 §6 Internal 层）。
 */
@Slf4j
@DubboService
public class CartDubboService implements CartService {

    @Autowired
    private CartManager cartManager;

    /*
    public CartDubboService(CartManager cartManager) {
        this.cartManager = cartManager;
    }
    */

    @Override
    public ApiResult<List<CartItemDTO>> getCartItems(String cartId, boolean onlyChecked) {
        List<CartItemEntity> items = onlyChecked
                ? cartManager.getCheckedItems(cartId)
                : cartManager.listItems(cartId);

        List<CartItemDTO> dtos = items.stream()
                .map(this::toDTO)
                .toList();
        return ApiResult.success(dtos);
    }

    @Override
    public ApiResult<String> mergeCart(String anonymousCartId, String userId) {
        String targetCartId = cartManager.mergeCart(anonymousCartId, userId);
        return ApiResult.success(targetCartId);
    }

    @Override
    public ApiResult<Void> refreshCartPrice(String cartId) {
        cartManager.refreshPrice(cartId);
        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> clearCheckedItems(String cartId) {
        cartManager.clearCheckedItems(cartId);
        return ApiResult.success();
    }

    @Override
    public ApiResult<Integer> getCartCount(String cartId) {
        return ApiResult.success(cartManager.getItemCount(cartId));
    }

    private CartItemDTO toDTO(CartItemEntity entity) {
        return new CartItemDTO(
                entity.getItemId(),
                entity.getSkuId(),
                entity.getSkuName(),
                entity.getImageUrl(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getUnitPrice().multiply(BigDecimal.valueOf(entity.getQuantity())),
                entity.getSelected() == 1,
                entity.getPromotionInfo()
        );
    }
}
