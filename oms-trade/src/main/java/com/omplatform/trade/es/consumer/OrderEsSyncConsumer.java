package com.omplatform.trade.es.consumer;

import com.omplatform.trade.event.OrderEventPublisher.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ES 同步消费者 — 订阅订单生命周期事件并同步到 ES。
 * <p>
 * 所有 topic 共享同一个 consumerGroup {@code trade-es-sync-group}，
 * RocketMQ 根据 topic 分发到对应的 listener。
 */
@Slf4j
@Component
public class OrderEsSyncConsumer {

    @Autowired
    private OrderEsSyncService syncService;

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_CREATED", consumerGroup = "trade-es-sync-group")
    public class OrderCreatedListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_PAID", consumerGroup = "trade-es-sync-group")
    public class OrderPaidListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_SHIPPED", consumerGroup = "trade-es-sync-group")
    public class OrderShippedListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_COMPLETED", consumerGroup = "trade-es-sync-group")
    public class OrderCompletedListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_CANCELLED", consumerGroup = "trade-es-sync-group")
    public class OrderCancelledListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }

    @Component
    @RocketMQMessageListener(topic = "order-ORDER_HELD", consumerGroup = "trade-es-sync-group")
    public class OrderHeldListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            syncService.syncOrder(event.orderNo(), event.businessType());
        }
    }
}
