package com.omplatform.marketing.pipeline;

import com.omplatform.marketing.member.MemberTierService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 运费计算（计价管道第 5 步，ADR-045/046）。
 * <p>
 * 规则：
 * <ul>
 *   <li>会员 L3+ 免运费</li>
 *   <li>订单金额 > 99 免运费</li>
 *   <li>否则按物流区域计算（当前简化：统一 10 元）</li>
 * </ul>
 */
@Slf4j
@Component
@Order(5)
public class ShippingFeeCalculator implements PricePipeline.PriceCalculator {

    @Autowired
    private MemberTierService memberTierService;

    /*public ShippingFeeCalculator(MemberTierService memberTierService) {
        this.memberTierService = memberTierService;
    }*/

    @Override
    public void calculate(PricePipeline.PriceContext context, PricePipeline.PriceResult result) {
        BigDecimal finalTotal = context.getOriginalTotal()
                .subtract(result.getMemberDiscount())
                .subtract(result.getPromotionDiscount())
                .subtract(result.getCouponDiscount());

        // 判断是否免运费
        if (context.getMemberTier() != null) {
            MemberTierService.Tier tier = MemberTierService.Tier.valueOf(context.getMemberTier());
            if (memberTierService.isFreeShipping(tier, finalTotal)) {
                result.setShippingFee(BigDecimal.ZERO);
                log.debug("运费: 免运费 (tier={}, amount={})", tier, finalTotal);
                return;
            }
        }

        // 订单金额 > 99 免运费
        if (finalTotal.compareTo(BigDecimal.valueOf(99)) >= 0) {
            result.setShippingFee(BigDecimal.ZERO);
            log.debug("运费: 免运费 (amount={})", finalTotal);
            return;
        }

        // 统一运费 10 元
        result.setShippingFee(BigDecimal.TEN);
        log.debug("运费: 10 元 (amount={})", finalTotal);
    }
}
