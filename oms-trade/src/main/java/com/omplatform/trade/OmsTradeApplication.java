package com.omplatform.trade;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * oms-trade 启动类。
 * <p>
 * 合并原 6 个服务（order-core + workflow + cart + order-query + notification + id-gen）。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
public class OmsTradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsTradeApplication.class, args);
    }
}
