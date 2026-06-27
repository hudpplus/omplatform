# ADR-019：订单导入导出 + 异步任务中心

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台的查询和导出能力全部走同步接口。财务对账、运营数据分析、批量订单导入等场景直接通过业务接口查询数据库或 ES，缺乏专门的异步任务处理机制。

```
当前导出流程（改造前）：

财务/运营 → 后台 → order-query-service → ES 查询
                      │
                      ▼
              1. 同步执行查询
              2. 返回大量数据（可能上万条）
              3. HTTP 连接保持等待
              4. 超时（常见 30s → 502）
                      │
                      ▼
              前端：「导出失败，请重试」
```

随着日订单量增长（100 万+/天），同步导出的问题日益突出：

| 场景 | 数据量 | 当前表现 |
|------|--------|---------|
| 财务月度对账 | 30-50 万条 | HTTP 超时，分多次手工导出再合并 |
| 运营周报 | 10-20 万条 | 浏览器卡死，经常要求重试 |
| 客服订单查询 | 5000-1 万条 | 勉强可用，但后台响应慢 |
| 批量导入改地址 | 2000-5000 条 | 逐条调用接口，耗时数小时 |
| BI 数据同步 | 全量（千万级） | 离线脚本直连数据库，无管控 |

### 存在的问题

**问题 1：同步导出上限低**  
HTTP 接口的默认超时（30s）和网关超时（60s）限制了导出规模。超过 1 万条订单的导出几乎必然超时，导致财务团队被迫分多次导出再 Excel 合并——低效且易出错。

**问题 2：大查询影响在线服务**  
ES 的深度分页（from + size 超过 1 万）会产生巨大的协调节点开销。一次运营导出扫描 10 万条数据，可能导致 ES CPU 飙升、GC 抖动，影响在线查询的 P99。

**问题 3：缺少任务状态跟踪**  
提交一个导出请求后，当前没有任何进度反馈。用户不知道「是在执行」「还剩多少」「预计多久完成」，体验类似黑盒。

**问题 4：导出权限无管控**  
当前任何有后台权限的人员都可以导出完整订单数据，无法按角色限制导出字段（如财务可看金额、客服不可看；运营不可看买家手机号）。存在数据泄露风险。

**问题 5：批量导入无事务保障**  
批量改地址、批量退款等操作当前逐条调用 API，无事务边界。如果 5000 条中有 1 条失败，已成功执行的 4999 条无法回滚，且无失败重试机制。

**问题 6：导出格式单一**  
当前只支持 CSV 导出，财务要求的 Excel 多 Sheet（按月份分 Sheet）、B2B 合同导出 PDF 等需求无法满足，临时靠 Python 脚本后处理。

### 当前数据

| 指标 | 当前值 | 说明 |
|------|--------|------|
| 单次最大导出量 | ~1 万条 | 超过后网关超时 |
| 月度对账导出耗时 | 2-3 人天 | 分多次手工导出 + 合并 |
| ES 深度分页最大条数 | 1 万条 | scroll 可突破，但无管控 |
| 批量导入成功率 | 无统计 | 逐条调用，部分失败无感知 |
| 导出失败率 | ~15% | 超时 + 浏览器内存不足 |

---

## 决策

建立 **异步任务中心（Async Job Center）**，将导入导出等耗时操作从同步 HTTP 模式改造为异步任务模式：

1. **基于 RocketMQ 的任务队列**：每个导出/导入请求封装为 Job 消息，提交后立即返回 `jobId`，后台异步执行
2. **三级导出策略**：按数据量自动路由到不同处理管道（同步 < 1 万 → 异步 CSV/Excel 1-10 万 → 异步分片压缩 > 10 万）
3. **ES Scroll + Point-in-Time**：深度导出使用 PIT 快照一致性 + Scroll 流式取数，避免深翻页
4. **角色级权限管控**：按角色限制可导出字段和最大条数，所有操作写入审计日志
5. **前端轮询进度**：前端通过 `jobId` 轮询进度条、预估剩余时间、下载链接

---

## 详细设计

### 1. 整体架构

```
异步任务中心架构（改造后）：

                    用户请求（导出/导入）
                           │
                           ▼
                   ┌───────────────┐
                   │  Async Job    │
                   │  Controller   │
                   │  ① 校验权限    │
                   │  ② 创建 Job   │
                   │  ③ 返回 jobId │
                   └───────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
       ┌──────────┐ ┌──────────┐ ┌──────────┐
       │  RocketMQ │ │  Redis   │ │  MySQL   │
       │  任务队列  │ │ 进度缓存 │ │ Job 持久化│
       └────┬─────┘ └──────────┘ └──────────┘
            │
     ┌──────┴──────┐
     │  Job Worker │ ← 可水平扩展的 Worker 集群
     │  (Consumer) │
     └──────┬──────┘
            │
     ┌──────┴──────────────────────────────┐
     │           执行引擎                    │
     │                                     │
     │  ┌──────────┐  ┌─────────────────┐   │
     │  │ ES Scroll │  │ 批量导入执行器    │   │
     │  │ + PIT     │  │ (事务 + 回滚)    │   │
     │  └──────────┘  └─────────────────┘   │
     │  ┌──────────┐  ┌─────────────────┐   │
     │  │ CSV/Excel│  │ OSS 上传/下载   │   │
     │  │ 生成器    │  │ (预签名 URL)    │   │
     │  └──────────┘  └─────────────────┘   │
     └──────────────────────────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  OSS 存储桶   │
                    │  /export/*    │
                    │  /import/*    │
                    └──────────────┘
```

### 2. 任务生命周期

