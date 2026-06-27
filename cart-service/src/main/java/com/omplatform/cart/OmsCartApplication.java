package com.omplatform.cart;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 购物车服务启动类（ADR-044）。
 */
@EnableDubbo
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
public class OmsCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsCartApplication.class, args);
    }
}
