package com.omplatform.marketing.pipeline;

import com.omplatform.marketing.member.MemberTierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 会员折扣计算（计价管道第 2 步，ADR-045/046）。
 * <p>
 * 根据会员等级折扣率计算会员价，覆盖原价。
 * 会员价覆盖逻辑：行单价 = 原价 × 折扣率。
 */
@Slf4j
@Component
@Order(2)
public class MemberDiscountCalculator implements PricePipeline.PriceCalculator {

    @Autowired
    private MemberTierService memberTierService;

    /*public MemberDiscountCalculator(MemberTierService memberTierService) {
        this.memberTierService = memberTierService;
    }*/

    @Override
    public void calculate(PricePipeline.PriceContext context, PricePipeline.PriceResult result) {
        if (context.getMemberTier() == null) return;

        MemberTierService.Tier tier = MemberTierService.Tier.valueOf(context.getMemberTier());
        double rate = memberTierService.getDiscountRate(tier);

        if (rate >= 1.0) return; // L0 无折扣

        BigDecimal memberDiscount = BigDecimal.ZERO;
        for (PricePipeline.PriceContext.ItemLine item : context.getItems()) {
            BigDecimal originalLinePrice = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal memberPrice = originalLinePrice.multiply(BigDecimal.valueOf(rate))
                    .setScale(2, RoundingMode.HALF_UP);
            memberDiscount = memberDiscount.add(originalLinePrice.subtract(memberPrice));
        }

        result.setMemberDiscount(memberDiscount);
        log.debug("会员折扣: tier={}, discount={}", tier, memberDiscount);
    }
}
