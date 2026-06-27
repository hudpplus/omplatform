package com.omplatform.marketing.config;

import com.omplatform.marketing.pipeline.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * 计价管道注册配置（ADR-045）。
 * <p>
 * 在启动时按序注册所有计价步骤到 PricePipeline。
 * 步骤顺序由 @Order 注解控制：
 * <pre>
 *   basic_price(1) → member_discount(2) → promotion_discount(3)
 *   → coupon_discount(4) → shipping_fee(5)
 * </pre>
 */
@Slf4j
@Configuration
public class PipelineConfig {

    @Autowired
    private PricePipeline pricePipeline;
    @Autowired
    private BasicPriceCalculator basicPriceCalculator;
    @Autowired
    private MemberDiscountCalculator memberDiscountCalculator;
    @Autowired
    private PromotionDiscountCalculator promotionDiscountCalculator;
    @Autowired
    private CouponDiscountCalculator couponDiscountCalculator;
    @Autowired
    private ShippingFeeCalculator shippingFeeCalculator;

    /*public PipelineConfig(PricePipeline pricePipeline,
                          BasicPriceCalculator basicPriceCalculator,
                          MemberDiscountCalculator memberDiscountCalculator,
                          PromotionDiscountCalculator promotionDiscountCalculator,
                          CouponDiscountCalculator couponDiscountCalculator,
                          ShippingFeeCalculator shippingFeeCalculator) {
        this.pricePipeline = pricePipeline;
        this.basicPriceCalculator = basicPriceCalculator;
        this.memberDiscountCalculator = memberDiscountCalculator;
        this.promotionDiscountCalculator = promotionDiscountCalculator;
        this.couponDiscountCalculator = couponDiscountCalculator;
        this.shippingFeeCalculator = shippingFeeCalculator;
    }*/

    @PostConstruct
    public void registerCalculators() {
        log.info("注册计价管道步骤...");
        pricePipeline.registerCalculator(basicPriceCalculator);
        pricePipeline.registerCalculator(memberDiscountCalculator);
        pricePipeline.registerCalculator(promotionDiscountCalculator);
        pricePipeline.registerCalculator(couponDiscountCalculator);
        pricePipeline.registerCalculator(shippingFeeCalculator);
        log.info("计价管道注册完成（5 步骤）");
    }
}
