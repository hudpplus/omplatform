package com.omplatform.seckill.blockhandler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.omplatform.common.api.ApiResult;
import com.omplatform.seckill.dto.SeckillExecuteRequest;
import com.omplatform.seckill.dto.SeckillExecuteResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 秒杀 Sentinel 流控降级处理器。
 * <p>
 * 当 {@code @SentinelResource("seckill:execute")} 触发限流或熔断时，
 * Sentinel 反射调用此类的静态方法返回降级响应。
 */
@Slf4j
public final class SeckillBlockHandler {

    private SeckillBlockHandler() {}

    /**
     * 秒杀入口限流降级处理方法。
     * <p>
     * 方法签名必须与 {@code @SentinelResource} 的 blockHandler 声明一致：
     * 返回值 + 原方法参数 + BlockException 参数。
     */
    public static ApiResult<SeckillExecuteResult> executeBlock(
            Long activityId, SeckillExecuteRequest request, String buyerId, BlockException ex) {

        log.warn("[Sentinel] 秒杀限流降级: buyerId={}, activityId={}, type={}",
                buyerId, activityId, ex.getClass().getSimpleName());

        SeckillExecuteResult result = SeckillExecuteResult.builder()
                .status("FLOW_LIMITED")
                .message("系统繁忙，请稍后重试")
                .build();
        return ApiResult.error(result.getStatus(), result.getMessage());
    }
}
