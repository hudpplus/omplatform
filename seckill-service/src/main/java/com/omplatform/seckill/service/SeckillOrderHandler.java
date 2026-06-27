package com.omplatform.seckill.service;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.seckill.SeckillOrderSagaService;
import com.omplatform.seckill.config.RedisTokenBucketRateLimiter;
import com.omplatform.seckill.dto.SeckillExecuteResult;
import com.omplatform.seckill.entity.SeckillActivityEntity;
import com.omplatform.seckill.mq.SeckillOrderMessage;
import com.omplatform.seckill.mq.SeckillOrderProducer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 秒杀订单处理器 — 秒杀链路入口编排（G1 高热路径）。
 * <p>
 * 流程（令牌桶 + MQ 异步 + 三级库存）：
 * <ol>
 *   <li>活动校验（存在 + ACTIVE + 时间窗口）</li>
 *   <li>令牌桶限流（Redis Lua，活动级）</li>
 *   <li>防重提交（SET NX）</li>
 *   <li>买家限购检查（通过 Dubbo 调用 oms-trade）</li>
 *   <li>Redis Lua 原子抢库存（三级库存：available -1, held +1）</li>
 *   <li>发送 RocketMQ → 立即返回 PROCESSING</li>
 * </ol>
 * <p>
 * 订单创建 Saga 由 {@link com.omplatform.seckill.mq.SeckillOrderConsumer} 异步执行。
 * 支付成功后调用 {@link #deductStock} 确认扣减（held -1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderHandler {

    private final SeckillActivityService activityService;
    private final StringRedisTemplate redisTemplate;
    private final RedisTokenBucketRateLimiter tokenBucketRateLimiter;
    private final SeckillOrderProducer seckillOrderProducer;

    /** 秒杀订单 Saga 服务（Dubbo 调用 oms-trade） */
    @DubboReference
    private SeckillOrderSagaService seckillOrderSagaService;

    /** 秒杀抢库存 Lua 脚本 */
    private DefaultRedisScript<List> grabScript;
    /** 秒杀释放库存 Lua 脚本 */
    private DefaultRedisScript<List> releaseScript;
    /** 秒杀支付确认扣减 Lua 脚本 */
    private DefaultRedisScript<List> deductScript;

    /** hold 过期时间（秒），默认 15 分钟 */
    private static final long HOLD_TTL_SECONDS = 900;

    /** 幂等去重 TTL（秒），防止同一 requestId 重复提交 */
    private static final long IDEMPOTENT_TTL_SECONDS = 10;

    /** 幂等去重 Redis Key 前缀 */
    private static final String DEDUP_KEY_PREFIX = "seckill:dedup:";

    @PostConstruct
    public void init() {
        grabScript = loadScript("lua/seckill_grab.lua");
        releaseScript = loadScript("lua/seckill_release.lua");
        deductScript = loadScript("lua/seckill_deduct.lua");
        log.info("[秒杀] Lua 脚本加载完成: grab/release/deduct");
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<List> loadScript(String classpath) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        try {
            String text = new String(
                    new ClassPathResource(classpath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            script.setScriptText(text);
        } catch (IOException e) {
            throw new RuntimeException("加载 Lua 脚本失败: " + classpath, e);
        }
        return script;
    }

    /**
     * 执行秒杀。
     * <p>
     * 入口经 Sentinel 限流 → 验证 → 令牌桶 → 去重 → 限购 → 三级库存抢库存 → 发 MQ。
     *
     * @param activityId  活动 ID
     * @param buyerId     买家 ID
     * @param request     抢购请求
     * @return 秒杀执行结果（异步时为 {@code PROCESSING}）
     */
    public SeckillExecuteResult execute(Long activityId, String buyerId,
                                        com.omplatform.seckill.dto.SeckillExecuteRequest request) {
        // ====== 1. 校验活动 ======
        SeckillActivityEntity activity = activityService.getById(activityId);
        if (activity == null || !"ACTIVE".equals(activity.getStatus())) {
            return failed("ACTIVITY_INVALID", "活动不存在或未开始");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            return failed("ACTIVITY_INVALID", "不在活动时间范围内");
        }

        // ====== 2. 令牌桶限流（应用层，活动级别） ======
        var tbResult = tokenBucketRateLimiter.tryAcquire(activityId);
        if (!tbResult.allowed()) {
            log.warn("[秒杀] 令牌桶限流: buyerId={}, activityId={}, remaining={}",
                    buyerId, activityId, tbResult.remainingTokens());
            return failed("TOKEN_BUCKET_LIMITED", "系统繁忙，请稍后重试");
        }

        // ====== 3. 防重提交：SET NX 幂等键 ======
        String dedupKey = DEDUP_KEY_PREFIX + activityId + ":" + request.getRequestId();
        Boolean deduped = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, buyerId, java.time.Duration.ofSeconds(IDEMPOTENT_TTL_SECONDS));
        if (Boolean.FALSE.equals(deduped)) {
            log.warn("[秒杀] 重复提交: buyerId={}, activityId={}, requestId={}",
                    buyerId, activityId, request.getRequestId());
            return failed("DUPLICATE_SUBMIT", "请勿重复提交");
        }

        // ====== 4. 买家限购（Dubbo 调用 oms-trade 查询订单数） ======
        if (activity.getLimitPerUser() != null && activity.getLimitPerUser() > 0) {
            Long bought = seckillOrderSagaService.countBuyerOrders(buyerId, activityId).getData();
            if (bought != null && bought >= activity.getLimitPerUser()) {
                return failed("BUYER_LIMIT", "已达到该活动购买上限: " + activity.getLimitPerUser());
            }
        }

        // ====== 5. Redis Lua 原子抢库存（三级库存） ======
        String orderNo = generateOrderNo();
        String availableKey = SeckillActivityService.buildAvailableKey(activityId, request.getSkuId());
        String heldKey = SeckillActivityService.buildHeldKey(activityId, request.getSkuId());
        String holdKey = SeckillActivityService.buildHoldKey(orderNo);

        List<Object> grabResult = redisTemplate.execute(
                grabScript,
                Arrays.asList(availableKey, heldKey, holdKey),
                String.valueOf(request.getQuantity()),
                String.valueOf(HOLD_TTL_SECONDS),
                orderNo);

        int code = parseGrabCode(grabResult);
        if (code != 1) {
            String msg = grabResult != null && grabResult.size() > 1
                    ? grabResult.get(1).toString() : "SOLD_OUT";
            return failed("SOLD_OUT", msg);
        }

        // ====== 6. 发送 RocketMQ 消息，异步创建订单 ======
        SeckillOrderMessage msg = new SeckillOrderMessage(
                activityId, buyerId, request.getSkuId(), request.getQuantity(),
                orderNo, activity.getSeckillPrice(), request.getRequestId(), LocalDateTime.now());

        try {
            seckillOrderProducer.sendOrderMessage(msg);
        } catch (Exception e) {
            // MQ 发送失败 → 立即释放库存
            releaseStock(activityId, request.getSkuId(), request.getQuantity(), orderNo);
            log.error("[秒杀] MQ 发送失败，库存已释放: orderNo={}, error={}", orderNo, e.getMessage(), e);
            return failed("ORDER_FAILED", "系统繁忙，请稍后重试");
        }

        log.info("[秒杀] 抢购成功，订单处理中: buyerId={}, activityId={}, orderNo={}",
                buyerId, activityId, orderNo);

        return SeckillExecuteResult.builder()
                .status("PROCESSING")
                .message("抢购成功，订单处理中")
                .orderNo(orderNo)
                .build();
    }

    /**
     * 释放秒杀库存（Saga 失败或 MQ 发送失败时补偿）。
     * <p>
     * 三级库存调整：held -1, available +1。
     */
    public void releaseStock(Long activityId, String skuId, int quantity, String orderNo) {
        try {
            String availableKey = SeckillActivityService.buildAvailableKey(activityId, skuId);
            String heldKey = SeckillActivityService.buildHeldKey(activityId, skuId);
            String holdKey = SeckillActivityService.buildHoldKey(orderNo);
            redisTemplate.execute(
                    releaseScript,
                    Arrays.asList(availableKey, heldKey, holdKey),
                    String.valueOf(quantity), orderNo);
            log.info("[秒杀] 库存已释放: activityId={}, sku={}, orderNo={}", activityId, skuId, orderNo);
        } catch (Exception e) {
            log.warn("[秒杀] 释放库存异常（TTL 兜底）: {}", e.getMessage());
        }
    }

    /**
     * 支付确认扣减库存（支付成功后调用）。
     * <p>
     * 三级库存：held -1，标记"预留→已售"。
     * 同时调用 DB {@code confirmDeduct} 记录已售数量。
     *
     * @param activityId 秒杀活动 ID
     * @param skuId      SKU
     * @param quantity   数量
     * @param orderNo    订单号
     */
    public void deductStock(Long activityId, String skuId, int quantity, String orderNo) {
        try {
            String heldKey = SeckillActivityService.buildHeldKey(activityId, skuId);
            String holdKey = SeckillActivityService.buildHoldKey(orderNo);
            redisTemplate.execute(
                    deductScript,
                    Arrays.asList(heldKey, holdKey),
                    String.valueOf(quantity), orderNo);
            activityService.confirmDeduct(activityId, quantity);
            log.info("[秒杀] 支付确认扣减完成: activityId={}, sku={}, orderNo={}, qty={}",
                    activityId, skuId, orderNo, quantity);
        } catch (Exception e) {
            log.error("[秒杀] 支付确认扣减异常: activityId={}, orderNo={}, error={}",
                    activityId, orderNo, e.getMessage(), e);
        }
    }

    // ========== 辅助 ==========

    private SeckillExecuteResult failed(String status, String message) {
        return SeckillExecuteResult.builder()
                .status(status)
                .message(message)
                .build();
    }

    private String generateOrderNo() {
        return "SECKILL" + System.currentTimeMillis();
    }

    private int parseGrabCode(List<?> result) {
        if (result == null || result.isEmpty()) return 0;
        Object code = result.get(0);
        if (code instanceof Number n) return n.intValue();
        return 0;
    }
}
