# ADR-024：慢 SQL 治理

## 状态

已接受

---

## 背景

### 现状分析

订单中台以 OceanBase 为主存储，MyBatis-Plus 为 ORM 框架，日订单量 100 万+。当前在 SQL 质量管控方面存在以下空白：

**问题 1：SQL Review 缺失**  
CI 流水线（`cicd-pipeline.md`）已有 SonarQube 代码分析、JaCoCo 覆盖率等门禁，但缺少 SQL 审查环节。慢 SQL、全表扫描、缺乏索引的查询直到压测或线上才暴露。

**问题 2：无法区分「业务增长」与「SQL 退化」**  
当前监控可以观察 DB 整体 CPU/连接数，但无法区分性能下降的原因是**业务量增长**还是**某条 SQL 执行计划劣化**。P99 抖动时排查耗时长。

**问题 3：索引治理靠人工**  
`order` 表的索引通过 `KEY idx_xxx (col1, col2)` 在建表时定义，索引的新增/删除/变更没有标准流程，缺少覆盖率分析和冗余索引检测。

**问题 4：典型慢查询场景无统一优化模式**  
订单查询常见的「我的订单列表」「按状态统计」「分页深度翻页」等场景，不同开发者可能写出不同质量的 SQL，缺少推荐实现模式。

### OceanBase 特性对慢 SQL 的影响

| 特性 | 对查询的影响 | 需注意的点 |
|------|-------------|-----------|
| **LSM-Tree 存储** | 没有 MySQL 的 B+ 树聚簇索引，Compaction 期间可能影响查询性能 | 大范围扫描性能不如 MySQL |
| **分布式执行计划** | 跨分区查询会变成分布式执行，聚合和排序可能在协调节点完成 | 务必带分区键 WHERE 条件 |
| **全局索引 / 局部索引** | 全局索引跨分区、局部索引分区内 | 局部索引必须带分区键才能分区裁剪 |
| **SQL Plan 绑定** | OceanBase 支持 `OUTLINE` 绑定执行计划 | 紧急处理慢查询的利器 |
| **弱一致性读** | `/*+ READ_CONSISTENCY(WEAK) */` 可走 Follower 节点 | 非关键查询（如列表）可用 |

---

## 决策

**建立三层 SQL 治理体系：**

| 层 | 方案 | 目的 |
|----|------|------|
| **L1 CI SQL Review Gate** | MyBatis-Plus SQL 静态分析 + 自定义规则 + 性能基线 | 在 MR 阶段拦截劣质 SQL |
| **L2 运行时慢 SQL 检测** | OceanBase 慢查询日志 + Prometheus 告警 + SQL Plan 监控 | 实时发现线上慢 SQL |
| **L3 索引治理流程** | pt-query-digest / 索引建议 + 标准化变更流程 | 系统性优化索引 |

**理由：** 三层覆盖 SQL 全生命周期——提交时（CI）、运行时（监控）、持续优化（索引）。每层解决不同阶段的 SQL 问题，互不替代。

---

## 详细设计

### 1. CI SQL Review Gate

#### 1.1 SQL 静态分析规则

在 CI 中引入 SQL 静态分析工具（选型：**SQLFluff** / **SonarQube SQL Plugin**），对 MyBatis Mapper XML 中的 SQL 进行检查：

```yaml
# .gitlab-ci.yml — SQL Review 阶段
stages:
  - sql-review

sql-review:
  stage: sql-review
  image: sqlfluff/sqlfluff:latest
  script:
    - sqlfluff lint src/main/resources/**/*.xml
      --dialect mysql
      --rules SQL_Lint_Rules.md
  rules:
    - if: '$CI_MERGE_REQUEST_ID'      # MR 时执行
  allow_failure: false                # 阻断

# 补充：MyBatis SQL 提取检查
mybatis-sql-scan:
  stage: sql-review
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn mybatis-check:analyze        # 自定义 Maven 插件
    - python scripts/check-sql-patterns.py  # 额外的模式检查
```

#### 1.2 SQL 审查规则定义

