# ADR-016：多 AZ 缓存一致性优化

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台采用多 AZ（Availability Zone）部署架构，各 AZ 独立部署完整的服务栈，包括独立的 Redis Cluster。各 AZ Redis 之间不做跨 AZ 实时同步。

```
当前多 AZ 部署模型（改造前）：

         ┌─────────────────┐   ┌─────────────────┐
         │   AZ-A (主)      │   │   AZ-B (备)      │
         │                  │   │                  │
         │  order-core     │   │  order-core     │
         │  payment-core   │   │  payment-core   │
         │  order-query    │   │  order-query    │
         │  ─────────────  │   │  ─────────────  │
         │  Redis Cluster A│   │  Redis Cluster B│
         │  (全量缓存)      │   │  (空/冷启动)    │
         │  Elasticsearch A│   │  Elasticsearch B│
         └─────────────────┘   └─────────────────┘
                   │                       │
                   └──────────┬────────────┘
                              │
                     ┌────────┴────────┐
                     │   SLB/Gateway   │
                     │  (主 AZ 流量)    │
                     └─────────────────┘
```

当 AZ-A 发生故障，流量切换到 AZ-B 时，AZ-B 的 Redis 是「冷」的——没有任何缓存数据。所有请求在短时间内同时穿透到 ES，造成缓存击穿和 ES 压力陡增。

```
AZ 切换时的缓存击穿（改造前）：

  故障前：AZ-A 故障
         │
         ▼
  Gateway 切换流量到 AZ-B
         │
         ▼
  AZ-B 所有请求 → Redis miss → 全部穿透到 ES
         │
         ▼
  ES 瞬间 QPS 飙升（正常 2000 → 瞬间 10000+）
         │
         ▼
  ES 响应变慢 → order-query 超时 → 接口 P99 劣化
         │
         ▼
  缓存逐步回填 → 5-10min 后恢复正常
```

### 存在的问题

**问题 1：AZ 切换后缓存冷启动时间长**  
新 AZ 的 Redis 完全为空，所有买家订单列表缓存需要逐个回填。20 万 DAU 的缓存重建需要 5-10 分钟，期间 ES 承受全部查询压力。

**问题 2：ES 被击穿导致级联故障**  
缓存 miss 导致 ES QPS 突增到平时的 5-10 倍，可能触发 ES 的 circuit breaker 或 GC 抖动，使 ES 本身也变得不稳定。

**问题 3：分布式锁跨 AZ 不透明**  
Redisson 锁默认绑定到当前 AZ 的 Redis Master。AZ 切换后，持有锁的服务在新 AZ 无法识别已有锁，可能导致重复执行（订单重复处理、库存超卖）。

**问题 4：主备 AZ 缓存策略无差异**  
主 AZ 和备 AZ 使用相同的缓存策略（全量缓存、短 TTL）。但备 AZ 仅在容灾时使用，平时处于低负载状态，缓存资源浪费。

**问题 5：跨 AZ 状态迁移缺乏协调**  
AZ 切换时，除了缓存，还有 MQ 消费位点、本地计数器、定时任务状态等需要协调迁移。当前缺少统一的 AZ 切换协调器。

### 当前指标

| 指标 | 当前值 | 说明 |
|------|--------|------|
| AZ 切换 RTO | 5-10min | 缓存冷启动为主要耗时 |
| AZ 切换时 ES QPS 峰值 | ~10000 | 正常 ~2000，切换时 5× |
| 切换期间 P99 延迟 | 500ms-2s | ES 过载导致 |
| 分布式锁感知延迟 | 无保护 | 重复执行风险 |
| 主 AZ Redis 内存 | 24GB (3×8GB) | 全量缓存 |
| 备 AZ Redis 内存 | 24GB (3×8GB) | 几乎空闲 |

---

## 决策

实施**多 AZ 缓存一致性优化**，核心策略：

1. **缓存预热策略**：AZ 切换前/切换时主动预热备 AZ Redis，而非被动等待查询回填
2. **分布式锁跨 AZ 兼容**：基于 Redisson MultiLock 实现跨 AZ 锁感知 + 看门狗续期
3. **主备差异化缓存策略**：主 AZ 全量缓存 + 短 TTL，备 AZ 热数据缓存 + 长 TTL，冷启动时阶梯恢复

### 理由

| 维度 | 评估 |
|------|------|
| **效果** | AZ 切换 RTO 从 5-10min → < 30s |
| **命中率** | 预热后切换瞬间缓存命中率 > 90% |
| **成本** | 中等 — 预热逻辑 + MultiLock 改造 + 协调器组件 |
| **一致性** | 最终一致，与当前单 AZ 模型一致 |
| **风险** | 低 — 预热失败不影响正确性（走 ES 降级） |
| **基础设施** | Redis 和 ES 已有，无新增组件 |

---

## 详细设计

### 1. 整体架构

