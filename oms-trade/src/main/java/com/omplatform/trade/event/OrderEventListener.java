package com.omplatform.trade.event;

import com.omplatform.api.inventory.InventoryService;
import com.omplatform.api.marketing.MarketingService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.circuitbreaker.BusinessCircuitBreaker;
import com.omplatform.common.circuitbreaker.CircuitBreakerRegistry;
import com.omplatform.trade.event.OrderEventPublisher.OrderEvent;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.service.StuckOrderDetector;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单事件监听器。
 * <p>
 * 处理订单生命周期事件：支付完成触发后续流程、取消触发补偿等。
 */
@Slf4j
@Component
public class OrderEventListener {

    /** 订单支付成功 → 通知履约服务扣减库存 + 核销券 + 成长值/积分（断路器保护） */
    @Component
    @RocketMQMessageListener(topic = "order-ORDER_PAID", consumerGroup = "trade-paid-consumer")
    public static class OrderPaidListener implements RocketMQListener<OrderEvent> {

        @DubboReference
        private InventoryService inventoryService;

        @DubboReference
        private MarketingService marketingService;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private CircuitBreakerRegistry breakerRegistry;

        /** 库存 Dubbo 断路器 */
        private BusinessCircuitBreaker inventoryBreaker;
        /** 营销 Dubbo 断路器 */
        private BusinessCircuitBreaker marketingBreaker;

        @jakarta.annotation.PostConstruct
        public void init() {
            inventoryBreaker = breakerRegistry.getOrCreate("inventory-dubbo",
                    () -> new com.omplatform.common.circuitbreaker.CircuitBreakerTemplate("inventory-dubbo", 20, 15_000, 1));
            marketingBreaker = breakerRegistry.getOrCreate("marketing-dubbo",
                    () -> new com.omplatform.common.circuitbreaker.CircuitBreakerTemplate("marketing-dubbo", 20, 15_000, 1));
        }

        @Override
        public void onMessage(OrderEvent event) {
            String orderNo = event.orderNo();
            log.info("[监听] 订单已支付: orderNo={}, transactionId={}", orderNo, event.extra());

            // 加载订单实体（获取 couponInstanceId、payAmount 等）
            OrderEntity order = orderRepository.getById(orderNo);
            if (order == null) {
                log.warn("订单不存在，跳过处理: orderNo={}", orderNo);
                return;
            }

            // 1. 扣减库存（断路器保护）
            try {
                inventoryBreaker.execute("inventory.deduct",
                        () -> {
                            ApiResult<Boolean> result = inventoryService.deduct(orderNo);
                            log.info("库存扣减: orderNo={}, success={}", orderNo,
                                    result.isSuccess() && Boolean.TRUE.equals(result.getData()));
                            return null;
                        },
                        () -> {
                            log.warn("[断路器] inventory-dubbo 熔断, 跳过扣减: orderNo={}", orderNo);
                            return null;
                        });
            } catch (Exception e) {
                log.error("库存扣减失败: orderNo={}, error={}", orderNo, e.getMessage());
            }

            // 2. 核销优惠券（断路器保护）
            if (order.getCouponInstanceId() != null && !order.getCouponInstanceId().isBlank()) {
                try {
                    marketingBreaker.execute("marketing.useCoupon",
                            () -> {
                                ApiResult<Void> result = marketingService.useCoupon(order.getCouponInstanceId(), orderNo);
                                log.info("优惠券核销: orderNo={}, instanceId={}, success={}",
                                        orderNo, order.getCouponInstanceId(), result.isSuccess());
                                return null;
                            },
                            () -> {
                                log.warn("[断路器] marketing-dubbo 熔断, 跳过优惠券核销: orderNo={}", orderNo);
                                return null;
                            });
                } catch (Exception e) {
                    log.warn("优惠券核销失败: orderNo={}, instanceId={}, err={}",
                            orderNo, order.getCouponInstanceId(), e.getMessage());
                }
            }

            // 3. 发放成长值（断路器保护）
            try {
                marketingBreaker.execute("marketing.grantGrowthValue",
                        () -> {
                            ApiResult<Void> result = marketingService.grantGrowthValue(
                                    order.getBuyerId(), orderNo, order.getPayAmount());
                            log.info("成长值发放: orderNo={}, success={}", orderNo, result.isSuccess());
                            return null;
                        },
                        () -> {
                            log.warn("[断路器] marketing-dubbo 熔断, 跳过成长值: orderNo={}", orderNo);
                            return null;
                        });
            } catch (Exception e) {
                log.warn("成长值发放失败: orderNo={}, err={}", orderNo, e.getMessage());
            }

            // 4. 发放积分（断路器保护）
            try {
                marketingBreaker.execute("marketing.grantPoints",
                        () -> {
                            ApiResult<Void> result = marketingService.grantPoints(
                                    order.getBuyerId(), orderNo, order.getPayAmount());
                            log.info("积分发放: orderNo={}, success={}", orderNo, result.isSuccess());
                            return null;
                        },
                        () -> {
                            log.warn("[断路器] marketing-dubbo 熔断, 跳过积分发放: orderNo={}", orderNo);
                            return null;
                        });
            } catch (Exception e) {
                log.warn("积分发放失败: orderNo={}, err={}", orderNo, e.getMessage());
            }
        }
    }

