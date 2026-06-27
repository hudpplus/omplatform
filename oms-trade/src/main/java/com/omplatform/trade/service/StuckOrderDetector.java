package com.omplatform.trade.service;

import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卡单检测器（ADR-039 §3.2）。
 * <p>
 * 定期扫描超时未转换的订单，按超时矩阵配置触发告警或自动操作。
 * <p>
 * 超时矩阵由 Apollo 配置驱动，默认值见 {@link #DEFAULT_TIMEOUT_MATRIX}。
 */
@Slf4j
@Component
public class StuckOrderDetector {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StateMachineEngine stateMachineEngine;

    /** 默认超时矩阵（分钟） */
    private static final Map<OrderStatus, TimeoutConfig> DEFAULT_TIMEOUT_MATRIX = new ConcurrentHashMap<>();

    static {
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.PENDING_PAY,
                new TimeoutConfig(30, TimeoutAction.AUTO_CLOSE));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.PAID,
                new TimeoutConfig(1440, TimeoutAction.ALERT_ONLY));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.TO_SHIP,
                new TimeoutConfig(4320, TimeoutAction.ALERT_ONLY));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.SHIPPED,
                new TimeoutConfig(10080, TimeoutAction.AUTO_CONFIRM));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.HOLD,
                new TimeoutConfig(2880, TimeoutAction.ALERT_ONLY));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.REFUNDING,
                new TimeoutConfig(4320, TimeoutAction.RECONCILE));
        DEFAULT_TIMEOUT_MATRIX.put(OrderStatus.RETURNING,
                new TimeoutConfig(21600, TimeoutAction.RECONCILE));
    }

    /* public StuckOrderDetector(OrderRepository orderRepository,
                              StateMachineEngine stateMachineEngine) {
        this.orderRepository = orderRepository;
        this.stateMachineEngine = stateMachineEngine;
    } */

    /**
     * 每 5 分钟扫描一次卡单。
     */
    @Scheduled(fixedDelay = 300_000)
    public void detect() {
        log.debug("卡单检测器扫描开始...");

        for (Map.Entry<OrderStatus, TimeoutConfig> entry : DEFAULT_TIMEOUT_MATRIX.entrySet()) {
            OrderStatus status = entry.getKey();
            TimeoutConfig config = entry.getValue();

            List<OrderEntity> stuckOrders = orderRepository.findStuckOrders(status, config.maxDurationMinutes());

            for (OrderEntity order : stuckOrders) {
                log.warn("发现卡单: orderNo={}, status={}, duration>{}min",
                        order.getOrderNo(), status, config.maxDurationMinutes());
                handleStuckOrder(order, config);
            }
        }
    }

    private void handleStuckOrder(OrderEntity order, TimeoutConfig config) {
        switch (config.action) {
            case AUTO_CLOSE -> {
                log.info("自动关闭超时订单: orderNo={}", order.getOrderNo());
                stateMachineEngine.transition(order.getOrderNo(),
                        order.getStatus(), OrderStatus.CLOSED,
                        TransitionContext.systemContext("支付超时自动关闭"));
            }
            case AUTO_CONFIRM -> {
                log.info("自动确认收货: orderNo={}", order.getOrderNo());
                stateMachineEngine.transition(order.getOrderNo(),
                        order.getStatus(), OrderStatus.DELIVERED,
                        TransitionContext.systemContext("超时自动确认收货"));
            }
            case ALERT_ONLY ->
                    log.warn("卡单告警: orderNo={}, status={}, 请人工处理", order.getOrderNo(), order.getStatus());
            case RECONCILE ->
                    log.warn("触发对账: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
        }
    }

    // ========== 类型定义 ==========

    record TimeoutConfig(int maxDurationMinutes, TimeoutAction action) {}

    enum TimeoutAction {
        AUTO_CLOSE,     // 自动关闭
        AUTO_CONFIRM,   // 自动确认收货
        ALERT_ONLY,     // 仅告警
        RECONCILE       // 触发对账
    }
}
