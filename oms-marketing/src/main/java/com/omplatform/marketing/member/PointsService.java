package com.omplatform.marketing.member;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 积分服务（ADR-046）。
 * <p>
 * 规则：
 * <ul>
 *   <li>Earn rate 1%-5%（按等级，等级越高比例越高）</li>
 *   <li>100:1 抵扣（100 积分 = 1 元）</li>
 *   <li>12 月滚动过期</li>
 * </ul>
 */
@Slf4j
@Service
public class PointsService {

    /**
     * 计算消费应得积分。
     *
     * @param tier   会员等级
     * @param amount 支付金额
     * @return 积分数量
     */
    public long calculateEarnPoints(MemberTierService.Tier tier, BigDecimal amount) {
        double rate = switch (tier) {
            case L0 -> 0.01;
            case L1 -> 0.01;
            case L2 -> 0.02;
            case L3 -> 0.03;
            case L4 -> 0.04;
            case L5 -> 0.05;
        };
        return (long) (amount.doubleValue() * rate);
    }

    /**
     * 计算积分可抵扣金额。
     *
     * @param points 积分数量
     * @return 可抵扣金额
     */
    public BigDecimal calculateDiscount(long points) {
        // 100 积分 = 1 元
        return BigDecimal.valueOf(points / 100);
    }
}
