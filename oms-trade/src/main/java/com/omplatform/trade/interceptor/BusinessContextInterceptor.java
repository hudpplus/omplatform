package com.omplatform.trade.interceptor;

import com.omplatform.trade.sharding.BusinessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Web 拦截器 — 从请求头解析 business_type 并设置到 {@link BusinessContext}。
 * <p>
 * 客户端通过 HTTP Header {@code X-Business-Type} 传递业务线标识。
 * 无此 Header 时默认使用 {@code ecommerce}。
 */
@Component
public class BusinessContextInterceptor implements HandlerInterceptor {

    /** 默认业务线 */
    private static final String DEFAULT_BUSINESS_TYPE = "ecommerce";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String businessType = request.getHeader("X-Business-Type");
        if (businessType == null || businessType.isBlank()) {
            businessType = DEFAULT_BUSINESS_TYPE;
        }
        String buyerId = request.getHeader("X-Buyer-Id");

        BusinessContext.setAll(businessType, buyerId, null);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        BusinessContext.clear();
    }
}
