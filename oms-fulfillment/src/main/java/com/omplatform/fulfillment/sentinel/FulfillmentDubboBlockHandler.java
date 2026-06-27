package com.omplatform.fulfillment.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.omplatform.api.inventory.InventoryService.SkuHoldRequest;
import com.omplatform.api.inventory.InventoryService.SkuStockDTO;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 履约服务 Sentinel 限流/熔断 blockHandler。
 */
@Slf4j
public final class FulfillmentDubboBlockHandler {

    private FulfillmentDubboBlockHandler() {}

    public static ApiResult<Boolean> holdBlock(List<SkuHoldRequest> skuQuantities, String orderNo,
                                                BlockException e) {
        log.warn("[Sentinel] hold 被限流: orderNo={}", orderNo);
        return ApiResult.success(false);
    }

    public static ApiResult<Boolean> deductBlock(String orderNo, BlockException e) {
        log.warn("[Sentinel] deduct 被限流: orderNo={}", orderNo);
        return ApiResult.success(false);
    }

    public static ApiResult<Boolean> releaseHoldBlock(String orderNo, BlockException e) {
        log.warn("[Sentinel] releaseHold 被限流: orderNo={}", orderNo);
        return ApiResult.success(false);
    }

    public static ApiResult<Boolean> restoreBlock(String orderNo, Object context, BlockException e) {
        log.warn("[Sentinel] restore 被限流: orderNo={}", orderNo);
        return ApiResult.success(false);
    }

    public static ApiResult<List<SkuStockDTO>> batchCheckAvailabilityBlock(List<String> skuIds,
                                                                            BlockException e) {
        log.warn("[Sentinel] batchCheckAvailability 被限流");
        return ApiResult.error("TOO_MANY_REQUESTS", "库存查询繁忙");
    }
}
