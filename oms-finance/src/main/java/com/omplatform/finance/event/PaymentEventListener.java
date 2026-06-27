package com.omplatform.finance.event;

import com.omplatform.api.order.OrderService;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;
import com.omplatform.finance.event.PaymentEventPublisher.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付事件监听器。
 */
@Slf4j
@Component
public class PaymentEventListener {

    /** 支付成功 → 通知交易系统更新订单状态 */
    @Component
    @RocketMQMessageListener(topic = "payment-PAYMENT_SUCCESS", consumerGroup = "finance-pay-success-consumer")
    public static class PaymentSuccessListener implements RocketMQListener<PaymentEvent> {

        @DubboReference
        private OrderService orderService;

        @Override
        public void onMessage(PaymentEvent event) {
            log.info("[监听] 支付成功: orderNo={}, transactionId={}, amount={}",
                    event.orderNo(), event.transactionId(), event.amount());

            // 调用 oms-trade.OrderService.processPayment() 更新订单状态
            try {
                TransitionContextDTO ctx = new TransitionContextDTO();
                ctx.setOperatorId("SYSTEM");
                ctx.setOperatorType("SYSTEM");
                ctx.setSource("PAYMENT_CALLBACK");
                ctx.setReason("支付回调-支付成功");

                ApiResult<com.omplatform.api.order.dto.OrderDTO> result = orderService.processPayment(
                        event.orderNo(), event.amount(), event.channel(),
                        event.transactionId(), ctx);

                if (result.isSuccess()) {
                    log.info("订单状态已更新为 PAID: orderNo={}", event.orderNo());
                } else {
                    log.warn("订单状态更新失败: orderNo={}, msg={}",
                            event.orderNo(), result.getMessage());
                }
            } catch (Exception e) {
                log.error("支付回调处理异常: orderNo={}, error={}",
                        event.orderNo(), e.getMessage());
            }
        }
    }

    /** 退款成功 → 通知交易系统 */
    @Component
    @RocketMQMessageListener(topic = "payment-REFUND_SUCCESS", consumerGroup = "finance-refund-consumer")
    public static class RefundSuccessListener implements RocketMQListener<PaymentEvent> {

        @DubboReference
        private OrderService orderService;

        @Override
        public void onMessage(PaymentEvent event) {
            log.info("[监听] 退款成功: orderNo={}, refundNo={}", event.orderNo(), event.transactionId());

            // 调用 oms-trade OrderService 更新订单状态为 REFUNDED
            try {
                TransitionContextDTO ctx = new TransitionContextDTO();
                ctx.setOperatorId("SYSTEM");
                ctx.setOperatorType("SYSTEM");
                ctx.setSource("PAYMENT_CALLBACK");
                ctx.setReason("退款成功");

                ApiResult<com.omplatform.api.order.dto.OrderDTO> result = orderService.refundOrder(
                        event.orderNo(), ctx);

                if (result.isSuccess()) {
                    log.info("订单状态已更新为 REFUNDED: orderNo={}", event.orderNo());
                } else {
                    log.warn("退款状态更新失败: orderNo={}, msg={}",
                            event.orderNo(), result.getMessage());
                }
            } catch (Exception e) {
                log.error("退款回调处理异常: orderNo={}, error={}",
                        event.orderNo(), e.getMessage());
            }
        }
    }
}
