package com.omplatform.seckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀服务启动类。
 * <p>
 * 独立部署的秒杀微服务，通过 Dubbo 调用 oms-trade 的订单创建 Saga 能力。
 */
@SpringBootApplication
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}
