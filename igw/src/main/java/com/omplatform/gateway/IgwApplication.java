package com.omplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * IGW — Internal Gateway 启动类。
 * <p>
 * 统一内部流量入口（Buyer/Admin 合一），
 * 职责：JWT 认证、路由、限流熔断、审计日志。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class IgwApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgwApplication.class, args);
    }
}
