# ADR-040：高性能与高可用架构

## 状态

已接受

---

## 背景

### 现状分析

订单系统处于核心交易链路，当前非功能性设计存在以下问题：

**问题 1：性能基线分散且不完整**  
ADR-015 定义了容量规划基线（单实例 QPS）、ADR-024 定义了慢 SQL 性能基线、ADR-014/016 定义了缓存策略、ADR-027 定义了 SLI/SLO 目标。但缺乏**单一 SLA 矩阵**，端到端交易延迟（下单→支付→发货链路）无 SLI 目标。

**问题 2：可用性目标不足**  
ADR-027 定义 order-core 可用性 SLI 为 99.95%（年故障 4.38 小时），但订单系统作为核心交易链路需要 **99.99%+**（年故障 < 52.56 分钟）。当前容灾设计（同城 3-AZ）的 C4/C5 文档引用存在错误（disaster-recovery-plan.md 引用 ADR-018 为"单元化多活"——实际 ADR-018 为监控增强；引用 ADR-014 为"DB 高可用"——实际 ADR-014 为热数据缓存）。

**问题 3：缓存体系缺少本地层**  
ADR-014 设计了 Redis 热缓存层，但本地 JVM 缓存（Caffeine）完全无设计文档，缺少多级缓存策略和热点 key 防护机制。

**问题 4：限流降级阈值未对齐**  
ADR-029 和 degradation-strategy.md 定义的 Sentinel 限流阈值与 ADR-027 的 SLI 目标未对齐，存在"降级触发比 SLO 过敏"或"阈值与基线矛盾"的风险（架构一致性报告 M15）。

**问题 5：数据一致性模式分散**  
Saga（ADR-020）、幂等（ADR-030）、状态机（ADR-039）各自定义重叠的一致性保障机制，缺少统一的集成架构。本地消息表模式（可靠事件表）完全未设计。统一 ID 生成缺失（架构一致性报告 H14）。

### 目标

1. **高性能**：大促峰值毫秒级响应，核心接口（下单/查询）P99 < 200ms
2. **高可用**：99.99%+ 可用性，同城 dual-active 容灾，RTO < 60s
3. **数据一致性**：订单与支付/库存/履约的最终一致性，零数据丢失

### 术语定义

| 术语 | 说明 |
|------|------|
| **多级缓存** | Caffeine(L1) → Redis(L2) → ES(L3) 三层缓存架构 |
| **Peak Shaving** | 通过 MQ 缓冲/请求排队削平流量峰值 |
| **Dual-Active** | 两个 AZ 同时承载业务流量（AZ-A 写 + AZ-A/AZ-B 读） |
| **错误预算** | (1 - SLO) × 总请求数，允许的故障次数上限 |
| **本地消息表** | 在业务数据库事务中写入 event_outbox 表，保证事件不丢失 |
| **断路恢复** | Circuit Breaker 自动恢复的三阶段：Closed → Open → Half-Open |

---

## 架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                      ADR-040 架构总览                                 │
└──────────────────────────────────────────────────────────────────────┘

性能层:
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ 多级缓存      │  │ 异步削峰      │  │ DB 优化       │  │ 热点防护      │
  │ Caffeine L1  │  │ Redis Buffer  │  │ HikariCP     │  │ Key Sharding │
  │ Redis L2     │  │ MQ Queue     │  │ Keyset Pagi  │  │ L1 吸收热点   │
  │ ES L3        │  │ Write-behind │  │ 只读路由      │  │ Request Col. │
  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

可用性层:
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ 99.99% 分解   │  │ Sentinel      │  │ 断路器        │  │ Dual-Active  │
  │ order-core   │  │ Per-Service   │  │ Sentinel     │  │ AZ-A 写      │
  │ 99.995%      │  │ 动态阈值      │  │ 业务断路器     │  │ 两 AZ 读     │
  │ 错误预算      │  │ Apollo 配置   │  │ Apollo 降级   │  │ RTO < 60s    │
  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

一致性层:
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ 本地消息表     │  │ 事务消息      │  │ 三层集成      │  │ 统一 ID       │
  │ event_outbox │  │ RMQ half-    │  │ Saga(ADR-020)│  │ Snowflake    │
  │ XXL-Job 兜底  │  │ commit/check │  │ + 状态机(039) │  │ Leaf Segment │
  │              │  │ back         │  │ + 幂等(030)  │  │ ADR-041      │
  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘
```

---

# Part A — 性能架构

---

## 1. 多级缓存架构 (L1: Caffeine + L2: Redis + L3: ES)

### 1.1 三层缓存设计

当前 ADR-014 已在 ES 前增加 Redis 热缓存层（L2），缺少本地 JVM 缓存（L1）。本设计引入三层缓存体系：

```
读取路径:
  GET order.getById(orderId)
      │
  ┌───┴───┐
  │ L1    │ ← Caffeine (5s TTL, max 10k entries)
  └───┬───┘
      │ miss
  ┌───┴───┐
  │ L2    │ ← Redis (30s TTL, 现有 ADR-014)
  └───┬───┘
      │ miss
  ┌───┴───┐
  │ L3    │ ← ES (fallback, ~50ms)
  └───────┘ → 异步回填 L2 → L1

写入失效:
  OceanBase binlog → Canal → L2 Redis 更新(ADR-014)
      → L1 Caffeine 无分布式失效机制
      → 依赖 L1 5s TTL 自动过期 → 下次读取回填

延迟预算:
  L1 hit:   < 0.1ms (Caffeine 内存读取)
  L2 hit:   < 2ms   (Redis 网络读取)
  L3 query: ~50ms   (ES 全链路)
```

### 1.2 Caffeine L1 配置

```java
// l1-caffeine-config.yml
caffeine:
  order-detail-cache:
    maximum-size: 10000           // 最多 10000 条，防止 GC 压力
    expire-after-write: 5s        // 写后 5s 过期，短 TTL 容忍最终一致
    record-stats: true            // 记录命中率/逐出量
    key-pattern: "order:{orderId}"
  buyer-session-cache:
    maximum-size: 50000
    expire-after-write: 60s
    key-pattern: "session:{buyerId}"
```

**使用范围控制**：
- `order.getById(String orderId)` — 缓存单条订单详情（高频读取）
- `order.getByOrderNo(String orderNo)` — 缓存订单号反查（客服场景）
- **不缓存** `order.listByBuyer()` — 列表返回结果大、买家维度多、缓存爆炸

**Apollo 配置（动态调整）**：

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `cache.l1.order-detail.max-size` | 10000 | L1 最大条目数 |
| `cache.l1.order-detail.ttl-seconds` | 5 | L1 TTL（秒） |
| `cache.l1.order-detail.enabled` | true | L1 是否启用（紧急降级） |

### 1.3 L1 缓存实现

```java
@Component
public class L1CacheManager {
    
    private final Map<String, Cache<String, Object>> caches = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> type, Callable<T> loader) {
        Cache<String, Object> cache = caches.computeIfAbsent(cacheName, this::buildCache);
        Object value = cache.get(key, k -> {
            try {
                return loader.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return value != null ? (T) value : null;
    }
    
    public void evict(String cacheName, String key) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }
    
    public void evictAll(String cacheName) {
        Cache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }
    
    private Cache<String, Object> buildCache(String name) {
        CacheConfig config = loadConfigFromApollo(name);
        return Caffeine.newBuilder()
            .maximumSize(config.getMaxSize())
            .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)
            .recordStats()
            .removalListener((key, value, cause) -> {
                Metrics.counter("cache.l1.eviction", "cache", name, "cause", cause.name()).increment();
            })
            .build();
    }
}
```

### 1.4 多级缓存读取模板

```java
@Component
public class MultiLevelCacheTemplate {
    
