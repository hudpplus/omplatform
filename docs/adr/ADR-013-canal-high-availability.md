# ADR-013：Canal 高可用与多分区消费优化

## 状态

已接受

## 背景

订单中台采用 **Canal 主路径 + MQ 事件辅助刷新** 的 ES 数据同步方案。Canal 监听 OceanBase binlog，将 `order` 表的数据变更实时同步到 Elasticsearch。但当前的 Canal 部署架构存在多个单点故障和性能瓶颈。

### 当前架构（待优化）

```
OceanBase（主库）
    │
    ├─ binlog ──► Canal Server（单节点）
    │                  │
    │                  ├─ 串行解析所有表的 binlog
    │                  └─ 直接写入 ES（同步阻塞）
    │
    ├─ binlog ──► MQ 事件（仅辅助刷新，非主路径）
    └─ ──► XXL-Job 定时全量对账（兜底）
```

### 已知问题

**问题 1：单点故障**

Canal Server 单节点部署，宕机后 ES 数据同步完全中断，依赖 XXL-Job 兜底对账恢复（延迟至少 1 小时+）。Canal 进程 OOM 或网络分区时，binlog 消费位点可能丢失（位点存储在本地文件而非持久化存储），重启后需重新消费，导致数据窗口期增大。

**问题 2：串行消费瓶颈**

单 Canal Instance 串行处理所有 `order` 表的 binlog 变更，未利用分区并行能力。OceanBase 单表 binlog 输出存在性能上限，日订单 100 万 + 状态变更频繁时，Canal 同步延迟在高峰期可达 5-15 秒。

```
Canal Instance
    │
    └─ 串行解析 order 表 binlog ──► 逐条写入 ES
        (高峰期延迟 5-15s)
```

**问题 3：无消费隔离**

ES 写入和缓存刷新的数据变更混在同一通道中，ES 写入阻塞时缓存刷新也会被阻塞。两个场景的延迟敏感度不同——缓存刷新可以接受秒级延迟，但 ES 写入阻塞导致订单查询延迟升高，直接影响用户体验。

```
Canal binlog ──► 同一通道
                    ├─ ES 写入（延迟敏感，用户可见）
                    └─ 缓存刷新（延迟可容忍）
```

**问题 4：位点管理脆弱**

当前 Canal 的位点（Position）存储在本地文件或内存中。Canal 重启或漂移后，位点信息丢失或落后，导致：
- 重复消费大量 binlog（浪费时间）
- 跳过大段 binlog（数据不一致）
- 依赖 XXL-Job 全量对账修复

**问题 5：无延迟监控与自动化恢复**

缺少 Canal 同步延迟的实时监控和告警，延迟劣化时无法及时发现。Canal 进程崩溃后需人工介入重启，无自动恢复机制。同步异常时无重试队列或死信处理，异常数据直接丢弃。

## 决策

针对 Canal 同步架构，从 5 个维度进行优化：

1. **Canal 集群化**：部署 2+ Canal Server，ZK/Redis 选主，故障自动切换
2. **多分区并行消费**：按 OceanBase 分区拆分 binlog 流，多 Canal Instance 并行处理
3. **消费隔离**：ES 写入与缓存刷新分离到不同的 Consumer Group
4. **位点持久化**：位点存储在 Redis 或 OceanBase，支持故障后精准恢复
5. **监控自动化**：实时延迟监控 + 自动恢复 + 重试队列

## 详细设计

### 一、Canal 集群架构

#### 1.1 整体部署拓扑

```
                        ┌─────────────────────┐
                        │   ZooKeeper 集群     │
                        │   (3 nodes)          │
                        └──────────┬──────────┘
                                   │ 选主 / 元数据管理
                                   │
                    ┌──────────────┴──────────────┐
                    │         Canal Cluster         │
                    │                              │
                    │   ┌──────────────────────┐   │
                    │   │  Canal Admin Server   │   │
                    │   │  (管理节点，1 主 1 备)   │   │
                    │   └──────────┬───────────┘   │
                    │              │ 管理/监控       │
                    │    ┌─────────┼─────────┐     │
                    │    │         │         │     │
                    │ ┌──▼──┐  ┌──▼──┐  ┌──▼──┐  │
                    │ │ Canal│  │ Canal│  │ Canal│  │
                    │ │Server│  │Server│  │Server│  │
                    │ │  A   │  │  B   │  │  C   │  │
                    │ │(主)  │  │(从)  │  │(从)  │  │
                    │ └──┬──┘  └──┬──┘  └──┬──┘  │
                    └────┼────────┼────────┼──────┘
                         │        │        │
                    ┌────┴────────┴────────┴──────┐
                    │        RocketMQ Cluster      │
                    │  Topic: canal-order-binlog    │
                    │  (64 分区，按 order_id hash)  │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────┴───────────────┐
                    │       Consumer 层             │
                    │                              │
                    │  ┌──────────────────┐  ┌────┐│
                    │  │ ES Writer Group  │  │Cache││
                    │  │ (保序, 4 实例)    │  │Writer││
                    │  └──────────────────┘  └────┘│
                    └──────────────────────────────┘
```

#### 1.2 Canal Server 集群化

**部署规格**：

| 节点 | 角色 | 规格 | 数量 | 部署方式 |
|------|------|------|------|---------|
| Canal Admin | 管理节点 | 2C/4G | 2（主备） | K8s Deployment |
| Canal Server A | Instance 主 | 4C/8G | 1 | K8s StatefulSet |
| Canal Server B | 热备 | 4C/8G | 1 | K8s StatefulSet |
| Canal Server C | 冷备/扩容 | 4C/8G | 1 | K8s StatefulSet |

**选主机制**（基于 ZooKeeper）：

