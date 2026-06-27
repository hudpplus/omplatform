package com.omplatform.cart.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 购物车事件发布器（ADR-044 §7）。
 */
@Slf4j
@Component
public class CartEventPublisher {

    @Autowired
    private RocketMQTemplate rocketMq;

    /*
    public CartEventPublisher(RocketMQTemplate rocketMq) {
        this.rocketMq = rocketMq;
    }
    */

    /** 加购事件 */
    public void itemAdded(String cartId, String skuId, int quantity) {
        publish("cart_item_added", new CartEvent(cartId, skuId, quantity));
    }

    /** 移出事件 */
    public void itemRemoved(String cartId, String skuId) {
        publish("cart_item_removed", new CartEvent(cartId, skuId, 0));
    }

    /** 合并事件 */
    public void cartMerged(String anonymousCartId, String userId, String targetCartId) {
        publish("cart_merged", new CartMergeEvent(anonymousCartId, userId, targetCartId));
    }

    /** 过期事件 */
    public void cartExpired(String cartId) {
        publish("cart_expired", new CartEvent(cartId, null, 0));
    }

    /** 价格刷新事件 */
    public void priceRefreshRequested(String cartId) {
        publish("cart_price_refresh", new CartEvent(cartId, null, 0));
    }

    private void publish(String topic, Object payload) {
        log.info("[事件] 发布 {}: {}", topic, payload);
        rocketMq.convertAndSend("cart-" + topic, payload);
    }

    // ========== 事件体 ==========

    public record CartEvent(String cartId, String skuId, int quantity) {}
    public record CartMergeEvent(String anonymousCartId, String userId, String targetCartId) {}
}
