package com.omplatform.extgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Ext GW — External Gateway 启动类。
 * <p>
 * 外部流量入口：HMAC 签名验证、渠道认证、应用配额管理、Open API 版本路由。
 * 接收渠道 Webhook（天猫/京东/抖音）和第三方开发者 Open API 请求。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class EgwApplication {

    public static void main(String[] args) {
        SpringApplication.run(EgwApplication.class, args);
    }
}
