package com.omplatform.common.circuitbreaker.sentinel;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel AOP 配置（启用 @SentinelResource 注解支持）。
 * <p>
 * 所有使用 {@code @SentinelResource} 的模块只需引入此配置类。
 */
@Configuration
public class SentinelConfig {

    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
