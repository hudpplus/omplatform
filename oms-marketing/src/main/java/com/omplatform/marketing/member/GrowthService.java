package com.omplatform.marketing.member;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 成长值服务（ADR-046）。
 * <p>
 * 复合模型：消费基值 × 类目系数 + 频次奖励。
 */
@Slf4j
@Service
public class GrowthService {

    /**
     * 计算消费获得的成长值。
     *
     * @param amount     支付金额
     * @param categoryFactor 类目系数（1.0 基准）
     * @param orderCount 本月已下单次数
     * @return 成长值
     */
    public long calculateGrowth(java.math.BigDecimal amount,
                                 double categoryFactor,
                                 int orderCount) {
        long baseGrowth = (long) (amount.doubleValue() * categoryFactor);
        // 频次奖励：本月第 5 单起每单额外 +10
        long frequencyBonus = Math.max(0, (orderCount - 4) * 10L);
        return baseGrowth + frequencyBonus;
    }
}