```
改造后多 AZ 部署模型：

         ┌──────────────────────────────┐
         │       AZ 切换协调器           │
         │  (AZSwitchCoordinator)        │
         │  触发 → 预热 → 切换 → 校验    │
         └────┬────────────┬─────────────┘
              │            │
         ┌────▼─────────┐ ┌▼──────────────┐
         │   AZ-A (主)   │ │   AZ-B (备)   │
         │               │ │               │
         │  Redis A      │ │  Redis B      │
         │  全量缓存 TTL30│ │ 热缓存 TTL 120│
         │  ───────────  │ │  ───────────  │
         │  预热触发器   │ │  WarmUp Agent │
         │  (监听切换)    │ │  (常驻)       │
         └──────┬───────┘ └───────┬───────┘
                │                 │
         ┌──────┴─────────────────┴──────┐
         │       Redisson MultiLock      │
         │  跨 AZ 共享锁状态（通过仲裁）   │
         └───────────────────────────────┘
```

**核心组件：**

| 组件 | 职责 | 部署 |
|------|------|------|
| **AZSwitchCoordinator** | AZ 切换的总协调器：触发预热 → 健康检查 → 切换 → 校验 | 独立服务或 Gateway 插件 |
| **WarmUp Agent** | 备 AZ 常驻缓存预热进程，接收预热指令批量加载缓存 | 每个 AZ 各部署 1 个 |
| **Redisson MultiLock** | 跨 AZ 分布式锁，切换后新主 AZ 能感知旧锁状态 | 应用层依赖 |
| **Switch Probe** | AZ 健康探针 + 切换检测，用于触发预热事件 | Gateway/负载均衡层 |

### 2. 缓存预热策略

#### 2.1 预热触发机制

```
预热触发场景：

主动预热（优先）：
  ── 场景：计划内维护 / AZ 轮换 / 大促前
  ── 触发：运维手动触发或大促自动化脚本
  ── 时机：切换前 15-30min 开始预热
  ── 优势：充足的预热时间，不影响切换 RTO

被动预热（容灾）：
  ── 场景：AZ 故障自动切换
  ── 触发：AZSwitchCoordinator 检测到主 AZ 健康检查失败
  ── 时机：检测到故障瞬间立即启动预热
  ── 限制：预热与切换同时进行，要求预热速度足够快
```

**切换检测机制：**

```java
public class AZSwitchDetector {

    // 每个 AZ 的健康检查端点（基于 Gateway 探针）
    // 当主 AZ 连续 3 次健康检查失败（间隔 5s）→ 判定故障
    // 触发预热 + 切换流程

    private static final int HEALTH_CHECK_INTERVAL_SECONDS = 5;
    private static final int MAX_FAILURE_THRESHOLD = 3;

    // 健康检查逻辑
    public boolean isAZHealthy(String azId) {
        // 1. 检查 K8s Node 健康状态
        // 2. 检查核心服务 readiness probe
        // 3. 检查 Redis Cluster 可达性
        // 4. 检查 ES 集群健康状态
        // 全部通过 → 健康；任一失败 → 不健康
    }

    // 检测到故障时：通知 AZSwitchCoordinator
    public void onAZFailure(String failedAzId, String standbyAzId) {
        // 1. 触发备 AZ 缓存预热（立即）
        warmUpCoordinator.startWarmUp(standbyAzId);

        // 2. 等待预热完成或超时（最多 30s）
        boolean ready = warmUpCoordinator.awaitReady(standbyAzId, 30, TimeUnit.SECONDS);

        // 3. 切换流量
        if (ready) {
            gateway.switchTraffic(standbyAzId);
        } else {
            // 预热未完成 → 仍然切换但记录告警（走 ES 降级）
            gateway.switchTraffic(standbyAzId);
            alarmClient.alert("AZ switch with incomplete warm-up", P2);
        }
    }
}
```

#### 2.2 预热流水线

```
WarmUp Agent 预热流程（按优先级分 3 个梯队）：

┌─────────────────────────────────────────────────────┐
│                 预热指令到达                          │
│  { azId: "AZ-B", reason: "planned_failover" }       │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ Step 1 — 第一梯队：超热数据（最近 15min 活跃买家）   │
│                                                      │
│  查询 ES: {"range": {"gmt_modified": {"gte":         │
│           "now-15m"}}}                               │
│  → 提取活跃 buyer_id                                      │
│  → 批量查询这些买家的订单列表                          │
│  → 写入备 AZ Redis（TTL = 120s）                     │
│  目标：覆盖 60% 的买家                                │
│  预计：3-5s                                          │
├──────────────────────────────────────────────────────┤
│ Step 2 — 第二梯队：热数据（最近 1h 活跃）            │
│                                                      │
│  查询 ES: {"range": {"gmt_modified": {"gte":         │
│           "now-1h"}}}                                │
│  → 提取 buyer_id                                          │
│  → 批量查询 + 写入（跳过 Step 1 已覆盖的 buyer_id）         │
│  目标：覆盖 85% 的买家                                │
│  预计：10-20s                                        │
├──────────────────────────────────────────────────────┤
│ Step 3 — 第三梯队：温数据（最近 24h 非终态订单）      │
│                                                      │
│  分批查询 ES 最近 24h 有订单变更的 buyer_id               │
│  → 写入备 AZ Redis（TTL = 120s）                     │
│  目标：覆盖 95%+ 的买家                              │
│  预计：30-60s（异步/后台执行，不阻塞切换）            │
└──────────────────────────────────────────────────────┘
```

