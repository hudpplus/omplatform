# ADR-041：统一 ID 生成策略

## 状态

已接受

---

## 背景

### 现状分析

当前项目存在 **6+ 种不同 ID 格式**，无统一 ID 生成服务（架构一致性报告 H14）：

| 实体 | 当前格式 | 生成方式 | 问题 |
|------|---------|---------|------|
| order_id | 未定义 | 未定义 | 无格式定义 |
| order_no | `ORD20260613123456789` | 未定义（可能 DB 自增） | 分布式下自增不可行 |
| saga_id | `SAGA20260612-001` | 未定义（可能时间+序列） | 格式不一致 |
| async_job_id | UUID | `UUID.randomUUID()` | 无序，不适合 DB 聚簇索引 |
| delayed_task_id | `generateTaskId()` | 各实现自定义 | 无法统一管理 |
| user_id | `u_10086` | 前缀+序列 | 格式无法扩展 |
| webhook_event_id | `evt_` 前缀 | UUID | 格式不一致 |
| idempotency_key | UUID v4 | 客户端生成（ADR-030） | 外部格式，不纳入 ID 生成 |

### 问题

1. **无序 ID 影响 DB 性能**：UUID v4 完全随机，InnoDB 聚簇索引频繁页分裂
2. **无全局唯一保证**：多服务独立生成，跨服务 trace 时无法区分来源
3. **格式不统一**：运维排障时无法从 ID 一眼识别实体类型
4. **时序不友好**：无法按时间排序/筛选（如"找出今天创建的所有 saga"）

---

## 决策

采用 **Leaf（美团开源）** 双模式 ID 生成：

| 模式 | 适用 | 性能 | 有序 |
|------|------|------|------|
| **Segment 模式** | order_id, order_no, payment_no | ~10k ID/s per instance | ✅ 单调递增 |
| **Snowflake 模式** | saga_id, event_id, trace_id, job_id | ~100k ID/s per instance | ✅ 时间有序 |

### 理由

- **Snowflake 零依赖**：无需 DB 或 Redis 即可生成全局唯一 ID
- **Segment 有序**：适合 InnoDB 聚簇索引，避免 UUID 随机 IO
- **Leaf 成熟**：已在业界广泛验证（美团、滴滴）
- **两步迁移**：新增 ID 用新格式，存量兼容处理

---

## 详细设计

### 1. 架构总览

```
┌───────────────────────────────────────────────────────────────┐
│                    IdGenerator (接口)                          │
│  ┌─────────────────┐             ┌─────────────────┐          │
│  │ SnowflakeGen     │             │ SegmentGen       │          │
│  │ (worker=Redis)   │             │ (DB-backed)      │          │
│  │ ~100k/s          │             │ ~10k/s           │          │
│  └────────┬────────┘             └────────┬─────────┘          │
│           │                               │                     │
│           v                               v                     │
│   ┌──────────────┐              ┌──────────────────┐           │
│   │ Nacos/Redis  │              │ id_allocator DB   │           │
│   │ Worker ID    │              │ (segment table)   │           │
│   └──────────────┘              └──────────────────┘           │
└───────────────────────────────────────────────────────────────┘
```

### 2. ID 格式定义

#### 2.1 Snowflake ID（64-bit long）

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
├─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┬─┤
│0│           timestamp (41 bits)      │  worker  │  sequence    │
│ │           (~69年)                  │  (10bit) │  (12bit)     │
└─┴───────────────────────────────────┴──────────┴─────────────┘
```

- **1-bit sign**: 固定 0（正数）
- **41-bit timestamp**: 毫秒级，自定义 epoch（2026-01-01），可用 ~69 年
- **10-bit worker**: 支持 1024 个 worker（机器 + 进程组合）
- **12-bit sequence**: 每毫秒 4096 个 ID

#### 2.2 Segment ID（单调递增 long）

```
格式: {biz_tag 前缀} + {自增数字}
例: 1008612123456 （纯数字，适合作为主键）
    ORD20260613123456789 （展示用，base36 编码）
