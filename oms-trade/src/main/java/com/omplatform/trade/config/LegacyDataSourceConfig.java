package com.omplatform.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 旧 `order` 表数据源配置（独立于 ShardingSphere，仅用于 ADR-017 数据迁移/对账）。
 * <p>
 * ShardingSphere 接管了 {@code spring.datasource}，因此旧表（{@code oms_trade.order}）
 * 需要额外配置一个独立数据源来绕过分片引擎直接访问。
 * <p>
 * 用于：
 * <ul>
 *   <li>{@link com.omplatform.trade.migration.service.DataMigrationService} — 读取旧表 → 写入新表</li>
 *   <li>{@link com.omplatform.trade.migration.job.ReconciliationJob} — 数据一致性对账</li>
 * </ul>
 */
@Configuration
public class LegacyDataSourceConfig {

    /**
     * 旧表数据源：直连 {@code oms_trade} 数据库，不走 ShardingSphere。
     * <p>
     * 配置前缀 {@code legacy.datasource}（见 application.yml）。
     */
    @Bean
    @ConfigurationProperties(prefix = "legacy.datasource")
    public DataSource legacyDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 旧表数据源对应的 JdbcTemplate。
     */
    @Bean
    public JdbcTemplate legacyJdbcTemplate(DataSource legacyDataSource) {
        return new JdbcTemplate(legacyDataSource);
    }
}