```
Canal Server 启动流程：
  1. 尝试在 ZK `/canal/cluster/leader` 创建临时顺序节点
  2. 序号最小的节点成为 Leader（主），其他为 Follower（备）
  3. Leader 负责解析 binlog 并投递到 RocketMQ
  4. Follower 监听 Leader 的 ZK 会话保活
  5. Leader 宕机 → 会话超时（默认 10s）→ 临时节点删除
  6. 最小的 Follower 感知节点删除 → 竞选为新 Leader
  7. 从上次持久化的位点开始消费（位点存储在 Redis，见下文）
```

**ZK 节点数据结构**：

```
/canal/cluster/
    ├── leader/            # 选主节点
    │   ├── server-001     # 临时顺序节点（当前 Leader）
    │   └── server-002     # 临时顺序节点（备）
    ├── instances/
    │   └── order-mq/      # Instance 元数据
    │       ├── position   # 当前消费位点（持久化到 Redis 的引用）
    │       └── config     # Instance 配置
    └── admin/
        └── config         # Admin 配置
```

**故障切换流程**：

```
时间线：
  T0: 主 Canal Server A 宕机
  T1: ZK 检测到 A 的会话超时（约 10s）
  T2: Server B 感知到 Leader 节点删除
  T3: Server B 竞选为新 Leader（更新 ZK 节点）
  T4: Server B 从 Redis 读取最后位点，开始消费
  T5: 延迟恢复到正常水平（约 3-10s）
  
  总 RTO ≈ 15-20s（ZK 会话超时 + 位点恢复 + 连接建立）
  
  注意：RPO = 0（位点已持久化到 Redis，不丢数据）
  — 但位点持久化间隔为 1s，最多丢失 1s 内的位点变更（最多重读 1s 的 binlog）
```

#### 1.3 位点持久化

**位点存储策略**：Redis Hash（替代本地文件）

```
Redis Key: canal:position:order-mq
Hash Fields:
  journalName:    "mysql-bin.000123"       # 当前 binlog 文件名
  position:       32569783412               # 当前位点偏移
  timestamp:      1700000000000             # 位点更新时间戳（毫秒）
  serverId:       1                         # DB server ID
  gtidSet:        "a1b2c3d4-1234-5678-..."  # MySQL/OB GTID
  batchId:        45678                     # Canal 内部 batch ID

持久化时机：
  ─ 每批次处理完成后同步写入 Redis（SET with EX 60s）
  ─ 每 1s 异步刷新（防频繁写入）
  ─ Canal 重启时优先读取 Redis 位点 OR 读取 RocketMQ 消费进度
  
  选主时：
    ─ 新 Leader 从 Redis 读取最后位点
    ─ 如果 Redis 位点不存在或过期 → 从 RocketMQ 的 consumer offset 推算位点
    ─ 如果 RocketMQ offset 也不存在 → 回退到 1 小时前的位点（容忍少量重复）
```

**位点恢复优先级**（降级策略）：

```
高 ┌────────────────────────────────┐
   │  1. Redis 持久化位点（精确）      │ ← 最理想
   │  2. RocketMQ Consumer Offset   │ ← 精确但可能滞后
   │  3. RocketMQ 最近消息时间戳推算   │ ← 约 30s 精度
   │  4. 1 小时前的位点（兜底）         │ ← 容忍 1h 重复消费，业务幂等
低 └────────────────────────────────┘
```

### 二、多分区并行消费

#### 2.1 分区策略

OceanBase `order` 表按 `buyer_id` HASH 分为 64 个分区。Canal 利用这个分区结构实现并行消费：

```
Canal Server（主）
    │
    ├─ Partition 0-15  → Canal Instance A（16 分区）
    ├─ Partition 16-31 → Canal Instance B（16 分区）
    ├─ Partition 32-47 → Canal Instance C（16 分区）
    └─ Partition 48-63 → Canal Instance D（16 分区）
    
    每个 Instance 独立解析、投递到 RocketMQ 对应分区
    分区数量 = Consumer 实例数 × N（N ≥ 4，避免 rebalance 不均匀）
```

**Canal Instance 配置**（每个分区组独立配置）：

```properties
# Canal Instance 配置：canal-order-part-0.properties
canal.instance.mysql.slaveId = 1001                # 每个 Instance 不同 slaveId
canal.instance.filter.regex = order_db\\.order     # 只过滤 order 表

# 订阅的 OceanBase 分区
canal.instance.ob.partitions = 0-15                # 分区范围

# RocketMQ 投递配置
canal.mq.topic = canal-order-binlog
canal.mq.partitions = 0-15                         # 对应 RocketMQ 分区
canal.mq.dynamicTopic = false
canal.mq.enableDynamicQueue = false

# 批次配置
canal.instance.batchSize = 2048                    # 每批次最大行数
canal.instance.fetchSize = 4096                    # 每次 fetch 行数
canal.instance.batchTimeout = 500                  # 批次超时 ms
```

**RocketMQ Topic 设计**：

```
Topic: canal-order-binlog
  ─ 分区数: 64（与 OceanBase order 表分区数一致）
  ─ 投递路由: canal.instance.partitions = RocketMQ partition
  ─ 同一条订单记录（相同 buyer_id）始终投递到同一分区
  ─ 保证单分区内的 binlog 变更顺序

  分区对应关系：
    OceanBase Partition 0  →  Canal Instance A  →  RocketMQ Partition 0
    OceanBase Partition 1  →  Canal Instance A  →  RocketMQ Partition 1
    ...
    OceanBase Partition 64 →  Canal Instance D  →  RocketMQ Partition 63
```

#### 2.2 Consumer 端并行消费

**ES Writer Group**（4 个 Consumer 实例）：

