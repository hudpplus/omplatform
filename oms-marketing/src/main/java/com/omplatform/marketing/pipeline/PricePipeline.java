package com.omplatform.marketing.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 计价管道（ADR-045）。
 * <p>
 * 按序执行多个价格计算步骤：
 * <pre>
 *   basic_price → member_discount → promotion_discount → coupon_discount → shipping_fee
 * </pre>
 * <p>
 * 每个步骤实现 {@link PriceCalculator} 接口，步骤顺序由 @Order 注解控制。
 */
@Slf4j
@Component
public class PricePipeline {

    private final List<PriceCalculator> calculators = new ArrayList<>();

    /**
     * 注册计价步骤（由 Spring @PostConstruct 或配置驱动）。
     */
    public void registerCalculator(PriceCalculator calculator) {
        calculators.add(calculator);
        log.info("注册计价步骤: {}", calculator.getClass().getSimpleName());
    }

    /**
     * 执行计价管道。
     *
     * @param context 计价上下文
     * @return 计算结果
     */
    public PriceResult calculate(PriceContext context) {
        PriceResult result = new PriceResult();
        result.setOriginalTotal(context.getOriginalTotal());

        for (PriceCalculator calculator : calculators) {
            calculator.calculate(context, result);
        }

        // 计算最终金额：(原价 - 会员折扣 - 促销折扣 - 优惠券折扣) + 运费
        BigDecimal afterDiscount = context.getOriginalTotal()
                .subtract(result.getMemberDiscount())
                .subtract(result.getPromotionDiscount())
                .subtract(result.getCouponDiscount());
        result.setFinalTotal(afterDiscount.add(result.getShippingFee()));

        log.info("计价完成: 原价={}, 最终={}, 优惠={}",
                result.getOriginalTotal(), result.getFinalTotal(), result.getTotalDiscount());
        return result;
    }

    // ========== 上下文 & 结果 ==========

    /**
     * 计价上下文。
     */
    public static class PriceContext {
        private String buyerId;
        private String shopId;
        private String memberTier;
        private List<ItemLine> items;
        private String couponInstanceId;
        private String addressId;
        private BigDecimal originalTotal;

        public PriceContext() {}

        // ========== getter/setter ==========

        public String getBuyerId() { return buyerId; }
        public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

        public String getShopId() { return shopId; }
        public void setShopId(String shopId) { this.shopId = shopId; }

        public String getMemberTier() { return memberTier; }
        public void setMemberTier(String memberTier) { this.memberTier = memberTier; }

        public List<ItemLine> getItems() { return items; }
        public void setItems(List<ItemLine> items) { this.items = items; }

        public String getCouponInstanceId() { return couponInstanceId; }
        public void setCouponInstanceId(String couponInstanceId) { this.couponInstanceId = couponInstanceId; }

        public String getAddressId() { return addressId; }
        public void setAddressId(String addressId) { this.addressId = addressId; }

        public BigDecimal getOriginalTotal() { return originalTotal; }
        public void setOriginalTotal(BigDecimal originalTotal) { this.originalTotal = originalTotal; }

        /**
         * 商品行。
         */
        public static class ItemLine {
            private String skuId;
            private int quantity;
            private BigDecimal unitPrice;
            private String categoryId;

            public ItemLine() {}

            public String getSkuId() { return skuId; }
            public void setSkuId(String skuId) { this.skuId = skuId; }

            public int getQuantity() { return quantity; }
            public void setQuantity(int quantity) { this.quantity = quantity; }

            public BigDecimal getUnitPrice() { return unitPrice; }
            public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

            public String getCategoryId() { return categoryId; }
            public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        }
    }

    /**
     * 计价结果。
     */
    public static class PriceResult {
        private BigDecimal originalTotal = BigDecimal.ZERO;
        private BigDecimal memberDiscount = BigDecimal.ZERO;
        private BigDecimal promotionDiscount = BigDecimal.ZERO;
        private BigDecimal couponDiscount = BigDecimal.ZERO;
        private BigDecimal shippingFee = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private BigDecimal finalTotal = BigDecimal.ZERO;

        public PriceResult() {}

        // ========== getter/setter ==========

        public BigDecimal getOriginalTotal() { return originalTotal; }
        public void setOriginalTotal(BigDecimal originalTotal) { this.originalTotal = originalTotal; }

        public BigDecimal getMemberDiscount() { return memberDiscount; }
        public void setMemberDiscount(BigDecimal memberDiscount) { this.memberDiscount = memberDiscount; }

        public BigDecimal getPromotionDiscount() { return promotionDiscount; }
        public void setPromotionDiscount(BigDecimal promotionDiscount) { this.promotionDiscount = promotionDiscount; }

        public BigDecimal getCouponDiscount() { return couponDiscount; }
        public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

        public BigDecimal getShippingFee() { return shippingFee; }
        public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

        public BigDecimal getTax() { return tax; }
        public void setTax(BigDecimal tax) { this.tax = tax; }

        public BigDecimal getFinalTotal() { return finalTotal; }
        public void setFinalTotal(BigDecimal finalTotal) { this.finalTotal = finalTotal; }

        public BigDecimal getTotalDiscount() {
            return memberDiscount.add(promotionDiscount).add(couponDiscount);
        }
    }

    /**
     * 计价步骤接口。
     */
    @FunctionalInterface
    public interface PriceCalculator {
        void calculate(PriceContext context, PriceResult result);
    }
}
