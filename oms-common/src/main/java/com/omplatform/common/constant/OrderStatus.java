package com.omplatform.common.constant;

/**
 * 订单 13 态定义（ADR-039）。
 * <p>
 * 转换矩阵见 {@code OrderStateTransitionMatrix}。
 */
public enum OrderStatus {

    // ========== 正向流转 ==========

    /** 待支付（初始状态） */
    PENDING_PAY,

    /** 已支付 */
    PAID,

    /** 待发货 */
    TO_SHIP,

    /** 已发货 */
    SHIPPED,

    /** 已签收 */
    DELIVERED,

    /** 已完成（终态） */
    COMPLETED,

    // ========== 逆向流转 ==========

    /** 已取消（终态） */
    CANCELLED,

    /** 超时关闭（终态） */
    CLOSED,

    // ========== 退款/退货 ==========

    /** 退款中（仅退款，不退货） */
    REFUNDING,

    /** 退货中（需退货后退款） */
    RETURNING,

    /** 已退款（终态） */
    REFUNDED,

    // ========== 异常/干预 ==========

    /** 挂起（库存不足/风控） */
    HOLD,

    /** 冻结（管理员人工锁定） */
    FROZEN;

    // ========== 快捷判断 ==========

    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == CLOSED || this == REFUNDED;
    }

    public boolean isRefundRelated() {
        return this == REFUNDING || this == RETURNING || this == REFUNDED;
    }

    public boolean isAbnormal() {
        return this == HOLD || this == FROZEN;
    }
}
