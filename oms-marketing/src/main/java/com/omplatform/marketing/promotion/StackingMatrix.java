package com.omplatform.marketing.promotion;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 优惠叠加互斥矩阵（ADR-045）。
 * <p>
 * 定义 促销×促销、促销×券 的二维互斥/叠加规则。
 * <p>
 * 规则说明：
 * <ul>
 *   <li>🚫 MUTEX — 互斥，取优惠金额大的</li>
 *   <li>✅ STACKABLE — 可叠加</li>
 *   <li>⚠️ CONDITIONAL — 条件叠加</li>
 * </ul>
 */
@Slf4j
@Component
public class StackingMatrix {

    /**
     * 判断两个优惠类型是否可叠加。
     *
     * @param typeA 优惠类型 A
     * @param typeB 优惠类型 B
     * @return 叠加关系
     */
    public StackingRelation checkRelation(PromotionType typeA, PromotionType typeB) {
        // 拼团/秒杀与任何优惠互斥
        if (typeA == PromotionType.GROUP_BUY || typeB == PromotionType.GROUP_BUY
                || typeA == PromotionType.FLASH_SALE || typeB == PromotionType.FLASH_SALE) {
            return StackingRelation.MUTEX;
        }

        // 平台券 vs 商家券 → 互斥
        if ((typeA == PromotionType.PLATFORM_COUPON && typeB == PromotionType.SHOP_COUPON)
                || (typeA == PromotionType.SHOP_COUPON && typeB == PromotionType.PLATFORM_COUPON)) {
            return StackingRelation.MUTEX;
        }

        // 单品直降 vs 满减 → 互斥
        if ((typeA == PromotionType.ITEM_DISCOUNT && typeB == PromotionType.FULL_REDUCTION)
                || (typeA == PromotionType.FULL_REDUCTION && typeB == PromotionType.ITEM_DISCOUNT)) {
            return StackingRelation.MUTEX;
        }

        // 满减 vs 平台券 → 可叠加
        if ((typeA == PromotionType.FULL_REDUCTION && typeB == PromotionType.PLATFORM_COUPON)
                || (typeA == PromotionType.PLATFORM_COUPON && typeB == PromotionType.FULL_REDUCTION)) {
            return StackingRelation.STACKABLE;
        }

        // 会员价 vs 满减/折扣 → 可叠加
        if ((typeA == PromotionType.MEMBER_PRICE && typeB == PromotionType.FULL_REDUCTION)
                || (typeA == PromotionType.MEMBER_PRICE && typeB == PromotionType.DISCOUNT)
                || (typeA == PromotionType.FULL_REDUCTION && typeB == PromotionType.MEMBER_PRICE)
                || (typeA == PromotionType.DISCOUNT && typeB == PromotionType.MEMBER_PRICE)) {
            return StackingRelation.STACKABLE;
        }

        // 折扣 vs 平台券 → 条件叠加
        if ((typeA == PromotionType.DISCOUNT && typeB == PromotionType.PLATFORM_COUPON)
                || (typeA == PromotionType.PLATFORM_COUPON && typeB == PromotionType.DISCOUNT)) {
            return StackingRelation.CONDITIONAL;
        }

        // 同类型互斥（两个满减、两个折扣）
        if (typeA == typeB) {
            return StackingRelation.MUTEX;
        }

        return StackingRelation.STACKABLE;
    }

    @Getter
    public enum PromotionType {
        ITEM_DISCOUNT("单品直降"),
        FULL_REDUCTION("满减"),
        DISCOUNT("折扣"),
        PLATFORM_COUPON("平台券"),
        SHOP_COUPON("商家券"),
        GROUP_BUY("拼团"),
        FLASH_SALE("秒杀"),
        MEMBER_PRICE("会员价");

        private final String displayName;

        PromotionType(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum StackingRelation {
        MUTEX,        // 🚫 互斥
        STACKABLE,    // ✅ 可叠加
        CONDITIONAL   // ⚠️ 条件叠加
    }
}
