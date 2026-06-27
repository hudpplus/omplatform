package com.omplatform.finance.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付渠道管理器（ADR-042）。
 * <p>
 * 管理三方支付渠道（支付宝/微信）的接入和路由。
 * 每个渠道实现 {@link PaymentChannel} 接口。
 */
@Slf4j
@Component
public class PaymentChannelManager {

    private final Map<String, PaymentChannel> channels = new ConcurrentHashMap<>();

    /**
     * 注册支付渠道（由 Spring @PostConstruct 驱动）。
     */
    public void registerChannel(PaymentChannel channel) {
        channels.put(channel.channelType(), channel);
        log.info("注册支付渠道: {}", channel.channelType());
    }

    /**
     * 发起支付请求。
     *
     * @param channelType 渠道类型（ALIPAY / WECHAT）
     * @param request     支付请求
     * @return 支付参数（跳转 URL / 小程序参数）
     */
    public PaymentResponse requestPayment(String channelType, PaymentRequest request) {
        PaymentChannel channel = channels.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("不支持的支付渠道: " + channelType);
        }
        return channel.charge(request);
    }

    /**
     * 处理支付回调。
     */
    public PaymentCallbackResult handleCallback(String channelType, String rawCallback) {
        PaymentChannel channel = channels.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("不支持的支付渠道: " + channelType);
        }
        return channel.verifyAndParseCallback(rawCallback);
    }

    /**
     * 发起退款。
     */
    public String processRefund(String channelType, RefundRequest request) {
        PaymentChannel channel = channels.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("不支持的支付渠道: " + channelType);
        }
        return channel.refund(request);
    }

    // ========== 类型定义 ==========

    public record PaymentRequest(
            String orderNo,
            BigDecimal amount,
            String subject,
            String description,
            String notifyUrl
    ) {}

    public record PaymentResponse(
            boolean success,
            String redirectUrl,
            String prepayId,
            String tradeNo
    ) {}

    public record PaymentCallbackResult(
            boolean verified,
            String transactionId,
            BigDecimal paidAmount,
            String paidAt,
            String rawData
    ) {}

    public record RefundRequest(
            String orderNo,
            BigDecimal amount,
            String transactionId,
            String reason
    ) {}

    /**
     * 支付渠道 SPI 接口。
     */
    public interface PaymentChannel {
        String channelType();
        PaymentResponse charge(PaymentRequest request);
        PaymentCallbackResult verifyAndParseCallback(String rawCallback);
        String refund(RefundRequest request);
    }
}
