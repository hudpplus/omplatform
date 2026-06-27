package com.omplatform.marketing.coupon;

import com.omplatform.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.omplatform.common.constant.ErrorCode.COUPON_UNAVAILABLE;

/**
 * 优惠券服务（ADR-045）。
 * <p>
 * 生命周期：Issue → Lock → Use → Rollback
 */
@Slf4j
@Service
public class CouponService {

    /**
     * 锁定优惠券（下单时调用）。
     *
     * @param couponInstanceId 优惠券实例 ID
     * @param orderNo          订单号
     */
    public void lockCoupon(String couponInstanceId, String orderNo) {
        // 1. 校验券状态为 AVAILABLE
        // 2. 更新为 LOCKED，关联 orderNo
        log.info("锁定优惠券: instance={}, order={}", couponInstanceId, orderNo);
    }

    /**
     * 核销优惠券（支付成功时调用）。
     */
    public void useCoupon(String couponInstanceId, String orderNo) {
        // LOCKED → USED
        log.info("核销优惠券: instance={}, order={}", couponInstanceId, orderNo);
    }

    /**
     * 回退优惠券（取消订单/退款时调用）。
     */
    public void rollbackCoupon(String couponInstanceId, String orderNo) {
        // LOCKED/USED → AVAILABLE
        log.info("回退优惠券: instance={}, order={}", couponInstanceId, orderNo);
    }

    /**
     * 计算优惠券折扣金额。
     */
    public java.math.BigDecimal calculateDiscount(String couponInstanceId,
                                                   java.math.BigDecimal orderAmount) {
        // 根据券模板计算：满减/折扣/直减
        return java.math.BigDecimal.ZERO;
    }
}
