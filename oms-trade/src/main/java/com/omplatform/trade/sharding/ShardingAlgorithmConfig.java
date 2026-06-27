package com.omplatform.trade.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ShardingSphere 分片算法注册。
 * <p>
 * 在 {@code application.yml} 的 {@code shardingsphere.rules.sharding.sharding-algorithms} 中
 * 通过 {@code type: CLASS_BASED} + {@code algorithmClassName} 引用此算法。
 */
@Configuration
public class ShardingAlgorithmConfig {

    @Bean
    public StandardShardingAlgorithm<String> orderShardingAlgorithm() {
        return new OrderShardingAlgorithm();
    }
}