```
Job 状态机：

          ┌──────────┐
          │  PENDING  │ ← 创建成功后初始状态
          └────┬─────┘
               │ Consumer 拉取到消息
               ▼
          ┌──────────┐
          │  RUNNING  │ ← 开始执行
          └────┬─────┘
               │
     ┌─────────┼─────────┐
     │         │         │
     ▼         ▼         ▼
 ┌───────┐ ┌───────┐ ┌───────┐
 │SUCCESS│ │FAILED │ │CANCEL │
 └───────┘ └───────┘ └───────┘
     │         │
     │         ▼
     │    ┌──────────┐
     │    │  RETRY   │ ← 可重试的失败（限流/超时）
     │    └──────────┘
     │         │ (重试 3 次后)
     │         ▼
     │    ┌──────────┐
     │    │ FAILED   │
     │    └──────────┘
     │
     ▼
 ┌──────────┐
 │ EXPIRED  │ ← 文件保留 7 天后自动清理
 └──────────┘
```

```java
/**
 * Job 状态枚举
 */
public enum JobStatus {
    PENDING,    // 等待执行
    RUNNING,    // 执行中
    SUCCESS,    // 执行成功
    FAILED,     // 执行失败（不可重试）
    RETRY,      // 执行失败（可重试）
    CANCELLED,  // 用户取消
    EXPIRED     // 已过期（文件已清理）
}

/**
 * Job 数据结构
 */
@Data
public class AsyncJob {
    private String jobId;            // 全局唯一 ID（UUID）
    private JobType type;            // EXPORT / IMPORT
    private JobStatus status;        // 当前状态
    private String creator;          // 创建人
    private String role;             // 创建人角色（用于权限判断）
    private JobParams params;        // 任务参数（JSON）
    private JobResult result;        // 结果信息
    private Integer progress;        // 进度百分比 0-100
    private String errorMessage;     // 失败原因
    private LocalDateTime createdAt; // 创建时间
    private LocalDateTime updatedAt; // 更新时间
    private LocalDateTime expiredAt; // 文件过期时间
}

/**
 * Job 参数（示例：导出参数）
 */
@Data
public class ExportJobParams {
    private ExportFormat format;        // CSV / EXCEL / PDF
    private String queryCriteria;       // 查询条件（JSON）
    private List<String> fields;        // 导出字段列表
    private String sortBy;              // 排序字段
    private Boolean shardEnable;        // 是否启用分片（> 10 万条自动启用）
    private String fileName;            // 自定义文件名
}

/**
 * Job 结果
 */
@Data
public class JobResult {
    private String downloadUrl;         // OSS 预签名 URL
    private Long fileSize;              // 文件大小（bytes）
    private Integer totalRecords;       // 总记录数
    private Integer shardCount;         // 分片数量（分片导出时 > 1）
    private List<String> shardUrls;     // 分片下载 URL 列表
    private Long executionDurationMs;   // 实际执行耗时
}
```

### 3. 三级导出策略

```
根据数据量自动路由：

┌──────────────────────────────────────────────────────────────┐
│                    导出请求（含查询条件）                       │
└────────────────────────┬─────────────────────────────────────┘
                         │
                   ┌─────┴─────┐
                   │ 前置校验    │
                   │ ① 权限检查  │
                   │ ② 条数预估  │
                   └─────┬─────┘
                         │
              ┌──────────┴──────────┐
              │                     │
        预估 < 1 万            预估 >= 1 万
              │                     │
              ▼                     ▼
     ┌────────────────┐   ┌────────────────────┐
     │  Tier 1：同步   │   │  Tier 2/3：异步     │
     │                │   │                    │
     │ • 同步查询 ES   │   │ • 创建 AsyncJob    │
     │ • 直接返回 JSON │   │ • 返回 jobId       │
     │ • 浏览器下载     │   │ • 后台异步执行      │
     │ • 限 30s 超时   │   │ • 前端轮询进度      │
     └────────────────┘   └─────────┬──────────┘
                                     │
                          ┌──────────┴──────────┐
                          │                     │
                   1 万 <= 条 < 10 万      条 >= 10 万
                          │                     │
                          ▼                     ▼
               ┌──────────────────┐  ┌────────────────────┐
               │  Tier 2：单文件   │  │  Tier 3：分片导出   │
               │                  │  │                    │
               │ • ES Scroll 取数  │  │ • 按日期/ID 范围    │
               │ • 单 CSV/Excel   │  │   拆分为 N 个分片    │
               │ • 上传 OSS        │  │ • 每个分片独立执行    │
               │ • 通知下载链接     │  │ • 各分片并行生成     │
               │ • 典型耗时 10-60s │  │ • OSS 打包为 zip    │
               └──────────────────┘  │ • 典型耗时 1-10min  │
                                     └────────────────────┘
```

#### 3.1 Tier 1：小量同步导出（< 1 万条）

```java
/**
 * Tier 1 同步导出 —— 数据量小，直接返回
 * 使用 ES Search After 避免深翻页
 */
@RestController
@RequestMapping("/api/v1/export")
public class SyncExportController {

    @GetMapping("/sync")
    public ResponseEntity<Resource> syncExport(@Valid ExportQuery query) {
        // 1. 校验导出条数上限
        long estimatedCount = orderQueryService.estimateCount(query.toSearchQuery());
        if (estimatedCount > 10_000) {
            return ResponseEntity.badRequest().body(/* 提示使用异步导出 */);
        }

        // 2. 执行查询（ES Search After，限制最大 1 万条）
        List<OrderDoc> orders = orderQueryService.searchWithScroll(
            query.toSearchQuery(), 10_000);

        // 3. 权限过滤字段（按角色移除不可见字段）
        List<Map<String, Object>> filtered = FieldPermissionFilter
            .filter(orders, query.getFields(), currentUser.getRole());

        // 4. 生成 CSV
        String csv = CsvGenerator.generate(filtered);

        // 5. 返回文件流
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=orders.csv")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));
    }
}
```

