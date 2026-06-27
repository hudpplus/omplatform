# ADR-031: 数据归档与生命周期管理

## 状态
已接受

## 背景

### 现状分析

当前数据保留策略散落在 10+ 个 ADR 中，缺少统一的生命周期治理框架：

| ADR/文档 | 数据类型 | 保留策略 | 清理方式 |
|---------|---------|---------|---------|
| ADR-010 (事件 Schema) | 事件归档表 | 180 天 → OSS | 按月分区，自动归档 |
| ADR-012 (ES ILM) | ES 索引 | Hot 30d → Warm 90d → Cold 180d → Delete 365d | ILM 自动执行 |
| ADR-017 (业务线隔离) | 业务订单 | 电商 3 年 / 本地生活 1 年 / B2B 7 年 | 计划中 |
| ADR-019 (异步任务) | 导出文件 | 7 天 | XXL-Job 每日清理 |
| ADR-020 (Saga) | Saga 日志 | 30 天 | expire_at 字段（无自动清理） |
| ADR-021 (延迟任务) | 调度记录 | 7 天（任务）/ 30 天（幂等）/ 90 天（执行日志） | XXL-Job 每日清理 |
| ADR-022 (灰度) | 灰度数据 | 7 天 | 独立表自动清理 |
| ADR-026 (认证授权) | Token 黑名单 | 有效期截止后待清理 | 每日 3am 定时 Job |
| ADR-028 (密钥管理) | 审计日志 | 3 年 | 计划中 |
| security.puml | 数据生命周期 | "90d→3y→5y→7y→匿名化" | 仅一行概念描述 |

**存在的问题**：

1. **各自为政**：每个 ADR 独立定义清理策略，缺乏统一编排和监控
2. **冷存储未设计**：ADR-010 提了 "180 天归档到 OSS" 但无实现细节（OSS 路径结构、归档格式、检索流程）
3. **冲突未解决**：ADR-012 ES ILM 365d 删除，但 B2B 业务数据需保留 7 年（ADR-017），缺少 ES 冷索引保留方案
4. **合规缺失**：无个保法（PIPL）数据最小化原则落地、无 GDPR "被遗忘权" API、无数据删除审计轨迹
5. **安全风险**：物理删除无观察期，一旦误删无法恢复
6. **缺少统一元数据**：没有 `data_lifecycle_config` 表或 Apollo 配置来集中管理所有实体的保留策略

### 目标

1. 建立统一的数据生命周期治理框架（热 → 温 → 冷 → 匿名化 → 清除）
2. 设计 OSS 冷存储归档方案（格式、路径、检索）
3. 设计分步安全删除流程（逻辑标记 → 观察期 → 匿名化 → 物理清除）
4. 制定法规合规策略（等保三级/个保法/GDPR）
5. 解决 ES ILM 与业务线长周期保留的冲突

## 决策

### 方案对比：生命周期编排引擎

| 维度 | 方案 A：XXL-Job 统一编排 | 方案 B：各服务自行调度 | 方案 C：独立 Lifecycle 服务 |
|------|-------------------------|---------------------|--------------------------|
| 架构 | 中心化调度 | 无中心 | 独立微服务 |
| 一致性 | 统一策略配置 | 各自实现，可能不一致 | 统一策略 + 执行分离 |
| 灵活性 | 中（Job 间无依赖编排） | 高 | 高 |
| 运维成本 | 低（已有 XXL-Job） | 低 | 高（新增服务） |
| 扩展性 | 中 | 低（重复实现） | 高（独立演进） |

**选择：方案 A（XXL-Job 统一编排）**

**选型理由**：
- XXL-Job 已在项目中使用（ADR-019/020/021），无需引入新组件
- 数据清理/归档是典型的定时批处理场景，XXL-Job 的分片广播适合大规模数据分批处理
- 统一管理所有生命周期 Job，方便监控和运维

### 数据删除安全策略

```
逻辑删除（is_deleted=1）→ [90 天观察期] → 匿名化(PII 清零) → [30 天保留] → 物理清除
           ↑ 可恢复             ↑ 可部分恢复           ↑ 不可恢复              ↑ 最终
```

**选择理由**：
- 逻辑删除 + 观察期防止误删导致数据丢失
- 匿名化阶段确保 PII 数据在物理删除前已被清除（满足等保三级残留信息保护要求）
- 物理删除前保留 30 天，用于最终对账和数据一致性校验

