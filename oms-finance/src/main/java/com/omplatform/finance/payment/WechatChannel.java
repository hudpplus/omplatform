package com.omplatform.finance.payment;

import com.omplatform.finance.config.PaymentProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.Notification;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.core.util.PemUtil;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.util.stream.Collectors;

/**
 * 微信支付渠道（V3 API 官方 SDK 实现）。
 * <p>
 * 支持 Native 支付（二维码），使用 RSA 自动证书管理器。
 */
@Slf4j
@Component
public class WechatChannel implements PaymentChannelManager.PaymentChannel {

    private final PaymentProperties.Wechat config;

    private RSAAutoCertificateConfig certificateConfig;
    private NativePayService nativePayService;
    private RefundService refundService;

    public WechatChannel(PaymentProperties properties) {
        this.config = properties.getWechat();
    }

    @PostConstruct
    public void init() {
        try {
            PrivateKey privateKey = loadPrivateKey();

            certificateConfig = new RSAAutoCertificateConfig.Builder()
                    .merchantId(config.getMchId())
                    .privateKey(privateKey)
                    .merchantSerialNumber(config.getMchSerialNo())
                    .apiV3Key(config.getApiV3Key())
                    .build();

            nativePayService = new NativePayService.Builder()
                    .config(certificateConfig)
                    .build();

            refundService = new RefundService.Builder()
                    .config(certificateConfig)
                    .build();

            log.info("[微信支付] SDK 初始化完成: mchId={}, appId={}",
                    config.getMchId(), config.getAppId());
        } catch (Exception e) {
            log.error("[微信支付] SDK 初始化失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String channelType() {
        return "WECHAT";
    }

    @Override
    public PaymentChannelManager.PaymentResponse charge(PaymentChannelManager.PaymentRequest request) {
        log.info("[微信支付] 发起支付: orderNo={}, amount={}, subject={}",
                request.orderNo(), request.amount(), request.subject());

        try {
            PrepayRequest prepayRequest = new PrepayRequest();
            prepayRequest.setAppid(config.getAppId());
            prepayRequest.setMchid(config.getMchId());
            prepayRequest.setOutTradeNo(request.orderNo());
            prepayRequest.setDescription(request.subject());
            prepayRequest.setNotifyUrl(config.getNotifyUrl());

            Amount amount = new Amount();
            int totalFen = request.amount().multiply(BigDecimal.valueOf(100)).intValue();
            amount.setTotal(totalFen);
            amount.setCurrency("CNY");
            prepayRequest.setAmount(amount);

            PrepayResponse response = nativePayService.prepay(prepayRequest);
            String codeUrl = response.getCodeUrl();

            log.info("[微信支付] Native 预支付成功: orderNo={}, codeUrl={}",
                    request.orderNo(), codeUrl);

            return new PaymentChannelManager.PaymentResponse(
                    true, codeUrl, null, null);

        } catch (Exception e) {
            log.error("[微信支付] 预支付异常: orderNo={}, err={}",
                    request.orderNo(), e.getMessage(), e);
            return new PaymentChannelManager.PaymentResponse(false, null, null, null);
        }
    }

    @Override
    public PaymentChannelManager.PaymentCallbackResult verifyAndParseCallback(String rawCallback) {
        log.info("[微信支付] 解析回调数据");
        return parseCallbackData(rawCallback);
    }

    @Override
    public String refund(PaymentChannelManager.RefundRequest request) {
        log.info("[微信支付] 发起退款: orderNo={}, amount={}, tradeNo={}",
                request.orderNo(), request.amount(), request.transactionId());

        try {
            CreateRequest refundRequest = new CreateRequest();
            refundRequest.setTransactionId(request.transactionId());
            refundRequest.setOutTradeNo(request.orderNo());
            refundRequest.setOutRefundNo("REF" + request.orderNo() + "_" + System.currentTimeMillis());
            refundRequest.setReason(request.reason());
            refundRequest.setNotifyUrl(config.getNotifyUrl());

            AmountReq refundAmount = new AmountReq();
            int totalFen = request.amount().multiply(BigDecimal.valueOf(100)).intValue();
            refundAmount.setRefund((long) totalFen);
            refundAmount.setTotal((long) totalFen);
            refundAmount.setCurrency("CNY");
            refundRequest.setAmount(refundAmount);

            com.wechat.pay.java.service.refund.model.Refund response =
                    refundService.create(refundRequest);

            log.info("[微信支付] 退款成功: orderNo={}, refundId={}",
                    request.orderNo(), response.getRefundId());
            return response.getRefundId();

        } catch (Exception e) {
            log.error("[微信支付] 退款异常: orderNo={}, err={}",
                    request.orderNo(), e.getMessage(), e);
            throw new RuntimeException("微信退款异常", e);
        }
    }

    // ========== 公开方法（供回调控制器使用） ==========

    /**
     * 验证微信支付异步通知签名。
     * <p>
     * 使用 SDK 的 NotificationParser 完成验签 + 资源解密。
     * RSAAutoCertificateConfig 本身实现了 NotificationConfig 接口，可直接使用。
     *
     * @param body      POST 请求体（JSON）
     * @param signature Wechatpay-Signature 请求头
     * @param timestamp Wechatpay-Timestamp 请求头
     * @param nonce     Wechatpay-Nonce 请求头
     * @param serial    Wechatpay-Serial 请求头
     * @return 解密后的回调资源（resource 中的 plaintext）
     */
    public String verifyNotification(String body, String signature,
                                     String timestamp, String nonce, String serial) {
        try {
            NotificationParser parser = new NotificationParser(certificateConfig);

            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .signature(signature)
                    .timestamp(timestamp)
                    .nonce(nonce)
                    .body(body)
                    .build();

            Notification notification = parser.parse(requestParam, Notification.class);
            String plaintext = notification.getPlaintext();

            log.info("[微信支付] 通知验签通过");
            return plaintext;
        } catch (Exception e) {
            log.error("[微信支付] 通知验签失败: {}", e.getMessage());
            throw new RuntimeException("微信通知验签失败", e);
        }
    }

    /**
     * 查询微信支付交易状态。
     *
     * @param orderNo 商户订单号
     * @return 交易状态枚举名（SUCCESS / REFUND / NOTPAY / CLOSED / USERPAYING / PAYERROR）
     */
    public String queryTradeState(String orderNo) {
        log.info("[微信支付] 查询交易: orderNo={}", orderNo);
        try {
            QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest();
            req.setOutTradeNo(orderNo);
            req.setMchid(config.getMchId());
            Transaction transaction = nativePayService.queryOrderByOutTradeNo(req);
            String tradeState = transaction.getTradeState() != null
                    ? transaction.getTradeState().name() : "UNKNOWN";
            log.info("[微信支付] 查询结果: orderNo={}, state={}", orderNo, tradeState);
            return tradeState;
        } catch (Exception e) {
            log.error("[微信支付] 查询交易异常: orderNo={}, err={}", orderNo, e.getMessage());
            return "ERROR";
        }
    }

    // ========== 内部 ==========

    private PrivateKey loadPrivateKey() {
        try {
            String path = config.getPrivateKeyPath();
            if (path.startsWith("classpath:")) {
                String resourcePath = path.substring("classpath:".length());
                try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
                    // 读取 PEM 内容为字符串，再加载私钥
                    String pemContent = new BufferedReader(new InputStreamReader(is))
                            .lines().collect(Collectors.joining("\n"));
                    return PemUtil.loadPrivateKeyFromString(pemContent);
                }
            } else {
                return PemUtil.loadPrivateKeyFromPath(path);
            }
        } catch (Exception e) {
            throw new RuntimeException("加载微信商户私钥失败: " + config.getPrivateKeyPath(), e);
        }
    }

    /**
     * 从回调 JSON 中解析交易数据。
     * <p>
     * 微信 V3 回调解密后的 resource 格式：
     * <pre>
     * {
     *   "transaction_id": "420000...",
     *   "out_trade_no": "ORD...",
     *   "amount": { "total": 100, "currency": "CNY" },
     *   "success_time": "2026-06-14T10:00:00+08:00"
     * }
     * </pre>
     */
    private PaymentChannelManager.PaymentCallbackResult parseCallbackData(String resourcePlaintext) {
        try {
            String transactionId = extractJsonValue(resourcePlaintext, "transaction_id");
            String orderNo = extractJsonValue(resourcePlaintext, "out_trade_no");
            String amountStr = extractNestedJsonValue(resourcePlaintext, "amount", "total");
            String paidAt = extractJsonValue(resourcePlaintext, "success_time");

            BigDecimal paidAmount = null;
            if (amountStr != null) {
                try {
                    paidAmount = new BigDecimal(amountStr)
                            .divide(BigDecimal.valueOf(100));
                } catch (Exception e) {
                    log.warn("[微信支付] 解析金额失败: {}", e.getMessage());
                }
            }

            log.info("[微信支付] 回调数据解析完成: orderNo={}, tradeNo={}, amount={}",
                    orderNo, transactionId, paidAmount);

            return new PaymentChannelManager.PaymentCallbackResult(
                    true, transactionId, paidAmount, paidAt, resourcePlaintext);

        } catch (Exception e) {
            log.error("[微信支付] 回调解析异常: {}", e.getMessage(), e);
            return new PaymentChannelManager.PaymentCallbackResult(
                    false, null, null, null, resourcePlaintext);
        }
    }

    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '\"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '\"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end))
                    || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }

    private String extractNestedJsonValue(String json, String parentKey, String childKey) {
        if (json == null) return null;
        String parentSearch = "\"" + parentKey + "\"";
        int parentIdx = json.indexOf(parentSearch);
        if (parentIdx < 0) return null;
        int braceStart = json.indexOf('{', parentIdx);
        if (braceStart < 0) return null;
        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd < 0) return null;
        String nested = json.substring(braceStart, braceEnd + 1);
        return extractJsonValue(nested, childKey);
    }

    private int findMatchingBrace(String json, int start) {
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
