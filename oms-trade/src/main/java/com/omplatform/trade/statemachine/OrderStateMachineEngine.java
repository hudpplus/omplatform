package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.ErrorCode;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.common.exception.BizException;
import com.omplatform.common.exception.OptimisticLockException;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机引擎实现（ADR-039 §1.5）。
 * <p>
 * 自定义轻量引擎，基于 EnumMap 转换表 + Guard/Action 注册表。
 * 非 Spring Statemachine —— 13 态场景无需引入重型框架。
 */
@Slf4j
@Component
public class OrderStateMachineEngine implements StateMachineEngine {

    @Autowired
    private OrderStateTransitionMatrix matrix;
    @Autowired
    private OrderRepository orderRepository;

    /** 守卫注册表：Map<(当前状态, 目标状态), List<Guard>> */
    private final Map<TransitionKey, List<StateGuard>> guardRegistry = new HashMap<>();

    /** 入口动作注册表：Map<目标状态, List<EntryAction>> */
    private final Map<OrderStatus, List<EntryAction>> entryActions = new HashMap<>();

    /** 出口动作注册表：Map<当前状态, List<ExitAction>> */
    private final Map<OrderStatus, List<ExitAction>> exitActions = new HashMap<>();

    /* public OrderStateMachineEngine(OrderStateTransitionMatrix matrix,
                                   OrderRepository orderRepository) {
        this.matrix = matrix;
        this.orderRepository = orderRepository;
    } */

    @Override
    public OrderStatus transition(String orderId, OrderStatus current, OrderStatus target,
                                  TransitionContext context) {
        log.info("状态转换: order={}, {} → {}, operator={}, reason={}",
                orderId, current, target, context.getOperatorId(), context.getReason());

        // 1. 校验合法性
        if (!matrix.isValid(current, target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                    String.format("非法状态转换: %s → %s", current, target));
        }

        // 2. 执行守卫（除非跳过）
        if (!context.isSkipGuards()) {
            evaluateGuards(orderId, current, target, context);
        }

        // 3. 乐观锁获取订单
        OrderEntity order = orderRepository.findByIdForUpdate(orderId);
        if (order.getStatus() != current) {
            throw new OptimisticLockException(orderId, current, order.getStatus());
        }

        // 4. 执行出口动作（离开 oldState）
        executeExitActions(current, orderId, context);

        // 5. CAS 更新状态
        int updated = orderRepository.updateStatusWithVersionCheck(
                orderId, current, target, order.getVersion());
        if (updated == 0) {
            throw new OptimisticLockException(orderId, current, target);
        }

        // 6. 执行入口动作（进入 newState）
        executeEntryActions(target, orderId, current, context);

        log.info("状态转换成功: order={}, {} → {}", orderId, current, target);
        return target;
    }

    @Override
    public List<OrderStatus> transitionBatch(List<String> orderIds, OrderStatus current,
                                              OrderStatus target, TransitionContext context) {
        List<OrderStatus> results = new ArrayList<>();
        for (String orderId : orderIds) {
            results.add(transition(orderId, current, target, context));
        }
        return results;
    }

    @Override
    public boolean canTransition(OrderStatus current, OrderStatus target) {
        return matrix.isValid(current, target);
    }

    @Override
    public Set<OrderStatus> allowedTargets(OrderStatus current) {
        return matrix.getAllowedTargets(current);
    }

    @Override
    public void registerGuard(OrderStatus current, OrderStatus target, StateGuard guard) {
        guardRegistry.computeIfAbsent(new TransitionKey(current, target), k -> new ArrayList<>())
                .add(guard);
        log.debug("注册守卫: {} → {}, guard={}", current, target, guard.getClass().getSimpleName());
    }

    @Override
    public void registerEntryAction(OrderStatus state, EntryAction action) {
        entryActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
    }

    @Override
    public void registerExitAction(OrderStatus state, ExitAction action) {
        exitActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
    }

    // ========== 内部 ==========

    private void evaluateGuards(String orderId, OrderStatus current,
                                OrderStatus target, TransitionContext context) {
        List<StateGuard> guards = guardRegistry.get(new TransitionKey(current, target));
        if (guards == null) return;
        for (StateGuard guard : guards) {
            if (!guard.evaluate(orderId, context)) {
                log.warn("守卫拒绝: order={}, {} → {}, reason={}",
                        orderId, current, target, guard.rejectReason());
                throw new BizException(ErrorCode.ORDER_STATE_GUARD_REJECTED, guard.rejectReason());
            }
        }
    }

    private void executeEntryActions(OrderStatus state, String orderId,
                                     OrderStatus from, TransitionContext context) {
        List<EntryAction> actions = entryActions.getOrDefault(state, Collections.emptyList());
        for (EntryAction action : actions) {
            action.onEntry(orderId, from, context);
        }
    }

    private void executeExitActions(OrderStatus state, String orderId,
                                    TransitionContext context) {
        List<ExitAction> actions = exitActions.getOrDefault(state, Collections.emptyList());
        for (ExitAction action : actions) {
            action.onExit(orderId, null, context);
        }
    }

    /** 内部 key：用于 Guard 注册表 lookup */
    record TransitionKey(OrderStatus current, OrderStatus target) {}
}