## 详细设计

### 1. 数据分级与保留策略矩阵

**四级分类**：

| 级别 | 名称 | 存储介质 | 访问频率 | 性能要求 |
|------|------|---------|---------|---------|
| **L1 热** | 在线交易数据 | OceanBase / Redis | 高频（秒级） | P99 < 10ms |
| **L2 温** | 历史查询数据 | OceanBase / ES | 中频（分钟~小时） | P99 < 200ms |
| **L3 冷** | 归档数据 | OSS（Parquet） | 低频（天~月） | 查询可等待 5min+ |
| **L4 销毁** | 过期数据 | - | 永不访问 | 安全清除 |

**保留策略矩阵**：

| 数据域 | 数据类型 | L1 热 | L2 温 | L3 冷 | L4 销毁 | 合规依据 |
|-------|---------|-------|-------|-------|--------|---------|
| **订单-电商** | 订单/订单项/物流 | 90d | 1y | 3y | 3y+90d | 电商法 |
| **订单-本地生活** | 订单/核销记录 | 30d | 180d | 1y | 1y+90d | 业务需要 |
| **订单-B2B** | 订单/审批/分期 | 180d | 3y | 7y | 7y+90d | 合同法 |
| **支付** | 支付流水/退款 | 90d | 1y | 5y | 5y+90d | 金融合规 |
| **事件** | 领域事件归档 | 30d | 180d | 2y | 2y+90d | 审计需要 |
| **日志** | ES 索引 | 30d | 90d | 180d | 365d | 运维需要 |
| **任务** | 导出/异步任务 | 3d | 7d | 30d | 30d+90d | 存储成本 |
| **幂等** | idempotent_record | 7d | - | 30d(观察期) | 37d | 存储成本 |
| **Saga** | 分布式事务日志 | 7d | 14d | 30d | 30d+90d | 一致性需要 |
| **Auth** | Token 黑名单 | - | - | TTL+7d | TTL+7d | 安全需要 |
| **密钥审计** | Vault 操作日志 | 30d | 1y | 3y | 3y+90d | 等保三级 |
| **通用审计** | 审计日志 | 30d | 180d | 2y | 2y+90d | 审计需要 |

### 2. 生命周期元数据注册

**配置表**：