```

#### 2.3 各实体 ID 格式

| 实体 | 模式 | 格式 | 示例 | 说明 |
|------|------|------|------|------|
| **order_id** | Segment | `1008612123456` | 纯数字 long | InnoDB 聚簇索引主键 |
| **order_no** | Segment | `ORD{yyMMdd}{12位序列}` | ORD260613123456789012 | 展示用，unique 索引 |
| **payment_id** | Segment | `PAY{yyMMdd}{12位序列}` | PAY260613123456789012 | 展示用 |
| **saga_id** | Snowflake | `734512345678901248` | 纯数字 long | 全局唯一，无 DB 依赖 |
| **event_id** | Snowflake | `734512345678901249` | 纯数字 long | 同一机器内单调递增 |
| **async_job_id** | Snowflake | `734512345678901250` | 纯数字 long | 替代 UUID |
| **delayed_task_id** | Snowflake | `734512345678901251` | 纯数字 long | 替代 自定义 format |
| **user_id** | Segment | `10086` | 纯数字 long | 去掉 u_ 前缀（展示时可加） |

### 3. Snowflake 实现

```java
@Component
public class SnowflakeIdGenerator implements IdGenerator {
    
    private final long workerId;
    private final long epoch = 1767225600000L; // 2026-01-01 00:00:00 UTC
    private final long workerIdBits = 10L;
    private final long sequenceBits = 12L;
    private final long maxWorkerId = ~(-1L << workerIdBits);
    private final long workerIdShift = sequenceBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits;
    private final long sequenceMask = ~(-1L << sequenceBits);
    
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    private final AtomicLong counter = new AtomicLong(0);
    