#### 3.2 Tier 2：异步单文件导出（1 万 ~ 10 万条）

```java
/**
 * Tier 2 异步导出 —— 使用 ES Scroll + PIT，单文件
 */
@Component
public class AsyncExportWorker implements MessageListener {

    /**
     * 消费 RocketMQ 导出任务
     */
    @Override
    public Action consume(Message message, ConsumeContext context) {
        ExportJob job = JsonUtils.parse(message.getBody(), ExportJob.class);
        jobService.updateStatus(job.getJobId(), JobStatus.RUNNING);

        try {
            // 1. 创建 Point-in-Time（快照一致性，5 分钟有效期）
            String pitId = esClient.createPit("orders-search", "5m");

            // 2. 使用 Scroll + PIT 流式取数
            List<OrderDoc> allOrders = new ArrayList<>();
            String searchAfter = null;
            int batchSize = 2000;
            int fetched = 0;

            do {
                SearchResponse<OrderDoc> response = esClient.scrollWithPit(
                    pitId, searchAfter, batchSize, job.getParams().getQueryCriteria());

                List<OrderDoc> batch = response.getDocs();
                allOrders.addAll(batch);
                fetched += batch.size();
                searchAfter = response.getSearchAfter();

                // 更新进度（基于预估总数）
                int progress = (int) ((double) fetched / estimatedTotal * 100);
                jobService.updateProgress(job.getJobId(), progress);

            } while (searchAfter != null && !Thread.currentThread().isInterrupted());

            // 3. 释放 PIT
            esClient.deletePit(pitId);

            // 4. 权限过滤字段
            List<Map<String, Object>> filtered = FieldPermissionFilter
                .filter(allOrders, job.getParams().getFields(), job.getRole());

            // 5. 生成 Excel（支持多 Sheet 自动拆分）
            File excelFile = ExcelGenerator.generate(filtered, job.getParams().getFormat());

            // 6. 上传 OSS
            String ossKey = "export/" + job.getJobId() + "/" + job.getParams().getFileName();
            String downloadUrl = ossClient.upload(ossKey, excelFile, 7); // 7 天有效期

            // 7. 更新 Job 结果
            jobService.complete(job.getJobId(), JobResult.builder()
                .downloadUrl(downloadUrl)
                .totalRecords(allOrders.size())
                .executionDurationMs(duration)
                .build());

            return Action.CommitMessage;

        } catch (Exception e) {
            jobService.fail(job.getJobId(), e.getMessage());
            return Action.ReconsumeLater;  // 可重试的异常
        }
    }
}
```

#### 3.3 Tier 3：分片异步导出（> 10 万条）

```java
/**
 * Tier 3 分片导出 —— 将大查询拆为多个小分片并行执行
 */
@Component
public class ShardedExportStrategy {

    private static final int SHARD_SIZE = 50_000; // 每片 5 万条

    /**
     * 计算分片范围并提交子任务
     * 
     * 分片策略：
     * - 按时间范围分片（如果查询条件包含时间范围）
     * - 按 ID 范围分片（按 order_id 的 mod 值分区）
     * - 按 buyer_id hash 分片
     */
    public List<ShardTask> splitIntoShards(ExportJob job) {
        long estimatedTotal = estimateTotal(job.getParams().getQueryCriteria());
        int shardCount = (int) Math.ceil((double) estimatedTotal / SHARD_SIZE);

        // 查询条件中的时间范围，按天切割
        DateRange range = extractDateRange(job.getParams().getQueryCriteria());
        List<ShardTask> shards = new ArrayList<>();

        if (range != null && range.getDays() >= shardCount) {
            // 按天分片：每天一个分片
            for (int i = 0; i < shardCount; i++) {
                LocalDate dayStart = range.getStart().plusDays(i);
                LocalDate dayEnd = dayStart.plusDays(1);
                shards.add(ShardTask.builder()
                    .parentJobId(job.getJobId())
                    .shardIndex(i)
                    .queryCriteria(buildDayQuery(dayStart, dayEnd, job.getParams().getQueryCriteria()))
                    .fileName(String.format("%s_part_%02d", job.getParams().getFileName(), i))
                    .build());
            }
        } else {
            // 按 order_id hash 分片
            for (int i = 0; i < shardCount; i++) {
                shards.add(ShardTask.builder()
                    .parentJobId(job.getJobId())
                    .shardIndex(i)
                    .queryCriteria(buildHashQuery(i, shardCount, job.getParams().getQueryCriteria()))
                    .fileName(String.format("%s_part_%02d", job.getParams().getFileName(), i))
                    .build());
            }
        }

        return shards;
    }

    /**
     * 并行执行所有分片
     * 每个分片作为一个独立 RocketMQ 消息发送
     */
    public void executeShards(List<ShardTask> shards) {
        shards.parallelStream().forEach(shard -> {
            // 每个分片发送到同一 Topic，用 shardIndex 区分
            rocketMqProducer.send("ASYNC_EXPORT_SHARD", shard);
        });
    }

    /**
     * 所有分片完成后，合并为 zip 包
     * 由 ShardCompletionWatcher 检测到所有分片完成后触发
     */
    public JobResult mergeShards(String parentJobId, List<ShardResult> shardResults) {
        // 创建 zip 包
        File zipFile = ZipMerger.merge(shardResults.stream()
            .map(ShardResult::getOssKey)
            .collect(Collectors.toList()));

        // 上传 zip 到 OSS
        String ossKey = "export/" + parentJobId + "/merged.zip";
        String downloadUrl = ossClient.upload(ossKey, zipFile, 7);

        return JobResult.builder()
            .downloadUrl(downloadUrl)
            .totalRecords(shardResults.stream().mapToInt(ShardResult::getRecordCount).sum())
            .shardCount(shardResults.size())
            .shardUrls(shardResults.stream().map(ShardResult::getDownloadUrl).collect(Collectors.toList()))
            .build();
    }
}
```

