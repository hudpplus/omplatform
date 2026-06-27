package com.omplatform.marketing.pipeline;

import com.omplatform.marketing.coupon.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 优惠券折扣计算（计价管道第 4 步，ADR-045）。
 * <p>
 * 应用用户已锁定的优惠券，计算券抵扣金额。
 */
@Slf4j
@Component
@Order(4)
public class CouponDiscountCalculator implements PricePipeline.PriceCalculator {

    @Autowired
    private CouponService couponService;

    /*public CouponDiscountCalculator(CouponService couponService) {
        this.couponService = couponService;
    }*/

    @Override
    public void calculate(PricePipeline.PriceContext context, PricePipeline.PriceResult result) {
        if (context.getCouponInstanceId() == null) return;

        // 计算当前已累计金额（原价 - 会员折扣 - 促销折扣）
        BigDecimal currentAmount = context.getOriginalTotal()
                .subtract(result.getMemberDiscount())
                .subtract(result.getPromotionDiscount());

        BigDecimal couponDiscount = couponService.calculateDiscount(
                context.getCouponInstanceId(), currentAmount);
        result.setCouponDiscount(couponDiscount);

        log.debug("优惠券折扣: coupon={}, discount={}", context.getCouponInstanceId(), couponDiscount);
    }
}
