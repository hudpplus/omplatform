package com.omplatform.marketing.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 基础价格计算（计价管道第 1 步）。
 * <p>
 * 汇总商品原价：Σ(单价 × 数量)
 */
@Slf4j
@Component
@Order(1)
public class BasicPriceCalculator implements PricePipeline.PriceCalculator {

    @Override
    public void calculate(PricePipeline.PriceContext context, PricePipeline.PriceResult result) {
        BigDecimal total = context.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        context.setOriginalTotal(total);
        log.debug("基础价格: {}", total);
    }
}