    public <T> T get(String cacheName, String l1Key, String l2Key, 
                     Class<T> type, Callable<T> dbLoader) {
        // 1. L1: Caffeine
        L1CacheConfig l1Config = getL1Config(cacheName);
        if (l1Config.isEnabled()) {
            T l1Value = l1CacheManager.get(cacheName, l1Key, type, () -> {
                // L2: Redis via HotCacheTemplate (ADR-014)
                try {
                    return redisCacheTemplate.get(l2Key, type, l1Config.getL2Ttl(), dbLoader);
                } catch (Exception e) {
                    // L3: ES fallback
                    return dbLoader.call();
                }
            });
            if (l1Value != null) return l1Value;
        }
        
        // 2. L1 未命中或未启用 → L2 → L3
        return redisCacheTemplate.get(l2Key, type, l1Config.getL2Ttl(), dbLoader);
    }
}
```

### 1.5 内存与容量

| 指标 | 值 |
|------|-----|
| L1 最大条目 | 10,000 条 |
| 单条缓存大小 | ~2KB（完整订单详情 JSON） |
| 预估内存 | 10,000 × 2KB = 20MB |
| L1 GC 压力 | 低（20MB / JVM Heap 4GB < 1%） |
| 预期 L1 命中率（getById） | ~60%（订单详情读取有局部性） |
| 预期 L2 命中率（列表查询） | ~80%（参考 ADR-014 评估） |

### 1.6 L1 监控指标

```java
// Micrometer L1 指标
@Bean
public MeterRegistry l1Metrics() {
    // 命中率
    Gauge.builder("cache.l1.hit.ratio", l1CacheManager, 
                  cm -> cm.getStats("order-detail-cache").hitRate())
        .description("L1 Caffeine hit ratio")
        .register(registry);
    
    // 大小
    Gauge.builder("cache.l1.size", l1CacheManager,
                  cm -> cm.getStats("order-detail-cache").estimatedSize())
        .description("L1 Caffeine estimated size")
        .register(registry);
    
    // 逐出率
    Gauge.builder("cache.l1.eviction.rate", l1CacheManager,
                  cm -> cm.getStats("order-detail-cache").evictionCount())
        .description("L1 Caffeine total evictions")
        .register(registry);
}
```

---

## 2. 异步削峰模式

### 2.1 请求排队（Gateway 层）

大促峰值时，Sentinel 将超阈值的请求放入队列，而非直接拒绝：

```yaml
# Sentinel 排队配置
order-core.createOrder:
  degrade:
    - type: THREAD_POOL_QUEUE
      queueSize: 1000
      queueTimeoutMs: 2000
      # 队列满 → 429 Too Many Requests
      fallback: "系统繁忙，请稍后再试"
```

**优先级队列**（关键业务优先进入）：

| 优先级 | 请求类型 | Queue Timeout |
|--------|---------|--------------|
| P0 | 支付回调 | 5000ms |
| P1 | 订单创建 | 2000ms |
| P2 | 订单查询 | 1000ms |
| P3 | 订单导出 | 500ms（超时即拒） |

### 2.2 MQ 异步缓冲

非核心写路径通过 RocketMQ 异步削峰：

```
同步路径（核心写）:
  CreateOrder API → validate → Saga(state machine + event_outbox) → response

异步路径（非核心写）:
  Order modification API → validate → MQ → consumer → execute
```

**写操作分类**：

| 操作 | 路径 | 理由 |
|------|------|------|
| order_create | 同步 Saga | 资金操作，必须实时返回 |
| order_pay | 同步 Saga | 资金操作，必须实时返回 |
| order_cancel | 同步（如未支付）/ 异步（如已支付需退款） | 未支付可立即取消 |
| order_modify | 异步 MQ | 地址备注修改不涉及资金 |
| order_export | 异步 MQ | 非实时操作 |
| order_note | 异步 MQ | 客服备注，AP 场景（At Most Once） |

### 2.3 批量写入模式

**DB 批量写入**：
```sql
-- INSERT INTO ... VALUES ... (批量)
INSERT INTO order_item (order_no, sku_id, quantity, price) VALUES
('ORD001', 'SKU001', 1, 29900),
('ORD001', 'SKU002', 2, 19900),
...
LIMIT 500;
-- 必须配置 rewriteBatchedStatements=true (MySQL JDBC 驱动)
```

**ES 批量写入**（增强 ADR-012）：
```yaml
canal.es-writer:
  bulk-processor:
    max-actions: 5000      # 最大 5000 条/批
    max-size: 5mb          # 最大 5MB/批
    flush-interval: 5s     # 最常 5s flush
  retry:
    max-retries: 3
    backoff: exponential   # 1s → 2s → 4s
```

**Redis Pipeline 写入**（增强 ADR-014）：
```
canal.cache-writer:
  batch-window-ms: 500     # 攒批窗口 500ms
  max-batch-size: 100      # 最大 100 条/批
  use-pipeline: true       # Redis PIPELINE 批量 SET + EXPIRE
```

### 2.4 Write-Behind 模式（非核心路径）

对于审计日志、操作日志、非关键统计等场景，使用 Write-Behind 模式：

```
API → MQ (非事务消息，AP) → Consumer 攒批 → DB 批量写入

优势：
  - API 响应时间不受 DB 写入延迟影响
  - 批量写入减少 DB 连接数和 IOPS
  - 失败后 MQ 重试机制兜底

限制：
  - 最终一致（秒级窗口）
  - 适用于 AP 场景（不涉及资金）
```

---

## 3. 数据库优化

### 3.1 连接池统一配置

所有服务统一 HikariCP 配置（Apollo 命名空间 `datasource`）：

```yaml
# 核心服务（order-core, payment-core, inventory-core）
hikari:
  core:
    maximum-pool-size: 20
    minimum-idle: 5
    connection-timeout: 3000       # 3s 快速失败
    idle-timeout: 300000           # 5min 空闲回收
    max-lifetime: 600000           # 10min 最大存活
    leak-detection-threshold: 10000 # 10s 连接泄漏检测

# 非核心服务（order-query, fulfillment, notification）
hikari:
  non-core:
    maximum-pool-size: 10
    minimum-idle: 3
    connection-timeout: 5000
    idle-timeout: 300000
    max-lifetime: 600000
```

### 3.2 读写分离路由

```java
// 使用 @Transactional(readOnly = true) 自动路由到只读节点
@Service
public class OrderQueryService {
    
    // 只读 → 路由到 follower
    @Transactional(readOnly = true)
    public OrderDTO getById(String orderId) {
        // 优先查 L1/L2 缓存 → L3 ES 回退（不查 OB 主库）
        return multiLevelCacheTemplate.get("order-detail", 
            "order:" + orderId, "hot:order:detail:" + orderId,
            OrderDTO.class, () -> esQuery.getById(orderId));
    }
    