```yaml
# sql-review-rules.yaml — SQL 审查规则
rules:
  # ===== 强制阻断规则（阻断 MR） =====
  - id: NO_SELECT_STAR
    severity: BLOCKER
    description: "禁止 SELECT *，必须明确列出查询字段"
    pattern: "SELECT\\s+\\*"
    message: "请明确定义查询字段，避免 SELECT *"

  - id: NO_FULL_TABLE_SCAN
    severity: BLOCKER
    description: "WHERE 条件必须包含索引列"
    check: "missing_index_in_where"
    message: "查询条件 {{columns}} 缺少索引覆盖"

  - id: NO_CROSS_PARTITION_WITHOUT_SHARDING_KEY
    severity: BLOCKER
    description: "分区表查询必须带分区键"
    check: "missing_partition_key"
    table_rules:
      order: "WHERE 必须包含 buyer_id 或 gmt_create"
      event_archive: "WHERE 必须包含 gmt_create"

  - id: MAX_JOIN_TABLE_COUNT
    severity: BLOCKER
    description: "单条 SQL JOIN 表数不超过 3 张"
    check: "join_count <= 3"

  # ===== 警告规则（不阻断，需人工确认） =====
  - id: AVOID_LIKE_PREFIX_WILDCARD
    severity: MAJOR
    description: "LIKE 语句避免前导通配符 %xxx"
    pattern: "LIKE\\s+'%"
    message: "前导 % 无法使用索引，建议使用 ES 替代模糊搜索"

  - id: USE_BATCH_OPERATION
    severity: MAJOR
    description: "循环 SQL 须用批量操作替代"
    check: "loop_sql_detected"
    message: "检测到 for 循环内执行 SQL，建议使用 foreach 批量操作"

  - id: AVOID_NOT_IN
    severity: MAJOR
    description: "避免 NOT IN，建议使用 NOT EXISTS 或 LEFT JOIN"
    pattern: "NOT\\s+IN"
    message: "NOT IN 可能导致全表扫描，建议改为 NOT EXISTS"

  - id: CHECK_INDEX_FOR_ORDER_BY
    severity: MAJOR
    description: "ORDER BY 字段须有索引覆盖"
    check: "order_by_index_check"

  - id: PAGINATION_MUST_USE_CURSOR
    severity: MAJOR
    description: "大数据量分页须用游标分页（Keyset Pagination）"
    check: "offset_pagination_limit"
    threshold: 10000  # offset > 10000 时告警
```

#### 1.3 自定义 Maven 插件：MyBatis SQL Analyzer

```java
/**
 * Maven 插件 — 在 compile 阶段分析 MyBatis Mapper XML
 *
 * 功能：
 * 1. 提取所有 Mapper XML 中的 SQL
 * 2. 解析 SQL 的 SELECT/FROM/WHERE/JOIN/ORDER BY 结构
 * 3. 对比数据库索引元信息，检查查询是否命中索引
 * 4. 检测 SQL 反模式（SELECT *, NOT IN, 前导 % 等）
 * 5. 输出分析报告到 target/sql-review/
 */
@Mojo(name = "sql-analyze", requiresProject = true,
      defaultPhase = LifecyclePhase.COMPILE)
public class MyBatisSqlAnalyzerMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.resources}")
    private Resource[] resources;

    @Parameter(defaultValue = "sql-review-rules.yaml")
    private String rulesConfig;

    @Override
    public void execute() throws MojoExecutionException {
        // 1. 收集所有 Mapper XML
        List<File> mappers = collectMapperFiles();
        getLog().info("Found " + mappers.size() + " mapper files");

        // 2. 加载索引元信息（从 OceanBase information_schema 快照）
        IndexRegistry indexRegistry = loadIndexMetadata();

        // 3. 逐条分析 SQL
        List<SqlViolation> violations = new ArrayList<>();
        for (File mapper : mappers) {
            List<SqlStatement> statements = parseMapper(mapper);
            for (SqlStatement stmt : statements) {
                violations.addAll(AnalyzerChain.analyze(stmt, indexRegistry));
            }
        }

        // 4. 输出报告
        ReportWriter.write(violations, new File("target/sql-review/report.html"));

        // 5. 阻断判断
        boolean hasBlocker = violations.stream()
                .anyMatch(v -> v.getSeverity() == Severity.BLOCKER);
        if (hasBlocker) {
            throw new MojoExecutionException(
                "SQL Review 未通过，发现 " + violations.size() + " 条违规");
        }
    }
}
```

#### 1.4 MR 检查清单集成

在 GitLab CI MR 模板中增加 SQL Review 项：

```yaml
# .gitlab/merge_request_templates/default.md
## SQL Review Checklist

- [ ] 新增/修改的 SQL 已通过 SQLFlint 检查
- [ ] SELECT 查询带上 WHERE 条件（无全表扫描）
- [ ] 分区表查询包含分区键（buyer_id / gmt_create）
- [ ] JOIN 表数 <= 3
- [ ] 无 SELECT * 
- [ ] 无 NOT IN / 前导 % LIKE
- [ ] 大批量查询使用游标分页（Keyset Pagination）
- [ ] 查询字段已覆盖对应索引
- [ ] 表数据量 > 100 万的查询已评估查询计划
```

### 2. 性能基线（Performance Baseline）

#### 2.1 核心查询基线定义

对订单中台的核心查询路径，建立性能基线数据库：