```sql
CREATE TABLE `data_lifecycle_config` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT,
    `domain`          VARCHAR(64)     NOT NULL COMMENT '数据域（order/payment/event/log/task/auth）',
    `entity_name`     VARCHAR(128)    NOT NULL COMMENT '实体名称（order/payment_record）',
    `business_line`   VARCHAR(32)     NOT NULL DEFAULT 'all' COMMENT '业务线（ecommerce/locallife/b2b/all）',
    `retain_hot_days` INT             NOT NULL COMMENT '热数据保留天数',
    `retain_warm_days` INT            NOT NULL COMMENT '温数据保留天数',
    `retain_cold_days` INT            NOT NULL COMMENT '冷数据（归档）保留天数',
    `archive_enabled` TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用冷存储归档',
    `archive_format`  VARCHAR(16)     DEFAULT 'parquet' COMMENT '归档格式（parquet/json/csv）',
    `anonymize_fields` VARCHAR(1024)  COMMENT '需要匿名化的字段列表（逗号分隔）',
    `purge_grace_days` INT            NOT NULL DEFAULT 90 COMMENT '删除前观察期（天）',
    `status`          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    `gmt_create`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_domain_entity_bl` (`domain`, `entity_name`, `business_line`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据生命周期配置表';
```

**Apollo 同步**：

```java
@Component
public class LifecycleConfigManager {
    
    private static final String NAMESPACE = "data.lifecycle";
    
    @PostConstruct
    public void init() {
        Config config = ConfigService.getConfig(NAMESPACE);
        
        // 初始加载
        refreshConfig();
        
        // 监听变更
        config.addChangeListener(changeEvent -> refreshConfig());
    }
    
    @SneakyThrows
    private void refreshConfig() {
        // 从 DB 加载生命周期配置
        List<DataLifecycleConfig> configs = lifecycleConfigMapper.selectAll();
        lifecycleConfigCache = configs.stream()
            .collect(Collectors.toMap(
                c -> c.getDomain() + ":" + c.getEntityName(),
                Function.identity()));
    }
    
    /**
     * 获取实体的保留策略。
     */
    public RetentionPolicy getRetentionPolicy(String domain, String entityName, String bizLine) {
        DataLifecycleConfig config = lifecycleConfigCache.get(domain + ":" + entityName);
        if (config == null) {
            return RetentionPolicy.defaultPolicy(); // 默认 180d
        }
        return config.toRetentionPolicy();
    }
}
```

**@LifecycleConfig 注解**：

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LifecycleConfig {
    
    String domain();
    
    String entity();
    
    int hotDays() default 90;
    
    int warmDays() default 365;
    
    int coldDays() default 1825; // 5 年
    
    String[] anonymizeFields() default {};
    
    boolean enableArchive() default false;
}

// 使用示例
@LifecycleConfig(
    domain = "payment",
    entity = "payment_record",
    hotDays = 90,
    warmDays = 365,
    coldDays = 1825,
    anonymizeFields = {"buyer_phone", "buyer_name"},
    enableArchive = true
)
public class PaymentRecord { ... }
```

### 3. 冷存储归档流程

**整体流程**：

```
XXL-Job (ArchivalJob) 每小时执行：
  1. 查询待归档数据（retain_hot_days ≤ 当前天数 < retain_warm_days）
  2. 分批读取（每批 5000 条）
  3. 序列化为 Parquet 格式
  4. 上传到 OSS（按日期和业务域分目录）
  5. 写入归档清单表
  6. 标记 OB 源数据为归档状态
  7. 释放 OB 存储空间（DROP PARTITION 或 TRUNCATE）
```

**OSS 路径结构**：

```
oss://omplatform-archive/{env}/
  └── {domain}/
      └── {entity}/
          └── {business_line}/
              └── {year}/
                  └── {month}/
                      └── {batch_id}.parquet

示例：
oss://omplatform-archive/prod/
  order/order_ecommerce/all/2026/01/20260115_001.parquet
  order/order_ecommerce/all/2026/01/20260115_002.parquet
  payment/payment_record/all/2026/01/20260115_001.parquet
```

**归档索引表**：

```sql
CREATE TABLE `archive_manifest` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `domain`            VARCHAR(64)     NOT NULL COMMENT '数据域',
    `entity_name`       VARCHAR(128)    NOT NULL COMMENT '实体名称',
    `business_line`     VARCHAR(32)     NOT NULL DEFAULT 'all',
    `oss_path`          VARCHAR(1024)   NOT NULL COMMENT 'OSS 完整路径',
    `archive_date`      DATE            NOT NULL COMMENT '归档日期（分区字段）',
    `record_count`      INT             NOT NULL COMMENT '归档记录数',
    `file_size_bytes`   BIGINT          NOT NULL COMMENT '归档文件大小',
    `data_start_date`   DATE            NOT NULL COMMENT '归档数据的开始日期',
    `data_end_date`     DATE            NOT NULL COMMENT '归档数据的结束日期',
    `checksum`          VARCHAR(64)     NOT NULL COMMENT '文件 MD5',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED/VERIFIED/FAILED',
    `gmt_create`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_domain_date` (`domain`, `archive_date`),
    KEY `idx_entity_date` (`entity_name`, `data_start_date`, `data_end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档清单表';
```

**ArchivalJob 实现**：

```java
@Component
public class ArchivalJob {
    
    @XxlJob("dataArchivalJob")
    public ReturnT<String> archive(String param) {
        // 1. 获取所有启用了归档的生命周期配置
        List<DataLifecycleConfig> configs = lifecycleConfigMapper.selectByArchiveEnabled(true);
        
        for (DataLifecycleConfig config : configs) {
            // 2. 计算需要归档的时间范围
            LocalDate archiveBefore = LocalDate.now().minusDays(config.getRetainHotDays());
            
            // 3. 分批查询并归档
            int offset = 0;
            int batchSize = 5000;
            
            while (true) {
                List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT * FROM " + config.getEntityName() + 
                    " WHERE gmt_create < ? ORDER BY id LIMIT ? OFFSET ?",
                    archiveBefore, batchSize, offset);
                
                if (records.isEmpty()) break;
                
                // 4. 序列化为 Parquet
                String ossPath = buildOssPath(config, archiveBefore, offset);
                parquetWriter.write(records, ossPath);
                
                // 5. 记录归档清单
                archiveManifestMapper.insert(ArchiveManifest.builder()
                    .domain(config.getDomain())
                    .entityName(config.getEntityName())
                    .ossPath(ossPath)
                    .recordCount(records.size())
                    .archiveDate(LocalDate.now())
                    .dataEndDate(archiveBefore)
                    .checksum(FileUtils.md5(ossPath))
                    .build());
                
                offset += records.size();
            }
            
            // 6. 标记源数据为已归档（逻辑标记）
            jdbcTemplate.update(
                "UPDATE " + config.getEntityName() + 
                " SET is_archived = 1, gmt_archived = NOW()" +
                " WHERE gmt_create < ? AND is_archived = 0",
                archiveBefore);
        }
        
        return ReturnT.SUCCESS;
    }
}
```

### 4. 数据删除与匿名化

**三步安全删除流程**：

```
Phase 1 - 逻辑删除（XXL-Job 每日执行）：
  条件：gmt_create < NOW() - retain_cold_days
  操作：UPDATE SET is_deleted=1, gmt_deleted=NOW()
  可恢复：是（管理员可在 90d 内撤销）

Phase 2 - 匿名化（Phase 1 后 90 天）：
  条件：is_deleted=1 AND gmt_deleted < NOW() - purge_grace_days
  操作：UPDATE SET phone=NULL, name='ANONYMIZED', id_card=NULL, address='ANONYMIZED'
        SET is_anonymized=1, gmt_anonymized=NOW()
  可恢复：否（PII 已清除，但业务数据仍可保留）

Phase 3 - 物理清除（Phase 2 后 30 天）：
  条件：is_anonymized=1 AND gmt_anonymized < NOW() - 30
  操作：DELETE FROM table WHERE id IN (...) （每批 1000 条，防止锁竞争）
        → 记录删除审计日志
  可恢复：否（数据永久删除）
```

**匿名化实现**：

```java
@Component
public class DataAnonymizeJob {
    
    @XxlJob("dataAnonymizeJob")
    public ReturnT<String> anonymize(String param) {
        // 1. 获取需要匿名化的生命周期配置
        List<DataLifecycleConfig> configs = lifecycleConfigMapper.selectAll();
        
        for (DataLifecycleConfig config : configs) {
            if (config.getAnonymizeFields() == null) continue;
            
            String[] fields = config.getAnonymizeFields().split(",");
            // 2. 构建匿名化 SQL
            String anonymizeSql = buildAnonymizeSql(config.getEntityName(), fields);
            
            // 3. 分批执行（每批 1000 条）
            int affected = jdbcTemplate.update(anonymizeSql + 
                " WHERE is_deleted=1 AND is_anonymized=0" +
                " AND gmt_deleted < NOW() - INTERVAL ? DAY" +
                " LIMIT 1000", config.getPurgeGraceDays());
            
            XxlJobHelper.log("[{}] 匿名化 {} 条记录", 
                config.getEntityName(), affected);
        }
        
        return ReturnT.SUCCESS;
    }
    
    private String buildAnonymizeSql(String table, String[] fields) {
        // 按照字段类型生成匿名化 SET 子句
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        for (String field : fields) {
            field = field.trim();
            switch (guessFieldType(field)) {
                case "phone":    sql.append(field).append("=CONCAT(LEFT(").append(field).append(",3),'****',RIGHT(").append(field).append(",4)),"); break;
                case "name":     sql.append(field).append("='ANONYMIZED',"); break;
                case "id_card":  sql.append(field).append("=CONCAT(LEFT(").append(field).append(",6),'**********',RIGHT(").append(field).append(",4)),"); break;
                case "address":  sql.append(field).append("='ANONYMIZED',"); break;
                default:         sql.append(field).append("=NULL,"); break;
            }
        }
        sql.append("is_anonymized=1,gmt_anonymized=NOW() ");
        return sql.toString();
    }
}
```

**物理清除 Job**：

```java
@Component
public class DataPurgeJob {
    
    @XxlJob("dataPurgeJob")
    public ReturnT<String> purge(String param) {
        List<DataLifecycleConfig> configs = lifecycleConfigMapper.selectAll();
        
        for (DataLifecycleConfig config : configs) {
            // 已匿名化并超过 30 天观察期 → 物理删除
            int totalDeleted = 0;
            while (true) {
                int deleted = jdbcTemplate.update(
                    "DELETE FROM " + config.getEntityName() +
                    " WHERE is_anonymized=1" +
                    " AND gmt_anonymized < NOW() - INTERVAL 30 DAY" +
                    " AND gmt_deleted < NOW() - INTERVAL ? DAY" +
                    " LIMIT 1000", config.getPurgeGraceDays() + 30);
                
                if (deleted == 0) break;
                totalDeleted += deleted;
                
                // 记录审计日志
                auditLogService.record(AuditLog.builder()
                    .action("DATA_PURGE")
                    .resource(config.getEntityName())
                    .detail("物理删除 " + deleted + " 条记录")
                    .build());
            }
            
            XxlJobHelper.log("[{}] 物理删除 {} 条记录", config.getEntityName(), totalDeleted);
        }
        
        return ReturnT.SUCCESS;
    }
}
```

### 5. ES 索引生命周期自动化

**解决 ES ILM 与长周期保留的冲突**：

ES ILM（ADR-012 定义 365d delete）与 B2B 业务线 7 年保留期矛盾。解决方案：

```
┌───────────────────────────────────────────────────────┐
│  ES 索引生命周期（按业务线差异化）                      │
├─────────────┬──────────┬──────────┬───────────────────┤
│  阶段        │ 电商      │ 本地生活  │ B2B               │
├─────────────┼──────────┼──────────┼───────────────────┤
│  Hot        │ 30d      │ 30d      │ 30d               │
│  Warm       │ 90d →    │ 90d →    │ 180d →            │
│             │ 1shrink   │ 1shrink  │ 1shrink           │
│  Cold       │ 180d →   │ 180d →   │ 365d →            │
│             │ freeze    │ freeze   │ freeze            │
│  Delete     │ 365d     │ 365d     │ ║ 7y（保留冷索引） ║ │
│  ↓ B2B      │          │          │ 删除/归档到 OSS    │
└─────────────┴──────────┴──────────┴───────────────────┘
```

**B2B 冷索引保留策略**：

```json
{
  "policy": {
    "phases": {
      "hot":  { "min_age": "0d",   "actions": { "rollover": { "max_size": "50GB", "max_age": "30d" }}},
      "warm": { "min_age": "180d", "actions": { "allocate": { "number_of_replicas": 0 }, "forcemerge": { "max_num_segments": 1 }, "shrink": { "number_of_shards": 1 }}},
      "cold": { "min_age": "365d", "actions": { "freeze": {} }},
      "delete": { "min_age": "2555d", "actions": { "delete": {} }}  // 7 年
    }
  }
}
```

**ES 数据归档到 OSS**：

```java
@Component
public class EsArchiveJob {
    
    @XxlJob("esIndexArchiveJob")
    public ReturnT<String> archiveOldIndices(String param) {
        // 对超过 365 天的非 B2B 索引和超过 7 年的 B2B 索引：
        // 1. 快照（Snapshot）到 OSS → 2. 删除 ES 索引 → 3. 记录归档清单
        
        List<String> oldIndices = esClient.getOldIndices(365);
        for (String index : oldIndices) {
            String businessLine = extractBizLine(index);
            int retentionDays = getRetentionDays(businessLine); // 365 / 2555
            int indexAge = getIndexAge(index);
            
            if (indexAge >= retentionDays) {
                // 快照到 OSS
                esClient.snapshot(index, "oss-repository", 
                    "snapshot_" + index + "_" + LocalDate.now());
                // 写入归档清单
                archiveManifestMapper.insert(esArchiveManifest(index));
                // 删除 ES 索引
                esClient.deleteIndex(index);
            }
        }
        return ReturnT.SUCCESS;
    }
}
```

### 6. 冷数据查询策略

**查询路由规则**：

```
用户查询请求
  ↓
① 确定查询时间范围
  ├── 范围 < retain_hot_days → 查 OceanBase（正常响应）
  ├── 范围 < retain_warm_days → 查 OceanBase + ES（可接受延迟）
  ├── 范围 < retain_cold_days → 判断是否已归档
  │   ├── 未归档 → 查 OceanBase（慢查询警告）
  │   └── 已归档 → 提示使用异步导出（ADR-019）
  └── 范围 > retain_cold_days → 拒绝（提示使用离线导出 Job）
```

**归档数据恢复流程**：

```java
/**
 * 归档数据恢复流程（通过 ADR-019 异步任务中心）：
 * 1. 用户提交 "归档数据导出" 请求（选择类型、日期范围）
 * 2. Async Job 查询 archive_manifest → 从 OSS 下载 Parquet
 * 3. 转换为 CSV/Excel → 上传 OSS → 通知用户下载
 * 
 * 限制：
 * - 每次查询跨度不得超过 1 年
 * - 单个用户每天限 5 次
 * - 全量数据导出超过 10 万条走异步
 */
```

### 7. 法规合规框架

**等保三级映射**：

| 等保要求 | 实现方式 | 对应功能 |
|---------|---------|---------|
| 数据分类分级 | 生命周期配置表 + 四级分类 | `data_lifecycle_config` domain 字段 |
| 数据存储冗余 | OceanBase 三副本 + OSS 冗余存储 | 基础架构 |
| 数据删除审批 | 物理删除前记录审计日志 | `data_purge_audit` 表 |
| 残留信息保护 | 匿名化阶段清除 PII 后等 30 天才物理删除 | DataAnonymizeJob |
| 审计日志留存 | 操作日志保留 180 天 | 审计日志表 + 归档到 OSS |

**个保法（PIPL）映射**：

| 要求 | 实现 | 触发条件 |
|------|------|---------|
| 数据最小化 | 按业务线配置差异化保留周期 | 数据创建时 |
| 删除权（第 47 条） | 用户注销 → 15 天内删除 PII 数据 | `DELETE_USER_DATA` Job |
| 查阅权 | 数据可查询但已匿名化不可逆 | 查询 API |
| 同意撤回 | 用户撤回同意 → 停止收集 + 清除历史 | 用户操作 + Job |

**GDPR 被遗忘权 API**：

```java
@RestController
@RequestMapping("/admin/v1/compliance")
public class ComplianceController {
    
    /**
     * 被遗忘权：用户请求删除所有个人数据。
     * 需在 30 天内完成（GDPR 规定 "无不当延迟"）。
     */
    @PostMapping("/erasure-request")
    @RequirePermission(role = "admin")
    public ComplianceResponse submitErasureRequest(@RequestBody ErasureRequest request) {
        // 1. 验证用户身份
        // 2. 创建数据清除工单（记录到 data_purge_audit）
        String ticketId = auditLogService.createErasureTicket(request.getUserId());
        
        // 3. 触发紧急 PII 清理（不走正常 90d 观察期）
        complianceJob.emergencyPiiClean(request.getUserId());
        
        // 4. 记录合规审计：who + when + what data
        auditLogService.record(AuditLog.builder()
            .action("GDPR_ERASURE")
            .resource("user:" + request.getUserId())
            .detail("GDPR 被遗忘权请求，票据号: " + ticketId)
            .operator(request.getRequestedBy())
            .build());
        
        return ComplianceResponse.success(ticketId, 
            "数据清除请求已受理，将在 30 天内完成处理");
    }
}
```

**审计轨迹表**：

```sql
CREATE TABLE `data_purge_audit` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `action`        VARCHAR(32)     NOT NULL COMMENT 'LOGICAL_DELETE/ANONYMIZE/PHYSICAL_PURGE/ARCHIVE/RESTORE',
    `domain`        VARCHAR(64)     NOT NULL COMMENT '数据域',
    `entity_name`   VARCHAR(128)    NOT NULL COMMENT '实体名称',
    `record_count`  INT             NOT NULL COMMENT '影响记录数',
    `batch_id`      VARCHAR(64)     NOT NULL COMMENT '批次 ID',
    `operator`      VARCHAR(64)              COMMENT '操作人（系统操作时为 NULL）',
    `detail`        TEXT             COMMENT '操作详情',
    `gmt_create`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_action_time` (`action`, `gmt_create`),
    KEY `idx_domain_time` (`domain`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据清除审计表';
```

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | 生命周期治理框架：`data_lifecycle_config` 表 + 策略矩阵 + Apollo 命名空间 | 1d | 配置表 + 策略管理器 |
| P2 | 冷存储归档：ArchivalJob + Parquet 序列化 + OSS 上传 + 归档清单表 | 1.5d | 归档 Job + OSS 路径规范 |
| P3 | 三步清理：LogicalDeleteJob + AnonymizeJob + PurgeJob + 审计表 | 1.5d | 三个 Job + 审计轨迹 |
| P4 | ES ILM 差异化 + B2B 冷索引保留 + ES 快照到 OSS | 0.5d | ES 策略调整 + 归档 Job |
| P5 | 合规 API + 紧急清理 + GDPR 被遗忘权接口 | 1d | 合规 Controller + Job |

**合计**：5.5 人天

## 上线检查清单

- [ ] 基础设施：`data_lifecycle_config` 表 + `archive_manifest` 表 + `data_purge_audit` 表 DDL 执行
- [ ] 基础设施：OSS 归档 Bucket 创建 + 目录结构初始化
- [ ] 基础设施：ES ILM 策略调整（区分业务线保留期限）
- [ ] 基础设施：ES snapshot repository 配置（OSS 仓库）
- [ ] 代码：LifecycleConfigManager（Apollo + DB 双驱动）
- [ ] 代码：ArchivalJob（XXL-Job，每小时执行）
- [ ] 代码：LogicalDeleteJob（XXL-Job，每日凌晨执行）
- [ ] 代码：DataAnonymizeJob（XXL-Job，逻辑删除 90d 后执行）
- [ ] 代码：DataPurgeJob（XXL-Job，匿名化 30d 后执行）
- [ ] 代码：EsArchiveJob（XXL-Job，ES 快照归档）
- [ ] 代码：ComplianceController（被遗忘权 API）
- [ ] 配置：Apollo `data.lifecycle` 命名空间填充所有实体的保留策略
- [ ] 监控：`data_purge_audit` 异常操作告警
- [ ] 监控：归档成功率 / 清理延迟告警
- [ ] 测试：归档数据恢复演练（季度一次）

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-012** (ES ILM) | 扩展 ADR-012 ILM 策略：电商 365d delete，B2B 改为 7y（保留冷索引）+ OSS 快照归档 |
| **ADR-010** (事件归档) | 替换 ADR-010 的事件归档设计（180d → OSS），纳入统一生命周期矩阵 |
| **ADR-017** (业务线隔离) | 各业务线差异化保留周期（1/3/7yr）作为生命周期矩阵的核心维度 |
| **ADR-019** (异步任务) | 导出任务 7d 清理纳入统一生命周期管理；归档数据恢复走 Async Job |
| **ADR-020** (Saga) | Saga 日志 30d 清理纳入统一管理 |
| **ADR-021** (延迟任务) | 任务执行日志 90d 清理纳入统一管理 |
| **ADR-028** (密钥管理) | 密钥审计日志 3 年保留与生命周期矩阵协调 |
| **ADR-023** (脱敏) | 匿名化阶段复用 ADR-023 的脱敏算法（PHONE/NAME/ID_CARD/ADDRESS） |
| **ADR-026** (认证授权) | 数据删除/匿名化 API 需要 `@RequirePermission(role = "admin")` 权限控制 |
| **security.puml** Layer 4 | 实现了 "数据生命周期自动管理" 节点的完整设计 |
| **ADR-011** (DDL) | OceanBase 分区清理（3 年以上）纳入 PurgeJob |
| **ADR-015** (容量规划) | 归档策略影响容量模型中的存储成本估算 |

## 备选方案评估

### 方案 B：各服务自行调度

每个服务独立实现清理 Job，各自定义删除策略。

- **优点**：每个服务最了解自身数据特性
- **缺点**：策略不统一 → 运维噩梦；新服务需重复实现删除逻辑；无合规审计保证
- **选型**：❌ 已拒绝

### 方案 C：独立 Lifecycle 微服务

创建一个独立的 data-lifecycle-service，通过 API 统一管理所有数据生命周期。

- **优点**：职责单一，可独立演进
- **缺点**：新增微服务增加运维负担；数据清理逻辑分散在业务服务中（清理 Job 仍需业务服务实现）；过度设计
- **选型**：❌ 已拒绝

### 方案选型：冷存储格式

| 格式 | 压缩率 | 查询能力 | 工具生态 | 选择 |
|------|--------|---------|---------|------|
| JSON | 1x | 需逐行解析 | 普及 | ❌ |
| CSV | 0.5x | 需逐行解析 | 普及 | ❌ |
| **Parquet** | **0.2x** | **谓词下推 + 列裁剪** | **Spark/Presto/Trino** | **✅** |
| Avro | 0.3x | 行式存储 | Hadoop | ❌ |

Parquet 选型理由：压缩率高（列式存储），支持谓词下推（查询时不需要下载全量数据），生态丰富（Spark/Presto/Trino 可直接查询 OSS 上的 Parquet）。