```
Consumer Group: canal-es-writer（保序消费）

┌──────────────────────────────────────────────────────┐
│  RocketMQ Topic: canal-order-binlog (64 partitions)  │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────┐ │
│  │ P0-P15   │  │ P16-P31  │  │ P32-P47  │  │P48-63│ │
│  │Consumer-1│  │Consumer-2│  │Consumer-3│  │  C-4 │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──┬───┘ │
│       │              │              │            │     │
│       ▼              ▼              ▼            ▼     │
│  ┌──────────────────────────────────────────────┐     │
│  │             批量写入 ES (Bulk API)             │     │
│  │        每个 Consumer 独立批量写入，互不影响       │     │
│  └──────────────────────────────────────────────┘     │
│                                                      │
│  保序保证：                                           │
│  ─ 同一 buyer_id 的订单变更 → 同一 RocketMQ 分区      │
│  ─ 同一分区由固定 Consumer 消费，顺序写入 ES           │
│  ─ 不同分区之间的顺序不保证（业务可接受）               │
└──────────────────────────────────────────────────────┘
```

**Consumer 配置**：

```yaml
# application.yml（ES Writer Consumer）
rocketmq:
  consumer:
    group: canal-es-writer
    topic: canal-order-binlog
    consumeMode: ORDERLY              # 保序消费（分区内有序）
    maxReconsumeTimes: 3               # 失败最多重试 3 次
    pullBatchSize: 128                 # 每次拉取 128 条
    consumeThreadMin: 4                # 最小消费线程
    consumeThreadMax: 8                # 最大消费线程
    suspendCurrentQueueTimeMillis: 1000  # 失败后等待 1s 重试
```

#### 2.3 容错与 Rebalance

**Consumer 增减时的分区 Rebalance**：

```
场景：ES Writer 从 4 个实例缩容到 3 个

前（4 consumers）：
  C1: P0-P15    C2: P16-P31    C3: P32-P47    C4: P48-P63

后（3 consumers）：
  C1: P0-P21    C2: P22-P42    C3: P43-P63
  
  ─ RocketMQ 自动 rebalance，重新分配分区
  ─ 原 C4 的 P48-P63 分区在 1-2s 内分配给 C1、C2、C3
  ─ rebalance 期间 P48-P63 的消费暂停，不影响其他分区的消费
  ─ 使用 RocketMQ 的 Sticky Rebalance 策略，尽量减少分区移动

扩容时同理：新增的 Consumer 自动接管分区，rebalance 时间 ≈ 1-2s
```

### 三、消费隔离

#### 3.1 多 Consumer Group 架构

将 ES 写入和缓存刷新分离到不同的 RocketMQ Consumer Group，从同一个 Topic 消费：

```
Topic: canal-order-binlog（64 分区）
    │
    ├─ Consumer Group A: canal-es-writer（4 实例）
    │   └─ 目标：ES 索引写入（保序、去重、批量 upsert）
    │   └─ 延迟敏感度：高（用户可见）
    │   └─ 失败处理：重试队列 → 死信 → 人工介入
    │
    ├─ Consumer Group B: canal-cache-writer（2 实例）
    │   └─ 目标：Redis 缓存刷新（删除过期缓存 Key）
    │   └─ 延迟敏感度：低（可接受秒级延迟）
    │   └─ 失败处理：无需重试（缓存 TTL 自动过期兜底）
    │
    └─ Consumer Group C: canal-biz-event（1 实例 [可选]）
        └─ 目标：触发下游业务事件（通知/物流查询/风控）
        └─ 延迟敏感度：中
        └─ 失败处理：重试 → 死信 → 定时扫描
```

**隔离优势**：

| 场景 | 隔离前（单通道） | 隔离后（多 Consumer Group） |
|------|-----------------|--------------------------|
| ES 写入慢/Bulk 拒绝 | 缓存刷新也阻塞 | 缓存刷新独立，不受影响 |
| 缓存故障 Redis 超时 | ES 写入被阻塞 | ES 写入独立，不受影响 |
| ES 升级/重启 | 全链路中断 | 缓存和业务事件依然正常处理 |
| 消费暂停（debug/backlog） | 所有消费者暂停 | 只暂停对应 Group |

#### 3.2 缓存刷新 Consumer 设计

缓存刷新 Consumer 不需要保序（同一 key 的多次删除幂等），但需要低资源消耗：

```yaml
# 缓存 Consumer 配置
rocketmq:
  consumer:
    group: canal-cache-writer
    topic: canal-order-binlog
    consumeMode: CONCURRENTLY         # 无序消费（删除操作幂等）
    pullBatchSize: 32
    consumeThreadMin: 2
    consumeThreadMax: 4
```

```java
@Component
@RocketMQMessageListener(
    topic = "canal-order-binlog",
    consumerGroup = "canal-cache-writer",
    consumeMode = ConsumeMode.CONCURRENTLY
)
public class CacheRefreshConsumer implements RocketMQListener<BinlogMessage> {

    private static final Predicate<String> CACHE_KEY_FILTER =
        key -> key.startsWith("order:") || key.startsWith("order_list:");

    @Override
    public void onMessage(BinlogMessage message) {
        String tableName = message.getTable();
        String eventType = message.getEventType();  // INSERT / UPDATE / DELETE

        // 提取订单 ID 列表
        List<Long> orderIds = extractOrderIds(message);

        // 批量删除缓存
        orderIds.parallelStream().forEach(orderId -> {
            String cacheKey = "order:" + orderId;
            redisTemplate.delete(cacheKey);
            // 删除买家订单列表缓存（需要按买家维度清理，稍微复杂）
            if (CACHE_KEY_FILTER.test(cacheKey)) {
                redisTemplate.delete(cacheKey);
            }
        });

        // 缓存删除失败不需要重试（TTL 自动过期兜底）
        // 但如果连续失败 > 10 次，记录告警日志
    }
}
```

### 四、ES 写入策略

#### 4.1 批量写入与幂等

