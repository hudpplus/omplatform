package com.omplatform.fulfillment.inventory;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 库存事件发布器（ADR-043 §12）。
 * <p>
 * 库存操作完成后发布 RocketMQ 事件，供订单/通知/渠道同步等下游消费。
 */
@Slf4j
@Component
public class InventoryEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMq;

    /** 库存预占成功 */
    public void reserved(String skuId, String holdId, int quantity, String orderNo) {
        publish("RESERVED", new InventoryEvent(skuId, holdId, quantity, orderNo, "RESERVED", LocalDateTime.now()));
    }

    /** 库存确认扣减 */
    public void confirmed(String skuId, String holdId, int quantity, String orderNo) {
        publish("CONFIRMED", new InventoryEvent(skuId, holdId, quantity, orderNo, "CONFIRMED", LocalDateTime.now()));
    }

    /** 库存预占释放 */
    public void released(String skuId, String holdId, int quantity, String orderNo) {
        publish("RELEASED", new InventoryEvent(skuId, holdId, quantity, orderNo, "RELEASED", LocalDateTime.now()));
    }

    /** Saga 补偿撤销扣减 */
    public void undone(String skuId, String holdId, int quantity, String orderNo) {
        publish("UNDONE", new InventoryEvent(skuId, holdId, quantity, orderNo, "UNDONE", LocalDateTime.now()));
    }

    /** 库存冻结 */
    public void frozen(String skuId, String reason) {
        InventoryEvent event = new InventoryEvent(skuId, null, 0, null, "FROZEN", LocalDateTime.now());
        publish("FROZEN", event);
        log.info("[事件] 库存已冻结: skuId={}, reason={}", skuId, reason);
    }

    /** 库存解冻 */
    public void unfrozen(String skuId) {
        InventoryEvent event = new InventoryEvent(skuId, null, 0, null, "UNFROZEN", LocalDateTime.now());
        publish("UNFROZEN", event);
        log.info("[事件] 库存已解冻: skuId={}", skuId);
    }

    /** 超时预占释放 */
    public void holdTimeout(String skuId, String holdId, int quantity, String orderNo) {
        InventoryEvent event = new InventoryEvent(skuId, holdId, quantity, orderNo, "TIMEOUT", LocalDateTime.now());
        publish("TIMEOUT", event);
    }

    private void publish(String topicSuffix, InventoryEvent event) {
        try {
            Message<InventoryEvent> message = MessageBuilder.withPayload(event)
                    .setHeader("eventType", topicSuffix)
                    .build();
            rocketMq.send("inventory-" + topicSuffix, message);
            log.debug("[事件] 发布 inventory-{}: skuId={}, holdId={}", topicSuffix, event.skuId(), event.holdId());
        } catch (Exception e) {
            log.warn("[事件] 发布失败 inventory-{}: skuId={}, err={}", topicSuffix, event.skuId(), e.getMessage());
        }
    }

    /**
     * 库存事件记录。
     */
    public record InventoryEvent(
            String skuId,
            String holdId,
            int quantity,
            String orderNo,
            String status,
            LocalDateTime occurredAt
    ) {}
}
