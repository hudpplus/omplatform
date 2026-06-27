package com.omplatform.finance.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付事件发布器。
 */
@Slf4j
@Component
public class PaymentEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMq;

    /*public PaymentEventPublisher(RocketMQTemplate rocketMq) {
        this.rocketMq = rocketMq;
    }*/

    /** 支付成功 */
    public void paymentSuccess(String orderNo, String channel, String transactionId, BigDecimal amount) {
        PaymentEvent event = new PaymentEvent(orderNo, channel, transactionId, amount, "SUCCESS", LocalDateTime.now());
        publish("PAYMENT_SUCCESS", event);
    }

    /** 支付失败 */
    public void paymentFailed(String orderNo, String channel, String errorMsg) {
        PaymentEvent event = new PaymentEvent(orderNo, channel, null, BigDecimal.ZERO, "FAILED", LocalDateTime.now());
        publish("PAYMENT_FAILED", event);
    }

    /** 退款成功 */
    public void refundSuccess(String orderNo, String channel, String refundNo, BigDecimal amount) {
        PaymentEvent event = new PaymentEvent(orderNo, channel, refundNo, amount, "REFUNDED", LocalDateTime.now());
        publish("REFUND_SUCCESS", event);
    }

    private void publish(String topic, PaymentEvent event) {
        log.info("[事件] 发布 {}: orderNo={}", topic, event.orderNo());
        Message<PaymentEvent> message = MessageBuilder.withPayload(event)
                .setHeader("eventType", topic)
                .build();
        rocketMq.send("payment-" + topic, message);
    }

    public record PaymentEvent(
            String orderNo,
            String channel,
            String transactionId,
            BigDecimal amount,
            String status,
            LocalDateTime occurredAt
    ) {}
}