**第三梯队不阻塞切换**：前两个梯队完成（覆盖 ~85% 买家）即可执行切换，第三梯队异步执行完成剩余 15% 的预热。切换后，未覆盖买家的第一次查询走 ES + 自动回填。

#### 2.3 预热限速与保护

```java
/**
 * 预热限速器 — 防止预热请求压垮 ES
 */
@Component
public class WarmUpRateLimiter {

    private final RateLimiter esRateLimiter = RateLimiter.create(500); // ES 查询限速 500 QPS
    private final RateLimiter redisWriteLimiter = RateLimiter.create(2000); // Redis 写入限速 2000 TPS

    public void warmUpBuyer(Long buyerId, WarmUpPriority priority) {
        // ES 查询限速
        esRateLimiter.acquire();
        List<OrderSummary> orders = esQueryBuyerOrders(buyerId);

        // Redis 写入限速
        redisWriteLimiter.acquire(orders.size());
        redisWriter.write(buildKey(buyerId), serialize(orders), 120, TimeUnit.SECONDS);
    }

    // 预热进度追踪
    private final AtomicInteger totalWarmed = new AtomicInteger(0);
    private final AtomicInteger totalTarget = new AtomicInteger(0);

    public Progress getProgress() {
        return new Progress(totalWarmed.get(), totalTarget.get());
    }
}
```

#### 2.4 预热数据一致性

```
预热时的数据一致性保证：

写时差问题：
  预热启动 → ES 查询数据 → Redis 写入完成
  这期间数据可能已变化（新订单创建、状态更新）

应对策略：
  ── TTL 覆盖：预热写入 TTL 120s（比常规 30s 更长）
  ── binlog 覆盖：Canal cache-writer 正常写入备 AZ Redis
      （Cache-writer 双写主备两个 Redis Cluster）
  ── 最坏情况：预热数据在 120s 后过期，新查询重新回填
  ── 不影响正确性：预热数据即使旧，也是「旧的正确数据」
```

**Canal cache-writer 的双 AZ 写入：**

```
ADR-013/014 中定义的 cache-writer 增强为双写模式：

Canal binlog → RocketMQ canal-order-binlog
                    │
              ┌─────┴─────┐
              │            │
        cache-writer  cache-writer (standby)
              │            │
         ┌────┴────┐   ┌──┴────┐
         │Redis A  │   │Redis B│
         │(主 AZ)   │   │(备 AZ) │
         └─────────┘   └───────┘

备 AZ 写入策略：
  ── 批量写入（攒批 500ms / 100 条）
  ── 异步模式（不阻塞主 AZ 写入）
  ── 写入失败不重试（备 AZ 预热数据可丢失，由 TTL 覆盖）
  ── TTL = 120s（备 AZ 长 TTL，减少预热期间过期）
```

### 3. 分布式锁跨 AZ 兼容

#### 3.1 问题分析

```
AZ 切换中的锁问题：

场景 1：扣减库存操作
  主 AZ (A) 持有 Redis 锁 "lock:stock:10086"
   └── 处理超时未完成，但看门狗仍在续期
   └── AZ-A 故障，流量切换到 AZ-B
   └── AZ-B 收到扣减同一库存的请求
   └── 锁检查：AZ-B 的 Redis 中没有该锁 → 允许操作
   └── 结果：重复扣减 → 库存超卖 ❌

场景 2：订单定时任务
  order-core 在 AZ-A 持有分布式锁执行定时关单任务
   └── AZ 切换 → AZ-B 启动
   └── AZ-B 检查锁（不存在）→ 也执行关单任务
   └── 结果：重复关单 → 重复退款 ❌
```

#### 3.2 Redisson MultiLock 方案

使用 Redisson MultiLock 实现跨 AZ 锁一致性：锁需要同时在多个 Redis Cluster 上获取才算成功。