    // 强一致性查询 → 路由到 leader
    @Transactional
    public OrderDTO getByIdForUpdate(String orderId) {
        return orderMapper.selectForUpdate(orderId);
    }
}
```

**OceanBase 弱一致性查询**（用于非关键查询）：
```sql
-- 添加 WEAK 一致性 hint，允许 follower 读取
SELECT /*+ READ_CONSISTENCY(WEAK) */ *
FROM `order`
WHERE buyer_id = ? AND order_status IN (?)
ORDER BY gmt_create DESC
LIMIT 20;
```

**适用场景**：

| 查询类型 | 一致性要求 | 路由目标 |
|---------|-----------|---------|
| 买家查看订单列表 | 最终一致 | follower (WEAK) |
| 买家查看订单详情 | 最终一致 | L1/L2 → follower |
| 客服查看订单 | 最终一致 | follower (WEAK) |
| 支付回调查订单 | 强一致 | leader (默认) |
| 系统内部校验 | 强一致 | leader (STRONG) |
| XXL-Job 批量扫描 | 最终一致 | follower (WEAK) |

### 3.3 Keyset 分页（游标分页）

所有涉及大结果集的查询必须使用 Keyset 分页：

```java
// 标准 keyset 分页实现
public PageResult<Order> listByBuyer(Long buyerId, String orderStatus, 
                                      Long lastOrderId, Date lastCreateTime, 
                                      int pageSize) {
    // 通过 ShardingSphere 路由到 buyer_id 所在分片
    List<Order> orders = orderMapper.selectByBuyerWithKeyset(
        buyerId, orderStatus, lastOrderId, lastCreateTime, pageSize + 1);
    
    boolean hasMore = orders.size() > pageSize;
    if (hasMore) {
        orders = orders.subList(0, pageSize);
    }
    
    Order last = orders.get(orders.size() - 1);
    return PageResult.of(orders, hasMore, last.getId(), last.getGmtCreate());
}

// Mapper XML — keyset 分页 SQL
// SELECT * FROM `order`
// WHERE buyer_id = #{buyerId}
//   AND order_status = #{orderStatus}
//   AND (gmt_create < #{lastCreateTime} 
//        OR (gmt_create = #{lastCreateTime} AND id < #{lastOrderId}))
// ORDER BY gmt_create DESC, id DESC
// LIMIT #{pageSize}
```

**禁止 `OFFSET` 的场景**（通过代码审查强制）：
- 扫描超过 10,000 行的分页查询
- 所有 XXL-Job 批量扫描（必须用 keyset 或时间范围分段）
- ES 查询深分页（使用 `search_after` 替代 `from`）

### 3.4 索引优化矩阵

| 查询模式 | SQL Pattern | 推荐索引 | 说明 |
|---------|------------|---------|------|
| 买家订单列表 | `WHERE buyer_id=? ORDER BY gmt_create DESC` | `idx_buyer_gmt_create (buyer_id, gmt_create DESC)` | 覆盖列表查询 |
| 买家订单列表 + 状态 | `WHERE buyer_id=? AND status IN (?) ORDER BY gmt_create` | `idx_buyer_status_gmt_create (buyer_id, status, gmt_create DESC)` | 覆盖按状态筛选 |
| 商家订单列表 | `WHERE seller_id=? ORDER BY gmt_create DESC` | `idx_seller_gmt_create (seller_id, gmt_create DESC)` | 商家端查询 |
| 订单号查询 | `WHERE order_no=?` | `uk_order_no (order_no)` | 唯一索引 |
| 状态扫描 | `WHERE status=? AND status_expires_at<NOW()` | `idx_status_expires (status, status_expires_at)` | StuckOrderDetector |
| 退款对账 | `WHERE status IN (REFUNDING,RETURNING)` | `idx_status_refund (status, gmt_modified)` | RefundReconcileJob |
| 支付回调 | `WHERE payment_id=?` | `uk_payment_id (payment_id)` | 唯一索引 |

---

## 4. 热点 Key 防护

### 4.1 热点检测

```java
@Component
public class HotKeyDetector {
    
    // 本地计数窗口（每秒重置）
    private final LoadingCache<String, AtomicLong> counter = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.SECONDS)
        .build(key -> new AtomicLong(0));
    
    // Redis 命令延迟超过 10ms 的热点检测
    public void recordAccess(String key) {
        long count = counter.get(key).incrementAndGet();
        if (count > HOT_THRESHOLD_PER_SEC) {
            // 上报热点到 Prometheus
            Metrics.counter("hotkey.detected", "key", key).increment();
            // 触发本地缓存保护
            l1CacheManager.promoteToL1(key);
        }
    }
}
```

### 4.2 反热点策略

| 策略 | 实现 | 适用场景 |
|------|------|---------|
| **Key Sharding** | `hot:order:list:{buyer_id % 1000}:{buyer_id}`（已有 ADR-014） | 买家订单列表缓存 |
| **L1 吸收** | Caffeine 缓存 Top 10k 最热订单 ID | 订单详情查询 |
| **Request Collapsing** | 同一 orderId 的并发请求合并为一次 DB/Redis 查询 | 订单详情（大促热门商品） |
| **本地计数器** | 秒级热点计数器（Caffeine 1s 窗口） | 热点发现 → 触发 L1 提升 |
| **写缓冲** | Canal batch 500ms/100 条写入 Redis | 热点订单状态变更 |

### 4.3 Request Collapsing 实现

```java
@Component
public class RequestCollapser {
    
    private final Cache<String, CompletableFuture<OrderDTO>> futureCache = 
        Caffeine.newBuilder()
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .build();
    
