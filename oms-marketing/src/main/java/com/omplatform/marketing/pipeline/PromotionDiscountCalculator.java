package com.omplatform.marketing.pipeline;

import com.omplatform.marketing.promotion.StackingMatrix;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 促销折扣计算（计价管道第 3 步，ADR-045）。
 * <p>
 * 应用满减/折扣促销活动，遵守叠加互斥矩阵规则。
 * 当前实现：读取商品上的 promotionInfo 并计算折扣。
 */
@Slf4j
@Component
@Order(3)
public class PromotionDiscountCalculator implements PricePipeline.PriceCalculator {

    @Autowired
    private StackingMatrix stackingMatrix;

    /*public PromotionDiscountCalculator(StackingMatrix stackingMatrix) {
        this.stackingMatrix = stackingMatrix;
    }*/

    @Override
    public void calculate(PricePipeline.PriceContext context, PricePipeline.PriceResult result) {
        BigDecimal promotionDiscount = BigDecimal.ZERO;

        for (PricePipeline.PriceContext.ItemLine item : context.getItems()) {
            // 检查商品是否有促销活动（由上游传入，简化处理）
            // 实际应从 promotion_definition/activity 表中查询
            BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));

            // 模拟满减计算：满 200 减 30
            if (itemTotal.compareTo(BigDecimal.valueOf(200)) >= 0) {
                // 按比例分摊折扣到各商品
                BigDecimal discount = BigDecimal.valueOf(30)
                        .multiply(itemTotal)
                        .divide(context.getOriginalTotal(), 2, RoundingMode.HALF_UP);
                promotionDiscount = promotionDiscount.add(discount);
            }
        }

        result.setPromotionDiscount(promotionDiscount);
        log.debug("促销折扣: discount={}", promotionDiscount);
    }
}