```yaml
# sql-performance-baselines.yaml
baselines:
  # ===== 订单核心查询 =====
  - id: "order.get-by-id"
    description: "主键查询订单详情"
    sql_pattern: "SELECT * FROM `order` WHERE order_id = ?"
    table: "order"
    index: "PRIMARY KEY (id)"
    p99_baseline_ms: 3
    p99_warning_ms: 10
    p99_critical_ms: 50
    explain_plan:
      - "TABLE SCAN: order (pk access)"  # OceanBase 预期计划

  - id: "order.list-by-buyer"
    description: "买家订单列表（最近 30 天）"
    sql_pattern: "SELECT ... FROM `order` WHERE buyer_id = ? AND gmt_create > ? ORDER BY gmt_create DESC LIMIT ?"
    table: "order"
    index: "idx_buyer_id (buyer_id, gmt_create)"
    p99_baseline_ms: 15
    p99_warning_ms: 50
    p99_critical_ms: 200
    explain_plan:
      - "TABLE SCAN: order (idx_buyer_id range scan, backward)"

  - id: "order.list-by-seller"
    description: "商家订单列表（最近 7 天）"
    sql_pattern: "SELECT ... FROM `order` WHERE seller_id = ? AND gmt_create > ? ORDER BY gmt_create DESC LIMIT ?"
    table: "order"
    index: "idx_seller_id (seller_id, gmt_create)"
    p99_baseline_ms: 20
    p99_warning_ms: 80
    p99_critical_ms: 300

  - id: "order.status-summary"
    description: "买家订单状态统计"
    sql_pattern: "SELECT order_status, COUNT(*) FROM `order` WHERE buyer_id = ? AND gmt_create > ? GROUP BY order_status"
    table: "order"
    index: "idx_buyer_id (buyer_id, gmt_create)"
    p99_baseline_ms: 10
    p99_warning_ms: 30
    p99_critical_ms: 100

  - id: "order.batch-status-update"
    description: "超时关单批量更新"
    sql_pattern: "UPDATE `order` SET order_status = ? WHERE order_status = ? AND gmt_create < ? LIMIT ?"
    table: "order"
    index: "idx_order_status (order_status, gmt_create)"
    p99_baseline_ms: 5
    p99_warning_ms: 20
    p99_critical_ms: 100

  - id: "order.partition-maintenance"
    description: "分区维护操作"
    sql_pattern: "ALTER TABLE `order` ADD SUBPARTITION ..."
    table: "order"
    p99_baseline_ms: 500  # 分区操作是 DDL，基线不同
    p99_warning_ms: 3000
    p99_critical_ms: 10000

  # ===== 异步任务查询 =====
  - id: "async-job.scan-pending"
    description: "XXL-Job 扫描待处理任务"
    sql_pattern: "SELECT * FROM job_async WHERE status = ? AND execute_at < ? ORDER BY execute_at LIMIT ?"
    table: "job_async"
    index: "idx_status_execute_at (status, execute_at)"
    p99_baseline_ms: 10
    p99_warning_ms: 50
    p99_critical_ms: 200

  # ===== ES 查询基线 =====
  - id: "es.order-search"
    description: "ES 订单搜索"
    query_type: "elasticsearch"
    p99_baseline_ms: 50
    p99_warning_ms: 200
    p99_critical_ms: 500
```

#### 2.2 基线对比流程

```
压测/生产 → 采集 P99/P50/扫描行数 → 对比基线 → 异常判定

                        ┌───────────────────┐
                        │ Performance Snapshot │
                        │  P50: 8ms          │
                        │  P99: 22ms         │
                        │  Rows Scanned: 500 │
                        └─────────┬─────────┘
                                  │
                        ┌─────────▼─────────┐
                        │ 基线对比引擎        │
                        │                    │
                        │ P99: 22ms vs 15ms  │
                        │ ▲ 46% ↑            │
                        └─────────┬─────────┘
                                  │
              ┌───────────────────┬┘
              │                   │
    P99 < 1.5×baseline    P99 >= 1.5×baseline
    或 P99 < warning              │
              │           ┌───────▼────────┐
              │           │ 扫描行数比较     │
              │           │ 上次 500 → 5000 │
              │           │ ▲ 10× ↑        │
              │           └───────┬────────┘
              │                   │
              │           ┌───────▼────────┐
              │           │ 执行计划是否变化？│
              │           │  EXPLAIN 快照    │
              │           │ 对比上次          │
              │           └───────┬────────┘
              │                   │
              │           ┌───────▼────────┐
              │           │ 告警: SQL 退化   │
              │           │ P1 级别          │
              │           │ + 关联上次变更    │
              │           └────────────────┘
              │
        基线更新          保留新基线
```

#### 2.3 基线数据采集

```java
/**
 * SQL 性能基线采集器
 *
 * 部署方式：在 order-core 中作为 Spring Boot Starter 嵌入
 * 采集时机：每条核心查询执行后异步记录
 * 数据存储：Prometheus Histogram + 本地日志文件（备用）
 */
@Component
public class SqlPerformanceCollector {

    private final MeterRegistry meterRegistry;

    // 按 queryId 分桶的 P50/P99/P999 Histogram
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    // 扫描行数记录（从 OceanBase 响应中提取）
    private final Map<String, AtomicLong> scannedRows = new ConcurrentHashMap<>();

    public void record(String queryId, long durationMs, long rowsScanned) {
        // Prometheus Histogram
        Timer timer = timers.computeIfAbsent(queryId,
                id -> Timer.builder("sql_performance_" + id)
                        .publishPercentiles(0.5, 0.99, 0.999)
                        .register(meterRegistry));
        timer.record(Duration.ofMillis(durationMs));

        // 扫描行数（用于判断是否因缺乏索引导致扫描过多）
        scannedRows.computeIfAbsent(queryId, id -> new AtomicLong())
                .set(rowsScanned);

        // 基线告警检查
        checkBaseline(queryId, durationMs);
    }

    private void checkBaseline(String queryId, long durationMs) {
        Baseline baseline = BASELINES.get(queryId);
        if (baseline == null) return;

        if (durationMs > baseline.getP99CriticalMs()) {
            // P1 告警：严重超基线
            alertService.send(new Alert(
                    AlertLevel.P1,
                    "SQL 性能严重退化: " + queryId,
                    "当前 " + durationMs + "ms, 基线 " + baseline.getP99BaselineMs() + "ms"
            ));
        } else if (durationMs > baseline.getP99WarningMs()) {
            // P2 告警：超警告线
            alertService.send(new Alert(
                    AlertLevel.P2,
                    "SQL 性能退化: " + queryId,
                    "当前 " + durationMs + "ms, 警告线 " + baseline.getP99WarningMs() + "ms"
            ));
        }
    }
}
```