```java
@Component
@RocketMQMessageListener(
    topic = "canal-order-binlog",
    consumerGroup = "canal-es-writer",
    consumeMode = ConsumeMode.ORDERLY   // 保序
)
public class EsWriterConsumer implements RocketMQListener<List<BinlogMessage>> {

    private static final int BULK_SIZE = 512;       // 批量写入 512 条
    private static final int FLUSH_INTERVAL_MS = 2000; // 或 2s 刷一次

    private final List<BinlogMessage> buffer = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // 定时 flush 兜底
        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, MILLISECONDS);
    }

    @Override
    public void onMessage(List<BinlogMessage> messages) {
        synchronized (buffer) {
            buffer.addAll(messages);
            if (buffer.size() >= BULK_SIZE) {
                flush();
            }
        }
    }

    private void flush() {
        List<BinlogMessage> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }

        if (batch.isEmpty()) return;

        // 组装 BulkRequest
        BulkRequest request = new BulkRequest();
        for (BinlogMessage msg : batch) {
            String indexName = resolveIndexName(msg.getOrderCreateTime());

            switch (msg.getEventType()) {
                case "INSERT":
                case "UPDATE":
                    request.add(new IndexRequest(indexName)
                        .id(String.valueOf(msg.getOrderId()))
                        .source(msg.getDocument(), XContentType.JSON));
                    break;
                case "DELETE":
                    request.add(new DeleteRequest(indexName)
                        .id(String.valueOf(msg.getOrderId())));
                    break;
            }
        }

        // 执行批量写入（带重试）
        RetryUtils.retry(3, 1000, () -> {
            BulkResponse response = esClient.bulk(request, RequestOptions.DEFAULT);
            if (response.hasFailures()) {
                // 记录失败项
                for (BulkItemResponse item : response.getItems()) {
                    if (item.isFailed()) {
                        log.error("ES write失败: orderId={}, error={}, index={}",
                            item.getId(), item.getFailureMessage(), item.getIndex());
                        // 失败项写入重试队列（见 4.3 节）
                        retryQueue.offer(new RetryItem(item.getId(), item.getIndex(), item.getFailureMessage()));
                    }
                }
                throw new RuntimeException("Bulk 写入部分失败");
            }
        });
    }
}
```

#### 4.2 ES 写入优化参数

```json
{
  "index": {
    "refresh_interval": "30s",
    "translog": {
      "durability": "async",          // 异步 translog，提升写入性能
      "sync_interval": "10s",
      "flush_threshold_size": "1024mb"
    },
    "number_of_replicas": 1,
    "routing": {
      "required": true
    }
  }
}
```

**写入 QPS 预估**：

```
日订单 100 万：
  ─ 订单创建（INSERT）：   100 万 / 天 ≈ 12 次/秒
  ─ 订单状态变更（UPDATE）：约 3-5 倍 ≈ 40-60 次/秒
  ─ 总计：~70 次/秒

每 Consumer 处理 16 分区，高峰期（2h 集中）：
  ─ 单 Consumer TPS ≈ 20-30 次/秒
  ─ Bulk 512 条/批，每批约 15-25s 间隔
  ─ ES 集群轻松应对

大促期间（10 倍）：~700 次/秒
  ─ 调整 Bulk 为 2048 条/批
  ─ 适当增加 refresh_interval 到 60s
  ─ 仍有余量
```

#### 4.3 重试与死信队列

```
消费失败时，采用分级重试策略：

第 1 级：Consumer 内部重试
  ─ RocketMQ ORDERLY 模式下，失败后挂起当前队列 1-3s
  ─ 最多重试 3 次
  ─ 适用于：ES 临时限流、网络抖动

第 2 级：延迟队列（RocketMQ 重试 Topic）
  ─ 3 次重试仍失败 → 投递到 RocketMQ 重试 Topic（canal-es-retry）
  ─ 延迟级别：5s → 30s → 5min → 30min → 2h
  ─ 重试 5 次（总时间约 2.5h）
  ─ 适用于：ES 滚动升级期间短暂的不可用

第 3 级：死信队列 + 定时扫描
  ─ 重试 Topic 消费 5 次仍失败 → 投递到死信队列（canal-es-dlq）
  ─ XXL-Job 定时扫描死信队列（每 10min）
  ─ 人工介入：查明朝线失败根因，修复后手动重放

第 4 级：兜底 — 全量对账
  ─ XXL-Job 每日凌晨执行 OB ↔ ES 全量对账
  ─ 发现不一致 → 重新同步
  ─ 死信队列中的所有记录由对账任务统一修复
  ─ 避免死信队列积压导致长期不一致

重试队列架构：

┌──────────────────────────────────────────────────────────────────┐
│                       正常消费链路                                  │
│                                                                  │
│  RocketMQ ──► Consumer ──► 成功 → 提交 offset                     │
│              (canal-es-writer)                                    │
│                  │                                                │
│                  ▼ 失败（重试 3 次）                                │
│            ┌─────────────┐                                        │
│            │ 重试 Topic   │ ──► 延迟消费 → 重试                    │
│            │(重试5次)     │     (5s/30s/5min/30min/2h)            │
│            └──────┬──────┘                                        │
│                   │ 5 次仍失败                                    │
│            ┌──────▼──────┐                                        │
│            │  死信队列    │ ──► XXL-Job 定时扫描 + 人工介入         │
│            │ (canal-     │                                        │
│            │  es-dlq)    │     ┌─────────────────────┐            │
│            └──────┬──────┘     │ 全量对账 XXL-Job      │            │
│                   ├──────────► │ (每天凌晨 2:00)       │            │
│                   │            │ OB ↔ ES 逐条对比      │            │
│                   │            │ 不一致 → 重新同步      │            │
│                   │            └─────────────────────┘            │
│            兜底修复                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### 4.4 批量写入的 ES Java Client 配置

```java
@Configuration
public class EsClientConfig {