    public SnowflakeIdGenerator() {
        this.workerId = initWorkerId(); // 从 Redis/Nacos 分配
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range: " + workerId);
        }
    }
    
    public synchronized long nextId() {
        long timestamp = timeGen();
        
        if (timestamp < lastTimestamp) {
            // 时钟回拨：等待
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                wait(offset); // 等待回拨结束
                timestamp = timeGen();
            } else {
                // 超过 5ms 时钟回拨 → 报警 + 用 lastTimestamp +1
                timestamp = lastTimestamp;
            }
        }
        
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 当前毫秒用满 4096 → 等下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        return ((timestamp - epoch) << timestampLeftShift)
             | (workerId << workerIdShift)
             | sequence;
    }
    
    private long initWorkerId() {
        // 优先从 Redis 获取（分布式锁 + 自增）
        // fallback: 从 Nacos 实例元数据获取
        // fallback: IP 地址 + 端口哈希（临时方案）
        return redisTemplate.opsForValue()
            .increment("idgen:worker:counter") % maxWorkerId;
    }
    
    private long timeGen() {
        return System.currentTimeMillis();
    }
    
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }
}
```

### 4. Segment 模式实现

```sql
-- ID 分配器表 (独立 DB 或 order-core 库)
CREATE TABLE `id_allocator` (
    `biz_tag`     VARCHAR(32)  NOT NULL COMMENT '业务标识: order_id/order_no/payment_id',
    `max_id`      BIGINT       NOT NULL DEFAULT 1 COMMENT '当前最大已分配 ID',
    `step`        INT          NOT NULL DEFAULT 1000 COMMENT '步长',
    `description` VARCHAR(128) DEFAULT NULL,
    `gmt_create`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE,
    PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化数据
INSERT INTO id_allocator (biz_tag, max_id, step, description) VALUES
('order_id', 1, 1000, '订单 ID'),
('order_no', 1, 1000, '订单号'),
('payment_id', 1, 1000, '支付 ID');
```

```java
@Component
public class SegmentIdGenerator implements IdGenerator {
    
    private final Map<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    
    @Override
    public synchronized long nextId(String bizTag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(bizTag, this::initBuffer);
        Long id = buffer.nextId();
        if (id == null) {
            // 当前 segment 用完 → 从 DB 获取新 segment
            refreshSegment(buffer);
            id = buffer.nextId();
        }
        return id;
    }
    
    private SegmentBuffer initBuffer(String bizTag) {
        return new SegmentBuffer(bizTag, fetchSegmentFromDB(bizTag));
    }
    
    private Segment fetchSegmentFromDB(String bizTag) {
        // SELECT max_id, step FROM id_allocator WHERE biz_tag = ? FOR UPDATE
        // UPDATE id_allocator SET max_id = max_id + step WHERE biz_tag = ?
        IdAllocator allocator = allocatorMapper.selectForUpdate(bizTag);
        allocatorMapper.incrementMaxId(bizTag, allocator.getStep());
        return new Segment(allocator.getMaxId(), allocator.getMaxId() + allocator.getStep());
    }
}
```

### 5. Worker ID 分配

| 方式 | 优先级 | 说明 | 风险 |
|------|--------|------|------|
| **Redis INCR** | P0 | `IDGEN:WORKER:counter` 自增 % 1024 | Redis 宕机 -> Nacos fallback |
| **Nacos metadata** | P1 | `spring.cloud.nacos.discovery.metadata.worker-id` | 需部署时指定 |
| **IP + Port hash** | P2 | `abs(ip:port.hashCode()) % 1024` | 哈希冲突（低概率） |

### 6. 迁移策略

| 实体 | 当前格式 | 目标格式 | 迁移方式 | 兼容处理 |
|------|---------|---------|---------|---------|
| order_id | 未定义（DB 自增） | Segment | 新订单用新 ID | 已有数据兼容 |
| order_no | `ORD{date}{seq}` | Segment 统一 | 新格式即用 | 格式一致，无需兼容 |
| saga_id | `SAGA{date}-{seq}` | Snowflake | 新 saga 用新格式 | `saga_id` 列改为 varchar(64) |
| async_job_id | UUID | Snowflake | 新 job 用新格式 | 列可存 UUID 或 long |
| delayed_task_id | 自定义 | Snowflake | 新 task 用新格式 | 列兼容 |
| user_id | `u_10086` | 纯数字 | 新用户用纯数字 | 展示时保留 u_ 前缀 |
| event_id | UUID/evt_ | Snowflake | 新事件用新格式 | 列兼容 |

### 7. 监控

```java
// IdGenerator 统一监控
Gauge.builder("idgen.worker.id", this, IdGenerator::getWorkerId)
    .description("Current worker ID")
    .register(registry);

Counter.builder("idgen.snowflake.generated")
    .description("Snowflake ID generation count")
    .tag("service", serviceName)
    .register(registry);

Counter.builder("idgen.segment.generated")
    .description("Segment ID generation count")
    .tag("biz_tag", bizTag)
    .register(registry);

Counter.builder("idgen.clock.rewind")
    .description("Clock rewind detection count")
    .register(registry);
```

### 8. 风险

| 风险 | 缓解 |
|------|------|
| Snowflake 时钟回拨 > 5ms | 告警 + 暂停 ID 生成，直到时钟恢复 |
| Redis 宕机导致 worker ID 无法分配 | Nacos 元数据 + IP 哈希双重 fallback |
| Segment DB 不可用 | 内存中当前 segment 可以用完（~1000 个 ID），期间 DB 恢复即可 |
| Worker ID 耗尽（> 1024） | 扩展 workerIdBits 到 12（4096 worker） |

---

## 与现有文档的关联

| 文档 | 变更 |
|------|------|
| ADR-020 Saga | `saga_id` 格式改为 Snowflake |
| ADR-019 AsyncJob | `async_job_id` 格式改为 Snowflake |
| ADR-021 DelayedTask | `task_id` 格式改为 Snowflake |
| ADR-030 Idempotency | `idempotency_key` 保持 UUID v4（客户端生成） |
| ADR-033 Event Schema | `event_id` 格式改为 Snowflake |
| architecture-consistency-report | H14 修复关闭 |
