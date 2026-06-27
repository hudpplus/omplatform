package com.omplatform.risk;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * oms-risk-integration 启动类（ACL 防腐层）。
 * <p>
 * 集成外部风控平台，提供黑白名单缓存和三级降级能力。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
public class OmsRiskIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsRiskIntegrationApplication.class, args);
    }
}
