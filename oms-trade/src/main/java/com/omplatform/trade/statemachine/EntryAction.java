package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.OrderStatus;

/**
 * 入口动作（ADR-039 §1.7）。
 * 进入某个状态时自动执行（如注册超时任务、发布事件）。
 */
@FunctionalInterface
public interface EntryAction {

    /**
     * @param orderId 订单号
     * @param from    来源状态
     * @param context 转换上下文
     */
    void onEntry(String orderId, OrderStatus from, TransitionContext context);
}
