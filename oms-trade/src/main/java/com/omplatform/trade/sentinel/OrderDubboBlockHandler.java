package com.omplatform.trade.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 订单服务 Sentinel 限流/熔断 blockHandler。
 * <p>
 * 方法签名要求：public static、返回 ApiResult<?>、参数与原方法一致 + 末尾 BlockException。
 */
@Slf4j
public final class OrderDubboBlockHandler {

    private OrderDubboBlockHandler() {}

    public static ApiResult<OrderDTO> createOrderBlock(CreateOrderRequest request,
                                                        TransitionContextDTO context,
                                                        BlockException e) {
        log.warn("[Sentinel] createOrder 被限流: buyerId={}", request != null ? request.getBuyerId() : "?");
        return ApiResult.error("TOO_MANY_REQUESTS", "下单请求过于频繁，请稍后重试");
    }

    public static ApiResult<OrderDTO> processPaymentBlock(String orderNo, BigDecimal paidAmount,
                                                           String payChannel, String transactionId,
                                                           TransitionContextDTO context,
                                                           BlockException e) {
        log.warn("[Sentinel] processPayment 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "支付处理繁忙，正在排队处理");
    }

    public static ApiResult<OrderDTO> cancelOrderBlock(String orderNo,
                                                        TransitionContextDTO context,
                                                        BlockException e) {
        log.warn("[Sentinel] cancelOrder 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "取消失败，请稍后重试");
    }

    public static ApiResult<OrderDTO> shipOrderBlock(String orderNo, String logisticsCompany,
                                                      String logisticsNo,
                                                      TransitionContextDTO context,
                                                      BlockException e) {
        log.warn("[Sentinel] shipOrder 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "发货请求过于频繁");
    }

    public static ApiResult<OrderDTO> refundOrderBlock(String orderNo,
                                                        TransitionContextDTO context,
                                                        BlockException e) {
        log.warn("[Sentinel] refundOrder 被限流: orderNo={}", orderNo);
        return ApiResult.error("TOO_MANY_REQUESTS", "退款请求繁忙，请稍后重试");
    }
}
