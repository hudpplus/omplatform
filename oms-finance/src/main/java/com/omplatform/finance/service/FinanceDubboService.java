package com.omplatform.finance.service;

import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.api.payment.PaymentService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.finance.event.PaymentEventPublisher;
import com.omplatform.finance.entity.PaymentOrder;
import com.omplatform.finance.entity.RefundOrder;
import com.omplatform.finance.mapper.PaymentOrderMapper;
import com.omplatform.finance.mapper.RefundOrderMapper;
import com.omplatform.finance.payment.PaymentChannelManager;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omplatform.tcc.TccParticipantStateClient;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资金 Dubbo 服务实现。
 */
@Slf4j
@DubboService
public class FinanceDubboService implements PaymentService {

    @Autowired
    private PaymentChannelManager paymentChannelManager;
    @Autowired
    private PaymentEventPublisher eventPublisher;
    @Autowired
    private PaymentOrderMapper paymentOrderMapper;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private TccParticipantStateClient tccClient;

    @Override
    @SentinelResource(value = "finance.requestPayment",
            blockHandler = "requestPaymentBlock",
            blockHandlerClass = com.omplatform.finance.sentinel.FinanceDubboBlockHandler.class)
    public ApiResult<String> requestPayment(String orderNo, BigDecimal amount,
                                             String payChannel, TransitionContextDTO context) {
        log.info("[Dubbo] 发起支付: orderNo={}, amount={}, channel={}",
                orderNo, amount, payChannel);

        String channelType = payChannel != null ? payChannel.toUpperCase() : "ALIPAY";
        PaymentChannelManager.PaymentRequest request = new PaymentChannelManager.PaymentRequest(
                orderNo, amount, "订单支付", "订单 " + orderNo + " 支付", null);

        try {
            PaymentChannelManager.PaymentResponse response =
                    paymentChannelManager.requestPayment(channelType, request);
            if (response != null && response.success()) {
                // 持久化支付单
                PaymentOrder paymentOrder = new PaymentOrder();
                paymentOrder.setPaymentNo(generatePaymentNo());
                paymentOrder.setOrderNo(orderNo);
                paymentOrder.setChannel(channelType);
                paymentOrder.setAmount(amount);
                paymentOrder.setStatus("PENDING");
                paymentOrder.setGmtCreate(LocalDateTime.now());
                paymentOrder.setGmtModified(LocalDateTime.now());
                try {
                    paymentOrderMapper.insert(paymentOrder);
                } catch (Exception e) {
                    log.warn("支付单持久化失败(不影响支付): orderNo={}, err={}", orderNo, e.getMessage());
                }

                String payUrl = response.redirectUrl();
                log.info("支付发起成功: orderNo={}, url={}", orderNo, payUrl);
                return ApiResult.success(payUrl);
            } else {
                log.warn("支付发起失败: orderNo={}, channel={}", orderNo, channelType);
                return ApiResult.error("PAY_FAILED", "支付发起失败");
            }
        } catch (Exception e) {
            log.error("支付异常: orderNo={}, error={}", orderNo, e.getMessage());
            return ApiResult.error("PAY_ERROR", "支付异常: " + e.getMessage());
        }
    }

