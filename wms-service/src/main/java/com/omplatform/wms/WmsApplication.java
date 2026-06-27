package com.omplatform.wms;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * wms-service 启动类。
 * <p>
 * 仓储管理服务：仓库/库区/库位/批次库存/入库/出库/盘点/补货。
 */
@EnableDubbo
@EnableDiscoveryClient
@SpringBootApplication
@EnableScheduling
@MapperScan("com.omplatform.wms.mapper")
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