### 4. ES Scroll + Point-in-Time 深度导出

```
PIT + Scroll 流程：

  ┌─ 请求方 ─┐     ┌─ ES 集群 ─┐     ┌─ OSS ────┐
  │           │     │           │     │          │
  │ ① POST    │────→│ open pit  │     │          │
  │   /pit    │←────│ pit_id    │     │          │
  │           │     │           │     │          │
  │ ② POST    │────→│ scroll +  │     │          │
  │   /search │     │ pit search│     │          │
  │           │←────│ 2000 条   │     │          │
  │           │     │ search_af │     │          │
  │           │     │ ter       │     │          │
  │ ③ 循环     │────→│ 继续取数   │     │          │
  │           │←────│ 2000 条   │     │          │
  │ ④ 写完批次 │     │           │────→│ 上传 OSS │
  │           │     │           │     │          │
  │ ⑤ DELETE  │────→│ delete pit│     │          │
  └───────────┘     └───────────┘     └──────────┘
```

优点：
- **快照一致性**：PIT 在打开时刻冻结索引状态，整个导出过程看到的是同一份数据快照，不受导出期间订单变更影响
- **无深翻页**：使用 `search_after` + PIT 而非 `from + size`，避免了深翻页的协调节点开销
- **资源友好**：Scroll 上下文在 ES 节点上保持轻量状态，5 分钟超时自动清理

> **注意：Cold/Frozen 阶段索引限制**  
> 当导出范围涉及 Cold / Frozen 阶段的 ES 索引时，Frozen 索引不支持 PIT 查询。针对此场景的备选方案：
> 1. **Searchable Snapshot**：利用 ES Searchable Snapshot 将 Frozen 索引的查询挂载到快照存储上，绕过 Frozen 节点的 PIT 限制。
> 2. **Reindex 到临时索引**：将目标 Cold/Frozen 索引的数据 reindex 到一个临时的 Hot 索引（短暂存活），再对该临时索引执行标准的 PIT + Scroll 导出。
> 3. **OSS 冷存储归档导出**：对于已归档到 OSS 冷存储的历史数据（如超过 6 个月的订单），直接通过 OSS 批量下载或 SelectObject 扫描导出，不经过 ES PIT 路径。
>
> 实际实施时建议在查询条件中自动识别索引所处的 ILM 阶段，动态路由到上述策略。

```java
/**
 * ES PIT + Scroll 工具类
 */
@Component
public class EsPitScrollExporter {

    private static final int DEFAULT_BATCH_SIZE = 2000;
    private static final String PIT_KEEP_ALIVE = "5m";

    /**
     * 使用 PIT + search_after 流式导出
     * 返回 Iterator 供调用方按需消费，避免全部加载到内存
     */
    public OrderIterator exportByPit(ExportQuery query, int batchSize) {
        // 1. 创建 PIT
        String pitId = esClient.createPit("orders-search", PIT_KEEP_ALIVE);

        // 2. 构建查询（不含 from/size）
        SearchRequest request = buildSearchRequest(query, pitId, batchSize);

        // 3. 返回流式迭代器
        return new OrderIterator(esClient, pitId, request);
    }

    /**
     * 流式迭代器 —— 每次迭代取一批，内存友好
     */
    public static class OrderIterator implements Iterator<List<OrderDoc>>, AutoCloseable {
        private final ElasticsearchClient client;
        private final String pitId;
        private final SearchRequest template;
        private String searchAfter;
        private boolean hasMore = true;

        @Override
        public boolean hasNext() {
            return hasMore;
        }

        @Override
        public List<OrderDoc> next() {
            // 设置 search_after
            template.setSearchAfter(searchAfter);

            SearchResponse<OrderDoc> response = client.search(template, OrderDoc.class);
            List<OrderDoc> hits = response.getHits().getHits().stream()
                .map(hit -> hit.getSource())
                .collect(Collectors.toList());

            if (hits.isEmpty()) {
                hasMore = false;
                close();  // 自动释放 PIT
                return Collections.emptyList();
            }

            // 更新 search_after（最后一条的排序值）
            searchAfter = response.getHits().getHits()
                .get(hits.size() - 1).getSortValues().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

            return hits;
        }

        @Override
        public void close() {
            try {
                client.deletePit(pitId);
            } catch (Exception e) {
                log.warn("delete pit failed: {}", pitId, e);
            }
        }
    }
}
```

### 5. 导入设计

```
批量导入流程：

  用户上传 CSV/Excel → 后台解析 → 数据校验 → 人工确认 → 异步执行
                                                         │
                                                    ┌────┴────┐
                                                    │ 事务策略  │
                                                    │         │
                                          ┌─────────┴────┐   │
                                          │ 逐条处理       │   │
                                          │ + 错误收集     │   │
                                          │ (默认策略)     │   │
                                          │              │   │
                                          │ 成功 N 条     │   │
                                          │ 失败 M 条     │   │
                                          │ 生成错误报告    │   │
                                          └──────────────┘   │
                                                   │        │
                                                   ▼        ▼
                                            ┌──────────────────┐
                                            │ OSS 错误报告下载   │
                                            │ 可修正后重新导入    │
                                            └──────────────────┘
```