    @Bean
    public RestClientBuilder restClientBuilder() {
        return RestClient.builder(
            new HttpHost("es-hot.internal", 9200, "http")
        )
        .setRequestConfigCallback(builder -> builder
            .setConnectTimeout(5000)        // 连接超时 5s
            .setSocketTimeout(60000)        // 套接字超时 60s（Bulk 大包）
            .setConnectionRequestTimeout(10000)  // 从连接池获取的超时
        )
        .setHttpClientConfigCallback(builder -> builder
            .setMaxConnTotal(100)                    // 总连接数
            .setMaxConnPerRoute(50)                  // 每路由连接数
            .setKeepAliveStrategy((response, context) ->
                TimeUnit.MINUTES.toMillis(5))        // 长连接 5min
            .evictIdleConnections(TimeUnit.MINUTES.toMillis(3), TimeUnit.MILLISECONDS)  // 空闲 3min 断开
            .setDefaultRequestConfig(RequestConfig.custom()
                .setContentCompressionEnabled(false)  // 关闭压缩（Bulk 写入场景）
                .build())
        );
    }

    @Bean
    public BulkProcessor bulkProcessor(RestClient restClient) {
        return BulkProcessor.builder(
            (request, bulkListener) -> restClient
                .performRequestAsync(request, bulkListener),
            new BulkProcessor.Listener() {
                @Override
                public void beforeBulk(long executionId, BulkRequest request) {
                    log.debug("ES Bulk 写入: {} 条", request.numberOfActions());
                }

                @Override
                public void afterBulk(long executionId, BulkRequest request,
                                       BulkResponse response) {
                    if (response.hasFailures()) {
                        log.warn("ES Bulk 部分失败: {}",
                            response.buildFailureMessage());
                    }
                }

                @Override
                public void afterBulk(long executionId, BulkRequest request,
                                       Throwable failure) {
                    log.error("ES Bulk 写入失败, 重试: {} 条",
                        request.numberOfActions(), failure);
                }
            })
            .setBulkActions(512)                          // 512 条触发 flush
            .setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB))  // 或 10MB
            .setFlushInterval(TimeValue.timeValueSeconds(2))      // 或 2s
            .setConcurrentRequests(4)                     // 4 个并发 Bulk 请求
            .setBackoffPolicy(BackoffPolicy
                .exponentialBackoff(TimeValue.timeValueSeconds(1), 3)) // 指数退避
            .build();
    }
}
```

### 五、延迟监控与告警

#### 5.1 延迟指标采集

**指标 1：Canal 端到端延迟**

```
定义：binlog 写入时间 → ES 文档可查询时间

计算公式：
  end_to_end_latency = es_doc_timestamp - binlog_timestamp

采集方式：
  ─ 每条 binlog 变更记录携带 broker 写入时间戳
  ─ ES 文档写入后记录 _timestamp 元数据
  ─ XXL-Job 每分钟统计最近 1000 条变更的延迟分布（P50/P95/P99）

指标暴露：
  Prometheus Gauge
  canal_latency_ms{type="end_to_end", partition="0-15"} 1234
```

**指标 2：RocketMQ 消费延迟**

```
定义：RocketMQ Broker 最新消息时间戳 ← Consumer 最新已消费时间戳

Prometheus 指标（RocketMQ Exporter）:
  rmq_consumer_offset{group="canal-es-writer", topic="canal-order-binlog"}
  rmq_producer_offset{group="canal-es-writer", topic="canal-order-binlog"}
  
  消费延迟 = producer_offset - consumer_offset（条数）
  时间延迟 ≈ 条数 / 平均写入 TPS（秒）
```

**指标 3：Canal 自身延迟**

```
Canal 内置指标（JMX Exporter）:
  ─ canal.instance.standby.running: 是否存活（0/1）
  ─ canal.instance.delayTime         # 延迟时间（ms）
  ─ canal.instance.receiveRawCount   # 接收的 binlog 条数
  ─ canal.instance.batchSize         # 批次大小

指标暴露到 Prometheus：
  # HELP canal_delay_ms Canal 实例延迟
  # TYPE canal_delay_ms gauge
  canal_delay_ms{instance="order-mq-0", host="canal-0"} 256
```

**指标 4：ES 写入健康度**

```
Prometheus 指标（ES Exporter + 自定义）:
  ─ es_bulk_rejected: ES 写入拒绝次数（Bulk Rejection）
  ─ es_write_latency_ms: ES 单次 Bulk 写入延迟
  ─ canal_es_write_success_rate: ES 写入成功率（最近 5min）
  ─ canal_dlq_count: 重试队列积压条数
```

#### 5.2 Prometheus 告警规则

```yaml
# canal-alerts.yml
groups:
  - name: canal
    rules:
      # === P1 告警 ===
      - alert: CanalHighDelay
        expr: canal_delay_ms > 10000
        for: 1m
        labels:
          severity: P1
        annotations:
          summary: "Canal 同步延迟超过 10s"
          description: "Canal instance {{ $labels.instance }} 延迟 {{ $value }}ms"

      - alert: CanalESWriteFailed
        expr: rate(canal_es_write_success_rate[5m]) < 0.99
        for: 2m
        labels:
          severity: P1
        annotations:
          summary: "ES 写入成功率低于 99%"
          description: "ES 写入成功率 {{ $value | humanizePercentage }}"

      # === P2 告警 ===
      - alert: CanalHighDelayWarning
        expr: canal_delay_ms > 5000
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "Canal 同步延迟超过 5s"

      - alert: CanalInstanceDown
        expr: canal_instance_up == 0
        for: 1m
        labels:
          severity: P2
        annotations:
          summary: "Canal Instance {{ $labels.instance }} 宕机"

      - alert: CanalDLQGrowing
        expr: canal_dlq_count > 100
        for: 10m
        labels:
          severity: P2
        annotations:
          summary: "Canal 死信队列积压 {{ $value }} 条"
          description: "需人工检查死信原因并处理"

      - alert: CanalRebalancingFrequent
        expr: rate(canal_rebalance_count[15m]) > 2
        labels:
          severity: P2
        annotations:
          summary: "Canal Consumer Rebalance 频繁"
          description: "15min 内 rebalance {{ $value }} 次，检查 Consumer 稳定性"

      - alert: BulkRejection
        expr: rate(es_bulk_rejected[5m]) > 0
        for: 5m
        labels:
          severity: P2
        annotations:
          summary: "ES Bulk Rejection 持续发生"
          description: "ES 写入队列满，检查 ES 集群负载"

      # === P3 告警 ===
      - alert: CanalDelayInfo
        expr: canal_delay_ms > 2000
        for: 10m
        labels:
          severity: P3
        annotations:
          summary: "Canal 同步延迟超过 2s（信息）"