### 3. 运行时慢 SQL 检测

#### 3.1 OceanBase 慢查询采集

```yaml
# 慢查询采集配置（基于 OceanBase 的 GV$OB_SQL_AUDIT 视图）
slow-query-collector:
  enabled: true
  collection-interval: 60s          # 每 60s 采集一次
  slow-query-threshold-ms: 200      # 超过 200ms 视为慢查询
  capture-plan: true                # 同时采集执行计划

  # 采集 SQL
  query: |
    SELECT
        svr_ip,
        request_id,
        sql_id,
        query_sql,
        elapsed_time / 1000 AS elapsed_ms,
        cpu_time / 1000 AS cpu_ms,
        queue_time / 1000 AS queue_ms,
        wait_time / 1000 AS wait_ms,
        execute_time / 1000 AS execute_ms,
        return_rows,
        affected_rows,
        row_cache_hit,
        bloom_filter_cache_hit,
        block_cache_hit,
        plan_type,
        is_hit_plan,
        trans_hash
    FROM GV$OB_SQL_AUDIT
    WHERE elapsed_time > 200000     -- 200ms 以上
      AND request_time > NOW() - INTERVAL 5 MINUTE
    ORDER BY elapsed_time DESC
    LIMIT 100;

  # 锁等待采集
  lock-wait-query: |
    SELECT * FROM GV$OB_LOCKS
    WHERE blocked > 0;

  # 执行计划劣化检测
  plan-regression-query: |
    SELECT
        sql_id,
        plan_hash,
        avg_elapsed_time,
        executions
    FROM GV$OB_PLAN_CACHE_STAT
    WHERE avg_elapsed_time > 100000  -- 100ms+
    ORDER BY avg_elapsed_time DESC;
```

#### 3.2 慢 SQL 告警规则

```yaml
# Prometheus Alert 规则
groups:
  - name: slow-sql-alerts
    rules:
      # P1：大量慢查询
      - alert: HighSlowQueryRate
        expr: rate(slow_query_total[5m]) > 10
        for: 3m
        labels:
          severity: P1
          team: dba
        annotations:
          summary: "慢查询率过高（当前 {{ $value }}/s）"
          description: "5min 慢查询速率超过 10/s，请检查 DB 负载和慢查询列表"

      # P1：单条 SQL 性能严重劣化
      - alert: CriticalSqlDegradation
        expr: sql_performance_ratio{quantile="0.99"} > 3
        for: 5m
        labels:
          severity: P1
          team: dba
        annotations:
          summary: "SQL {{ $labels.query_id }} P99 已达基线的 {{ $value }} 倍"
          description: "请立即检查执行计划和索引状态"

      # P2：全表扫描 SQL
      - alert: FullTableScanDetected
        expr: rate(full_table_scan_total[5m]) > 0
        for: 1m
        labels:
          severity: P2
          team: dev
        annotations:
          summary: "检测到全表扫描 SQL（{{ $labels.sql_id }}）"
          description: "可能原因：缺少索引或 WHERE 条件不准确"

      # P2：慢查询 P99 超警告线
      - alert: SlowSqlWarning
        expr: sql_performance_ratio{quantile="0.99"} > 1.5
        for: 10m
        labels:
          severity: P2
          team: dev
        annotations:
          summary: "SQL {{ $labels.query_id }} P99 超出警告线"
          description: "当前 P99 {{ $value }}× 基线，请关注趋势"

      # P2：扫描行数异常增长
      - alert: RowsScannedAnomaly
        expr: sql_scanned_rows_ratio > 5
        for: 5m
        labels:
          severity: P2
          team: dev
        annotations:
          summary: "SQL {{ $labels.query_id }} 扫描行数突增 {{ $value }}×"
          description: "可能的索引失效或数据分布变化"
```

#### 3.3 OceanBase SQL Plan Bind（紧急处理）