```java
/**
 * 跨 AZ 分布式锁 — Redisson MultiLock + 看门狗
 *
 * 锁获取条件：同时在主 AZ 和备 AZ 的 Redis 上获取锁
 * 锁释放条件：只需在持有锁的 AZ 释放（故障时备 AZ 锁自动过期）
 */
@Component
public class CrossAZDistributedLock {

    @Autowired
    private RedissonClient redisAClient;      // AZ-A Redis
    @Autowired
    private RedissonClient redisBClient;      // AZ-B Redis

    private static final long LOCK_WAIT_SECONDS = 3;     // 获取锁等待时间
    private static final long LOCK_LEASE_SECONDS = 30;   // 锁持有时间（看门狗自动续期）
    private static final long WATCHDOG_INTERVAL_MS = 10000; // 看门狗续期间隔

    /**
     * 获取跨 AZ 锁
     * 需要同时在两个 AZ 的 Redis 上获取锁才算成功
     */
    public boolean tryLock(String lockKey) {
        RLock lockA = redisAClient.getLock(lockKey);
        RLock lockB = redisBClient.getLock(lockKey);

        // MultiLock：需要同时在两个 Redis 上获取锁
        RedissonMultiLock multiLock = new RedissonMultiLock(lockA, lockB);

        try {
            // waitTime: 3s — 获取锁的最大等待时间
            // leaseTime: 30s — 锁自动释放时间（看门狗自动续期）
            return multiLock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放跨 AZ 锁
     */
    public void unlock(String lockKey) {
        RLock lockA = redisAClient.getLock(lockKey);
        RLock lockB = redisBClient.getLock(lockKey);
        RedissonMultiLock multiLock = new RedissonMultiLock(lockA, lockB);

        if (multiLock.isHeldByCurrentThread()) {
            multiLock.unlock();
        }
    }

    /**
     * AZ 切换时的锁迁移
     *
     * AZ-A 故障后：
     * 1. AZ-A 的 Redis 锁可能仍然存在（看门狗因故障停止续期 → 自动过期）
     * 2. AZ-B 的 Redis 锁在切换前已被 MultiLock 持有
     * 3. 切换后 MultiLock 在新主 AZ 重新持有 → 检查时发现锁仍被持有 → 不会重复执行
     *
     * 为什么不阻塞切换？
     *   AZ-A Redis 锁在 LOCK_LEASE_SECONDS(30s) 后自动过期
     *   如果切换耗时 > 30s，锁已释放 → 新 AZ 可以安全重入
     *   如果切换耗时 < 30s，锁仍在 → 新 AZ 不重复执行
     */
}
```

#### 3.3 AZ 切换锁状态处理

```
AZ 切换时锁状态迁移：

时间线：

T=0s   AZ-A 持有锁 "lock:stock:10086"（MultiLock 在两个 Redis 上均持有）
T=5s   AZ-A 故障
T=8s   AZSwitchDetector 判定 AZ-A 故障
T=10s  触发预热（AZ-B Redis 已有所需锁的一半，另一半在故障 AZ-A 上）
T=15s  流量切换到 AZ-B
         ── 新请求到达，尝试获取同一锁
         ── MultiLock 尝试在 AZ-B Redis 上获取锁
         ── 如果 T=0s 的看门狗仍在续期：AZ-B 上的锁还在 → 获取失败
         ── 如果 T=15s 已经超过 leaseTime 且看门狗未续期：AZ-B 上的锁已释放 → 获取成功

关键设计决策：
  锁的续期（看门狗）仅发生在持有锁的 AZ
  AZ-A 故障后 → 看门狗线程死亡 → 锁自动过期（30s max）
  新 AZ 在锁过期后即可正常获取

  ⚠️ 30s 的锁保护窗口意味着：
    ── 重复执行的保护窗口为 30s
    ── 超过 30s 后新 AZ 可以安全重入
    ── 适用于库存扣减、订单处理等场景（操作通常 < 5s）
```

#### 3.4 不使用 MultiLock 的备用方案

```
备用方案：锁状态共享 + 优雅过期

如果 MultiLock（双 AZ 同时获取锁）的延迟开销不可接受：

方案 B — Redis 共享仲裁：
  ── 使用独立的仲裁 Redis（第 3 个 Redis Cluster，仅用于锁）
  ── 锁获取：只需在仲裁 Redis 上获取，两个业务 Redis 不参与
  ── 锁检查：所有 AZ 查询同一个仲裁 Redis
  ── 优点：延迟低（单次 Redis 操作），跨 AZ 透明
  ── 缺点：引入仲裁 Redis 的单点（需要 Cluster）

方案 C — 业务层幂等：
  ── 不依赖分布式锁，靠业务幂等防御
  ── 库存扣减：Redis Lua 脚本保证扣减原子性（不会超卖）
  ── 关单：检查订单状态 + CAS 更新（订单已关不重复关）
  ── 退款：检查退款流水表（记录幂等键）
  ── 优点：架构简单，不需要跨 AZ 锁
  ── 缺点：需要逐一验证每个操作的幂等
```

### 4. 主备差异化缓存策略

#### 4.1 策略对比

