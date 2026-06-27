package com.omplatform.common.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Dubbo RPC 链路追踪自动配置。
 * <p>
 * 仅在 {@code io.micrometer.tracing.Tracer} 和 {@code org.apache.dubbo.rpc.Filter}
 * 同时可用时生效。
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "io.micrometer.tracing.Tracer",
        "org.apache.dubbo.rpc.Filter"
})
public class TracingDubboAutoConfiguration {

    @Bean
    public TracingDubboFilter tracingDubboFilter(Tracer tracer, Propagator propagator) {
        return new TracingDubboFilter(tracer, propagator);
    }
}
