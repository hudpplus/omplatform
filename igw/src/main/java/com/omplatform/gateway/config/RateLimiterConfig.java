package com.omplatform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Sentinel/Redis 限流配置。
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 按用户 ID 限流（从 JWT 解析）。
     * 匿名请求按 IP 限流。
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId == null) {
                userId = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            }
            return Mono.just(userId);
        };
    }
}