```java
/**
 * 批量导入服务
 */
@Service
public class BatchImportService {

    /**
     * 导入流程入口
     */
    public String submitImport(MultipartFile file, ImportType type, User operator) {
        // 1. 解析文件
        List<ImportRow> rows = FileParser.parse(file, type.getTemplate());

        // 2. 前置校验（格式、必填、业务规则）
        List<ValidationError> errors = validate(rows, type);
        if (!errors.isEmpty()) {
            // 上传错误报告到 OSS
            String errorUrl = uploadErrorReport(errors);
            throw new ImportValidationException(errors.size(), errorUrl);
        }

        // 3. 创建导入 Job（暂不执行，等待确认）
        ImportJob job = ImportJob.builder()
            .jobId(UUID.randomUUID().toString())
            .type(type)
            .operator(operator)
            .totalRows(rows.size())
            .status(ImportStatus.PENDING_CONFIRM)
            .rawFileUrl(uploadRawFile(file))
            .rows(rows)
            .build();
        jobRepository.save(job);

        return job.getJobId();
    }

    /**
     * 用户确认后异步执行
     * 使用 RocketMQ 异步处理
     */
    public void confirmAndExecute(String jobId) {
        ImportJob job = jobRepository.findById(jobId);
        job.setStatus(ImportStatus.PENDING);
        jobRepository.save(job);
        rocketMqProducer.send("ASYNC_IMPORT", job);
    }

    /**
     * 导入执行（逐条处理 + 错误收集）
     */
    public ImportResult execute(ImportJob job) {
        int successCount = 0;
        int failCount = 0;
        List<RowError> rowErrors = new ArrayList<>();

        for (int i = 0; i < job.getRows().size(); i++) {
            try {
                ImportRow row = job.getRows().get(i);
                switch (job.getType()) {
                    case UPDATE_ADDRESS:
                        orderService.updateAddress(row.toAddressUpdate());
                        break;
                    case BATCH_REFUND:
                        refundService.processRefund(row.toRefundRequest());
                        break;
                    case BATCH_MODIFY_REMARK:
                        orderService.updateRemark(row.toRemarkUpdate());
                        break;
                }
                successCount++;
            } catch (Exception e) {
                failCount++;
                rowErrors.add(RowError.builder()
                    .rowNumber(i + 2)  // +2 因为有表头行且从 0 开始
                    .orderId(job.getRows().get(i).getOrderId())
                    .errorMessage(e.getMessage())
                    .build());
            }
            // 更新进度
            int progress = (i + 1) * 100 / job.getRows().size();
            jobService.updateProgress(job.getJobId(), progress);
        }

        // 生成错误报告
        String errorReportUrl = null;
        if (!rowErrors.isEmpty()) {
            errorReportUrl = generateErrorReport(rowErrors);
        }

        return ImportResult.builder()
            .successCount(successCount)
            .failCount(failCount)
            .errorReportUrl(errorReportUrl)
            .build();
    }
}
```

导入类型与事务策略：

| 导入类型 | 事务策略 | 说明 |
|---------|---------|------|
| **修改地址** | 逐条执行 + 错误收集 | 每条独立事务，失败不影响其他条 |
| **批量退款** | 逐条 + 业务校验 | 每条独立事务，增加风控校验 |
| **批量改价** | 接入 Saga 框架（ADR-020） | 涉及资金，接入 Saga 事务框架（参见 ADR-020）以补偿回滚；若不引入 Saga，则明确标记为"不支持事务，需人工对账" |
| **批量标记** | 逐条执行 + 错误收集 | 标签类操作，容忍部分失败 |

### 6. 权限控制与审计

```java
/**
 * 导出字段权限矩阵
 * 定义每个角色可导出的字段列表
 */
@Component
public class FieldPermissionConfig {

    /**
     * 角色 → 可见字段映射
     * key: role name, value: 允许导出的字段列表
     * 不在列表中的字段将被自动移除
     */
    private static final Map<String, Set<String>> ROLE_FIELD_ALLOWLIST = Map.of(
        "finance", Set.of(
            "orderId", "buyerId", "buyerName", "totalAmount", "discountAmount",
            "payAmount", "paymentMethod", "paymentTime", "refundAmount",
            "status", "createTime", "businessType"
        ),
        "ops", Set.of(
            "orderId", "buyerId", "totalAmount", "status",
            "createTime", "businessType", "region"
        ),
        "cs", Set.of(
            "orderId", "buyerName", "status", "createTime",
            "deliveryStatus", "deliveryAddress", "logisticsNo"
            // 注意：cs 不可见 buyerId、amount、phone
        ),
        "admin", Set.of("ALL")  // 管理员可见所有字段
    );

    /**
     * 按角色过滤字段
     */
    public static List<Map<String, Object>> filter(
            List<OrderDoc> orders, List<String> requestedFields, String role) {

        Set<String> allowedFields = ROLE_FIELD_ALLOWLIST.getOrDefault(role, Collections.emptySet());
        boolean isAdmin = allowedFields.contains("ALL");

        return orders.stream()
            .map(order -> {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String field : requestedFields) {
                    if (isAdmin || allowedFields.contains(field)) {
                        filtered.put(field, getFieldValue(order, field));
                    } else {
                        filtered.put(field, "***");  // 脱敏处理
                    }
                }
                return filtered;
            })
            .collect(Collectors.toList());
    }

    /**
     * 每角色最大导出条数限制
     */
    private static final Map<String, Integer> ROLE_MAX_ROWS = Map.of(
        "finance", 500_000,
        "ops", 100_000,
        "cs", 10_000,
        "admin", 1_000_000
    );

    public static void validateExportLimit(String role, long estimatedRows) {
        int maxRows = ROLE_MAX_ROWS.getOrDefault(role, 10_000);
        if (estimatedRows > maxRows) {
            throw new ExportLimitExceededException(
                "角色 " + role + " 最大导出 " + maxRows + " 条，当前 " + estimatedRows);
        }
    }
}
```

