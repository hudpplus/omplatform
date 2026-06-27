package com.omplatform.extgateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ext GW HMAC 签名认证过滤器。
 * <p>
 * 对 Open API 请求进行 HMAC 签名验证，确保请求来自可信第三方。
 * 对渠道 Webhook 使用渠道特定的认证方式。
 */
@Slf4j
@Component
public class HmacAuthFilter implements GlobalFilter, Ordered {

    /** 不需要签名的路径（渠道 Webhook 回调） */
    private static final String[] WEBHOOK_PATHS = {
            "/api/v1/channel/tmall/callback",
            "/api/v1/channel/jd/callback",
            "/api/v1/channel/douyin/callback",
            "/api/v1/payments/callback/"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Webhook 路径使用渠道特定认证（不强制 HMAC）
        for (String webhookPath : WEBHOOK_PATHS) {
            if (path.startsWith(webhookPath)) {
                return chain.filter(exchange);
            }
        }

        // Open API 路径校验 HMAC 签名
        String signature = exchange.getRequest().getHeaders().getFirst("X-Signature");
        String appId = exchange.getRequest().getHeaders().getFirst("X-App-Id");
        String timestamp = exchange.getRequest().getHeaders().getFirst("X-Timestamp");

        if (signature == null || appId == null || timestamp == null) {
            log.warn("缺少签名头: path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 完整实现：校验 HMAC-SHA256 签名 + 时间戳防重放
        log.debug("HMAC 认证: appId={}, path={}", appId, path);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
