package com.omplatform.trade.service.atomic;

import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.saga.outbox.OutboxRepository;
import com.omplatform.saga.outbox.OutboxEntity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 原子服务模板基类（ADR-039 §2.1）。
 * <p>
 * 所有订单操作服务继承此类，获得统一生命周期：
 * <pre>
 *   1. validate()             → 前置校验
 *   2. transition()           → 状态转换（状态机引擎）
 *   3. doExecute()            → 执行业务逻辑
 *   4. publishEvent()         → 发布事件
 * </pre>
 * <p>
 * compensate() 可选覆盖，用于 Saga 补偿。
 */
@Slf4j
public abstract class AbstractAtomicOrderService {

    @Autowired
    protected StateMachineEngine stateMachineEngine;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired(required = false)
    protected OutboxRepository outboxRepository;

    @Autowired(required = false)
    protected ObjectMapper objectMapper;

    /**
     * 执行订单操作（模板方法）。
     *
     * @param orderId 订单号
     * @param context 转换上下文
     * @return 新状态
     */
    public OrderStatus execute(String orderId, TransitionContext context) {
        // 1. 前置校验
        validate(orderId, context);

        // 2. 状态转换
        OrderStatus current = resolveCurrentStatus(orderId);
        OrderStatus target = resolveTargetStatus();
        OrderStatus newStatus = stateMachineEngine.transition(orderId, current, target, context);

        // 3. 执行业务逻辑
        doExecute(orderId, context);

        // 4. 发布事件
        //  订单入库后发 MQ 的核心用途是：支付完成 → 异步扣库存 + 核销券 + 发积分
        publishEvent(orderId, newStatus, context);

        return newStatus;
    }

    /**
     * 补偿操作（Saga 集成时使用）。
     */
    public void compensate(String orderId, TransitionContext context) {
        log.info("补偿: order={}, service={}", orderId, getClass().getSimpleName());
        doCompensate(orderId, context);
    }

    // ========== 子类实现 ==========

    /** 前置校验 */
    protected abstract void validate(String orderId, TransitionContext context);

    /** 目标状态 */
    protected abstract OrderStatus resolveTargetStatus();

    /** 业务执行 */
    protected abstract void doExecute(String orderId, TransitionContext context);

    /** 事件发布 */
    protected abstract void publishEvent(String orderId, OrderStatus newStatus,
                                          TransitionContext context);

    /** 补偿（可选覆盖） */
    protected void doCompensate(String orderId, TransitionContext context) {
        // default no-op
    }

    /**
     * Write an outbox message that will be dispatched asynchronously.
     * This runs inside the same DB transaction as the calling service method.
     * If no OutboxRepository is available, this is a no-op.
     */
    protected void writeOutbox(String topic, String payload) {
        if (outboxRepository == null) return;
        OutboxEntity m = new OutboxEntity();
        m.setId(UUID.randomUUID().toString());
        m.setTopic(topic);
        m.setPayload(payload);
        m.setStatus("PENDING");
        m.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(m);
    }

    private OrderStatus resolveCurrentStatus(String orderId) {
        var e = orderRepository.getById(orderId);
        return e == null ? null : e.getStatus();
    }
}