```java
/**
 * 审计日志 —— 所有导入导出操作记录
 */
@Component
public class JobAuditLogger {

    /**
     * 审计日志数据结构
     * 写入专门的 audit_log 表（按月分区，保留 2 年）
     */
    public void log(AsyncJob job) {
        AuditLog log = AuditLog.builder()
            .jobId(job.getJobId())
            .operator(job.getCreator())
            .role(job.getRole())
            .action(job.getType().name())     // EXPORT / IMPORT
            .detail(JsonUtils.toJson(job.getParams()))
            .result(job.getStatus().name())
            .recordCount(job.getResult() != null ? job.getResult().getTotalRecords() : 0)
            .ip(clientIp())                     // 操作来源 IP
            .userAgent(userAgent())             // 浏览器/工具标识
            .operateTime(Instant.now())
            .build();

        auditLogRepository.insert(log);

        // 高风险操作单独告警
        if (isHighRisk(job)) {
            alertService.sendSecurityAlert(log);
        }
    }

    /**
     * 高风险判定条件
     */
    private boolean isHighRisk(AsyncJob job) {
        // 1. 非工作时间导出大量数据
        boolean isOffHours = isNightOrWeekend();
        // 2. 导出包含敏感字段（手机号、身份证）
        boolean containsSensitive = containsSensitiveFields(job.getParams().getFields());
        // 3. 短时间内大量导出
        boolean excessiveExport = isExcessiveExport(job.getCreator(), 1_000_000);

        return (isOffHours && containsSensitive) || excessiveExport;
    }
}
```

### 7. 前端轮询与交互

```
前端交互流程：

  用户点击「导出」
        │
        ▼
  前端：POST /api/v1/export/async
        │
        ▼
  后端：返回 { jobId: "job-xxx", status: "PENDING" }
        │
        ▼
  前端：GET /api/v1/jobs/job-xxx (轮询，每 2s)
        │
        ▼
  前端展示进度条：
  ┌─────────────────────────────────────┐
  │  📄 订单导出进行中                    │
  │  ████████████░░░░░░░░ 65%           │
  │  已导出 32,451 / 50,000 条           │
  │  预计剩余 23s                       │
  │  [取消导出]                          │
  └─────────────────────────────────────┘
        │
        ▼
  完成后：进度条 100%
  ┌─────────────────────────────────────┐
  │  ✅ 导出完成                         │
  │  📁 orders_20260611.csv             │
  │  📦 50,000 条 | 12.3 MB            │
  │  生成耗时 15.2s                     │
  │  [📥 下载] [📋 复制链接] [❌ 关闭]    │
  │  ⚠️ 文件将在 7 天后自动删除            │
  └─────────────────────────────────────┘
```

```java
/**
 * 前端轮询 API
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobPollingController {

    /**
     * 轮询 Job 进度（高频，2s 间隔）
     * 优先从 Redis 缓存读取，减少 DB 压力
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobProgressResponse> getProgress(@PathVariable String jobId) {
        // 1. 从 Redis 读取最新进度（缓存 5s）
        JobProgressResponse cached = redisTemplate
            .opsForValue().get("job:progress:" + jobId, JobProgressResponse.class);
        if (cached != null) {
            return ResponseEntity.ok(cached);
        }

        // 2. 缓存 miss → 从 DB 读取
        AsyncJob job = jobService.findById(jobId);
        JobProgressResponse response = JobProgressResponse.builder()
            .jobId(job.getJobId())
            .status(job.getStatus())
            .progress(job.getProgress())
            .totalRecords(job.getResult() != null ? job.getResult().getTotalRecords() : null)
            .downloadUrl(job.getResult() != null ? job.getResult().getDownloadUrl() : null)
            .shardUrls(job.getResult() != null ? job.getResult().getShardUrls() : null)
            .errorMessage(job.getErrorMessage())
            .estimatedRemainingSeconds(estimateRemaining(job))
            .fileSize(job.getResult() != null ? job.getResult().getFileSize() : null)
            .expiredAt(job.getExpiredAt())
            .build();

        // 写回 Redis（非终态短缓存，终态长缓存）
        int ttl = job.getStatus().isTerminal() ? 300 : 5;
        redisTemplate.opsForValue().set("job:progress:" + jobId, response, ttl, TimeUnit.SECONDS);

        return ResponseEntity.ok(response);
    }

    /**
     * 取消 Job
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        AsyncJob job = jobService.findById(jobId);
        if (!job.getStatus().canCancel()) {
            return ResponseEntity.badRequest().build();
        }
        // 标记取消（Worker 线程检测到 CANCELLED 后自行终止）
        jobService.cancel(jobId);
        // 发送取消消息到 RocketMQ（尽快通知 Worker）
        rocketMqProducer.send("ASYNC_JOB_CANCEL", jobId);
        return ResponseEntity.ok().build();
    }
}

/**
 * 前端轮询响应体
 */
@Data
@Builder
public class JobProgressResponse {
    private String jobId;
    private JobStatus status;
    private Integer progress;           // 0-100
    private Integer totalRecords;       // 总记录数
    private String downloadUrl;         // 下载链接
    private List<String> shardUrls;     // 分片链接
    private String errorMessage;        // 错误信息
    private Long estimatedRemainingSeconds; // 预估剩余秒数
    private Long fileSize;              // 文件大小
    private LocalDateTime expiredAt;    // 过期时间
}
```

### 8. 任务持久化与过期清理

```java
/**
 * Job 数据库表设计
 */
-- job_async 异步任务表
CREATE TABLE `job_async` (
    `job_id`          VARCHAR(64)    NOT NULL COMMENT '任务 ID',
    `type`            VARCHAR(16)    NOT NULL COMMENT 'EXPORT/IMPORT',
    `status`          VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `creator`         VARCHAR(64)    NOT NULL COMMENT '创建人',
    `role`            VARCHAR(32)    NOT NULL COMMENT '角色',
    `params`          JSON           NOT NULL COMMENT '任务参数',
    `result`          JSON           DEFAULT NULL COMMENT '结果信息',
    `progress`        TINYINT        DEFAULT 0 COMMENT '进度 0-100',
    `error_message`   TEXT           DEFAULT NULL COMMENT '错误信息',
    `retry_count`     TINYINT        DEFAULT 0 COMMENT '已重试次数',
    `max_retries`     TINYINT        DEFAULT 3 COMMENT '最大重试次数',
    `created_at`      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `expired_at`      DATETIME       NOT NULL COMMENT '文件过期时间',
    PRIMARY KEY (`job_id`),
    KEY `idx_creator_status` (`creator`, `status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务表';

