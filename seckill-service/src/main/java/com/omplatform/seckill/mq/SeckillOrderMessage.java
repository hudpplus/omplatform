package com.omplatform.seckill.mq;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单创建 MQ 消息（生产者 → 消费者）。
 * <p>
 * 秒杀链路中，入口（SeckillOrderHandler）在 Redis 抢库存成功后
 * 发送此消息；消费端收到后异步执行订单创建 Saga。
 *
 * @param activityId    秒杀活动 ID
 * @param buyerId       买家 ID
 * @param skuId         商品 SKU
 * @param quantity      数量
 * @param orderNo       已生成的订单号（用于 Saga 幂等 + 用户轮询）
 * @param seckillPrice  秒杀价
 * @param requestId     客户端幂等 ID
 * @param occurredAt    消息产生时间
 */
public record SeckillOrderMessage(
        Long activityId,
        String buyerId,
        String skuId,
        Integer quantity,
        String orderNo,
        BigDecimal seckillPrice,
        String requestId,
        LocalDateTime occurredAt
) {}
