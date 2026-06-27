package com.omplatform.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.omplatform.common.api.ApiResult;
import com.omplatform.seckill.blockhandler.SeckillBlockHandler;
import com.omplatform.seckill.dto.SeckillExecuteRequest;
import com.omplatform.seckill.dto.SeckillExecuteResult;
import com.omplatform.seckill.service.SeckillOrderHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀 API（G1 — 极高并发入口）。
 * <p>
 * 三层保护：
 * <ul>
 *   <li>Sentinel — 网络层限流（排队模式削峰 + 快速失败硬上限）</li>
 *   <li>令牌桶 — 应用层活动级限流（Redis Lua，突发削平）</li>
 *   <li>RocketMQ — 异步削峰，入口只抢库存，下单 Saga 由消费端执行</li>
 * </ul>
 * 防重：客户端传入 requestId，Redis SET NX 幂等去重
 * <p>
 * 响应说明：
 * <ul>
 *   <li>{@code PROCESSING} — 抢库存成功，订单创建中（异步 MQ），客户端应轮询订单状态</li>
 *   <li>{@code SUCCESS} — 仅同步降级时返回</li>
 *   <li>{@code TOKEN_BUCKET_LIMITED} — 令牌桶限流拒绝</li>
 *   <li>{@code FLOW_LIMITED} — Sentinel 限流拒绝</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillOrderHandler seckillOrderHandler;

    /**
     * 执行秒杀抢购。
     * POST /api/v1/seckill/{activityId}/execute
     *
     * @param activityId 秒杀活动 ID
     * @param request    抢购请求（含客户端幂等 ID）
     * @param buyerId    买家 ID（由 Gateway 解析 JWT 后传入）
     * @return 抢购结果
     */
    @PostMapping("/{activityId}/execute")
    @SentinelResource(value = "seckill:execute",
            blockHandler = "executeBlock",
            blockHandlerClass = SeckillBlockHandler.class)
    public ApiResult<SeckillExecuteResult> execute(
            @PathVariable Long activityId,
            @Valid @RequestBody SeckillExecuteRequest request,
            @RequestHeader("X-User-Id") String buyerId) {

        log.info("[秒杀] 请求: buyerId={}, activityId={}, skuId={}, qty={}, requestId={}",
                buyerId, activityId, request.getSkuId(), request.getQuantity(), request.getRequestId());

        SeckillExecuteResult result = seckillOrderHandler.execute(activityId, buyerId, request);

        // PROCESSING / SUCCESS 均作为成功响应返回（PROCESSING 表示 MQ 异步处理中）
        if ("SUCCESS".equals(result.getStatus()) || "PROCESSING".equals(result.getStatus())) {
            return ApiResult.success(result);
        }
        return ApiResult.error(result.getStatus(), result.getMessage());
    }
}
