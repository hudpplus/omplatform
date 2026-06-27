package com.omplatform.finance.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.finance.entity.PaymentOrder;
import com.omplatform.finance.event.PaymentEventPublisher;
import com.omplatform.finance.mapper.PaymentOrderMapper;
import com.omplatform.finance.payment.PaymentChannelManager;
import com.omplatform.finance.payment.WechatChannel;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付异步通知回调控制器（HTTP 端点）。
 * <p>
 * 接收支付宝/微信支付服务器主动推送的支付结果通知。
 * 验证签名后通过 MQ 事件驱动订单状态更新。
 */
@Slf4j
@RestController
@RequestMapping("/api/payment/callback")
public class PaymentCallbackController {

    @Autowired
    private PaymentChannelManager paymentChannelManager;

    @Autowired
    private PaymentEventPublisher eventPublisher;

    @Autowired(required = false)
    private WechatChannel wechatChannel;

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    /**
     * 支付宝异步通知回调。
     * <p>
     * 支付宝以 POST form 方式推送通知。
     */
    @PostMapping("/alipay")
    public String handleAlipayCallback(HttpServletRequest request) {
        log.info("[回调] 收到支付宝异步通知");

        try {
            // 1. 读取所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String[] values = entry.getValue();
                params.put(entry.getKey(), values != null && values.length > 0 ? values[0] : "");
            }

            // 2. 幂等检查：是否已有 SUCCESS 记录
            String orderNo = params.getOrDefault("out_trade_no", "");
            long paidCount = paymentOrderMapper.selectCount(
                    Wrappers.<PaymentOrder>lambdaQuery()
                            .eq(PaymentOrder::getOrderNo, orderNo)
                            .eq(PaymentOrder::getStatus, "SUCCESS"));
            if (paidCount > 0) {
                log.info("[回调] 支付宝重复通知，已处理跳过: orderNo={}", orderNo);
                return "success";
            }

            // 3. 转为 query string 格式交给 channel 验证
            String rawCallback = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));

            PaymentChannelManager.PaymentCallbackResult result =
                    paymentChannelManager.handleCallback("ALIPAY", rawCallback);

            // 4. 验证通过 → 写入支付单（原子检测：paymentNo = "PAID_" + orderNo 作为 PK 判重）
            if (result.verified()) {
                BigDecimal amount = result.paidAmount();
                if (amount == null) {
                    try {
                        amount = new BigDecimal(params.getOrDefault("receipt_amount", "0"));
                    } catch (Exception ignored) {}
                }
                String transactionId = result.transactionId() != null ? result.transactionId() : params.get("trade_no");

                boolean isFirst = tryInsertSuccessPayment(orderNo, "ALIPAY", transactionId, amount, rawCallback);
                if (isFirst) {
                    // 首次写入成功 → 发布事件（仅在 DB 写入成功后发，避免重复）
                    eventPublisher.paymentSuccess(orderNo, "ALIPAY", transactionId, amount);
                    log.info("[回调] 支付宝支付成功: orderNo={}, tradeNo={}, amount={}",
                            orderNo, transactionId, amount);
                } else {
                    log.info("[回调] 支付宝重复通知，已处理跳过: orderNo={}", orderNo);
                }
                return "success";
            } else {
                log.warn("[回调] 支付宝通知验签失败");
                return "failure";
            }
        } catch (Exception e) {
            log.error("[回调] 支付宝回调处理异常: {}", e.getMessage(), e);
            return "failure";
        }
    }

    /**
     * 微信支付异步通知回调。
     * <p>
     * 微信支付以 POST JSON 方式推送通知（V3 API）。
     * 使用 WechatChannel.verifyNotification() 完成验签 + 资源解密。
     */
    @PostMapping("/wechat")
    public String handleWechatCallback(
            @RequestBody(required = false) String body,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            HttpServletRequest request) {

        log.info("[回调] 收到微信支付异步通知");

        try {
            // 1. 读取 body
            if (body == null || body.isBlank()) {
                try (BufferedReader reader = request.getReader()) {
                    body = reader.lines().collect(Collectors.joining("\n"));
                }
            }
            if (body == null || body.isBlank()) {
                log.warn("[回调] 微信支付通知 body 为空");
                return "{\"code\":\"FAIL\",\"message\":\"empty body\"}";
            }

            // 2. SDK 验签 + 资源解密
            String resourcePlaintext = null;
            if (wechatChannel != null && signature != null && timestamp != null
                    && nonce != null && serial != null) {
                try {
                    resourcePlaintext = wechatChannel.verifyNotification(
                            body, signature, timestamp, nonce, serial);
                } catch (Exception e) {
                    log.warn("[回调] 微信通知验签失败: {}", e.getMessage());
                }
            }

            // 3. 解析回调（如果验签通过拿到明文，用明文解析；否则直接解析 body）
            String dataToParse = resourcePlaintext != null ? resourcePlaintext : body;

            PaymentChannelManager.PaymentCallbackResult result =
                    paymentChannelManager.handleCallback("WECHAT", dataToParse);

            // 4. 验证通过 → 写入支付单（原子检测：paymentNo = "PAID_" + orderNo 判重）
            if (result.verified()) {
                String orderNo = extractWechatOrderNo(dataToParse);
                BigDecimal amount = result.paidAmount();

                if (orderNo == null) {
                    log.warn("[回调] 微信回调中未找到 out_trade_no");
                    return "{\"code\":\"FAIL\",\"message\":\"no orderNo\"}";
                }

                boolean isFirst = tryInsertSuccessPayment(
                        orderNo, "WECHAT", result.transactionId(), amount, dataToParse);
                if (isFirst) {
                    eventPublisher.paymentSuccess(orderNo, "WECHAT", result.transactionId(), amount);
                    log.info("[回调] 微信支付成功: orderNo={}, tradeNo={}, amount={}",
                            orderNo, result.transactionId(), amount);
                } else {
                    log.info("[回调] 微信重复通知，已处理跳过: orderNo={}", orderNo);
                }

                return "{\"code\":\"SUCCESS\"}";
            } else {
                log.warn("[回调] 微信通知数据解析失败");
                return "{\"code\":\"FAIL\",\"message\":\"parse failed\"}";
            }
        } catch (Exception e) {
            log.error("[回调] 微信回调处理异常: {}", e.getMessage(), e);
            return "{\"code\":\"FAIL\",\"message\":\"internal error\"}";
        }
    }

    // ========== 内部 ==========

    /** 从微信回调 JSON 中提取 orderNo */
    private String extractWechatOrderNo(String body) {
        if (body == null) return null;
        String key = "\"out_trade_no\"";
        int idx = body.indexOf(key);
        if (idx < 0) return null;
        int colon = body.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = body.indexOf('"', colon);
        if (start < 0) return null;
        int end = body.indexOf('"', start + 1);
        if (end < 0) return null;
        return body.substring(start + 1, end);
    }

    /**
     * 原子写入支付成功记录。
     * <p>
     * 使用 {@code paymentNo = "PAID_" + orderNo} 作为主键，
     * 利用 PK 唯一约束保证只有第一个线程能 INSERT 成功。
     *
     * @return true = 首次写入成功；false = 已存在 SUCCESS 记录（重复回调）
     */
    private boolean tryInsertSuccessPayment(String orderNo, String channel,
                                            String transactionId, BigDecimal amount, String notifyRaw) {
        try {
            PaymentOrder po = new PaymentOrder();
            po.setPaymentNo("PAID_" + orderNo);
            po.setOrderNo(orderNo);
            po.setChannel(channel);
            po.setAmount(amount);
            po.setStatus("SUCCESS");
            po.setChannelTradeNo(transactionId);
            po.setPaidAt(LocalDateTime.now());
            po.setNotifyRaw(notifyRaw);
            po.setGmtCreate(LocalDateTime.now());
            po.setGmtModified(LocalDateTime.now());
            paymentOrderMapper.insert(po);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("写入支付单异常: orderNo={}, err={}", orderNo, e.getMessage());
            // 回退到 UPDATE 模式（兼容旧数据）
            return fallbackUpdate(orderNo, channel, transactionId, amount, notifyRaw);
        }
    }

    /** 回退：UPDATE 已有的 PENDING 支付单（兼容旧数据/ mock 场景） */
    private boolean fallbackUpdate(String orderNo, String channel,
                                   String transactionId, BigDecimal amount, String notifyRaw) {
        try {
            PaymentOrder record = new PaymentOrder();
            record.setStatus("SUCCESS");
            record.setChannelTradeNo(transactionId);
            record.setPaidAt(LocalDateTime.now());
            record.setNotifyRaw(notifyRaw);
            record.setGmtModified(LocalDateTime.now());

            LambdaUpdateWrapper<PaymentOrder> uw = Wrappers.<PaymentOrder>lambdaUpdate()
                    .eq(PaymentOrder::getOrderNo, orderNo);
            int updated = paymentOrderMapper.update(record, uw);
            return updated > 0;
        } catch (Exception e) {
            log.warn("fallback UPDATE 失败(不影响回调): orderNo={}, err={}", orderNo, e.getMessage());
            return false;
        }
    }
}
