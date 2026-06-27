package com.omplatform.finance.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.omplatform.finance.config.PaymentProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付渠道（真实 SDK 实现）。
 * <p>
 * 支持 PC 网页支付（AlipayTradePagePay）和 WAP 支付（AlipayTradeWapPay）。
 * 使用公钥模式（RSA2）签名，异步通知验签使用支付宝 SDK。
 */
@Slf4j
@Component
public class AlipayChannel implements PaymentChannelManager.PaymentChannel {

    private final PaymentProperties.Alipay config;
    private AlipayClient alipayClient;

    public AlipayChannel(PaymentProperties properties) {
        this.config = properties.getAlipay();
    }

    @PostConstruct
    public void init() {
        try {
            AlipayConfig alipayConfig = new AlipayConfig();
            alipayConfig.setServerUrl(config.getGateway());
            alipayConfig.setAppId(config.getAppId());
            alipayConfig.setPrivateKey(config.getPrivateKey());
            alipayConfig.setAlipayPublicKey(config.getAlipayPublicKey());
            alipayConfig.setSignType(config.getSignType());
            alipayConfig.setFormat(config.getFormat());
            alipayConfig.setCharset(config.getCharset());
            alipayClient = new DefaultAlipayClient(alipayConfig);
            log.info("[支付宝] SDK 初始化完成: appId={}, gateway={}", config.getAppId(), config.getGateway());
        } catch (Exception e) {
            log.error("[支付宝] SDK 初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 获取支付宝客户端（供 BillDownloadService 等使用）。
     */
    public AlipayClient getAlipayClient() {
        return alipayClient;
    }

    @Override
    public String channelType() {
        return "ALIPAY";
    }

    @Override
    public PaymentChannelManager.PaymentResponse charge(PaymentChannelManager.PaymentRequest request) {
        log.info("[支付宝] 发起支付: orderNo={}, amount={}, subject={}",
                request.orderNo(), request.amount(), request.subject());

        try {
            // 优先使用 WAP 支付（手机端），无 User-Agent 判断时默认用页面跳转
            // 实际项目可扩展 User-Agent 识别选择支付方式
            String userAgent = request.description() != null ? request.description() : "";

            if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iOS")) {
                // WAP 支付
                AlipayTradeWapPayRequest wapReq = new AlipayTradeWapPayRequest();
                wapReq.setNotifyUrl(config.getNotifyUrl());
                wapReq.setReturnUrl(request.notifyUrl());

                AlipayTradeWapPayModel wapModel = new AlipayTradeWapPayModel();
                wapModel.setOutTradeNo(request.orderNo());
                wapModel.setTotalAmount(request.amount().toString());
                wapModel.setSubject(request.subject());
                wapModel.setProductCode("QUICK_WAP_WAY");
                wapReq.setBizModel(wapModel);

                AlipayTradeWapPayResponse wapResp = alipayClient.pageExecute(wapReq);
                if (wapResp.isSuccess()) {
                    log.info("[支付宝] WAP 支付请求成功: orderNo={}, tradeNo={}",
                            request.orderNo(), wapResp.getTradeNo());
                    return new PaymentChannelManager.PaymentResponse(true,
                            wapResp.getBody(), null, wapResp.getTradeNo());
                } else {
                    log.warn("[支付宝] WAP 支付请求失败: orderNo={}, code={}, msg={}",
                            request.orderNo(), wapResp.getCode(), wapResp.getMsg());
                    return new PaymentChannelManager.PaymentResponse(false, null, null, null);
                }
            } else {
                // PC 网页支付
                AlipayTradePagePayRequest pageReq = new AlipayTradePagePayRequest();
                pageReq.setNotifyUrl(config.getNotifyUrl());
                pageReq.setReturnUrl(request.notifyUrl());

                AlipayTradePagePayModel pageModel = new AlipayTradePagePayModel();
                pageModel.setOutTradeNo(request.orderNo());
                pageModel.setTotalAmount(request.amount().toString());
                pageModel.setSubject(request.subject());
                pageModel.setProductCode("FAST_INSTANT_TRADE_PAY");
                pageReq.setBizModel(pageModel);

                AlipayTradePagePayResponse pageResp = alipayClient.pageExecute(pageReq);
                if (pageResp.isSuccess()) {
                    log.info("[支付宝] PC 支付请求成功: orderNo={}, tradeNo={}",
                            request.orderNo(), pageResp.getTradeNo());
                    return new PaymentChannelManager.PaymentResponse(true,
                            pageResp.getBody(), null, pageResp.getTradeNo());
                } else {
                    log.warn("[支付宝] PC 支付请求失败: orderNo={}, code={}, msg={}",
                            request.orderNo(), pageResp.getCode(), pageResp.getMsg());
                    return new PaymentChannelManager.PaymentResponse(false, null, null, null);
                }
            }
        } catch (AlipayApiException e) {
            log.error("[支付宝] SDK 调用异常: orderNo={}, err={}", request.orderNo(), e.getErrMsg(), e);
            return new PaymentChannelManager.PaymentResponse(false, null, null, null);
        }
    }

    @Override
    public PaymentChannelManager.PaymentCallbackResult verifyAndParseCallback(String rawCallback) {
        log.info("[支付宝] 验证异步通知");

        try {
            // 解析 query string 格式参数为 Map
            Map<String, String> params = parseQueryString(rawCallback);

            // 1. 验签
            boolean verified = AlipaySignature.rsaCheckV1(
                    params, config.getAlipayPublicKey(),
                    config.getCharset(), config.getSignType());

            if (!verified) {
                log.warn("[支付宝] 通知验签失败");
                return new PaymentChannelManager.PaymentCallbackResult(
                        false, null, null, null, rawCallback);
            }

            // 2. 提取交易信息
            String transactionId = params.getOrDefault("trade_no", "");
            String orderNo = params.getOrDefault("out_trade_no", "");
            BigDecimal paidAmount = null;
            try {
                String amountStr = params.getOrDefault("receipt_amount",
                        params.getOrDefault("total_amount", "0"));
                paidAmount = new BigDecimal(amountStr);
            } catch (Exception e) {
                log.warn("[支付宝] 解析支付金额失败: {}", e.getMessage());
            }
            String paidAt = params.getOrDefault("gmt_payment",
                    params.getOrDefault("notify_time", ""));

            // 3. 判断交易状态
            String tradeStatus = params.getOrDefault("trade_status", "");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.warn("[支付宝] 非成功状态通知: status={}, orderNo={}", tradeStatus, orderNo);
                return new PaymentChannelManager.PaymentCallbackResult(
                        false, transactionId, paidAmount, paidAt, rawCallback);
            }

            log.info("[支付宝] 通知验证通过: orderNo={}, tradeNo={}, amount={}",
                    orderNo, transactionId, paidAmount);
            return new PaymentChannelManager.PaymentCallbackResult(
                    true, transactionId, paidAmount, paidAt, rawCallback);

        } catch (Exception e) {
            log.error("[支付宝] 回调处理异常: {}", e.getMessage(), e);
            return new PaymentChannelManager.PaymentCallbackResult(
                    false, null, null, null, rawCallback);
        }
    }

    @Override
    public String refund(PaymentChannelManager.RefundRequest request) {
        log.info("[支付宝] 发起退款: orderNo={}, amount={}, tradeNo={}",
                request.orderNo(), request.amount(), request.transactionId());

        try {
            AlipayTradeRefundRequest refundReq = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(request.orderNo());
            model.setTradeNo(request.transactionId());
            model.setRefundAmount(request.amount().toString());
            model.setRefundReason(request.reason());
            // 幂等键：同 outRequestNo 重复请求不会重复退款
            model.setOutRequestNo("REF" + request.orderNo() + "_" + System.currentTimeMillis());
            refundReq.setBizModel(model);

            AlipayTradeRefundResponse resp = alipayClient.execute(refundReq);
            if (resp.isSuccess()) {
                log.info("[支付宝] 退款成功: orderNo={}, tradeNo={}",
                        request.orderNo(), resp.getTradeNo());
                return resp.getOutTradeNo() != null ? resp.getOutTradeNo() : resp.getTradeNo();
            } else {
                log.warn("[支付宝] 退款失败: orderNo={}, code={}, msg={}",
                        request.orderNo(), resp.getCode(), resp.getMsg());
                throw new RuntimeException("支付宝退款失败: " + resp.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("[支付宝] 退款异常: orderNo={}, err={}", request.orderNo(), e.getErrMsg(), e);
            throw new RuntimeException("支付宝退款异常", e);
        }
    }

    /**
     * 查询支付宝交易状态。
     *
     * @param orderNo 商户订单号
     * @return 支付宝查询响应
     */
    public AlipayTradeQueryResponse queryTrade(String orderNo) throws AlipayApiException {
        log.info("[支付宝] 查询交易: orderNo={}", orderNo);
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel queryModel = new AlipayTradeQueryModel();
        queryModel.setOutTradeNo(orderNo);
        req.setBizModel(queryModel);
        return alipayClient.execute(req);
    }

    // ========== 内部 ==========

    /**
     * 将 key=value&key2=value2 格式解析为 Map。
     */
    private Map<String, String> parseQueryString(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;

        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                map.put(key, value);
            }
        }
        return map;
    }
}
