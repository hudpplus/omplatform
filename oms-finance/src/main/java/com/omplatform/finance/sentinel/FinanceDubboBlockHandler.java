package com.omplatform.finance.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 资金服务 Sentinel 限流/熔断 blockHandler。
 */
@Slf4j
public final class FinanceDubboBlockHandler {

    private FinanceDubboBlockHandler() {}

    public static ApiResult<String> requestPaymentBlock(String orderNo, BigDecimal amount,
                                                         String payChannel, Object context,
                                                         BlockException e) {
        log.warn("[Sentinel] requestPayment 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "支付发起繁忙，请稍后重试");
    }

    public static ApiResult<String> processRefundBlock(String orderNo, BigDecimal amount,
                                                        String transactionId, Object context,
                                                        BlockException e) {
        log.warn("[Sentinel] processRefund 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "退款请求繁忙，请稍后重试");
    }

    public static ApiResult<Void> handlePaymentCallbackBlock(String orderNo, String channel,
                                                              String callbackParamsJson,
                                                              BlockException e) {
        log.warn("[Sentinel] handlePaymentCallback 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "回调处理繁忙");
    }
}
