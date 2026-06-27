package com.omplatform.marketing.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.omplatform.api.marketing.MarketingService.PriceRequest;
import com.omplatform.api.marketing.MarketingService.PriceResult;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 营销服务 Sentinel 限流/熔断 blockHandler。
 */
@Slf4j
public final class MarketingDubboBlockHandler {

    private MarketingDubboBlockHandler() {}

    public static ApiResult<PriceResult> calculatePriceBlock(PriceRequest request, BlockException e) {
        log.warn("[Sentinel] calculatePrice 被限流: buyerId={}", request != null ? request.buyerId() : "?");
        return ApiResult.error("TOO_MANY_REQUESTS", "计价服务繁忙，请稍后重试");
    }

    public static ApiResult<Void> lockCouponBlock(String couponInstanceId, String orderNo,
                                                   BlockException e) {
        log.warn("[Sentinel] lockCoupon 被限流: orderNo={}", orderNo);
        return ApiResult.success();
    }

    public static ApiResult<Void> useCouponBlock(String couponInstanceId, String orderNo,
                                                  BlockException e) {
        log.warn("[Sentinel] useCoupon 被限流: orderNo={}", orderNo);
        return ApiResult.success();
    }

    public static ApiResult<Void> grantGrowthValueBlock(String buyerId, String orderNo,
                                                         BigDecimal amount, BlockException e) {
        log.warn("[Sentinel] grantGrowthValue 被限流: orderNo={}", orderNo);
        return ApiResult.success();
    }

    public static ApiResult<Void> grantPointsBlock(String buyerId, String orderNo,
                                                    BigDecimal amount, BlockException e) {
        log.warn("[Sentinel] grantPoints 被限流: orderNo={}", orderNo);
        return ApiResult.success();
    }
}