    public CompletableFuture<OrderDTO> getOrder(String orderId) {
        return futureCache.get(orderId, key -> {
            // 第一个请求创建 Future，其余请求复用
            CompletableFuture<OrderDTO> future = new CompletableFuture<>();
            // 异步执行实际查询
            asyncExecutor.execute(() -> {
                try {
                    OrderDTO order = actualQuery(orderId);
                    future.complete(order);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        });
    }
}
```

### 4.4 DB 反热点

- **OceanBase 分区键**：`buyer_id` 作为分区键确保请求均匀分布
- **行级锁**：OceanBase 原生行级锁，避免表锁/页锁
- **禁止 `ORDER BY RAND()`**：全表扫描 + 临时排序
- **库存扣减**：Redis Lua 脚本预扣 → 异步同步 DB（库存热点场景）

---

## 5. 性能 SLA 矩阵

### 5.1 服务级性能基线

| 服务 | API / 操作 | P99 目标 | P99 警告 | P50 目标 | 吞吐量 (单实例) |
|------|-----------|---------|---------|---------|----------------|
| **order-core** | createOrder | 200ms | 500ms | 30ms | 2000 QPS |
| | cancelOrder | 200ms | 500ms | 30ms | 1000 QPS |
| | payCallback | 100ms | 300ms | 15ms | 2000 QPS |
| **order-query** | getById | 30ms | 80ms | 5ms | 8000 QPS |
| | listByBuyer | 50ms | 150ms | 15ms | 2000 QPS |
| **payment-core** | charge | 500ms（含外部网关） | 1s | 200ms | 1500 TPS |
| | refund | 3s（含外部网关） | 5s | 1s | 500 TPS |
| **inventory-core** | deduct | 30ms | 80ms | 5ms | 3000 TPS |
| | restore | 30ms | 80ms | 5ms | 3000 TPS |
| **fulfillment** | ship | 200ms | 500ms | 50ms | 1000 TPS |
| **refund-core** | processRefund | 1s | 3s | 200ms | 500 TPS |

### 5.2 基础设施性能基线

| 组件 | 操作 | P99 目标 | P99 警告 | 临界值 |
|------|------|---------|---------|--------|
| **Caffeine L1** | cache get | 0.1ms | 0.5ms | 1ms |
| **Redis L2** | cache get | 2ms | 10ms | 30ms |
| **OceanBase** | order.getById | 3ms | 10ms | 50ms |
| | order.listByBuyer | 15ms | 50ms | 200ms |
| **ES** | query | 50ms | 200ms | 500ms |
| **RocketMQ** | produce | 5ms | 20ms | 100ms |
| **Dubbo** | in-cluster | 10ms | 50ms | 200ms |

### 5.3 端到端交易延迟 SLI

| 交易流程 | P99 目标 | P99 警告 | 涉及组件 |
|---------|---------|---------|---------|
| **createOrder** (端到端) | 1s | 3s | Gateway → order-core → inventory → payment (Saga) |
| **refundOrder** (端到端) | 5s | 10s | Gateway → refund-core → payment-gateway |
| **orderQuery** (byId) | 50ms | 150ms | Gateway → order-query → L1/L2/L3 |
| **orderQuery** (listByBuyer) | 100ms | 300ms | Gateway → order-query → L2/L3 |
| **payCallback** (端到端) | 3s | 5s | Payment-gateway → payment-core → order-core |

---

# Part B — 高可用架构

---

## 1. 可用性目标分解

### 1.1 从 99.95% → 99.99%

当前 `order-core` SLI 目标为 99.95%（ADR-027），升级到 99.99% 意味着年故障时间从 4.38 小时降至 52.56 分钟。

**分解公式**：系统整体可用性 = ∏(各组件可用性)，假设组件故障独立：

```
当前 → 目标:
  99.95% (order-core)  → 99.995%  (52,560倍错误预算缩小)
  99.99% (OB)          → 99.999%  (自愈)
  99.95% (Redis)       → 99.99%
  99.95% (RocketMQ)    → 99.99%
  ---------------------------------
  ≈ 99.80% (系统)      → ≈ 99.99% (系统)
```

### 1.2 各组件可用性目标

| 组件 | 当前目标 | 新目标 | 年故障上限 | 策略 |
|------|---------|--------|-----------|------|
| order-core | 99.95% | 99.995% | 26.28 min | 多 AZ 部署 + HPA headroom + 优雅降级 |
| payment-core | 99.9% | 99.99% | 52.56 min | 多 AZ + 外部网关超时重试 + 异步补偿 |
| inventory-core | 99.95% | 99.99% | 52.56 min | Redis 预扣 + DB 最终同步 |
| order-query | 99.9% | 99.99% | 52.56 min | L1+L2+L3 三重冗余（任一可用即正常） |
| Gateway | 99.99% | 99.999% | 5.26 min | 多实例 + HPA + GSLB |
| OceanBase | 99.99% | 99.999% | 5.26 min | Paxos 3/5 自动故障转移 |
| Redis Cluster | 99.95% | 99.99% | 52.56 min | 跨 AZ 复制 + 自动降级 ES |
| RocketMQ | 99.95% | 99.99% | 52.56 min | 多 Broker + 同步刷盘 |
| **系统复合** | ~99.80% | **~99.99%** | **~52.56 min** | 各组件独立达标 + 故障物理隔离 |

### 1.3 错误预算分配

```yaml
# 错误预算配置 (monthly)
error-budget:
  order-core:
    slo: 0.99995              # 99.995%
    total_requests: 50_000_000  # 月均请求
    budget: 2500               # 月允许失败数
    fast_burn_threshold: 5%    # 2h 内燃烧 5% = 125 次失败
    slow_burn_threshold: 10%   # 1d 内燃烧 10% = 250 次失败
    
  payment-core:
    slo: 0.9999
    total_requests: 10_000_000
    budget: 1000
    
  order-query:
    slo: 0.9999
    total_requests: 200_000_000
    budget: 20000
```

### 1.4 燃烧率告警（更新 ADR-027）

```yaml
# 更新 ADR-027: 燃烧率告警规则
# 原值 0.9995(30d) → 0.99995(新SLI)
groups:
  - name: slo-burn-rate
    rules:
      # 快速燃烧（5% 错误预算用在 2h 内）
      - alert: SLOHighBurnRate
        expr: |
          (
            1 - sum(rate(omplatform_order_core_api_*_total{status=~"5xx"}[1h]))
              / sum(rate(omplatform_order_core_api_*_total[1h]))
          ) < 0.9995                              # 1h 可用性低于 99.95%
          and on()
          (
            1 - sum(rate(omplatform_order_core_api_*_total{status=~"5xx"}[30d]))
              / sum(rate(omplatform_order_core_api_*_total[30d]))
          ) < 0.99995                             # 30d 可用性低于 99.995%
        labels: { severity: P0 }
```

---

## 2. Sentinel 限流降级

### 2.1 per-service Sentinel 阈值矩阵

| 服务 | 资源名 | QPS 阈值 | RT 阈值(ms) | 最大并发 | 降级动作 |
|------|--------|---------|-------------|---------|---------|
| order-core | createOrder | 5000 | 200 | 200 | 429 + MQ buffer |
| | cancelOrder | 1000 | 300 | 100 | 429 |
| | payCallback | 5000 | 100 | 200 | 429 |
| payment-core | charge | 2000 | 1000 | 100 | 429 + retry hint |
| | refund | 500 | 2000 | 50 | 429 |
| | queryPayment | 5000 | 100 | 200 | 429 |
| inventory-core | deduct | 5000 | 50 | 200 | 429 |
| | restore | 5000 | 50 | 200 | 429 |
| | queryStock | 10000 | 20 | 500 | 429 |
| order-query | listByBuyer | 10000 | 100 | 300 | 503 (ES overload) |
| | getById | 20000 | 50 | 500 | 503 |
| fulfillment | ship | 2000 | 200 | 100 | 429 |
| | confirmDelivery | 2000 | 200 | 100 | 429 |

### 2.2 动态降级配置（Apollo）

```yaml
# Apollo Namespace: gateway.sentinel-rules
rules:
  # 预定义配置
  profiles:
    normal:
      order-core.createOrder.qps: 5000
      order-core.createOrder.degrade: 429
      order-query.listByBuyer.qps: 10000
      
    promotion:          # 日常大促
      order-core.createOrder.qps: 8000       # 提升上限
      order-core.createOrder.queueSize: 2000 # 加大排队
      order-core.cancelOrder.qps: 500         # 降低非核心
        
    flash-sale:         # 秒杀场景
      order-core.createOrder.qps: 15000
      order-core.createOrder.queueSize: 5000
      order-core.cancelOrder.qps: 200         # 砍非核心
      order-query.listByBuyer.qps: 5000       # 限读保写
        
    disaster:           # 容灾模式
      order-core.createOrder.qps: 3000       # 缩容 40%
      order-core.payCallback.qps: 3000
      order-query.listByBuyer.qps: 3000
      payment-core.charge.qps: 1000
```

### 2.3 预热保护

```yaml
# K8s 滚动部署后 JIT 预热期间避免误限流
warmup:
  enabled: true
  duration-seconds: 10     # 10s 预热窗口
  initial-ratio: 0.5       # 从 50% 容量开始
  strategy: SLOW_START     # 线性增长
```

### 2.4 更新 degradation-strategy.md

`degradation-strategy.md` §4.1 限流阈值表替换为上述 per-service 矩阵（修复 M15：不再使用通用条件），并更新：

- M16：`HALF_OPEN` 恢复策略从"半自动"修正为"全自动"
- 新增：各组件可用性目标列（引用 Part B §1.2）

---

## 3. 断路器模式

### 3.1 三层断路器体系

| 层 | 类型 | 粒度 | 恢复机制 | 示例 |
|----|------|------|---------|------|
| L1: Sentinel | 资源级 | Dubbo/HTTP 方法 | 自动 HALF_OPEN probe, 10s | `createOrder` 资源熔断 |
| L2: 业务断路器 | 服务级 | 业务组件 | 自动 Timer, 30s | `HotCacheTemplate` 熔断 |
| L3: Apollo 降级 | 平台级 | 全局开关 | 手动 / SRE 确认 | `degradation.level = L2` |

### 3.2 业务断路器接口

```java
public interface BusinessCircuitBreaker {
    
    /** 当前是否熔断 */
    boolean isOpen();
    
    /** 执行业务操作（带断路器保护） */
    <T> T execute(String operation, Callable<T> action, Callable<T> fallback);
    
    /** 成功回调 */
    void onSuccess();
    
    /** 失败回调 */
    void onFailure(Throwable t);
    
    /** 重置断路器 */
    void reset();
}

@Component
public class CircuitBreakerRegistry {
    
    private final Map<String, BusinessCircuitBreaker> breakers = new ConcurrentHashMap<>();
    
    public void register(String name, BusinessCircuitBreaker breaker) {
        breakers.put(name, breaker);
        // 注册 Prometheus Gauge
        Metrics.gauge("circuit.breaker.state", breaker, b -> b.isOpen() ? 1 : 0);
    }
    
    public BusinessCircuitBreaker get(String name) {
        return breakers.get(name);
    }
}
```

### 3.3 断路器实现模板

```java
@Component
public class CircuitBreakerTemplate implements BusinessCircuitBreaker {
    
    private final String name;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile boolean open = false;
    private volatile Instant lastFailureTime;
    
    // Apollo 配置注入
    private int threshold = 50;          // 连续 50 次失败触发
    private long recoveryTimeoutMs = 30000; // 30s 后尝试恢复
    
    @Override
    public <T> T execute(String operation, Callable<T> action, Callable<T> fallback) {
        if (open) {
            // 检查是否到恢复时间
            if (Duration.between(lastFailureTime, Instant.now()).toMillis() > recoveryTimeoutMs) {
                open = false;  // HALF_OPEN
                Metrics.counter("circuit.breaker.half_open", "breaker", name).increment();
            } else {
                Metrics.counter("circuit.breaker.rejected", "breaker", name, "op", operation).increment();
                return fallback.call();
            }
        }
        
        try {
            T result = action.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return fallback.call();
        }
    }
}
```

### 3.4 断路器注册表

| 断路器名称 | 组件 | 阈值 | 恢复时间 | 降级行为 | ADR 源 |
|-----------|------|------|---------|---------|--------|
| `hot-cache` | HotCacheTemplate | 50 次连续 Redis 异常 | 30s | 直读 ES（L3 fallback） | ADR-014 |
| `idempotent-store` | IdempotentStore | Redis 不可用 | 5s | 切换到 DB 唯一索引 | ADR-030 |
| `saga-executor` | SagaExecutor | 连续 3 次 SagaLog 写入失败 | 60s | 降级到 Choreography | ADR-020 |
| `canal-consumer` | CanalCacheWriter | 10 次 MQ 消费失败 | 30s | 跳过该批消息 | ADR-013 |
| `es-query` | OrderQueryService | ES 集群 health=red | 60s | 直读 OB（weak consistency） | ADR-012 |
| `payment-gateway` | PaymentClient | 5 次外部网关超时 | 30s | 队列重试 → P1 告警 | — |

### 3.5 监控指标

```java
// 所有断路器统一指标
// circuit.breaker.state{name="hot-cache", state="open"} → 1
// circuit.breaker.rejected{name="hot-cache", op="get"} → count
// circuit.breaker.half_open{name="hot-cache"} → count

// Grafana 看板: Circuit Breaker Overview
// Panel 1: 各断路器状态（颜色表: green=closed, yellow=half_open, red=open）
// Panel 2: 被拒绝请求数（stacked 柱状图, 按 breaker 分组）
// Panel 3: HALF_OPEN 恢复尝试次数
```

---

## 4. 同城 Dual-Active 容灾

### 4.1 架构模式

从"主备模式"升级为"**同城 Dual-Active**"：

```
┌─────────────────────────────────────────────────────────────────┐
│                          同城 Dual-Active                        │
│                                                                 │
│  AZ-A (主写 + 读)                AZ-B (读)          AZ-C (仲裁) │
│  ┌────────────────────┐    ┌────────────────────┐    ┌─────────┐ │
│  │ K8s Node Pool      │    │ K8s Node Pool      │    │ Arbiter  │ │
│  │ order-core     [2] │    │ order-core     [2] │    │ OBServer │ │
│  │ order-query   [4]  │    │ order-query   [4]  │    │ (投票)   │ │
│  │ payment-core  [2]  │    │ payment-core  [1]  │    └─────────┘ │
│  │ inventory-core [2] │    │ inventory-core [1] │               │
│  ├────────────────────┤    ├────────────────────┤               │
│  │ OB: OBServer-1(L)  │    │ OB: OBServer-3(F)  │               │
│  │ OB: OBServer-2(F)  │    │ OB: OBServer-4(F)  │               │
│  ├────────────────────┤    ├────────────────────┤               │
│  │ Redis Cluster (主)  │    │ Redis Cluster (从)  │               │
│  │ TTL 30s            │    │ TTL 120s           │               │
│  │ Canal cache-writer  │    │ Canal cache-writer │               │
│  ├────────────────────┤    ├────────────────────┤               │
│  │ RocketMQ (主)       │    │ RocketMQ (从)      │               │
│  └────────────────────┘    └────────────────────┘               │
└─────────────────────────────────────────────────────────────────┘

流量分发:
  GSLB: az-a.omplatform.com (weight 7) + az-b.omplatform.com (weight 3)
  
  写入: AZ-A (order-core/payment-core 写接口始终路由到 AZ-A)
  读取: AZ-A 70% + AZ-B 30% (order-query 两 AZ 均可处理)
  
  故障切换:
    AZ-A 宕机 → GSLB 切到 AZ-B (weight 10), RTO < 60s
    AZ-B 宕机 → GSLB 切走 AZ-B (weight 0), RTO < 10s
```

### 4.2 AZ 切换过程

```
AZ-A 完全故障 → 切换到 AZ-B:
  1. 故障确认 (10s) — 多探测点确认 AZ-A 失联
  2. OB 主从切换 (30s) — OBServer-3(F) → 提升为 Leader
  3. Redis 预热 (0s) — AZ-B 已有缓存（30% 流量预热）
  4. GSLB 切换 (10s) — DNS TTL=60s 生效
  5. RocketMQ 生产者切换 (5s) — 切换到 AZ-B Broker
  RTO 总计: ~60s
  RPO: < 5s (RocketMQ 异步复制延迟)

AZ-B 部分故障 → 灰度收缩至 AZ-A:
  1. 降级 AZ-B 查询流量到 0% (10s)
  2. AZ-B Redis 故障 → AZ-B 查询走 ES（同 AZ）
  RTO: < 10s
  RPO: 0
```

### 4.3 缓存预热策略（增强 ADR-016）

AZ-B 持续预热确保随时可以承接全部流量：

```yaml
az-b.cache-warmup:
  # 持续预热（非故障时才预热）
  mode: continuous              # 持续模式
  source-traffic-ratio: 0.3     # AZ-B 已承接 30% 读流量
  target-hit-rate: 0.85         # 目标命中率 85%
  ttl-standby: 120              # AZ-B 缓存 TTL 较长（减少刷新频率）
  ttl-recovery: 30              # 故障恢复后降至正常 TTL

  # 预热数据源
  warmup-sources:
    - type: SHADOW_READ          # 影子读取入口流量
      ratio: 0.1                 # 镜像 10% 的 AZ-A 读请求
    - type: XXL_JOB              # 定时预热
      cron: "0 */5 * * * ?"      # 每 5 分钟增量预热
      batch-size: 500
```

### 4.4 AZ 级降级矩阵

| AZ 组件状态 | 影响 | 降级动作 |
|------------|------|---------|
| AZ-A Redis Cluster 宕机 | AZ-A L2 缓存不可用 | AZ-A order-query 降级到 ES（同 AZ） |
| 两 AZ Redis 均宕机 | L2 不可用 | 全量查询降级到 ES |
| AZ-A OBServer1/2 宕机（< 3 节点） | 无影响 | OBProxy 自动路由到剩余节点 |
| AZ-A 全部 OBServer 宕机 | 写能力丧失 | OB 主从切换 + GSLB 切换（RTO < 60s） |
| AZ-A ↔ AZ-B 网络分区 | 分脑风险 | AZ-C Arbiter 仲裁 + OB 多数派 |
| AZ-B Redis Cluster 宕机 | AZ-B 查询降级 | AZ-B order-query 降级到 ES（同 AZ） |

### 4.5 更新 disaster-recovery-plan.md

修复 C4: 删除 `ADR-018 单元化多活` 引用，替换为 `deployment.puml` + `ADR-016 多 AZ 缓存优化`
修复 C5: 删除 `ADR-014 DB 高可用` 引用，替换为 `OceanBase 原生 Paxos 自动 failover` + `ADR-016 跨 AZ 协调`
新增 §2.2 双活模式描述

---

## 5. 多地域容灾规划（Roadmap）

### 5.1 架构方向

同城 3-AZ 无法容忍双 AZ 同时故障。中期规划的多地域容灾方向：

```
Region-A (杭州)                 Region-B (上海)
┌────────────────────┐         ┌────────────────────┐
│ AZ-A (主+读)        │         │ AZ-D (从)           │
│ AZ-B (读)           │         │ AZ-E (从)           │
│ AZ-C (仲裁)          │         │                      │
└────────┬───────────┘         └────────┬───────────┘
         │ 异步复制 (RPO < 60s)          │
         └───────────────────────────────┘
         
OB: 3-region, 5-replica 架构
  - Region-A: 2 副本 (AZ-A leader, AZ-B follower)
  - Region-B: 2 副本 (AZ-D follower, AZ-E follower)  
  - Region-C: 1 副本 (独立仲裁节点)
  
Redis: 跨地域异步复制 (Redis-shake / 自建)
ES: 跨地域 CCR (Cross Cluster Replication)
RocketMQ: 跨地域异步复制 (DLedger)
```

### 5.2 RTO/RPO 目标

| 故障范围 | RTO | RPO | 成本 |
|---------|-----|-----|------|
| 同城 AZ-A 故障 | < 60s | < 5s | 已有（内部资源） |
| 同城 3-AZ 全故障 | ~15min | ~60s | 需要 Region-B 资源（~1.5× 现有） |
| Region-A 全故障 | < 30min | < 60s | 需要 Region-B 全量资源（~2× 现有） |
| Region-A+B 全故障 | 不可恢复 | — | 灾难级（需离线备份恢复） |

### 5.3 实施优先级

| 阶段 | 内容 | 优先级 | 预计投入 |
|------|------|--------|---------|
| Phase 1 (已有) | 同城 3-AZ | ✅ 已就绪 | — |
| Phase 2 | 同城 Dual-Active（本 ADR Part B §4） | P1 | 2 周 |
| Phase 3 | 跨地域异步复制管道（OB/Redis/ES/MQ） | P2 | 4 周 |
| Phase 4 | Region-B 资源就绪 + 容灾演练 | P3 | 6 周 |

---

# Part C — 数据一致性架构

---

## 1. 本地消息表模式 (event_outbox)

### 1.1 架构

本地消息表（可靠事件表）是保证事件可靠投递的基础模式。业务操作与事件记录在同一本地事务中写入，XXL-Job 兜底扫描未投递事件。

```
业务操作流程:
  1. BEGIN TX
  2. UPDATE order SET status = ? WHERE order_no = ?
  3. INSERT INTO event_outbox (event_id, type, payload, status='PENDING')
  4. COMMIT TX
  5. SEND to RocketMQ (如果成功 → UPDATE status='SENT')
  6. IF MQ send failed → XXL-Job 兜底扫描 → 重新投递

XXL-Job 兜底:
  每 5s 扫描 PENDING 状态且 retry_count < max_retries 的事件
  → 重新发送到 RocketMQ
  → 超过 max_retries → 写入 DLQ → P1 告警
```

### 1.2 表结构

```sql
CREATE TABLE `event_outbox` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `event_id`        VARCHAR(64)  NOT NULL COMMENT '全局事件ID (Snowflake)',
    `aggregate_type`  VARCHAR(32)  NOT NULL COMMENT '聚合类型: order/payment/refund',
    `aggregate_id`    VARCHAR(64)  NOT NULL COMMENT '聚合ID (order_no)',
    `event_type`      VARCHAR(64)  NOT NULL COMMENT '事件类型: OrderCreated/PaymentSuccess/RefundComplete',
    `payload`         JSON         NOT NULL COMMENT '事件载荷 (JSON)',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retries`     INT          NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    `next_retry_at`   DATETIME(3)           DEFAULT NULL COMMENT '下次重试时间 (退避后)',
    `gmt_create`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `gmt_modified`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_at`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表 - 可靠事件投递';
```

### 1.3 使用范围

| 场景 | 必须使用 | 原因 |
|------|---------|------|
| 订单创建 → 发货指令 | ✅ | 涉及履约，不可丢失 |
| 支付成功 → 订单状态变更 | ✅ | 资金操作 |
| 退款结果 → 订单状态同步 | ✅ | 资金操作 |
| 订单取消 → 释放库存 | ✅ | 库存一致性 |
| 订单状态变更 → 通知 ES/缓存 | ❌ (使用 Canal binlog) | 可重建，不计对账范围 |
| 订单状态变更 → 买家通知 | ❌ (尽力通知) | AP 场景 |

### 1.4 XXL-Job 兜底投递

```java
@Component
public class EventOutboxJob {
    
    @XxlJob("eventOutboxDelivery")
    public void delivery() {
        // 每 5s 扫描 PENDING 状态 + 到达重试时间的事件
        List<EventOutbox> pendingEvents = outboxMapper.selectPending(
            100, // 每次取 100 条
            LocalDateTime.now()  // next_retry_at <= now
        );
        
        for (EventOutbox event : pendingEvents) {
            try {
                // 发送到 RocketMQ
                SendResult result = rocketMQTemplate.syncSend(
                    event.getEventType(),   // Topic = event_type
                    event.getPayload(),     // Message body
                    event.getEventId()      // Keys = event_id (去重)
                );
                
                if (result.getSendStatus() == SendStatus.SEND_OK) {
                    outboxMapper.markSent(event.getId());
                }
            } catch (Exception e) {
                // 重试次数 + 指数退避
                int nextDelay = calculateBackoff(event.getRetryCount());
                outboxMapper.markRetry(event.getId(), 
                    event.getRetryCount() + 1, nextDelay);
                
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    // 超过最大重试 → P1 告警
                    alertService.p1("Event delivery failed after max retries: " 
                        + event.getEventId());
                }
            }
        }
    }
    
    private int calculateBackoff(int retryCount) {
        // 指数退避: 5s, 10s, 20s, 40s, 80s
        return (int) (5 * Math.pow(2, retryCount));
    }
}
```

### 1.5 集成 RocketMQ 消息去重

```java
@Component
@RocketMQMessageListener(topic = "order-event", consumerGroup = "order-event-consumer")
public class OrderEventConsumer implements RocketMQListener<MessageExt> {
    
    @Override
    public void onMessage(MessageExt msg) {
        String eventId = msg.getKeys();
        // 幂等消费: event_id 记录在 consumer_record 表
        if (idempotentService.alreadyProcessed(eventId)) {
            return;  // 已消费，去重
        }
        
        // 业务处理
        processEvent(JSON.parseObject(msg.getBody(), OrderEvent.class));
        
        // 记录消费
        idempotentService.markProcessed(eventId);
    }
}
```

---

## 2. 事务消息模式（RocketMQ）

### 2.1 流程

RocketMQ 事务消息适用于"业务操作 + 消息发送"需要在同一个事务中保证的场景：

```
Producer (order-core):
  1. 发送 half message → RocketMQ (not visible to consumers)
  2. 执行本地事务 (order status change + event_outbox插入)
  3a. 本地事务成功 → COMMIT half message (consumers 可见)
  3b. 本地事务失败 → ROLLBACK half message
  4. RocketMQ 回查: 如果 commit/rollback 未收到，
     调用 checkLocalTransaction() 检查 event_outbox 记录

Consumer (inventory/fulfillment):
  5. 消费消息 → 执行业务逻辑
  6. 业务逻辑失败 → 本地补偿（非回滚消息，Saga 进行补偿）
```

### 2.2 使用场景

| 场景 | MQ Topic | 事务消息 | 回查方式 |
|------|---------|---------|---------|
| OrderCreated → 扣减库存 | `order-event` | ✅ | event_outbox 表查询 |
| OrderPaid → 发货准备 | `order-event` | ✅ | event_outbox 表查询 |
| OrderCancelled → 释放库存 | `order-event` | ✅ | event_outbox 表查询 |
| PaymentSuccess → 订单状态 | `payment-event` | ✅ | 支付记录表查询 |
| RefundComplete → 订单状态 | `refund-event` | ✅ | 退款记录表查询 |

### 2.3 回查实现

```java
@Component
@RocketMQTransactionListener(txProducerGroup = "order-tx-producer")
public class OrderTransactionChecker implements RocketMQLocalTransactionChecker {
    
    @Autowired
    private EventOutboxMapper outboxMapper;
    
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(MessageExt msg) {
        String eventId = msg.getKeys();
        
        EventOutbox record = outboxMapper.findByEventId(eventId);
        if (record == null) {
            // event_outbox 无记录 → 本地事务已回滚
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        
        if ("SENT".equals(record.getStatus())) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        
        // PENDING 状态 → 等待
        return RocketMQLocalTransactionState.UNKNOWN;
    }
}
```

### 2.4 事务消息 + event_outbox 双保险

```
事务消息为主路径, event_outbox 兜底:

  正常路径:
    half message → 本地事务 → COMMIT → 消费者收到

  异常路径 1: half message 发送失败
    → 不用 half message, 直接本地事务写入 event_outbox
    → XXL-Job 兜底投递到 RocketMQ（普通消息）

  异常路径 2: 本地事务成功但 COMMIT 未到达 RocketMQ
    → RocketMQ 回查 checkLocalTransaction() → event_outbox 已存在 → COMMIT

  异常路径 3: event_outbox 写入成功但 RocketMQ 异常
    → XXL-Job 兜底扫描 PENDING → 重新投递
```

---

## 3. Saga + 状态机 + 幂等 三层集成

### 3.1 集成执行流

```
请求（含 Idempotency-Key Header）
          │
     ┌────▼──────────────────────────────────────┐
     │ ① IdempotencyFilter (Gateway / Dubbo)     │ ← ADR-030
     │    Redis SET NX idempotency_key → 已存在?  │
     │    → 返回缓存结果（幂等）                    │
     └────┬──────────────────────────────────────┘
          │ 首次请求
     ┌────▼──────────────────────────────────────┐
     │ ② SagaExecutor 开始编排                     │ ← ADR-020
     │    写入 saga_instance (INIT)                │
     │    生成 saga_id (Snowflake, ADR-041)        │
     └────┬──────────────────────────────────────┘
          │ Step N: 执行每个 Saga 步骤
     ┌────▼──────────────────────────────────────┐
     │ ③ 每步执行:                                 │
     │    a. Dubbo 调用携带 Idempotency-Key       │ ← ADR-030
     │    b. StateMachineEngine.validateTransition│ ← ADR-039
     │    c. StateMachineEngine.executeTransition │ ← ADR-039
     │       → 乐观锁 CAS UPDATE order.status     │
     │       → INSERT event_outbox (本地事务)     │
     │    d. 写入 saga_step_log                    │
     └────┬──────────────────────────────────────┘
          │ 所有步骤成功
     ┌────▼──────────────────────────────────────┐
     │ ④ Saga COMPLETED                           │
     │    标记 saga_instance → COMPLETED           │
     │    RocketMQ 事务消息 → 通知下游              │
     └────────────────────────────────────────────┘
     
     失败路径:
     ┌────▼──────────────────────────────────────┐
     │ ⑤ Saga COMPENSATING                        │
     │    StateMachineEngine.compensate()          │
     │    (skip guards: forceTransition)           │
     │    执行反向补偿步骤                          │
     │    标记 saga_instance → COMPENSATED         │
     └────────────────────────────────────────────┘
```

### 3.2 三层依赖关系

```
┌─────────────┐  调用   ┌──────────────────┐
│ ADR-030      │ ──────→│ 各原子服务         │
│ 幂等框架      │         │ (Idempotency-Key) │
└─────────────┘         └──────────────────┘

┌─────────────┐         ┌──────────────────┐
│ ADR-020      │ ──────→│ ADR-039 状态机     │
│ Saga 编排     │         │ validateTransition │
│              │         │ executeTransition  │
│              │         │ compensate()       │
└─────────────┘         └──────────────────┘

┌─────────────┐         ┌──────────────────┐
│ ADR-040(C)   │ ──────→│ 事件投递            │
│ event_outbox │         │ transactional msg  │
│ 本地消息表    │         │ XXL-Job 兜底       │
└─────────────┘         └──────────────────┘
```

### 3.3 集成要点

1. **Saga ID 使用统一 ID 生成**（ADR-041 Snowflake），不再使用 `SAGA20260612-001` 格式
2. **原子服务入口先过幂等**：每个 Dubbo 调用携带 ADR-030 的 Idempotency-Key
3. **状态机转换在原子服务内部**：Saga 编排层不直接调用状态机，原子服务封装 transition + guard + entry/exit action
4. **补偿时跳过守卫**：`forceTransition()` 绕过 guard 条件（ADR-039 预留 forceTransition API）

### 3.4 更新 ADR-020

修复 H6：ADR-020 §6 (IdempotentChecker) 和 §12 (与现有文档的关联) 增加 ADR-030 引用：
- `saga_id + step_name` 唯一键将迁移到 ADR-030 全局幂等框架
- 同步 `idempotent_record` 表结构与 ADR-030 统一

---

## 4. 补偿与对账体系

### 4.1 补偿模式目录

| 业务操作 | 补偿操作 | 负责服务 | 重试策略 | 超时告警 |
|---------|---------|---------|---------|---------|
| createOrder → 创建订单 | cancelOrder → 取消订单 | order-core | 3x (1s, 5s, 30s) | P1 15min |
| deductInventory → 扣库存 | undoDeduct → 释放库存 | inventory-core | 5x (1s, 5s, 15s, 30s, 60s) | P1 15min |
| chargePayment → 扣款 | refund → 退款 | payment-core | 3x (10s, 60s, 300s 慢重试) | P1 5min |
| releaseCoupon → 释放优惠券 | reissueCoupon → 重新发放 | coupon-center | 3x (1s, 5s, 30s) | P2 1h |
| sendNotification → 通知 | 无（at-most-once） | notification | 0（放弃） | — |

### 4.2 数据对账矩阵

| 数据对 | 频率 | 工具 | 不一致处理 |
|--------|------|------|-----------|
| OB ↔ ES (订单数据) | 每日 02:00 | XXL-Job + Canal checker | 重建 ES index |
| OB ↔ Redis 热缓存 | 每小时 | XXL-Job + hot cache checker | 重建 Redis key |
| OB ↔ 支付网关 | 每 15min | XXL-Job + payment reconciler | P1 告警 + 自动修正 |
| OB ↔ 渠道（Tmall/JD） | 每 30min | XXL-Job + channel adapter | 状态同步 |
| Saga 实例 ↔ 订单状态 | 每 5min | XXL-Job SagaRecoveryJob | 修复卡住 Saga |
| 退款状态 ↔ 订单状态 | 每 30min | XXL-Job RefundReconcileJob | 自动推进状态 |

### 4.3 更新 ADR-020 H6 引用

ADR-020 §6 现有 `saga_id + step_name` 唯一键幂等 → **标注迁移路径**到 ADR-030：
> "注：本节的 IdempotentChecker（基於 `saga_id + step_name` 唯一键）将在 ADR-030 全局幂等框架落地后统一迁移至全局框架。迁移过渡期两种方式并行，优先使用 ADR-030 的 `Idempotency-Key` + Redis SET NX 路径。"

---

## 实施计划

| Phase | 内容 | 文件产出 | 人天 |
|-------|------|---------|------|
| **Phase 1** | 多级缓存设计 + 异步削峰 + DB 优化 + 热点防护 + SLA 矩阵 | ADR-040 Part A | 3d |
| **Phase 2** | 99.99% 分解 + Sentinel 阈值 + 断路器 + Dual-Active | ADR-040 Part B + update ADR-027/disaster-recovery-plan/degradation-strategy | 3.5d |
| **Phase 3** | 本地消息表 + 事务消息 + 三层集成 + 对账矩阵 | ADR-040 Part C + ADR-041 + reconciliation-matrix.md | 3d |
| **Phase 4** | 文档联动（C2/C4/C5/H6 修复 + feature-overview + optimization-opportunities） | 修改 6+ 份文档 | 2d |
| **合计** | | | **11.5d** |

---

## 与现有文档的关联

| 文档 | 关联内容 | 变更类型 |
|------|---------|---------|
| ADR-014 热数据缓存 | L1 Caffeine 增加于 L2 Redis 之上 | 🔗 增强引用 |
| ADR-015 容量规划 | Part A §5 SLA 矩阵引用 capacity 基线 | 🔗 增强引用 |
| ADR-016 多 AZ 缓存 | Part B §4 dual-active 增强 ADR-016 预热策略 | 🔗 增强引用 |
| ADR-020 Saga | Part C §3 三层集成 + §4 引用修复 H6 | ✅ 需要修改 |
| ADR-024 慢 SQL | Part A §3.4 索引矩阵引用基线 | 🔗 增强引用 |
| ADR-026 认证授权 | C2 GatewayAuthFilter Order 修复 | ✅ 需要修改 |
| ADR-027 可观测性 | Part B §1.4 更新 SLI 99.95%→99.99% | ✅ 需要修改 |
| ADR-029 网关 | Part B §2 用户服务 Sentinel 阈值 + C2 修复 | ✅ 需要修改 |
| ADR-030 幂等 | Part C §3 三层集成引用 | 🔗 增强引用 |
| ADR-039 订单生命周期 | Part C §3 状态机引擎集成引用 | 🔗 增强引用 |
| disaster-recovery-plan.md | Part B §4.5 修复 C4/C5 引用 + dual-active 模式 | ✅ 需要修改 |
| degradation-strategy.md | Part B §2.4 替换阈值矩阵 + 更新 M15/M16 | ✅ 需要修改 |
| feature-overview.md | §2/§11/§12 更新 | ✅ 需要修改 |
| optimization-opportunities.md | 新增 P1 项"高性能高可用架构" | ✅ 需要修改 |

---

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 99.99% 目标当前基础设施不达标 | 中 | 高 | 先以 99.99% 为复合目标，允许组件级偏差；一季后重新评估 |
| L1 Caffeine 增加 GC 压力 | 低 | 中 | 10k 上限控制，监控 GC 指标；必要时使用 Caffeine 的 weakKeys/softValues |
| event_outbox 增加写延迟 | 低 | 低 | 同库事务无额外网络开销；批量处理 |
| 多 AZ dual-active 增加运维复杂度 | 中 | 中 | 从 AZ-B 30% 开始灰度；先验证只读路径 |
| Sentinel 阈值配置错误导致误限流 | 低 | 高 | 灰度放量 + Pre 环境验证 + 快速回滚（Apollo 5s 生效） |

---

## 上线检查清单

### 前置条件
- [ ] Caffeine L1 内存评估：10k 条目 × 2KB = 20MB，确认 JVM heap 足够
- [ ] Apollo 命名空间 `cache.l1.*` 配置就绪（默认启用 L1，但可通过开关降级）
- [ ] Sentinel 阈值配置 via Apollo Namespace `gateway.sentinel-rules` 就绪
- [ ] event_outbox 表 DDL 准备好（同 OceanBase 实例）

### Phase 1 验证
- [ ] L1 Caffeine 命中率 > 50%（getById 查询）
- [ ] L1 Caffeine 内存占用 < 50MB（含 overhead）
- [ ] L1 降级开关：Apollo `cache.l1.order-detail.enabled=false` → 查询走 L2/L3
- [ ] Keyset 分页在所有分页查询中生效（无 OFFSET 查询）
- [ ] SLA 矩阵各指标在 Prometheus 中可查询

### Phase 2 验证
- [ ] order-core 99.995% SLI 在 Grafana 中可查
- [ ] Sentinel per-service 阈值生效（压测验证超过阈值返回 429）
- [ ] 断路器注册表各 breaker 状态可查
- [ ] 断路器手动触发 → 降级 → 恢复（HALF_OPEN 自动探测）
- [ ] AZ-B 30% 流量正常处理，缓存命中率 > 80%
- [ ] disaster-recovery-plan.md ADR 引用已修正

### Phase 3 验证
- [ ] event_outbox 表在订单同事务中写入
- [ ] RocketMQ 事务消息 + event_outbox 双保险测试：
  - MQ Broker 正常 → 事务消息路径
  - MQ Broker 宕机 → event_outbox 兜底 → XXL-Job 重新投递
- [ ] Saga + 状态机集成：Saga 步骤执行 → 状态机 transition → event_outbox
- [ ] ADR-020 §6/§12 引用已更新

### 回滚方案
- [ ] **L1 缓存**：Apollo `cache.l1.order-detail.enabled=false` → 5s 生效
- [ ] **Sentinel 阈值**：Apollo profile 切换 `normal` → 5s 生效
- [ ] **Dual-Active**：GSLB all traffic to AZ-A (weight 10) → 60s DNS 生效
- [ ] **event_outbox**：XXL-Job 暂停 → MQ 临时改为必达（退化为纯 MQ 路径）
