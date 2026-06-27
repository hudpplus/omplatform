package com.omplatform.api.marketing;

import com.omplatform.common.api.ApiResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 营销服务接口（Dubbo，由 oms-marketing 实现）。
 */
public interface MarketingService {

    /**
     * 计算订单价格（调用计价管道）。
     *
     * @param request 计价请求
     * @return 计价结果
     */
    ApiResult<PriceResult> calculatePrice(PriceRequest request);

    /**
     * 锁定优惠券。
     */
    ApiResult<Void> lockCoupon(String couponInstanceId, String orderNo);

    /**
     * 核销优惠券。
     */
    ApiResult<Void> useCoupon(String couponInstanceId, String orderNo);

    /**
     * 回退优惠券。
     */
    ApiResult<Void> rollbackCoupon(String couponInstanceId, String orderNo);

    /**
     * 获取会员信息。
     */
    ApiResult<MemberInfo> getMemberInfo(String buyerId);

    /**
     * 发放成长值（支付成功时调用）。
     */
    ApiResult<Void> grantGrowthValue(String buyerId, String orderNo, java.math.BigDecimal amount);

    /**
     * 发放积分（支付成功时调用）。
     */
    ApiResult<Void> grantPoints(String buyerId, String orderNo, java.math.BigDecimal amount);

    // ========== DTO ==========

    record PriceRequest(
            String buyerId,
            String shopId,
            List<ItemLine> items,
            String couponInstanceId,
            String addressId
    ) {
        public record ItemLine(String skuId, int quantity, BigDecimal unitPrice, String categoryId) {}
    }

    record PriceResult(
            BigDecimal originalTotal,
            BigDecimal memberDiscount,
            BigDecimal promotionDiscount,
            BigDecimal couponDiscount,
            BigDecimal shippingFee,
            BigDecimal finalTotal
    ) {}

    record MemberInfo(
            String buyerId,
            String tier,
            long growthValue,
            long pointsBalance,
            boolean freeShipping
    ) {}
}