```

#### 5.3 Grafana 看板

**看板 1：Canal 同步总览**

```
┌─────────────────────────────────────────────────────────────────────┐
│  实时同步延迟（折线图，按 Canal Instance 区分）                       │
│  最近 1h 延迟 P50/P95/P99 热力图                                    │
│  ES 写入 TPS（曲线，按 Consumer 区分）                               │
│  RocketMQ 消费 Lag（每个分区）                                       │
│  各 Consumer Group 消费状态                                          │
└─────────────────────────────────────────────────────────────────────┘
```

**看板 2：Canal 健康诊断**

```
┌─────────────────────────────────────────────────────────────────────┐
│  Canal Instance 存活状态（表格式，绿/红）                             │
│  各 Instance 位点差异（位点差距过大 → 可能分区不均匀）                 │
│  Consumer Rebalance 事件时间线                                      │
│  重试/死信队列积压趋势                                              │
│  最后 N 条死信消息详情（表格式：原因、时间、order_id）                 │
└─────────────────────────────────────────────────────────────────────┘
```

**看板 3：ES 写入健康**

```
┌─────────────────────────────────────────────────────────────────────┐
│  Bulk 写入延迟 P99/平均值                                           │
│  Bulk 写入吞吐（doc/s）                                            │
│  Bulk Rejection 计数                                                │
│  ES 集群写入 QPS（各节点分布）                                       │
│  ES Thread Pool 状态（bulk queue/rejected）                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 六、部署与配置

#### 6.1 K8s 部署

```yaml
# canal-server-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: canal-server
  namespace: middleware
spec:
  serviceName: canal-server-headless
  replicas: 3
  selector:
    matchLabels:
      app: canal-server
  template:
    metadata:
      labels:
        app: canal-server
    spec:
      containers:
        - name: canal
          image: canal/canal-server:v1.1.7
          ports:
            - containerPort: 11111  # Canal TCP 端口
            - containerPort: 11112  # Canal Admin 端口
            - containerPort: 9100   # Prometheus metrics
          env:
            - name: canal.admin.manager
              value: canal-admin:8089
            - name: canal.admin.port
              value: "11112"
            - name: canal.admin.register.auto
              value: "true"
            - name: canal.instance.database.driverClassName
              value: "com.oceanbase.jdbc.Driver"
            - name: canal.instance.master.address
              valueFrom:
                configMapKeyRef:
                  name: canal-config
                  key: ob.address
          resources:
            requests:
              cpu: "2"
              memory: 4Gi
            limits:
              cpu: "4"
              memory: 8Gi
          livenessProbe:
            tcpSocket:
              port: 11111
            initialDelaySeconds: 30
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 9100
            initialDelaySeconds: 20
            periodSeconds: 10
          volumeMounts:
            - mountPath: /home/admin/canal/conf
              name: canal-config
            - mountPath: /home/admin/canal/zk
              name: canal-zk-data
      volumes:
        - name: canal-config
          configMap:
            name: canal-config
  volumeClaimTemplates:
    - metadata:
        name: canal-zk-data
      spec:
        storageClassName: fast-ssd
        accessModes: [ "ReadWriteOnce" ]
        resources:
          requests:
            storage: 10Gi
```

#### 6.2 Canal 核心配置

```properties
# canal.properties（全局配置）
canal.port = 11111
canal.metrics.prometheus.enable = true
canal.metrics.prometheus.port = 9100

# ZK 集群
canal.zkServers = zk-0.zk-hs:2181,zk-1.zk-hs:2181,zk-2.zk-hs:2181

# 持久化位点到 Redis（替代本地文件）
canal.instance.global.mode = redis
canal.instance.global.redis.address = redis-cluster:6379
canal.instance.global.redis.password = ${REDIS_PASSWORD}
canal.instance.global.redis.db = 0
canal.instance.global.redis.timeout = 3000

# RocketMQ 投递
canal.serverMode = RocketMQ
canal.mq.servers = rmq-broker:9876
canal.mq.retries = 3
canal.mq.producerGroup = canal-producer
canal.mq.enableMessageTrace = true
canal.mq.accessChannel = local
```

#### 6.3 RocketMQ Consumer 启动配置

```yaml
# application.yml（ES Writer Consumer Service）
spring:
  application:
    name: canal-es-writer

rocketmq:
  name-server: rmq-namesrv:9876
  consumer:
    group: canal-es-writer
    topic: canal-order-binlog
    consume-mode: ORDERLY
    pull-batch-size: 128
    consume-thread-min: 4
    consume-thread-max: 8
    max-reconsume-times: 3
    suspend-current-queue-time-millis: 1000

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [tid=%X{transactionId}] - %msg%n"
  level:
    com.omplatform.canal: DEBUG
    org.elasticsearch.client: INFO

---
spring:
  config:
    activate:
      on-profile: prod

rocketmq:
  consumer:
    pull-batch-size: 512
    consume-thread-min: 8
    consume-thread-max: 16
```

### 七、故障场景与恢复

