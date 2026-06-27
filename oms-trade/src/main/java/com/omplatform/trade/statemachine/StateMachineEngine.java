package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.OrderStatus;

import java.util.List;
import java.util.Set;

/**
 * 订单状态机引擎（ADR-039 §1.3）。
 * <p>
 * 职责：根据转换矩阵校验状态转换合法性，执行守卫条件和生命周期钩子。
 * 被原子服务和 Saga 步骤调用。
 */
public interface StateMachineEngine {

    /**
     * 执行状态转换。
     *
     * @param orderId 订单号
     * @param current 当前状态（乐观锁校验）
     * @param target  目标状态
     * @param context 转换上下文
     * @return 转换后的新状态
     * @throws com.omplatform.common.exception.OptimisticLockException 并发冲突
     * @throws com.omplatform.common.exception.BizException 守卫不满足
     */
    OrderStatus transition(String orderId, OrderStatus current, OrderStatus target,
                           TransitionContext context);

    /**
     * 批量转换（拆单场景）。子订单独立转换，任一失败整体回滚。
     */
    List<OrderStatus> transitionBatch(List<String> orderIds, OrderStatus current,
                                      OrderStatus target, TransitionContext context);

    /**
     * 仅校验不执行。
     */
    boolean canTransition(OrderStatus current, OrderStatus target);

    /**
     * 获取某状态的合法出边。
     */
    Set<OrderStatus> allowedTargets(OrderStatus current);

    /**
     * 注册守卫。
     */
    void registerGuard(OrderStatus current, OrderStatus target, StateGuard guard);

    /**
     * 注册入口动作。
     */
    void registerEntryAction(OrderStatus state, EntryAction action);

    /**
     * 注册出口动作。
     */
    void registerExitAction(OrderStatus state, ExitAction action);
}