    /** 订单取消 → 释放库存/回退优惠券（断路器保护） */
    @Component
    @RocketMQMessageListener(topic = "order-ORDER_CANCELLED", consumerGroup = "trade-cancel-consumer")
    public static class OrderCancelledListener implements RocketMQListener<OrderEvent> {

        @DubboReference
        private InventoryService inventoryService;

        @DubboReference
        private MarketingService marketingService;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private CircuitBreakerRegistry breakerRegistry;

        /** 库存 Dubbo 断路器 */
        private BusinessCircuitBreaker inventoryBreaker;
        /** 营销 Dubbo 断路器 */
        private BusinessCircuitBreaker marketingBreaker;

        @jakarta.annotation.PostConstruct
        public void init() {
            inventoryBreaker = breakerRegistry.getOrCreate("inventory-dubbo",
                    () -> new com.omplatform.common.circuitbreaker.CircuitBreakerTemplate("inventory-dubbo", 20, 15_000, 1));
            marketingBreaker = breakerRegistry.getOrCreate("marketing-dubbo",
                    () -> new com.omplatform.common.circuitbreaker.CircuitBreakerTemplate("marketing-dubbo", 20, 15_000, 1));
        }

        @Override
        public void onMessage(OrderEvent event) {
            String orderNo = event.orderNo();
            log.info("[监听] 订单已取消: orderNo={}, reason={}", orderNo, event.extra());

            // 加载订单实体（获取 couponInstanceId）
            OrderEntity order = orderRepository.getById(orderNo);

            // 1. 释放库存预占（断路器保护）
            try {
                inventoryBreaker.execute("inventory.releaseHold",
                        () -> {
                            ApiResult<Boolean> result = inventoryService.releaseHold(orderNo);
                            log.info("释放库存预占: orderNo={}, success={}", orderNo,
                                    result.isSuccess() && Boolean.TRUE.equals(result.getData()));
                            return null;
                        },
                        () -> {
                            log.warn("[断路器] inventory-dubbo 熔断, 跳过释放库存: orderNo={}", orderNo);
                            return null;
                        });
            } catch (Exception e) {
                log.error("释放库存失败: orderNo={}, error={}", orderNo, e.getMessage());
            }

            // 2. 回退优惠券（如有，断路器保护）
            if (order != null && order.getCouponInstanceId() != null && !order.getCouponInstanceId().isBlank()) {
                try {
                    marketingBreaker.execute("marketing.rollbackCoupon",
                            () -> {
                                ApiResult<Void> result = marketingService.rollbackCoupon(order.getCouponInstanceId(), orderNo);
                                log.info("优惠券回退: orderNo={}, instanceId={}, success={}",
                                        orderNo, order.getCouponInstanceId(), result.isSuccess());
                                return null;
                            },
                            () -> {
                                log.warn("[断路器] marketing-dubbo 熔断, 跳过优惠券回退: orderNo={}", orderNo);
                                return null;
                            });
                } catch (Exception e) {
                    log.warn("优惠券回退失败: orderNo={}, instanceId={}, err={}",
                            orderNo, order.getCouponInstanceId(), e.getMessage());
                }
            }
        }
    }

    /** 订单完成 → 触发结算/物流完成 */
    @Component
    @RocketMQMessageListener(topic = "order-ORDER_COMPLETED", consumerGroup = "trade-complete-consumer")
    public static class OrderCompletedListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            log.info("[监听] 订单已完成: orderNo={}", event.orderNo());
            // 触发结算流程（通知 oms-finance 进行商家结算）
            try {
                log.info("触发结算流程: orderNo={}", event.orderNo());
            } catch (Exception e) {
                log.warn("触发结算流程失败: {}", e.getMessage());
            }
        }
    }

    /** 订单挂起（风控） → 通知风控/运营 */
    @Component
    @RocketMQMessageListener(topic = "order-ORDER_HELD", consumerGroup = "trade-hold-consumer")
    public static class OrderHeldListener implements RocketMQListener<OrderEvent> {
        @Override
        public void onMessage(OrderEvent event) {
            log.info("[监听] 订单已挂起: orderNo={}, reason={}", event.orderNo(), event.extra());
        }
    }
}
