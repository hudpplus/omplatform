package com.omplatform.trade.statemachine;

/**
 * 守卫条件（ADR-039 §1.6）。
 * <p>
 * 在状态转换执行前进行业务条件判断。每个守卫专注于一个业务条件。
 *
 * @FunctionalInterface — Lambda / Method Reference 友好
 */
@FunctionalInterface
public interface StateGuard {

    /**
     * 评估守卫条件。
     *
     * @param orderId 订单号
     * @param context 转换上下文
     * @return true = 通过，false = 拒绝转换
     */
    boolean evaluate(String orderId, TransitionContext context);

    /**
     * 守卫被拒绝时的业务提示。
     */
    default String rejectReason() {
        return "非法操作";
    }
}