| 维度 | 主 AZ | 备 AZ | 理由 |
|------|-------|-------|------|
| **缓存范围** | 全量热数据（所有活跃 buyer_id） | 缩减热数据（最近 1h 活跃 buyer_id） | 备 AZ 平时无流量，无需完整缓存 |
| **TTL** | 30s（常规） | 120s（长 TTL） | 备 AZ 长 TTL 减少刷新频率和资源消耗 |
| **更新方式** | Canal binlog 实时刷新 | Canal binlog 批量刷新（攒批 2s） | 备 AZ 可接受更大延迟 |
| **内存预算** | ~2.6GB 大促 | ~1GB（40%） | 备 AZ 只需覆盖核心买家 |
| **逐出策略** | allkeys-lru | volatile-lru（只逐出带 TTL 的 key） | 备 AZ 保留未设 TTL 的预热元数据 |
| **预热** | 启动 / 定时预热（常规策略） | 切换前主动预热（本 ADR 重点） | 备 AZ 预热由切换事件驱动 |

#### 4.2 备 AZ 缓存裁剪策略

```
备 AZ 缓存范围计算公式：

  活跃买家数 × 买家缓存大小 × 覆盖比例

  活跃买家数（最近 1h）：≈ 5 万（DAU 20 万的 25%）
  单个买家缓存大小：≈ 3KB（20 条 × 150 bytes）
  覆盖比例：85%（Step 1 + Step 2 预热覆盖）

  备 AZ 缓存大小：5万 × 3KB × 85% ≈ 130MB

  加上 Redis overhead：≈ 200MB
  （远小于主 AZ 的 700MB-2.6GB）
```

**备 AZ 缓存裁剪实现：**

```java
/**
 * 备 AZ 缓存写入策略
 * 只缓存最近 1h 有活跃操作的买家订单
 */
@Component
public class StandbyCacheStrategy {

    private static final long STANDBY_ACTIVE_THRESHOLD_MINUTES = 60; // 备 AZ 只缓存 1h 活跃
    private static final long STANDBY_TTL_SECONDS = 120;

    public boolean shouldCacheInStandby(CanalBinlogEvent event) {
        // 备 AZ 的判断条件比主 AZ 更严格
        // 只缓存最近 1h 有活跃的买家

        Date gmtModified = event.getAfterColumn("gmt_modified", Date.class);
        if (gmtModified == null) return false;

        long elapsed = System.currentTimeMillis() - gmtModified.getTime();
        return elapsed < TimeUnit.MINUTES.toMillis(STANDBY_ACTIVE_THRESHOLD_MINUTES);
    }

    public long getStandbyTtlSeconds() {
        return STANDBY_TTL_SECONDS; // 备 AZ 长 TTL
    }
}
```

#### 4.3 切换后的缓存渐进恢复

```
切换完成后，备 AZ → 新主 AZ 的缓存策略恢复：

Phase 1 — 切换后 0-5min（过渡期）：
  ── 缓存策略：维持备 AZ 策略（裁剪 + 长 TTL）
  ── Canal cache-writer 降级为单写（只写当前 Redis）
  ── 预热 agent 继续完成第三梯队预热

Phase 2 — 切换后 5-30min（恢复期）：
  ── TTL 从 120s 逐步降低到 60s → 30s（每 5min 降低一级）
  ── 缓存范围从「最近 1h」扩展到「最近 24h 非终态」
  ── 预热 agent 停止，切换为常规 Canal 刷新

Phase 3 — 切换后 > 30min（稳态）：
  ── 完全恢复为「主 AZ 策略」
  ── 缓存范围 + TTL 与原来一致
  ── 原备 AZ 正式升为主 AZ

配置化切换策略：
  az.switch.cache.phase1-duration-minutes = 5
  az.switch.cache.phase2-duration-minutes = 25
  az.switch.cache.initial-ttl-seconds = 120
  az.switch.cache.target-ttl-seconds = 30
```

### 5. AZ 切换协调器

#### 5.1 AZSwitchCoordinator 设计

