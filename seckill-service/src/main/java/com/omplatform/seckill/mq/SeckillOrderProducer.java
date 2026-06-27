package com.omplatform.seckill.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单创建消息生产者。
 * <p>
 * 在 Redis 抢库存成功后发送此消息，触发消费端异步执行订单创建 Saga。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /** Topic：秒杀订单创建请求（与消费端 {@code @RocketMQMessageListener(topic)} 一致） */
    private static final String TOPIC = "seckill-ORDER_CREATE_REQUEST";

    /**
     * 发送秒杀订单创建消息。
     *
     * @param message 订单创建消息
     */
    public void sendOrderMessage(SeckillOrderMessage message) {
        Message<SeckillOrderMessage> msg = MessageBuilder.withPayload(message)
                .setHeader("eventType", "ORDER_CREATE_REQUEST")
                .setHeader("orderNo", message.orderNo())
                .build();
        rocketMQTemplate.send(TOPIC, msg);
        log.info("[秒杀MQ] 已发送: orderNo={}, activityId={}, buyerId={}",
                message.orderNo(), message.activityId(), message.buyerId());
    }
}
