package com.omplatform.trade.config;

import com.omplatform.trade.interceptor.BusinessContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — 注册全局拦截器。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private BusinessContextInterceptor businessContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(businessContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/health/**");
    }
}