-- job_audit_log 审计日志表（按月分区）
CREATE TABLE `job_audit_log` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `job_id`          VARCHAR(64)    NOT NULL,
    `operator`        VARCHAR(64)    NOT NULL,
    `role`            VARCHAR(32)    NOT NULL,
    `action`          VARCHAR(16)    NOT NULL COMMENT 'EXPORT/IMPORT',
    `detail`          JSON           NOT NULL,
    `result`          VARCHAR(16)    NOT NULL,
    `record_count`    INT            DEFAULT 0,
    `ip`              VARCHAR(64)    DEFAULT NULL,
    `user_agent`      VARCHAR(256)   DEFAULT NULL,
    `operate_time`    DATETIME(3)    NOT NULL,
    PRIMARY KEY (`id`, `operate_time`),
    KEY `idx_operator` (`operator`),
    KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务审计日志'
PARTITION BY RANGE (TO_DAYS(`operate_time`)) (
    PARTITION p2025 VALUES LESS THAN (TO_DAYS('2026-01-01')),
    PARTITION p2026q1 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p2026q2 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p2026q3 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p2026q4 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

```java
/**
 * 过期清理 —— XXL-Job 每天凌晨执行
 * 清理逻辑：
 * 1. 已过期（expired_at < now）的 Job 标记为 EXPIRED
 * 2. 删除 OSS 上的对应文件
 * 3. 保留 DB 记录（仅标记状态，不删除）
 */
@Component
public class JobExpirationCleanup {

    @XxlJob("jobExpirationCleanup")
    public ReturnT<String> cleanup() {
        // 1. 查询所有已过期且未清理的 Job
        List<AsyncJob> expiredJobs = jobRepository.findExpired();

        for (AsyncJob job : expiredJobs) {
            // 2. 删除 OSS 文件
            if (job.getResult() != null && job.getResult().getDownloadUrl() != null) {
                ossClient.delete(extractOssKey(job.getResult().getDownloadUrl()));
            }
            // 3. 删除分片文件
            if (job.getResult() != null && job.getResult().getShardUrls() != null) {
                job.getResult().getShardUrls().forEach(url -> ossClient.delete(extractOssKey(url)));
            }
            // 4. 标记为已过期
            jobRepository.updateStatus(job.getJobId(), JobStatus.EXPIRED);
        }

        return ReturnT.SUCCESS("清理 %d 个过期 Job", expiredJobs.size());
    }
}
```

### 9. RocketMQ 主题与消费者配置

```yaml
# RocketMQ 主题配置
async-job:
  topics:
    export:
      name: "ASYNC_EXPORT"
      queue-num: 16
      # 按分片 key 哈希投递到同一队列，保证同一 Job 的分片顺序
    export-shard:
      name: "ASYNC_EXPORT_SHARD"
      queue-num: 8
    import:
      name: "ASYNC_IMPORT"
      queue-num: 8
    job-cancel:
      name: "ASYNC_JOB_CANCEL"
      queue-num: 4

# 消费者配置
rocketmq:
  consumers:
    async-export:
      topic: "ASYNC_EXPORT"
      consumer-group: "CG_ASYNC_EXPORT"
      concurrency: 8           # 8 个并发消费者
      max-retry: 3
      retry-interval: 5000     # 重试间隔 5s
    async-export-shard:
      topic: "ASYNC_EXPORT_SHARD"
      consumer-group: "CG_ASYNC_EXPORT_SHARD"
      concurrency: 16          # 分片更密集，更高并发
      max-retry: 3
    async-import:
      topic: "ASYNC_IMPORT"
      consumer-group: "CG_ASYNC_IMPORT"
      concurrency: 4           # 导入涉及写操作，降低并发
      max-retry: 3
```

### 10. 监控指标

| 指标 | 来源 | 看板用途 |
|------|------|---------|
| `async_job_submit_total` | Counter | 任务提交数（按 type/status 标签） |
| `async_job_execution_duration` | Histogram | 任务执行耗时分布 |
| `async_job_success_rate` | Gauge（Recording Rule） | 任务成功率 |
| `async_job_current_pending` | Gauge | 当前排队任务数 |
| `async_export_file_size` | Histogram | 导出文件大小分布 |
| `async_es_scroll_duration` | Histogram | ES Scroll 取数耗时 |
| `async_oss_upload_duration` | Histogram | OSS 上传耗时 |
| `async_import_row_success_rate` | Gauge | 逐条导入成功率 |

```yaml
# alerts/async_job_alerts.yml
groups:
  - name: async_job_alerts
    interval: 30s
    rules:
      # 任务成功率低于 90%
      - alert: AsyncJobSuccessRateLow
        expr: |
          sum(rate(async_job_submit_total{status="failed"}[30m])) 
          / sum(rate(async_job_submit_total[30m])) * 100 > 10
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "异步任务成功率低于 90%"

      # 排队任务积压 > 100
      - alert: AsyncJobQueueBacklog
        expr: async_job_current_pending > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "异步任务队列积压 > 100"

      # 大文件导出失败
      - alert: LargeExportFailure
        expr: async_job_submit_total{type="export", status="failed"} > 10
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "大文件导出失败超过 10 次"
```

### 11. 实施计划

| 阶段 | 任务 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | 异步 Job 框架搭建 | 基础设施 | 2 |
| 1.1 | AsyncJob 数据模型 + DB 表 | PR: job schema | 0.5 |
| 1.2 | RocketMQ Topic 配置 + 消费者基础框架 | PR: consumer 框架 | 0.5 |
| 1.3 | 前端轮询 API + 进度缓存（Redis） | PR: polling API | 0.5 |
| 1.4 | Job 过期清理 + 审计日志基础 | PR: cleanup job | 0.5 |
| **Phase 2** | 导出功能 | 核心能力 | 3 |
| 2.1 | Tier 1 同步导出（< 1 万条 CSV） | PR: sync export | 0.5 |
| 2.2 | Tier 2 异步导出（ES Scroll + PIT + CSV/Excel） | PR: async export | 1.5 |
| 2.3 | Tier 3 分片导出（分片 + OSS 合并 zip） | PR: sharded export | 1.0 |
| **Phase 3** | 导入功能 | 核心能力 | 1.5 |
| 3.1 | 文件上传 + 解析 + 校验（CSV/Excel） | PR: file parser | 0.5 |
| 3.2 | 导入确认流程（待确认 → 执行） | PR: import flow | 0.5 |
| 3.3 | 逐条执行 + 错误收集 + 报告生成 | PR: exec + error report | 0.5 |
| **Phase 4** | 权限 + 审计 + 前端 | 体验与安全 | 1.5 |
| 4.1 | 字段权限矩阵 + 导出条数限制 | PR: permission | 0.5 |
| 4.2 | 审计日志 + 风险告警 | PR: audit + alert | 0.5 |
| 4.3 | 前端进度条 + 下载交互 | PR: frontend | 0.5 |
| **Phase 5** | 监控 + 测试 + 文档 | 收尾 | 1 |
| 5.1 | Prometheus 指标 + 看板 | PR: monitoring | 0.3 |
| 5.2 | 集成测试（含压力测试 ES Scroll） | 测试报告 | 0.5 |
| 5.3 | 操作手册 + 团队培训 | Wiki 文档 | 0.2 |

**合计：9 人天**

### 12. 上线检查清单

#### 基础设施
- [ ] `job_async` 表已创建
- [ ] `job_audit_log` 表已创建（按月分区）
- [ ] RocketMQ Topic（ASYNC_EXPORT / ASYNC_EXPORT_SHARD / ASYNC_IMPORT / ASYNC_JOB_CANCEL）已创建
- [ ] RocketMQ Consumer Group 已配置
- [ ] OSS 存储桶（export/ import/ 目录）已创建
- [ ] OSS 预签名 URL 生成配置已完成

#### 代码
- [ ] AsyncJob 数据模型 + DAO + Service 已发布
- [ ] ES PIT + Scroll 导出工具已发布
- [ ] CSV/Excel 生成器已发布（支持多 Sheet）
- [ ] 分片导出 + zip 合并逻辑已发布
- [ ] 批量导入解析 + 校验 + 执行已发布
- [ ] 字段权限矩阵已配置
- [ ] 审计日志已接入
- [ ] 前端轮询 + 进度条已上线

#### 监控
- [ ] Prometheus 自定义指标已生效
- [ ] Grafana Job 看板已创建（任务状态/耗时/成功率）
- [ ] 告警规则（成功率/堆积/大文件失败）已启用

#### 测试
- [ ] 单元测试：ES Scroll + PIT 流式取数（Mock ES）
- [ ] 集成测试：1 万/10 万/50 万条导出（验证各 Tier 路由正确）
- [ ] 压力测试：大文件导出不影响在线 ES 查询 P99
- [ ] 导入测试：5000 条含 100 条错误 → 验证错误收集 + 报告
- [ ] 权限测试：不同角色导出 → 验证字段脱敏 + 条数限制
- [ ] 降级测试：RocketMQ 不可用 → 返回友好提示

#### 安全
- [ ] OSS 文件访问使用预签名 URL（7 天有效）
- [ ] 导出文件不包含明文手机号/身份证（即使是管理员导出也需审批）
- [ ] 审计日志保留 2 年
- [ ] 高风险导出自动触发安全告警

### 13. 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §4.8 ES 索引设计 | ES Scroll + PIT 导出复用 orders-search 索引，需注意导出期间不影响在线查询 |
| ADR-012 ES 索引 ILM | 导出使用的 PIT 快照与 ILM rollover 兼容（PIT 在 rollover 后仍有效） |
| ADR-015 容量规划模型 | 导出 Worker 集群的容量规划（高峰期导出排队估算） |
| ADR-018 监控大盘增强 | Job 监控指标接入「数据一致性体检看板」的修复任务面板 |

---

## 备选方案评估

### 方案 A（选定）：RocketMQ 异步任务 + ES PIT/Scroll + OSS

**优点**：与现有技术栈（RocketMQ + ES + OSS）无缝集成；Worker 可水平扩展；RocketMQ 自带重试和死信机制

**缺点**：导出大文件时 ES Scroll 上下文占用节点内存；分片合并逻辑增加复杂度

### 方案 B：Spring Async + 本地文件系统

**优点**：实现简单，无需消息队列依赖；适合小规模导出

**缺点**：无持久化，进程重启任务丢失；无法水平扩展；本地磁盘空间有限

**结论**：仅适用于原型验证，生产环境不可用。

### 方案 C：引入 Spark 离线计算引擎

**优点**：适合 TB 级全量数据导出/分析；天然支持分片并行

**缺点**：架构重（引入 Spark 集群）；部署运维成本高；小数据量（< 100 万条）优势不显著

**结论**：如果未来出现全量数据同步到数仓（ODS）的场景，可在后续引入 Spark。当前财务/运营导出规模用 ES + OSS 足够。