| 故障场景 | 影响 | 恢复机制 | RTO | RPO |
|---------|------|---------|-----|-----|
| Canal Server 宕机 | ES 同步中断 | ZK 选主 → 新 Server 从 Redis 恢复位点 → 继续消费 | 15-20s | < 1s |
| ZK 集群不可用 | 无法选主 | Leader 继续运行（不进不退），ZK 恢复后重新选主 | - | - |
| RocketMQ Broker 宕机 | 消息无法投递 | Canal 端重试队列缓冲，Broker 恢复后继续投递 | 30s-2min | 0（RocketMQ 持久化） |
| Redis 不可用 | 位点丢失 | 回退到 RocketMQ offset → 回退到 1h 前位点 | 10s | 最多 1h 重复消费 |
| ES 集群不可用 | 写入失败 | Bulk 重试 → 重试队列 → 死信队列 → 全量对账修复 | - | 最大 24h 不一致 |
| ES 写入限流（429） | Bulk Rejection | 指数退避等待 → 降速写入 | 30s-2min | 不影响 |
| Consumer 实例宕机 | 对应分区暂停消费 | RocketMQ 自动 Rebalance → 其他实例接管分区 | 3-5s | - |
| 网络分区 | 部分分区延迟升高 | RocketMQ 自动重连 → 追赶消费 → 恢复正常 | 10-30s | - |
| OceanBase 主备切换 | Canal 断连 | Canal 感知连接断开 → 重连到新主库 → 从 GTID 恢复 | 30s-1min | < 1s |

### 八、自动化运维

#### 8.1 XXL-Job 延迟巡检

```java
@Component
public class CanalMonitorJob {

    /**
     * 每分钟检查 Canal 同步延迟，超过阈值时自动处理
     */
    @XxlJob("canalDelayMonitor")
    public void monitorDelay() {
        // 1. 获取当前延迟（从 Prometheus 或 JMX）
        long delayMs = getCurrentDelay();
        log.info("Canal 当前延迟: {}ms", delayMs);

        // 2. 延迟 > 30s 且持续 3 分钟 → 触发诊断
        if (delayMs > 30_000) {
            runDiagnosis();
        }

        // 3. 延迟 > 60s → 触发紧急恢复
        if (delayMs > 60_000) {
            triggerEmergencyRecovery();
        }
    }

    private void runDiagnosis() {
        // 检查 Canal Instance 是否存活
        // 检查 RocketMQ 消费 Lag
        // 检查 ES 集群健康状态
        // 检查是否有死信堆积
        // 结果写入告警日志 + 通知
    }

    private void triggerEmergencyRecovery() {
        // 1. 尝试重启 Canal Instance（通过 K8s API）
        // 2. 如果重启无效 → 切换到备 Instance
        // 3. 如果所有 Instance 都异常 → 降级到 XXL-Job 全量同步模式
        // 4. 通知 SRE 介入
    }
}
```

#### 8.2 XXL-Job 死信重放

```java
@Component
public class CanalDLQJob {

    /**
     * 每 10 分钟扫描死信队列，自动重放可恢复的失败消息
     */
    @XxlJob("canalDLQConsumer")
    public void consumeDLQ() {
        List<DeadLetterMessage> messages = dlqService.scan(100);

        for (DeadLetterMessage msg : messages) {
            // 跳过重试次数过多的（标记为人工处理）
            if (msg.getRetryCount() > MAX_RETRIES) {
                log.warn("死信消息已超过最大重试次数，需人工介入: orderId={}", msg.getOrderId());
                continue;
            }

            try {
                // 重新写入 ES
                esWriterService.upsertDocument(msg.getOrderId(), msg.getEventType(), msg.getDocument());
                dlqService.markCompleted(msg.getId());
                log.info("死信消息重放成功: orderId={}", msg.getOrderId());
            } catch (Exception e) {
                dlqService.markRetried(msg.getId());
                log.error("死信消息重放失败，下次重试: orderId={}", msg.getOrderId(), e);
            }
        }
    }

    /**
     * 每天晚上 2:00 执行全量对账
     */
    @XxlJob("canalFullReconcile")
    public void fullReconcile() {
        log.info("开始 OB ↔ ES 全量对账");

        // 1. 扫描昨天有变更的订单（从 audit_log 获取变更列表）
        List<Long> changedOrderIds = auditLogService.getChangedOrderIds(yesterday());

        // 2. 分批对比（每批 1000）
        List<List<Long>> batches = Lists.partition(changedOrderIds, 1000);
        int inconsistencyCount = 0;

        for (List<Long> batch : batches) {
            for (Long orderId : batch) {
                Order order = orderRepository.findById(orderId);
                Map<String, Object> esDoc = esQueryService.getDocument(orderId);

                if (!compare(order, esDoc)) {
                    // 不一致 → 重新同步
                    esWriterService.upsertDocument(orderId, "UPDATE", buildDocument(order));
                    inconsistencyCount++;
                }
            }
        }

        log.info("全量对账完成: 检查 {} 条, 修复 {} 条不一致", changedOrderIds.size(), inconsistencyCount);

        if (inconsistencyCount > 0) {
            // 如果修复量 > 100 或占总检查量 > 0.1%，触发告警
            if (inconsistencyCount > 100 || (inconsistencyCount * 1000.0 / changedOrderIds.size()) > 1) {
                alertService.sendP2("OB ↔ ES 全量对账发现 " + inconsistencyCount + " 条不一致，已自动修复");
            }
        }
    }
}
```

### 九、兼容性与迁移

#### 9.1 从单节点 Canal 迁移到集群

迁移采用 **逐步切换** 策略，无需停服：

```
迁移流程：

Phase 1：部署集群（并行运行）
  ─ 部署 Canal Cluster（3 节点）
  ─ 部署新的 RocketMQ Topic（canal-order-binlog）
  ─ 部署 ES Writer Consumer Group
  ─ 新集群与旧 Canal 并行运行，双写 ES（幂等，无需停服）

Phase 2：验证新集群
  ─ 对比新/旧 Canal 的 ES 写入结果（按 order_id 对比文档内容）
  ─ 验证延迟指标（新集群延迟应 ≤ 旧集群）
  ─ 验证故障切换（手动 Kill Leader，确认自动切换 < 30s）
  ─ 运行 24h，确认无数据丢失或重复

Phase 3：切换主路径
  ─ 调整 ES 查询写入权重：新集群 100%，旧集群 0%
  ─ 保留旧 Canal 运行 7 天（只接收不写入 ES）
  ─ 持续监控 7 天，确认无异常

Phase 4：下线旧集群
  ─ 停止旧 Canal 进程
  ─ 释放旧资源（单节点服务器）
  ─ 更新文档和监控面板
```

