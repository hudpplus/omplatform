package com.omplatform.trade.event;

import com.omplatform.common.constant.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单事件发布器（ADR-038 §4）。
 */
@Slf4j
@Component
public class OrderEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMq;

    /* public OrderEventPublisher(RocketMQTemplate rocketMq) {
        this.rocketMq = rocketMq;
    } */

    /** 订单创建 */
    public void orderCreated(String orderNo, String buyerId, OrderStatus status) {
        publish("ORDER_CREATED", new OrderEvent(orderNo, buyerId, status, null, LocalDateTime.now()));
    }

    /** 订单支付成功 */
    public void orderPaid(String orderNo, String buyerId, String transactionId) {
        publish("ORDER_PAID", new OrderEvent(orderNo, buyerId, OrderStatus.PAID, transactionId, LocalDateTime.now()));
    }

    /** 订单发货 */
    public void orderShipped(String orderNo, String logisticsNo) {
        publish("ORDER_SHIPPED", new OrderEvent(orderNo, null, OrderStatus.SHIPPED, logisticsNo, LocalDateTime.now()));
    }

    /** 订单完成 */
    public void orderCompleted(String orderNo) {
        publish("ORDER_COMPLETED", new OrderEvent(orderNo, null, OrderStatus.COMPLETED, null, LocalDateTime.now()));
    }

    /** 订单取消 */
    public void orderCancelled(String orderNo, String reason) {
        publish("ORDER_CANCELLED", new OrderEvent(orderNo, null, OrderStatus.CANCELLED, reason, LocalDateTime.now()));
    }

    /** 订单挂起（风控） */
    public void orderHeld(String orderNo, String reason) {
        publish("ORDER_HELD", new OrderEvent(orderNo, null, OrderStatus.HOLD, reason, LocalDateTime.now()));
    }

    private void publish(String topic, OrderEvent event) {
        log.info("[事件] 发布 {}: orderNo={}, buyerId={}, status={}",
                topic, event.orderNo(), event.buyerId(), event.status());
        Message<OrderEvent> message = MessageBuilder.withPayload(event)
                .setHeader("eventType", topic)
                .build();
        rocketMq.send("order-" + topic, message);
    }

    public record OrderEvent(
            String orderNo,
            String buyerId,
            OrderStatus status,
            String extra,
            LocalDateTime occurredAt
    ) {}
}
