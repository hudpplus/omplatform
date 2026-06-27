package com.omplatform.trade.es.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 执行器配置。
 * <p>
 * 注册到 xxl-job-admin（docker-compose 中已包含），
 * 通过 {@code @XxlJob} 注解定义任务。
 */
@Slf4j
@Configuration
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:http://localhost:8082}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:omplatform}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:oms-trade}")
    private String appname;

    @Value("${xxl.job.executor.port:9999}")
    private int executorPort;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("XXL-Job 执行器初始化: admin={}, appname={}, port={}",
                adminAddresses, appname, executorPort);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appname);
        executor.setPort(executorPort);
        executor.setLogRetentionDays(logRetentionDays);
        return executor;
    }
}
