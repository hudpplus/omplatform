package com.omplatform.common.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;

/**
 * HTTP 链路追踪自动配置（Servlet 容器）。
 * <p>
 * 仅在 {@code io.micrometer.tracing.Tracer} 可用且为 Servlet Web 环境时生效。
 * 将 {@link TraceIdResponseAdvice} 注入 Spring 容器。
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
@ConditionalOnWebApplication(type = Type.SERVLET)
public class TracingHttpAutoConfiguration {

    @Bean
    public TraceIdResponseAdvice traceIdResponseAdvice() {
        return new TraceIdResponseAdvice();
    }
}