```java
/**
 * 执行计划绑定 —— 紧急处理慢 SQL
 *
 * OceanBase 支持通过 OUTLINE 绑定执行计划，
 * 在紧急情况下（如 SQL 执行计划突然劣化）使用。
 *
 * 场景：某条 SQL 优化器选择了错误的 JOIN 顺序或索引
 *      → 立即绑定额外的执行计划 → 恢复性能 → 后续分析根因
 */
@Component
public class OceanBasePlanBinder {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 绑定执行计划
     *
     * @param sqlId        OceanBase 的 SQL_ID
     * @param outlineName  OUTLINE 名称
     * @param hintSql      带 HINT 的 SQL（指定执行计划）
     */
    public void bindPlan(String sqlId, String outlineName, String hintSql) {
        String sql = String.format(
            "CREATE OUTLINE %s ON %s USING HINT %s",
            outlineName, hintSql, "/*+ INDEX(order idx_buyer_id) */"
        );
        jdbcTemplate.execute(sql);
        log.warn("Plan bound: sqlId={}, outline={}", sqlId, outlineName);
    }

    /**
     * 删除绑定
     */
    public void unbindPlan(String outlineName) {
        jdbcTemplate.execute("DROP OUTLINE " + outlineName);
    }

    /**
     * 查看当前绑定的计划
     */
    public List<PlanBinding> listBindings() {
        return jdbcTemplate.query(
            "SELECT * FROM DBA_OUTLINES WHERE owner = 'OMPLATFORM'",
            (rs, row) -> PlanBinding.builder()
                .name(rs.getString("name"))
                .sqlId(rs.getString("sql_id"))
                .created(rs.getTimestamp("created").toLocalDateTime())
                .enabled(rs.getString("enabled").equals("YES"))
                .build()
        );
    }
}
```

### 4. 索引治理流程

#### 4.1 索引规范

```yaml
# index-governance-rules.yaml
index-rules:
  # ===== 建索引规范 =====
  naming:
    unique_idx: "uk_{table}_{column}"
    normal_idx: "idx_{table}_{column}"
    fulltext_idx: "ft_{table}_{column}"

  # ===== 约束 =====
  constraints:
    max_indexes_per_table: 8           # 单表最多 8 个索引
    max_columns_per_index: 4           # 复合索引最多 4 列
    max_total_index_size_mb: 512       # 单表索引总占用不超过 512MB

  # ===== 冗余索引检测 =====
  redundancy_check:
    enabled: true
    schedule: "0 2 * * 0"              # 每周日凌晨 2 点
    query: |
      SELECT
          a.TABLE_NAME,
          a.INDEX_NAME AS redundant_index,
          b.INDEX_NAME AS covering_index,
          CONCAT(a.COLUMN_NAME, ',', a.SEQ_IN_INDEX) AS cols
      FROM INFORMATION_SCHEMA.STATISTICS a
      JOIN INFORMATION_SCHEMA.STATISTICS b
        ON a.TABLE_NAME = b.TABLE_NAME
        AND a.INDEX_NAME != b.INDEX_NAME
        AND a.COLUMN_NAME = b.COLUMN_NAME
        AND a.SEQ_IN_INDEX = b.SEQ_IN_INDEX
      WHERE a.TABLE_SCHEMA = 'omplatform'
        AND a.INDEX_NAME NOT LIKE 'PRIMARY'
      GROUP BY a.TABLE_NAME, a.INDEX_NAME
      HAVING COUNT(*) = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS c
                          WHERE c.TABLE_NAME = a.TABLE_NAME
                            AND c.INDEX_NAME = a.INDEX_NAME);

  # ===== 索引使用率检测 =====
  unused_index_detection:
    enabled: true
    schedule: "30 2 * * 0"
    min_days: 30                       # 30 天未被使用的索引视为冗余
    query: |
      SELECT
          OBJECT_NAME,
          INDEX_NAME,
          FLUSHED_KEY_READS,
          FLUSHED_KEY_WRITES
      FROM GV$OB_INDEX_USAGE
      WHERE OBJECT_SCHEMA = 'omplatform'
        AND LAST_USED < NOW() - INTERVAL 30 DAY;
```

#### 4.2 索引变更流程

```
索引变更标准流程
═══════════════════════════════════════

  开发者识别慢查询 / DBA 巡检发现索引问题
          │
          ▼
  ① 方案设计
     ├── 确定索引类型（UNIQUE / NORMAL / COVERING）
     ├── 评估锁表时间（OceanBase ADD INDEX 不锁表，ONLINE）
     ├── 预估存储空间（COMPRESSION 后大小）
     └── 回滚方案（DROP INDEX IF EXISTS）
          │
          ▼
  ② 代码提交
     ├── Liquibase changelog：<version>/add-idx-xxx.sql
     ├── SQL Review：通过 CI 静态分析
     └── MR 附上 EXPLAIN 和性能数据
          │
          ▼
  ③ 灰度执行
     ├── DEV/FAT：自动执行验证
     ├── UAT：手动执行验证
     ├── PRE：预发环境索引创建验证 + 压测
     └── PROD：低峰期执行（建议凌晨 2-5 点）
          │
          ▼
  ④ 验证上线
     ├── 索引创建成功 → 观察 30min DB 性能指标
     ├── 确认 SQL 查询使用新索引（EXPLAIN）
     └── 确有性能提升 → 关闭旧索引（标记 obsolete）
          │
          ▼
  ⑤ 清理（1 周后）
     ├── 确认新索引稳定 → DROP 冗余索引
     └── 更新索引治理报表
```

#### 4.3 Liquibase SQL 示例