```java
/**
 * AZ 切换协调器 — 统一编排预热 → 切换 → 恢复全流程
 */
@Component
public class AZSwitchCoordinator {

    @Autowired
    private WarmUpAgent warmUpAgent;
    @Autowired
    private TrafficSwitch trafficSwitch;
    @Autowired
    private LockManager lockManager;
    @Autowired
    private CacheStrategyManager cacheStrategyManager;

    private static final Logger log = LoggerFactory.getLogger(AZSwitchCoordinator.class);

    /**
     * AZ 切换主流程（计划内切换）
     */
    public SwitchResult plannedSwitch(String targetAzId) {
        log.info("Starting planned AZ switch to {}", targetAzId);
        SwitchResult result = new SwitchResult(targetAzId);

        try {
            // Step 1: 预热
            log.info("Step 1: Warming up cache for AZ {}", targetAzId);
            WarmUpProgress progress = warmUpAgent.startWarmUp(targetAzId);
            progress.await(30, TimeUnit.SECONDS); // 等待 30s 或完成
            result.setWarmUpProgress(progress);

            // Step 2: 锁状态迁移
            log.info("Step 2: Migrating lock state");
            lockManager.prepareForSwitch(targetAzId);

            // Step 3: 切换流量
            log.info("Step 3: Switching traffic");
            trafficSwitch.switchTo(targetAzId);
            result.setSwitchCompleted(true);

            // Step 4: 切换后恢复
            log.info("Step 4: Post-switch recovery");
            cacheStrategyManager.applyPhase1Strategy(targetAzId);

            log.info("AZ switch to {} completed successfully", targetAzId);
            return result;

        } catch (Exception e) {
            log.error("AZ switch failed", e);
            result.setError(e.getMessage());
            // 失败时回滚（如果可能）
            rollback(targetAzId);
            return result;
        }
    }

    /**
     * AZ 切换主流程（故障切换 — 快速路径）
     */
    public SwitchResult failoverSwitch(String failedAzId, String targetAzId) {
        log.warn("Starting failover AZ switch from {} to {}", failedAzId, targetAzId);

        // 故障切换走快速路径：不等预热完成
        // Step 1 & 2 并行执行
        CompletableFuture<WarmUpProgress> warmUpFuture =
            CompletableFuture.supplyAsync(() -> warmUpAgent.startWarmUp(targetAzId));

        // 等待最多 10s 让第一梯队预热完成
        WarmUpProgress progress = warmUpFuture.get(10, TimeUnit.SECONDS);

        // 切换（无论预热结果如何）
        trafficSwitch.switchTo(targetAzId);

        // 第三梯队异步执行
        warmUpAgent.continueBackgroundWarmUp(targetAzId);
        cacheStrategyManager.applyPhase1Strategy(targetAzId);

        return new SwitchResult(targetAzId, progress);
    }

    private void rollback(String targetAzId) {
        // 回滚逻辑：保持当前状态，记录失败原因
        log.error("Rolling back AZ switch to {}", targetAzId);
        alarmClient.alert("AZ switch rolled back", P1);
    }
}
```

#### 5.2 切换健康检查

```
AZ 切换前和切换后的健康检查清单：

切换前检查（pre-flight checks）：
  [ ] 目标 AZ 所有服务 readiness 正常
  [ ] 目标 AZ Redis Cluster 可达、容量充足
  [ ] 目标 AZ ES 集群 green 状态
  [ ] 目标 AZ RocketMQ 消费者已启动
  [ ] Canal 实例在目标 AZ 已就绪
  [ ] 预热进度 > 80%（计划内切换）/ > 0（故障切换）

切换后检查（post-flight checks）：
  [ ] Gateway 流量已正确路由到目标 AZ
  [ ] 核心接口（创建订单 / 查询订单）P99 < 200ms
  [ ] 缓存命中率 > 70%
  [ ] 分布式锁在新 AZ 正常工作
  [ ] 无大量连接错误
  [ ] MQ 消费位点正常推进
```

### 6. 监控指标

#### 6.1 Prometheus 指标

```java
// 多 AZ 缓存相关指标
@Bean
public MeterRegistry multiAzMeterRegistry() {

    // AZ 切换事件
    Counter.builder("az.switch.total")
        .tag("type", "planned")
        .tag("type", "failover")
        .description("Total number of AZ switch events")
        .register(registry);

    // 切换耗时
    Timer.builder("az.switch.duration")
        .tag("phase", "warmup")
        .tag("phase", "traffic_switch")
        .tag("phase", "total")
        .description("AZ switch phase duration")
        .publishPercentiles(0.5, 0.99)
        .register(registry);

    // 预热进度（每个 AZ）
    Gauge.builder("az.warmup.progress", warmUpAgent, agent -> agent.getProgress().getPercentage())
        .tag("az", "AZ-A")
        .tag("az", "AZ-B")
        .description("Cache warm-up progress percentage (0-100)")
        .register(registry);

    // 预热覆盖买家数
    Gauge.builder("az.warmup.buyers_covered", warmUpAgent, agent -> agent.getProgress().getBuyersCovered())
        .tag("az", "AZ-A")
        .tag("az", "AZ-B")
        .description("Number of buyers with warmed cache")
        .register(registry);

    // 备 AZ 缓存大小
    Gauge.builder("az.standby.cache_size_bytes", redisManager, m -> m.estimateStandbyCacheSize())
        .tag("az", "AZ-A")
        .tag("az", "AZ-B")
        .description("Standby AZ cache size estimate")
        .register(registry);

    // 跨 AZ 锁持有数
    Gauge.builder("az.lock.held_count", lockManager, m -> m.getActiveLockCount())
        .tag("az", "AZ-A")
        .tag("az", "AZ-B")
        .description("Number of active cross-AZ locks")
        .register(registry);

    return registry;
}
```

