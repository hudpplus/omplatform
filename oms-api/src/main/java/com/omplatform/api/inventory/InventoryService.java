package com.omplatform.api.inventory;

import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;

import java.util.List;

/**
 * 库存服务接口（Dubbo，由 oms-fulfillment 实现）。
 */
public interface InventoryService {

    /**
     * 预占库存（下单时调用，Redis Lua 原子操作）。
     *
     * @param skuQuantities 商品 SKU → 数量映射
     * @param orderNo       订单号
     * @return 预占是否成功
     */
    ApiResult<Boolean> hold(List<SkuHoldRequest> skuQuantities, String orderNo);

    /**
     * 确认扣减（支付成功后调用，从预占转为扣减）。
     */
    ApiResult<Boolean> deduct(String orderNo);

    /**
     * 释放预占（取消订单 / 超时关单时调用）。
     */
    ApiResult<Boolean> releaseHold(String orderNo);

    /**
     * 回滚库存（售后退款时调用）。
     */
    ApiResult<Boolean> restore(String orderNo, TransitionContextDTO context);

    /**
     * 批量查询库存可用量。
     */
    ApiResult<List<SkuStockDTO>> batchCheckAvailability(List<String> skuIds);

    // ========== 内部 DTO ==========

    record SkuHoldRequest(String skuId, int quantity) {}

    record SkuStockDTO(String skuId, int availableQuantity, int holdQuantity) {}
}