```xml
<!-- liquibase/v2.1.0/changelog.xml -->
<databaseChangeLog>
    <!-- 新增复合索引：买家查询优化 -->
    <changeSet id="add-idx-buyer-status" author="dba">
        <sql>
            ALTER TABLE `order` ADD INDEX `idx_buyer_status`
                (`buyer_id`, `order_status`, `gmt_create`)
            COMMENT '买家订单列表及状态筛选覆盖索引',
            ALGORITHM=INSTANT;
        </sql>
        <rollback>
            ALTER TABLE `order` DROP INDEX `idx_buyer_status`;
        </rollback>
    </changeSet>

    <!-- 删除冗余索引（验证已无查询使用） -->
    <changeSet id="drop-idx-order-status" author="dba">
        <preConditions onFail="MARK_RAN">
            <!-- 确认 idx_buyer_status 已存在且无查询使用 idx_order_status -->
            <sqlCheck expectedResult="0">
                SELECT COUNT(*) FROM GV$OB_SQL_AUDIT
                WHERE sql_id IN (
                    SELECT sql_id FROM GV$OB_PLAN_CACHE_STAT
                    WHERE plan_hash = ?
                );
            </sqlCheck>
        </preConditions>
        <sql>
            ALTER TABLE `order` DROP INDEX `idx_order_status`;
        </sql>
    </changeSet>
</databaseChangeLog>
```

### 5. 典型慢查询优化模式

针对订单中台的典型业务场景，定义统一的优化模式：

#### 模式 1：买家订单列表（最近 N 天）

```sql
-- ❌ 反模式：全表扫描或未走分区键
SELECT * FROM `order`
WHERE order_status = 'PAID'
ORDER BY gmt_create DESC
LIMIT 20;

-- ✅ 优化后：带分区键 + 覆盖索引
SELECT order_id, order_no, total_amount, order_status, gmt_create
FROM `order`
WHERE buyer_id = ?                          -- 分区键，分区裁剪
  AND order_status = 'PAID'
  AND gmt_create > DATE_SUB(NOW(), INTERVAL 30 DAY)  -- 避免扫描全部
ORDER BY gmt_create DESC
LIMIT 20;                                   -- Keyset Pagination 替代 offset

-- 索引：idx_buyer_status (buyer_id, order_status, gmt_create DESC)
```

#### 模式 2：游标分页（替代深分页）

```sql
-- ❌ 反模式：传统 offset 深分页（offset 100000 时性能灾难）
SELECT * FROM `order`
WHERE buyer_id = ?
ORDER BY id
LIMIT 20 OFFSET 100000;

-- ✅ 优化后：Keyset Pagination（游标分页）
-- 第一页：
SELECT order_id, order_no, total_amount, status, gmt_create
FROM `order`
WHERE buyer_id = ?
  AND gmt_create > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY id DESC
LIMIT 20;

-- 后续页（客户端传入 lastId = 上一页最后一条的 id）：
SELECT order_id, order_no, total_amount, status, gmt_create
FROM `order`
WHERE buyer_id = ?
  AND id < ?                              -- 游标条件，走主键索引
  AND gmt_create > DATE_SUB(NOW(), INTERVAL 30 DAY)
ORDER BY id DESC
LIMIT 20;
```

#### 模式 3：批量状态更新（超时关单）

```sql
-- ❌ 反模式：逐条更新（N 条 SQL = N 次网络往返）
for (Order order : expiredOrders) {
    orderRepository.updateStatus(order.getId(), "CLOSED");
}

-- ✅ 优化后：批量更新 + 分批 + LIMIT 控制
UPDATE `order`
SET order_status = 'CLOSED',
    gmt_modified = NOW()
WHERE order_status = 'PENDING_PAY'
  AND gmt_create < DATE_SUB(NOW(), INTERVAL 30 MINUTE)
  AND gmt_create > DATE_SUB(NOW(), INTERVAL 7 DAY)   -- 限制扫描范围
ORDER BY gmt_create ASC
LIMIT 1000;                                            -- 每次最多 1000 条

-- 配合 XXL-Job 循环执行，直到影响行数为 0
```

#### 模式 4：状态统计（Group By）

```sql
-- ❌ 反模式：全表分组统计（百万级数据时慢查询）
SELECT order_status, COUNT(*) FROM `order`
WHERE gmt_create > DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY order_status;

-- ✅ 优化后：命中复合索引
SELECT order_status, COUNT(*)
FROM `order`
WHERE buyer_id = ?                            -- 分区键，一次查询一个买家
  AND gmt_create > DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY order_status;

-- 索引：idx_buyer_status (buyer_id, order_status, gmt_create)

-- ✅ 全量状态统计（运营管理后台）：
-- 方案 A：使用物化视图 / 结果缓存
SELECT /*+ RESULT_CACHE */
    order_status,
    COUNT(*) AS cnt
FROM `order`
WHERE gmt_create >= DATE_FORMAT(NOW(), '%Y-%m-01')    -- 本月数据
GROUP BY order_status;

-- 方案 B：使用专用的"订单统计汇总表"（XXL-Job 每 5min 刷新）
```

#### 模式 5：关联查询优化