#### 6.2 告警规则

```yaml
groups:
  - name: multi_az
    rules:
      - alert: AzSwitchInProgress
        expr: az_switch_total > 0
        labels: { severity: P0 }
        annotations:
          summary: "AZ 切换进行中"
          description: "AZ 切换事件已触发，关注切换时长和预热进度"

      - alert: AzSwitchDurationLong
        expr: az_switch_duration_seconds{phase="total"} > 60
        labels: { severity: P1 }
        annotations:
          summary: "AZ 切换总时长超过 60s"
          description: "切换耗时 {{ $value }}s，检查预热是否正常"

      - alert: AzWarmUpSlow
        expr: az_warmup_progress < 50 and az_switch_duration_seconds{phase="warmup"} > 15
        for: 10s
        labels: { severity: P2 }
        annotations:
          summary: "缓存预热进度偏慢"
          description: "预热 {{ $value }}% 已耗时 > 15s，可能 ES 查询延迟高"

      - alert: StandbyCacheMissHigh
        expr: |
          sum(rate(hotcache_miss_total{az="standby"}[5m]))
          /
          (sum(rate(hotcache_hit_total{az="standby"}[5m])) + sum(rate(hotcache_miss_total{az="standby"}[5m])))
          > 0.5
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "备 AZ 缓存命中率低于 50%"
          description: "备 AZ 命中率 {{ $value | humanizePercentage }}，预热策略可能需要调整"

      - alert: CrossAzLockContention
        expr: rate(az_lock_acquire_failed_total[5m]) > 10
        for: 5m
        labels: { severity: P3 }
        annotations:
          summary: "跨 AZ 锁竞争频繁"
          description: "每秒 {{ $value }} 次锁获取失败，可能需要调整锁超时参数"

      - alert: AzLockHeldLong
        expr: az_lock_held_duration_seconds > 300
        labels: { severity: P2 }
        annotations:
          summary: "分布式锁持有时间超过 5min"
          description: "锁 {{ $labels.lock_key }} 持有超过 5min，可能存在死锁"
```

#### 6.3 Grafana 看板

```
看板：多 AZ 容灾监控（Multi-AZ Dashboard）

Panel 1 — AZ 健康状态（状态面板）
  ── 每个 AZ 的健康状态（绿/黄/红）
  ── 指标：服务 readiness + Redis 可达 + ES 健康
  ── 当前流量分布（AZ-A: 100% / AZ-B: 0%）

Panel 2 — AZ 切换时间线（事件图）
  ── 每次 AZ 切换的时间点、类型（计划/故障）、耗时
  ── 标注各阶段（预热/切换/恢复）耗时

Panel 3 — 预热进度（时序图）
  ── 当前预热百分比（0-100%）
  ── 已覆盖买家数 / 目标买家数
  ── 预热梯队进度（T1 / T2 / T3）

Panel 4 — 主备缓存大小对比（柱状图）
  ── 主 AZ 缓存大小 vs 备 AZ 缓存大小
  ── 按服务分（热缓存、状态缓存、锁等）

Panel 5 — 跨 AZ 锁状态（时序图）
  ── 活跃锁数量
  ── 锁获取成功率（%）
  ── 锁持有时间分布（P50 / P99）

Panel 6 — AZ 切换后恢复状态（时序图）
  ── 切换后缓存命中率恢复曲线
  ── 切换后 P99 延迟恢复曲线
  ── 切换后 ES QPS 变化
```

### 7. 回滚与降级

```
AZ 切换降级和回滚策略：

场景 1：预热缓慢或不完整
  ── 应对：不等预热完成即切换，未命中走 ES 降级
  ── 效果：切换 RTO 不受预热影响，但 ES 压力增大
  ── 回滚：如果切换后 ES 压力过大 → 切回原 AZ（如果原 AZ 已恢复）

场景 2：MultiLock 获取分布式锁失败
  ── 应对：降级为单 AZ 锁（仅在当前 AZ 的 Redis 获取）
  ── 效果：失去跨 AZ 保护，但业务不中断
  ── 恢复：MultiLock 恢复后自动重试跨 AZ 锁

场景 3：切换后缓存命中率持续低
  ── 应对：临场增加 TTL（Apollo 调整 120s → 300s）
  ── 效果：缓存过期变慢，命中率回升
  ── 根本原因：预热数据不足或 Canal 写入延迟

场景 4：切换后新 AZ 不稳定
  ── 应对：切回原 AZ（如果原 AZ 已恢复）
  ── 回滚命令：az-switch-rollback.sh
```

---

## 故障场景与处理

