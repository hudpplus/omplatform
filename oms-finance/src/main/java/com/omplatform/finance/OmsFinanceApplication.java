package com.omplatform.finance;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * oms-finance 启动类。
 */
@EnableDubbo
@EnableDiscoveryClient
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan("com.omplatform.finance.mapper")
@SpringBootApplication
public class OmsFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsFinanceApplication.class, args);
    }
}
