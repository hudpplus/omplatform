package com.omplatform.examples.seata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;

@Configuration
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true", matchIfMissing = false)
public class SeataDataSourceConfig {

    @Bean("seataDataSource")
    public DataSource seataDataSource(DataSource dataSource) {
        try {
            Class<?> proxyClass = Class.forName("io.seata.rm.datasource.DataSourceProxy");
            Constructor<?> ctor = proxyClass.getConstructor(DataSource.class);
            Object proxy = ctor.newInstance(dataSource);
            return (DataSource) proxy;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Seata is enabled but DataSourceProxy class not found on classpath", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Seata DataSourceProxy", e);
        }
    }
}

