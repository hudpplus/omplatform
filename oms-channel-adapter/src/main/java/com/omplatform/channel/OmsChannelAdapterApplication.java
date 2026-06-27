package com.omplatform.channel;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * oms-channel-adapter 启动类（ACL 防腐层）。
 * <p>
 * 独立于核心交易链路部署，新增渠道变更不影响其他服务。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
public class OmsChannelAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmsChannelAdapterApplication.class, args);
    }
}