| 场景 | 影响 | 自动处理 | RTO | RPO |
|------|------|---------|-----|-----|
| **AZ 故障 + 预热成功** | 缓存命中率 > 85%，平滑切换 | 预热 → 切换 → 恢复 | < 30s | 0 |
| **AZ 故障 + 预热不完整** | 缓存命中率 50-70%，ES 压力增大 | 不等预热完成，直接切换 | < 15s | 0 |
| **AZ 故障 + 预热失败** | 缓存全部 miss，ES 承压 | 切换 + ES 降级告警 | < 10s | 0 |
| **MultiLock 故障** | 分布式锁失去跨 AZ 保护 | 降级为单 AZ 锁 | 0（即时） | 0 |
| **预热误触发（无切换）** | 备 AZ 多 100-200MB 无用缓存 | TTL 120s 后自动过期 | 0 | 0 |
| **Canal 备 AZ 写入失败** | 备 AZ 缓存逐渐过时 | 标记备 AZ 缓存 stale，预热时重建 | 30s（TTL 过期） | 0~30s |
| **切换后新 AZ 不稳定** | 服务 P99 劣化 | 切回原 AZ（需人工确认原 AZ 已恢复） | 1-5min | 0~30s |

---

## 实施计划

### Phase 1：缓存预热引擎（2.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| WarmUp Agent 开发 | 1d | 三梯队预热流水线 + ES 限速查询 |
| AZSwitchDetector 开发 | 0.5d | 健康检查 + 切换检测 |
| Canal cache-writer 双写备 AZ | 0.5d | 备 AZ Redis 异步写入 |
| 单元测试 + 集成测试 | 0.5d | 覆盖预热/限速/双写/取消场景 |

### Phase 2：分布式锁跨 AZ（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| CrossAZDistributedLock 实现 | 1d | MultiLock 封装 + 看门狗 + 降级逻辑 |
| 锁迁移状态处理 | 0.5d | AZ 切换时锁的持有/释放/过期策略 |
| 单元测试 + 故障模拟 | 0.5d | 覆盖 AZ 故障时锁状态验证 |

### Phase 3：AZ 切换协调器（1.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| AZSwitchCoordinator 实现 | 0.5d | 计划切换 + 故障切换 + 回滚 |
| 切换前后健康检查 | 0.5d | pre-flight + post-flight checks |
| 主备缓存策略差异化 | 0.5d | Phase1/Phase2/Phase3 渐进恢复逻辑 |

### Phase 4：监控 + 演练（1 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| Prometheus 指标 + 告警规则 | 0.5d | 6 条告警规则 + Micrometer 埋点 |
| Grafana 看板 | 0.25d | 6-Panel 多 AZ 监控看板 |
| AZ 切换演练脚本 | 0.25d | 计划切换 + 故障切换演练 |

**总计：7 人天**

---

## 上线检查清单

### 预热能力
- [ ] WarmUp Agent 已部署到各 AZ，预热指令可正常触发
- [ ] 三梯队预热流水线正常（T1 超热数据 / T2 热数据 / T3 温数据）
- [ ] 预热限速正常（ES 500 QPS / Redis 2000 TPS）
- [ ] 预热进度可观测（Prometheus Gauge + 日志）
- [ ] Canal cache-writer 已配置双写备 AZ Redis

### 分布式锁
- [ ] CrossAZDistributedLock 可正常获取/释放 MultiLock
- [ ] 看门狗续期正常
- [ ] AZ 切换后锁状态正确处理（重复执行防护 30s 窗口）
- [ ] MultiLock 故障时自动降级为单 AZ 锁

### AZ 切换协调
- [ ] 计划内切换：预热 → 切换 → 恢复，全流程正常
- [ ] 故障切换：快速路径（不等预热完成）正常
- [ ] 切换前后健康检查正常通过
- [ ] 主备缓存差异化策略渐进恢复正常

### 回滚能力
- [ ] 切换后新 AZ 不稳定 → 可切回原 AZ
- [ ] 预热误触发 → TTL 120s 后自动清理
- [ ] 双写失败不影响主 AZ 写入

### 演练验证
- [ ] 模拟 AZ 故障，启动切换流程
- [ ] 对比切换时 ES QPS 峰值（改前 10000+ → 改后 < 3000）
- [ ] 对比缓存命中率恢复曲线（改前 5min → 改后 < 30s）

---

## 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §6 部署与容灾 | 补充多 AZ 切换的缓存预热策略和分布式锁兼容方案 |
| 架构文档 §6.2 多 AZ 部署 | 补充主备差异化缓存策略和 Canal 双写 |
| ADR-013 Canal HA | Canal cache-writer 双写备 AZ Redis 作为预热补充 |
| ADR-014 热缓存 | 主 AZ 缓存策略保持 ADR-014 设计不变，备 AZ 裁剪为子集 |
| ADR-015 容量模型 | 备 AZ 预热时的 ES QPS 增量纳入容量模型 |
| optimization-opportunities.md §7 | 本 ADR 是对 P2 #7 的详细展开 |
