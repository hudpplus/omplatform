package com.omplatform.fulfillment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * oms-fulfillment 启动类。
 * <p>
 * 合并原 inventory + logistics 服务。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
@EnableScheduling
@MapperScan("com.omplatform.fulfillment.repository")
public class OmsFulfillmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsFulfillmentApplication.class, args);
    }
}
