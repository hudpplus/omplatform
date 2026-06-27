package com.omplatform.trade.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADR-017 迁移配置 — 通过 Apollo 动态控制双写、读路由等迁移行为。
 * <p>
 * 本地开发默认值见 {@code application.yml} {@code omplatform.migration} 节。
 */
@Data
@Component
public class MigrationConfig {

    /** 是否开启双写（写新表同时写旧表，Apollo 动态控制） */
    @Value("${omplatform.migration.dual-write.enabled:false}")
    private boolean dualWriteEnabled;

    /** 读路由策略：shardingsphere（新表）| legacy（旧表） */
    @Value("${omplatform.migration.read-router:shardingsphere}")
    private String readRouter;

    /** 写路由策略：shardingsphere（新表）| legacy（旧表） */
    @Value("${omplatform.migration.write-router:shardingsphere}")
    private String writeRouter;

    public boolean isReadFromShardingSphere() {
        return "shardingsphere".equalsIgnoreCase(readRouter);
    }

    public boolean isWriteToShardingSphere() {
        return "shardingsphere".equalsIgnoreCase(writeRouter);
    }
}
