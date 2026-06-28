package com.omplatform.trade.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.omplatform.trade.sharding.BusinessContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 动态表名拦截器按 {@link BusinessContext#getBusinessType()} 将 {@code order_ecommerce}
 * 改写为 {@code order_locallife} / {@code order_b2b}，与底层 ShardingSphere 的路由配合，
 * 实现三业务线使用同一 OrderEntity 但查询不同物理分片。
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis-Plus 插件：动态表名 + 乐观锁。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 动态表名：按 BusinessContext 改写 order_ecommerce → order_{businessType}
        DynamicTableNameInnerInterceptor dynamic = new DynamicTableNameInnerInterceptor();
        dynamic.setTableNameHandler((sql, tableName) -> {
            if ("order_ecommerce".equals(tableName)) {
                String bt = BusinessContext.getBusinessType();
                String target = "order_" + bt;
                // 避免无效路由（防止 typo 导致 SQL 错误）
                if ("order_ecommerce".equals(target) || "order_locallife".equals(target) || "order_b2b".equals(target)) {
                    return target;
                }
            }
            if ("order_ecommerce_ext".equals(tableName)) {
                String bt = BusinessContext.getBusinessType();
                String target = "order_" + bt + "_ext";
                if ("order_ecommerce_ext".equals(target) || "order_locallife_ext".equals(target) || "order_b2b_ext".equals(target)) {
                    return target;
                }
            }
            return tableName;
        });
        interceptor.addInnerInterceptor(dynamic);

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
