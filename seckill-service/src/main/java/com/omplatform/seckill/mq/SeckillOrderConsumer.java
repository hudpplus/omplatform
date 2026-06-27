package com.omplatform.seckill.mq;

import com.google.common.util.concurrent.RateLimiter;
import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.seckill.SeckillOrderSagaService;
import com.omplatform.seckill.entity.SeckillActivityEntity;
import com.omplatform.seckill.service.SeckillActivityService;
import com.omplatform.seckill.service.SeckillOrderHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 秒杀订单创建消费者 — 异步执行下单 Saga（seckill-service 独立部署）。
 * <p>
 * 收到 {@link SeckillOrderProducer} 发送的消息后：
 * <ol>
 *   <li>幂等检查（Dubbo 调用 oms-trade）</li>
 *   <li>构建 CreateOrderRequest</li>
 *   <li>执行 CreateOrderSaga（Dubbo 调用 oms-trade）</li>
 *   <li>成功 → 三级库存扣减（held -1）+ 发布订单创建事件（Dubbo）；失败 → 释放库存</li>
 * </ol>
 * <p>
 * <b>削峰限速：</b>消费端使用 Guava {@link RateLimiter}（令牌桶）精确控制处理速率，
 * 防止瞬时洪峰压垮 DB / Dubbo 下游。<br>
 * 默认 200/s，通过环境变量 {@code SECKILL_CONSUMER_RATE} 配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "seckill-ORDER_CREATE_REQUEST",
        consumerGroup = "trade-seckill-consumer")
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessage> {

    private final SeckillActivityService activityService;
    private final SeckillOrderHandler seckillOrderHandler;

    /** 秒杀订单 Saga 服务（Dubbo 调用 oms-trade） */
    @DubboReference
    private SeckillOrderSagaService seckillOrderSagaService;

    /** 消费限速器（令牌桶），精确控制下游 DB/Dubbo 压力 */
    private RateLimiter rateLimiter;

    /** 默认消费速率个/秒（通过环境变量 SECKILL_CONSUMER_RATE 覆盖） */
    private static final double DEFAULT_CONSUMER_RATE = 200.0;

    @PostConstruct
    public void init() {
        double rate = getEnvDouble("SECKILL_CONSUMER_RATE", DEFAULT_CONSUMER_RATE);
        this.rateLimiter = RateLimiter.create(rate);
        log.info("[秒杀消费] 限速器已初始化: {} ops/s", rate);
    }

    @Override
    public void onMessage(SeckillOrderMessage message) {
        // 限速：令牌桶取令牌，未取到则阻塞等待
        // 防止消费者全力拉取把下游 DB / Dubbo / 支付打爆
        rateLimiter.acquire();

        log.info("[秒杀消费] 收到消息: orderNo={}, activityId={}, buyerId={}",
                message.orderNo(), message.activityId(), message.buyerId());

        // 1. 幂等检查 — Dubbo 调用 oms-trade 查询订单是否存在
        var orderResult = seckillOrderSagaService.getByOrderNo(message.orderNo());
        if (orderResult.isSuccess() && orderResult.getData() != null) {
            log.info("[秒杀消费] 订单已存在，跳过: orderNo={}", message.orderNo());
            return;
        }

        // 2. 获取活动信息
        SeckillActivityEntity activity = activityService.getById(message.activityId());
        if (activity == null) {
            log.warn("[秒杀消费] 活动不存在，释放库存: activityId={}, orderNo={}",
                    message.activityId(), message.orderNo());
            seckillOrderHandler.releaseStock(
                    message.activityId(), message.skuId(), message.quantity(), message.orderNo());
            return;
        }

        // 3. 构建 CreateOrderRequest
        CreateOrderRequest createReq = buildCreateOrderRequest(message, activity);

        // 4. 执行订单创建 Saga（Dubbo 调用 oms-trade）
        Boolean sagaOk;
        try {
            sagaOk = seckillOrderSagaService.createSeckillOrder(createReq).getData();
        } catch (Exception e) {
            log.error("[秒杀消费] Saga Dubbo 调用异常: orderNo={}, error={}", message.orderNo(), e.getMessage(), e);
            seckillOrderHandler.releaseStock(
                    message.activityId(), message.skuId(), message.quantity(), message.orderNo());
            return;
        }

        // 5. 处理结果
        if (Boolean.TRUE.equals(sagaOk)) {
            // Saga 成功 → 三级库存确认扣减：held -1，DB available_stock -1
            seckillOrderHandler.deductStock(
                    message.activityId(), message.skuId(), message.quantity(), message.orderNo());
            // 通知事件（Dubbo 调用 oms-trade）
            seckillOrderSagaService.publishOrderCreated(
                    message.orderNo(), message.buyerId(), "PENDING_PAY");
            log.info("[秒杀消费] 下单成功，库存已扣减: orderNo={}, buyerId={}",
                    message.orderNo(), message.buyerId());
        } else {
            seckillOrderHandler.releaseStock(
                    message.activityId(), message.skuId(), message.quantity(), message.orderNo());
            log.warn("[秒杀消费] Saga 失败，库存已释放: orderNo={}", message.orderNo());
        }
    }

    // ========== 辅助 ==========

    private static double getEnvDouble(String key, double defaultValue) {
        try {
            String val = System.getenv(key);
            return val != null ? Double.parseDouble(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 从 MQ 消息重建 CreateOrderRequest。
     */
    private CreateOrderRequest buildCreateOrderRequest(SeckillOrderMessage msg,
                                                        SeckillActivityEntity activity) {
        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .skuId(msg.skuId())
                .quantity(msg.quantity())
                .unitPrice(msg.seckillPrice())
                .build();

        return CreateOrderRequest.builder()
                .buyerId(msg.buyerId())
                .shopId("DEFAULT")
                .addressId("SECKILL")
                .remark("秒杀订单")
                .channelSource("SECKILL")
                .items(List.of(item))
                .seckillActivityId(msg.activityId())
                .seckillPrice(msg.seckillPrice())
                .seckillPipeline("default")
                .build();
    }
}