```sql
-- ❌ 反模式：多表 JOIN 且无索引
SELECT o.order_id, o.total_amount, oi.item_name, oi.quantity
FROM `order` o
JOIN `order_item` oi ON o.order_id = oi.order_id
WHERE o.buyer_id = ?
ORDER BY o.gmt_create DESC
LIMIT 20;

-- ✅ 优化后：用 ES 或 CQRS 替代 JOIN
-- 步骤 1：先从 order 表查订单列表（带索引）
SELECT order_id, total_amount
FROM `order`
WHERE buyer_id = ?
ORDER BY gmt_create DESC
LIMIT 20;

-- 步骤 2：用 IN 查 items（走 order_item.uk_order_id 索引）
SELECT order_id, item_name, quantity
FROM `order_item`
WHERE order_id IN (?, ?, ?);  -- 第 1 步结果的 order_id

-- 注意：order_item 表建索引：KEY idx_order_id (order_id)
```

### 6. 慢 SQL 看板 + 治理报表

```
Grafana Dashboard: "SQL 治理中心"
═══════════════════════════════════════════════════

Row 1: 全局概览
┌─────────────────────┬──────────────────┬──────────────────┐
│ DB CPU 使用率        │ 慢查询速率         │ 平均查询延迟       │
│ 当前: 45%            │ 当前: 3.2/s       │ 当前: 12ms         │
│ 昨日同期: 42%        │ 昨日: 2.8/s       │ 昨日: 10ms         │
└─────────────────────┴──────────────────┴──────────────────┘

Row 2: Top-N 慢查询
┌──────┬────────────────────────────┬───────────┬──────────┬────────┐
│ Rank │ SQL ID / Sample            │ 调用次数    │ Avg(ms)  │ P99    │
├──────┼────────────────────────────┼───────────┼──────────┼────────┤
│ #1   │ 3a8f2b... (order list)     │ 12,340    │ 350      │ 890    │
│ #2   │ 7d1e9c... (status summary) │ 8,921     │ 210      │ 650    │
│ #3   │ f4b2a1... (export scan)    │ 234       │ 180      │ 420    │
└──────┴────────────────────────────┴───────────┴──────────┴────────┘

Row 3: SQL 基线与退化检测
┌────────────────┬───────────┬──────────┬─────────┬──────────┐
│ Query ID       │ 基线 P99   │ 当前 P99  │ 比率    │ 趋势      │
├────────────────┼───────────┼──────────┼─────────┼──────────┤
│ order.by-id    │ 3ms       │ 4ms      │ 1.3×    │ → 稳定    │
│ order.by-buyer │ 15ms      │ 58ms     │ 3.9×    │ ↑ 告警    │
│ order.by-seller│ 20ms      │ 22ms     │ 1.1×    │ → 稳定    │
│ async.scan     │ 10ms      │ 45ms     │ 4.5×    │ ↑ 告警    │
└────────────────┴───────────┴──────────┴─────────┴──────────┘

Row 4: 索引治理
┌──────────────┬─────────┬──────────┬──────────┬──────────┐
│ 表名          │ 索引数   │ 冗余索引  │ 未使用索引 │ 建议新增  │
├──────────────┼─────────┼──────────┼──────────┼──────────┤
│ order        │ 6       │ 1        │ 0        │ 1        │
│ order_item   │ 3       │ 0        │ 0        │ 0        │
│ job_async    │ 2       │ 0        │ 0        │ 1        │
└──────────────┴─────────┴──────────┴──────────┴──────────┘

Row 5: 扫描行数分布
┌─────────────────────────────────────────────────────────────┐
│ 扫描行数热度图（查询 ID × 时间段）                             │
│                                                              │
│        06:00  08:00  10:00  12:00  14:00  16:00  18:00      │
│ by_buyer  500    480    520    510    530    490    510      │
│ by_seller 1200   1150   1300   1250   1280   1200   1220     │
│ export    50000  52000  48000  51000  49000  53000  50000    │  ← 异常高
│ scan      8000   7500   7800   8100   7900   7700   8000     │
└─────────────────────────────────────────────────────────────┘
```

### 7. Prometheus 指标

```yaml
# Prometheus metrics — sql_performance
metrics:
  - name: sql_performance_duration_ms
    type: histogram
    labels: [query_id, table, index_used]
    help: "SQL 执行耗时分布"
    buckets: [1, 3, 5, 10, 20, 50, 100, 200, 500, 1000]

  - name: sql_scanned_rows
    type: gauge
    labels: [query_id, table]
    help: "SQL 扫描行数（单次执行）"

  - name: sql_performance_ratio
    type: gauge
    labels: [query_id, quantile]
    help: "当前 P50/P99 与基线的比值"

  - name: slow_query_total
    type: counter
    labels: [sql_id, table]
    help: "慢查询累计次数"

  - name: full_table_scan_total
    type: counter
    labels: [sql_id, table]
    help: "全表扫描累计次数"

  - name: index_usage_ratio
    type: gauge
    labels: [table, index_name]
    help: "索引使用率（查询次数/存储开销）"

  - name: sql_plan_changed_total
    type: counter
    labels: [sql_id]
    help: "SQL 执行计划变化次数（劣化检测）"
```

---