#### 9.2 版本兼容性

| 组件 | 版本 | 说明 |
|------|------|------|
| Canal Server | 1.1.7+ | 1.1.7 开始支持 RocketMQ 模式 |
| Canal Admin | 1.1.7+ | 管理多 Instance |
| OceanBase JDBC | 2.4.0+ | 支持 OceanBase 4.x binlog 解析 |
| RocketMQ Client | 5.0.0+ | Consumer Group 隔离需要 5.x |
| Elasticsearch | 8.x | Bulk API 兼容 |
| ZooKeeper | 3.8+ | 3.8 开始支持 TTL 节点 |

### 十、回滚方案

#### 方案 A：切回单节点 Canal

```
触发条件：
  ─ 集群模式下出现频繁 Rebalance 或 Leader 切换
  ─ 多分区消费出现数据顺序错乱
  ─ 新集群延迟反而高于旧单节点

操作步骤：
  1. 保留旧 Canal 进程不停（迁移 Phase 3 中的保留措施）
  2. 将 ES 写入流量切回旧 Canal
  3. 停止 ES Writer Consumer
  4. 验证 ES 数据一致性（全量对账）
  5. 逐步下线集群组件

回滚耗时：15min
风险：低（旧 Canal 持续运行，数据未丢失）
```

#### 方案 B：降级为事件驱动同步

```
触发条件：
  ─ Canal 集群全面不可用（ZK + Canal Server 同时故障）
  ─ Redis + RocketMQ 同时故障，位点完全丢失
  ─ 需要快速恢复 ES 写入能力

操作步骤：
  1. 开启 order-core 的领域事件 → ES 直接写入开关
     (Apollo: canal.fallback.event-driven=true)
  2. order-core 发布 OrderCreatedEvent / OrderUpdatedEvent 时
     同步调用 ES 写入（异步非阻塞）
  3. XXL-Job 全量对账从 24h 改为 1h
  4. Canal 恢复后，关闭事件驱动模式，切回 Canal 主路径

回滚耗时：5min（Apollo 配置下发）
风险：中（order-core 增加 ES 写入耦合，「只增不删」原则）
说明：此方案为极端情况兜底，正常情况下不应启用
```

### 十一、实施计划

| 阶段 | 任务 | 时间 | 产出 |
|------|------|------|------|
| **Phase 1** | Canal Admin 部署 + ZK 集群搭建 | 0.5d | Admin 管理界面可用 |
| | Canal Server 3 节点部署 | 0.5d | 集群模式运行 |
| | Redis 位点持久化配置 | 0.5d | 位点不依赖本地文件 |
| | RocketMQ Topic 创建 + 分区规划 | 0.5d | 64 分区 Topic |
| **Phase 2** | ES Writer Consumer 开发 | 1d | 批量写入 + 幂等 |
| | 缓存刷新 Consumer 开发 | 0.5d | 消费隔离 |
| | 重试/死信队列实现 | 0.5d | 失败自动恢复 |
| | 延迟监控 + Prometheus 告警 | 0.5d | 告警规则部署 |
| **Phase 3** | 灰度上线 + 双写验证 | 1d | 新旧集群并行 |
| | 全量对账 XXL-Job | 0.5d | 一致性保障 |
| | 故障演练（Chaos Engineering） | 0.5d | 切换验证 |
| | 文档 + 运维手册 | 0.5d | 知识沉淀 |

**总计：约 7 人天**

### 十二、上线 Checklist

```
□ 前置条件
  □ ZooKeeper 3 节点集群已部署且可用
  □ Redis Cluster 已部署且可用
  □ RocketMQ 5.x 集群已部署，Topic 已创建（64 分区）
  □ Elasticsearch 集群健康（green status）
  □ Canal 1.1.7+ 镜像已推送至 Harbor

□ Canal 集群部署
  □ Canal Admin 已部署并可访问管理页面
  □ 3 个 Canal Server 已注册到 Admin，状态正常
  □ 每个 Instance 配置了正确的 OBServer 地址和分区范围
  □ 位点已持久化到 Redis（验证：重启后位点不丢失）

□ RocketMQ 消费
  □ ES Writer Consumer Group 已启动，正确消费所有分区
  □ 缓存 Writer Consumer Group 已启动
  □ 消费延迟 < 2s（验证：RocketMQ 控制台查看 Lag）
  □ 重试队列（canal-es-retry）已创建
  □ 死信队列（canal-es-dlq）已创建

□ ES 写入验证
  □ 批量 upsert 写入正常（查看 ES 文档数量匹配）
  □ DELETE 操作同步正常
  □ 幂等测试：重复消息不会产生重复文档
  □ Bulk Rejection 时自动退避正常

□ 监控告警
  □ Prometheus 已采集 canal_delay_ms 指标
  □ 告警规则已部署（延迟 > 10s P1，> 5s P2）
  □ Grafana 看板已导入
  □ 延迟 P99 基线已记录（上线前正常值）

□ 故障演练
  □ Kill Canal Leader → 自动切换 < 30s
  □ Kill Consumer Pod → 自动 Rebalance < 5s
  □ ES 集群停止 → 写入进入重试队列，恢复后继续
  □ Redis 不可用 → 位点降级到 RocketMQ offset

□ 回滚准备
  □ 旧 Canal 单节点保留运行（切换开关准备就绪）
  □ Apollo 降级开关已验证
  □ 全量对账 XXL-Job 已就绪（可手动触发）
```

### 十三、相关文档

- [ADR-012 ES 索引 ILM + 字段优化](ADR-012-es-index-ilm-and-field-optimization.md)
- [Oracle Canal 官方文档](https://github.com/alibaba/canal/wiki)
- [RocketMQ 消费者最佳实践](https://rocketmq.apache.org/docs/consumer-example/)
