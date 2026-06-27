package com.omplatform.finance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付渠道配置属性（绑定 Nacos oms-finance.yaml payment.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** 支付宝配置 */
    private Alipay alipay = new Alipay();

    /** 微信支付配置 */
    private Wechat wechat = new Wechat();

    @Data
    public static class Alipay {
        /** 支付宝应用 APP ID */
        private String appId = "2021000000000000";
        /** 应用私钥（PKCS8 格式 PEM） */
        private String privateKey = "";
        /** 支付宝公钥 */
        private String alipayPublicKey = "";
        /** 网关地址 */
        private String gateway = "https://openapi.alipay.com/gateway.do";
        /** 异步通知 URL */
        private String notifyUrl = "http://localhost:8082/api/payment/callback/alipay";
        /** 签名类型 */
        private String signType = "RSA2";
        /** 返回格式 */
        private String format = "json";
        /** 字符集 */
        private String charset = "UTF-8";
    }

    @Data
    public static class Wechat {
        /** 微信商户号 */
        private String mchId = "10000000";
        /** 应用 ID（公众号/小程序/网站应用） */
        private String appId = "wx0000000000000000";
        /** API v3 密钥（32 位，用于回调解密） */
        private String apiV3Key = "";
        /** 商户 API 私钥路径（classpath:cert/wechat-apiclient.key） */
        private String privateKeyPath = "classpath:cert/wechat-apiclient.key";
        /** 商户证书序列号 */
        private String mchSerialNo = "";
        /** 异步通知 URL */
        private String notifyUrl = "http://localhost:8082/api/payment/callback/wechat";
    }
}
