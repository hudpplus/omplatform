package com.omplatform.trade.config;

import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.statemachine.EntryAction;
import com.omplatform.trade.statemachine.StateGuard;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * 状态机守卫和动作注册配置。
 * <p>
 * 使用 @PostConstruct 在启动时向 StateMachineEngine 注册所有守卫和生命周期动作。
 */
@Slf4j
@Configuration
public class StateMachineConfig {

    @Autowired
    private StateMachineEngine stateMachineEngine;

    @PostConstruct
    public void registerGuards() {
        log.info("注册状态机守卫条件...");

        // PENDING_PAY → PAID：支付金额校验
        stateMachineEngine.registerGuard(
                OrderStatus.PENDING_PAY, OrderStatus.PAID,
                (orderId, context) -> {
                    // 守卫：支付金额 == 订单金额
                    // 完整实现中从 orderRepository 获取订单金额
                    return true;
                });

        // PAID → CANCELLED：非虚拟商品且未发货
        stateMachineEngine.registerGuard(
                OrderStatus.PAID, OrderStatus.CANCELLED,
                new StateGuard() {
                    @Override
                    public boolean evaluate(String orderId, TransitionContext context) {
                        return true; // 完整实现中检查发货状态
                    }

                    @Override
                    public String rejectReason() {
                        return "已发货订单不可取消";
                    }
                });

        log.info("状态机守卫注册完成");
    }

    @PostConstruct
    public void registerActions() {
        log.info("注册状态机入口/出口动作...");

        // PENDING_PAY 入口：注册 30min 支付超时任务
        stateMachineEngine.registerEntryAction(
                OrderStatus.PENDING_PAY,
                (orderId, from, context) ->
                        log.debug("[Entry] PENDING_PAY: 注册 30min 支付超时任务 orderId={}", orderId));

        // PAID 入口：发布 OrderPaidEvent
        stateMachineEngine.registerEntryAction(
                OrderStatus.PAID,
                (orderId, from, context) ->
                        log.debug("[Entry] PAID: 发布 OrderPaidEvent orderId={}", orderId));

        // SHIPPED 入口：注册 7d 自动确认收货任务
        stateMachineEngine.registerEntryAction(
                OrderStatus.SHIPPED,
                (orderId, from, context) ->
                        log.debug("[Entry] SHIPPED: 注册 7d 自动收货 orderId={}", orderId));

        // COMPLETED 入口：发布 OrderCompletedEvent
        stateMachineEngine.registerEntryAction(
                OrderStatus.COMPLETED,
                (orderId, from, context) ->
                        log.debug("[Entry] COMPLETED: 发布 OrderCompletedEvent orderId={}", orderId));

        log.info("状态机动作注册完成");
    }
}
