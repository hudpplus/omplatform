package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.OrderStatus;

/**
 * 出口动作（ADR-039 §1.7）。
 * 离开某个状态时自动执行（如取消定时器）。
 */
@FunctionalInterface
public interface ExitAction {

    /**
     * @param orderId 订单号
     * @param to      目标状态
     * @param context 转换上下文
     */
    void onExit(String orderId, OrderStatus to, TransitionContext context);
}