    @Override
    @SentinelResource(value = "finance.processRefund",
            blockHandler = "processRefundBlock",
            blockHandlerClass = com.omplatform.finance.sentinel.FinanceDubboBlockHandler.class)
    public ApiResult<String> processRefund(String orderNo, BigDecimal amount,
                                            String transactionId, TransitionContextDTO context) {
        log.info("[Dubbo] 退款: orderNo={}, amount={}, txId={}", orderNo, amount, transactionId);

        // 从 context extras 中获取渠道，默认 ALIPAY
        String channel = "ALIPAY";
        if (context != null && context.getExtras() != null && context.getExtras().containsKey("channel")) {
            Object ch = context.getExtras().get("channel");
            if (ch instanceof String) channel = ((String) ch).toUpperCase();
        }

        PaymentChannelManager.RefundRequest refundRequest = new PaymentChannelManager.RefundRequest(
                orderNo, amount, transactionId, "系统退款");

        // 持久化退款单（先插入 PENDING 记录）
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo(generateRefundNo());
        refundOrder.setOrderNo(orderNo);
        refundOrder.setAmount(amount);
        refundOrder.setReason("系统退款");
        refundOrder.setStatus("PENDING");
        refundOrder.setGmtCreate(LocalDateTime.now());
        refundOrder.setGmtModified(LocalDateTime.now());
        try {
            refundOrderMapper.insert(refundOrder);
        } catch (Exception e) {
            log.warn("退款单持久化失败(不影响退款): orderNo={}, err={}", orderNo, e.getMessage());
        }

        // 如果指定渠道不行，尝试另一个渠道（兼容旧调用方未传 channel）
        String[] channels = {channel, channel.equals("ALIPAY") ? "WECHAT" : "ALIPAY"};
        for (String ch : channels) {
            refundOrder.setChannel(ch);
            try {
                String refundNo = paymentChannelManager.processRefund(ch, refundRequest);
                // 成功 → 更新退款单
                refundOrder.setChannelRefundNo(refundNo);
                refundOrder.setStatus("SUCCESS");
                refundOrder.setGmtModified(LocalDateTime.now());
                try { refundOrderMapper.updateById(refundOrder); } catch (Exception ignored) {}

                log.info("退款成功: orderNo={}, channel={}, refundNo={}", orderNo, ch, refundNo);
                return ApiResult.success(refundNo);
            } catch (Exception e) {
                log.warn("{} 退款失败，尝试下一个渠道: orderNo={}, err={}", ch, orderNo, e.getMessage());
            }
        }

        // 所有渠道失败
        refundOrder.setStatus("FAILED");
        refundOrder.setGmtModified(LocalDateTime.now());
        try { refundOrderMapper.updateById(refundOrder); } catch (Exception ignored) {}

        log.error("所有渠道退款均失败: orderNo={}", orderNo);
        return ApiResult.error("REFUND_ERROR", "所有渠道退款均失败");
    }

    // ========== 内部方法 ==========

    /**
     * 生成支付单号。
     */
    private String generatePaymentNo() {
        return "P" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 生成退款单号。
     */
    private String generateRefundNo() {
        return "R" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Override
    public ApiResult<String> queryRefundStatus(String orderNo, String transactionId) {
        return ApiResult.success("SUCCESS");
    }

    @Override
    @SentinelResource(value = "finance.handlePaymentCallback",
            blockHandler = "handlePaymentCallbackBlock",
            blockHandlerClass = com.omplatform.finance.sentinel.FinanceDubboBlockHandler.class)
    public ApiResult<Void> handlePaymentCallback(String orderNo, String channel,
                                                  String callbackParamsJson) {
        log.info("[Dubbo] 支付回调: orderNo={}, channel={}", orderNo, channel);

        // 1. 渠道验签并解析回调
        try {
            PaymentChannelManager.PaymentCallbackResult result =
                    paymentChannelManager.handleCallback(
                            channel != null ? channel.toUpperCase() : "ALIPAY",
                            callbackParamsJson);

            if (result.verified()) {
                log.info("支付回调验签通过: orderNo={}, tradeNo={}, amount={}",
                        orderNo, result.transactionId(), result.paidAmount());

                // 2. 发布支付成功事件（驱动订单状态转换）
                eventPublisher.paymentSuccess(
                        orderNo,
                        channel != null ? channel.toUpperCase() : "ALIPAY",
                        result.transactionId() != null ? result.transactionId() : "UNKNOWN",
                        result.paidAmount() != null ? result.paidAmount() : BigDecimal.ZERO);

                // 3. 更新 TCC 状态（幂等保证）
                try {
                    if (callbackParamsJson != null && !callbackParamsJson.isBlank()) {
                        JsonNode root = objectMapper.readTree(callbackParamsJson);
                        String txId = root.has("txId") ? root.get("txId").asText(null) : null;
                        String participantId = root.has("participantId")
                                ? root.get("participantId").asText("payment-service") : "payment-service";
                        if (txId != null) {
                            tccClient.upsertStatus(txId, participantId, "CONFIRMED",
                                    orderNo, LocalDateTime.now());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("TCC 状态更新失败(不影响回调处理): {}", ex.getMessage());
                }

                return ApiResult.success();
            } else {
                log.warn("支付回调验签失败: orderNo={}, channel={}", orderNo, channel);
                return ApiResult.error("CALLBACK_VERIFY_FAILED", "回调验签失败");
            }
        } catch (Exception e) {
            log.error("支付回调处理异常: orderNo={}, error={}", orderNo, e.getMessage(), e);
            return ApiResult.error("CALLBACK_ERROR", "回调处理异常");
        }
    }
}
