package com.omplatform.marketing.member;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会员等级服务（ADR-046）。
 * <p>
 * 固定 6 级体系：L0 普通 → L5 黑卡。
 * 升级规则：成长值达到阈值立即升级，降级按季度评估。
 */
@Slf4j
@Service
public class MemberTierService {

    /**
     * 获取会员等级对应的折扣率。
     *
     * @param tier 等级
     * @return 折扣率（0.95 = 95 折）
     */
    public double getDiscountRate(Tier tier) {
        return switch (tier) {
            case L0 -> 1.0;   // 无折扣
            case L1 -> 0.98;  // 98 折
            case L2 -> 0.95;  // 95 折
            case L3 -> 0.92;  // 92 折
            case L4 -> 0.88;  // 88 折
            case L5 -> 0.85;  // 85 折
        };
    }

    /**
     * 判断是否免运费。
     */
    public boolean isFreeShipping(Tier tier, java.math.BigDecimal orderAmount) {
        // L3 以上免运费，或订单金额 > 99 免运费
        return tier.getLevel() >= Tier.L3.getLevel()
                || orderAmount.compareTo(java.math.BigDecimal.valueOf(99)) >= 0;
    }

    @Getter
    @AllArgsConstructor
    public enum Tier {
        L0(0, "普通", 0),
        L1(1, "铜卡", 100),
        L2(2, "银卡", 500),
        L3(3, "金卡", 2000),
        L4(4, "铂金", 8000),
        L5(5, "黑卡", 20000);

        private final int level;
        private final String displayName;
        private final int minGrowthValue;
    }
}
