package com.omplatform.trade.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 注意：MyBatis-Plus 3.5.9 已移除 PaginationInnerInterceptor，
 * 分页功能由 MybatisPlusInterceptor 内置处理。
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件：乐观锁。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 审计字段自动填充。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "gmtCreate", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "gmtModified", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "version", Long.class, 0L);
                this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "gmtModified", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
