package com.omplatform.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Dubbo RPC 调用链路追踪 Filter。
 * <p>
 * <ul>
 *   <li><b>Consumer 端</b> — 将当前 TraceContext 注入 Dubbo RPC 隐式参数</li>
 *   <li><b>Provider 端</b> — 从 RPC 参数提取 TraceContext，创建子 Span</li>
 * </ul>
 * <p>
 * 由 {@link TracingDubboAutoConfiguration} 按条件注册。
 */
@Slf4j
public class TracingDubboFilter implements Filter {

    private static final String SPAN_NAME_PREFIX = "dubbo/";

    private final Tracer tracer;
    private final Propagator propagator;

    public TracingDubboFilter(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (RpcContext.getContext().isProviderSide()) {
            return doProviderInvoke(invoker, invocation);
        } else {
            return doConsumerInvoke(invoker, invocation);
        }
    }

    // ========== Provider 侧：提取上游 TraceContext，创建子 Span ==========

    private Result doProviderInvoke(Invoker<?> invoker, Invocation invocation) {
        String spanName = SPAN_NAME_PREFIX + invocation.getMethodName();

        // 从 RPC 隐式参数提取追踪上下文，创建子 Span
        Map<String, String> headers = extractTraceHeaders(invocation);
        Span span;
        if (!headers.isEmpty()) {
            var builder = propagator.extract(headers,
                    (Map<String, String> map, String key) -> map.get(key));
            span = builder.name(spanName).start();
        } else {
            span = tracer.nextSpan().name(spanName).start();
        }

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            log.debug("[Tracing] Provider span started: {} (traceId={})",
                    spanName, span.context().traceId());
            return invoker.invoke(invocation);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    // ========== Consumer 侧：将当前 TraceContext 注入 RPC 参数 ==========

    private Result doConsumerInvoke(Invoker<?> invoker, Invocation invocation) {
        var currentContext = tracer.currentTraceContext().context();
        if (currentContext != null) {
            // 使用 B3 格式注入追踪上下文到 RPC 隐式参数
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(currentContext, carrier,
                    (Map<String, String> map, String key, String value) -> map.put(key, value));
            carrier.forEach(invocation::setAttachment);
            log.debug("[Tracing] Consumer injected trace: traceId={}",
                    currentContext.traceId());
        }
        return invoker.invoke(invocation);
    }

    // ========== 工具方法 ==========

    private static Map<String, String> extractTraceHeaders(Invocation invocation) {
        Map<String, String> headers = new HashMap<>();
        Map<String, String> attachments = invocation.getAttachments();
        if (attachments == null) return headers;

        for (Map.Entry<String, String> entry : attachments.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("x-b3-") || key.startsWith("traceparent")
                    || key.startsWith("tracestate")) {
                headers.put(key, entry.getValue());
            }
        }
        return headers;
    }
}
