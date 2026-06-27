package com.omplatform.marketing;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * oms-marketing 启动类。
 * <p>
 * 合并原 4 个服务（price + promotion + coupon + member）。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.omplatform.marketing.repository")
public class OmsMarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsMarketingApplication.class, args);
    }
}