## 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| **P1 SQL 静态分析** | SQLFluff 规则定义 + MyBatis Analyzer Maven 插件 + CI 集成 | 2.5d | CI SQL Review Gate 生效 |
| **P2 性能基线** | 基线定义 + SqlPerformanceCollector + Prometheus Histogram + 告警 | 2d | 核心 10 条查询基线 + 退化告警 |
| **P3 慢查询采集** | OceanBase GV$OB_SQL_AUDIT 采集器 + Grafana 看板 | 1.5d | 慢查询 TOP-N + 扫描行数热力图 |
| **P4 索引治理** | 冗余检测 + 未使用索引检测 + Liquibase 规范 + 看板 | 1.5d | 索引周报 + 变更流程标准化 |
| **P5 紧急处理工具** | OceanBase Plan Binding 工具 + 文档 | 1d | 执行计划绑定能力 |
| **P6 治理运营** | 典型查询优化模式文档 + 开发规范 + 周报自动化 | 1d | 开发者手册 + 治理报表 |

**总计：9.5 人天**

---

## 上线检查清单

### CI SQL Review
- [ ] SQLFluff / MyBatis Analyzer 插件在 MR 流水线中生效
- [ ] 阻断规则（SELECT * / 全表扫描 / 缺分区键）验证通过
- [ ] 警告规则（前导 % / NOT IN / 深分页）在 MR 中可见
- [ ] 存量 Mapper XML 已扫描一轮，修复所有 BLOCKER 问题

### 性能基线
- [ ] 核心 10 条查询的基线已录入 Prometheus
- [ ] P1 退化告警（P99 > 3× 基线）在测试环境验证通过
- [ ] P2 警告告警（P99 > 1.5× 基线）在测试环境验证通过
- [ ] 基线采集器不影响业务请求（异步非阻塞）

### 慢查询检测
- [ ] OceanBase 慢查询采集器部署并验证数据写入 Prometheus
- [ ] Grafana "SQL 治理中心"看板已完成
- [ ] TOP-N 慢查询列表可正确关联到服务 + API + 代码提交

### 索引治理
- [ ] 所有表索引数 <= 8，无冗余索引
- [ ] 索引变更流程已在开发规范中更新
- [ ] Liquibase 中所有索引变更都包含回滚 SQL

### 开发规范
- [ ] SQL Review 核对清单已加入 MR 模板
- [ ] 典型优化模式文档已发布到开发者 Wiki
- [ ] 游标分页、批量更新、JOIN 替代方案已推广

---

## 与其他 ADR 的关系

| ADR | 关系 |
|-----|------|
| **ADR-011**（在线 DDL） | 索引治理的 DDL 变更遵循 ADR-011 的 OceanBase 规范 |
| **ADR-015**（容量规划） | 性能基线数据作为容量规划的输入（服务 QPS 基准） |
| **ADR-019**（异步任务） | 异步任务的 SQL 查询（`job_async` 扫描）加入性能基线 |
| **ADR-021**（延迟任务） | 延迟任务的 DB 轮询 SQL 走本 ADR 的批量更新优化模式 |
| **cicd-pipeline.md** | SQL Review 阶段作为 PRE 部署门禁的补充 |
| **canary-release.md** | 灰度发布期间监控 SQL 基线退化，作为回滚条件之一 |
| **ADR-018**（监控大盘） | 慢 SQL 看板作为监控大盘的补充面板 |

---

## 附录：OceanBase SQL 调优速查表

```sql
-- 0. 查看当前慢查询
SELECT
    sql_id,
    LEFT(query_sql, 200) AS sql_sample,
    elapsed_time / 1000 AS elapsed_ms,
    execute_time / 1000 AS execute_ms,
    return_rows,
    affected_rows,
    plan_type,
    is_hit_plan
FROM GV$OB_SQL_AUDIT
WHERE request_time > NOW() - INTERVAL 5 MINUTE
ORDER BY elapsed_time DESC
LIMIT 10;

-- 1. 查看 SQL 执行计划
EXPLAIN EXTENDED
SELECT order_id, total_amount
FROM `order`
WHERE buyer_id = 12345
  AND gmt_create > '2026-01-01'
ORDER BY gmt_create DESC
LIMIT 20;

-- 2. 查看索引使用情况
SELECT * FROM GV$OB_INDEX_USAGE
WHERE OBJECT_SCHEMA = 'omplatform'
ORDER BY LAST_USED DESC;

-- 3. 绑定执行计划
CREATE OUTLINE my_outline ON
    SELECT /*+ INDEX(`order` idx_buyer_id) */
        order_id, total_amount
    FROM `order`
    WHERE buyer_id = 12345
USING HINT /*+ INDEX(`order` idx_buyer_status) */;

-- 4. 查询数据量分布（判断是否需要调整分区策略）
SELECT
    TABLE_NAME,
    PARTITION_NAME,
    NUM_ROWS,
    AVG_ROW_LEN,
    DATA_SIZE
FROM GV$OB_PARTITIONS
WHERE TABLE_SCHEMA = 'omplatform'
  AND TABLE_NAME = 'order'
ORDER BY PARTITION_NAME;

-- 5. 弱一致性读（非关键查询可使用）
SELECT /*+ READ_CONSISTENCY(WEAK) */ *
FROM `order`
WHERE buyer_id = ?
ORDER BY gmt_create DESC
LIMIT 20;
```
