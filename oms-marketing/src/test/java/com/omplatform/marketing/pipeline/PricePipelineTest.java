package com.omplatform.marketing.pipeline;

import com.omplatform.marketing.coupon.CouponService;
import com.omplatform.marketing.member.MemberTierService;
import com.omplatform.marketing.promotion.StackingMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 计价管道单元测试（ADR-045）。
 */
@ExtendWith(MockitoExtension.class)
class PricePipelineTest {

    private PricePipeline pipeline;

    @Mock
    private MemberTierService memberTierService;
    @Mock
    private CouponService couponService;

    private PricePipeline.PriceContext context;

    @BeforeEach
    void setUp() {
        pipeline = new PricePipeline();

        // 注册 5 个计价步骤
        pipeline.registerCalculator(new BasicPriceCalculator());
        pipeline.registerCalculator(new MemberDiscountCalculator(memberTierService));
        pipeline.registerCalculator(new PromotionDiscountCalculator(new StackingMatrix()));
        pipeline.registerCalculator(new CouponDiscountCalculator(couponService));
        pipeline.registerCalculator(new ShippingFeeCalculator(memberTierService));

        // 构建计价上下文
        context = new PricePipeline.PriceContext();
        context.setBuyerId("UT001");
        context.setMemberTier("L2");
        context.setAddressId("addr001");

        PricePipeline.PriceContext.ItemLine item1 = new PricePipeline.PriceContext.ItemLine();
        item1.setSkuId("SKU001");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("100.00"));
        item1.setCategoryId("CAT001");

        PricePipeline.PriceContext.ItemLine item2 = new PricePipeline.PriceContext.ItemLine();
        item2.setSkuId("SKU002");
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("50.00"));
        item2.setCategoryId("CAT002");

        context.setItems(List.of(item1, item2));
        context.setOriginalTotal(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("L0 会员无折扣时基本价格计算应正确")
    void basicPrice_correctTotal() {
        context.setMemberTier("L0");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L0)).thenReturn(1.0);
        // L0 无免运费
        when(memberTierService.isFreeShipping(any(), any()))
                .thenReturn(false);

        PricePipeline.PriceResult result = pipeline.calculate(context);

        assertEquals(new BigDecimal("250.00"), result.getOriginalTotal());
        assertEquals(BigDecimal.ZERO, result.getMemberDiscount());
        assertEquals(BigDecimal.ZERO, result.getShippingFee()); // 满 99 免运费
    }

    @Test
    @DisplayName("L3 金卡 92 折应正确计算会员折扣")
    void memberDiscount_correctForGold() {
        context.setMemberTier("L3");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L3)).thenReturn(0.92);
        // L3 免运费（tier >= L3）
        when(memberTierService.isFreeShipping(any(), any())).thenReturn(true);

        PricePipeline.PriceResult result = pipeline.calculate(context);

        assertEquals(new BigDecimal("20.00"), result.getMemberDiscount()); // 250*0.08=20
        assertEquals(BigDecimal.ZERO, result.getShippingFee());
    }

    @Test
    @DisplayName("L5 黑卡 85 折 + 促销满减应正确叠加")
    void memberAndPromotion_shouldStack() {
        context.setMemberTier("L5");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L5)).thenReturn(0.85);
        when(memberTierService.isFreeShipping(any(), any())).thenReturn(true);

        PricePipeline.PriceResult result = pipeline.calculate(context);

        // 会员折扣: 250 * 0.15 = 37.5
        // 促销满减: 250 >= 200 → 30 按比例分摊
        assertEquals(new BigDecimal("37.50"), result.getMemberDiscount());
        assert (result.getPromotionDiscount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("优惠券折扣应被正确计算")
    void couponDiscount_shouldApply() {
        context.setMemberTier("L0");
        context.setCouponInstanceId("COUPON001");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L0)).thenReturn(1.0);
        when(memberTierService.isFreeShipping(any(), any())).thenReturn(false);
        when(couponService.calculateDiscount(any(), any())).thenReturn(new BigDecimal("30.00"));

        PricePipeline.PriceResult result = pipeline.calculate(context);

        assertEquals(new BigDecimal("30.00"), result.getCouponDiscount());
        assertEquals(BigDecimal.ZERO, result.getShippingFee()); // 满 99 免运费
    }

    @Test
    @DisplayName("免运费阈值：满 99 免运费")
    void freeShipping_aboveThreshold() {
        context.setMemberTier("L0");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L0)).thenReturn(1.0);
        when(memberTierService.isFreeShipping(any(), any()))
                .thenReturn(false); // L0 不免，但金额 > 99 免
        // ShippingFeeCalculator 中判断 finalTotal >= 99 免运费
        // 此处 finalTotal = 250 - 0 - 0 - 0 = 250

        PricePipeline.PriceResult result = pipeline.calculate(context);

        assertEquals(BigDecimal.ZERO, result.getShippingFee());
    }

    @Test
    @DisplayName("最终金额计算公式应正确")
    void finalTotal_calculation() {
        context.setMemberTier("L0");
        when(memberTierService.getDiscountRate(MemberTierService.Tier.L0)).thenReturn(1.0);
        when(memberTierService.isFreeShipping(any(), any())).thenReturn(false);

        PricePipeline.PriceResult result = pipeline.calculate(context);

        // 原价 250 - 0 会员 - 24 促销 - 0 券 + 0 运费 = 226
        assertEquals(new BigDecimal("226.00"), result.getFinalTotal());
    }
}
