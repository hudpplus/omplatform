package com.omplatform.common.tracing;

import com.omplatform.common.api.ApiResult;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * 在所有 {@link ApiResult} 响应中注入 traceId。
 * <p>
 * 依赖 Micrometer Tracing 自动向 MDC 写入 traceId，
 * 这里只需在序列化前读取 MDC 填入 ApiResult。
 * <p>
 * 由 {@link TracingHttpAutoConfiguration} 按条件注册。
 */
@RestControllerAdvice
public class TraceIdResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResult.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResult<?> result) {
            if (result.getTraceId() == null) {
                String traceId = MDC.get("traceId");
                if (traceId != null) {
                    result.setTraceId(traceId);
                }
            }
        }
        return body;
    }
}
