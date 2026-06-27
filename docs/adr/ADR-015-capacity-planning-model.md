# ADR-015：性能容量规划模型

## 状态

已接受

---

## 背景

### 现状分析

当前架构文档中提到了压测目标和简单的资源估算，但缺乏系统化的容量评估模型和标准化的压测流程。各服务 Pod 的资源配置、HPA 阈值、链路容量依赖关系均为人工经验估计，未形成可复用的方法论。

```
当前容量规划流程（改造前）：

业务提出大促目标（如：日订单 100 万）
        │
        ▼
各团队独立评估所需资源
  ├── order-core: "先给 4 Pods，压测再看"
  ├── payment: "高峰 TPS 大概 2000？给 6 Pods 吧"
  ├── inventory: "Redis 扛得住，2 Pods 够了"
  └── order-query: "ES 集群压力大，扩容几个节点"
        │
        ▼
上线前匆忙压测 → 发现问题 → 紧急扩容
        │
        ▼
复盘 → 结论："下次早点压测"
```

这种模式存在显著问题：

1. **缺乏量化模型**：从业务目标（日订单量）到资源需求（Pods/节点数）的推导过程依靠直觉，没有数学公式支撑
2. **各自为政**：每个服务独立评估容量，忽略了上下游依赖的级联效应（某个链路瓶颈可能不在自身而在下游）
3. **压测后置**：压测在接近上线前才进行，发现问题后调度和扩容时间窗口紧张
4. **无水位线**：CPU/内存/连接数等指标没有明确的「安全水位线」定义，扩容/缩容决策靠应急响应

### 存在的问题

**问题 1：容量评估凭经验而非公式**  
当前从「业务目标 TPS」到「所需 Pod 数」的推导过程不透明。同样日订单 100 万，A 团队估算 4 Pods、B 团队估算 8 Pods，差异 2 倍但没有标准来判定孰对孰错。

**问题 2：链路瓶颈不自知**  
每个服务独立核算容量，但实际瓶颈往往在下游依赖：
- order-core 看似 TPS 足够，但下游 inventory 的 Redis 热点分片已到极限
- payment 调用外部三方支付，对方限频 1000 TPS 成为实际瓶颈
- order-query 给 ES 的查询 QPS 超过 ES 集群承载能力

**问题 3：压测成本高、复用性差**  
每次大促重头准备压测数据、搭建压测环境、编写压测脚本，缺少模板化的压测资产。不同业务线的压测结果格式不统一，难以横向对比。

**问题 4：弹性伸缩缺乏业务语义**  
当前 HPA 仅依赖 CPU/Memory 指标，但 Java 服务在流量上升时 CPU 反应滞后（GC 抖动、JIT 预热），导致扩缩容滞后于实际流量变化。

**问题 5：缺少资源预算视角**  
运维团队在资源规划时，需要一份「大促资源预算表」来提前采购/预留云资源。当前缺少从业务目标到资源预算的标准化转换器。

### 当前数据

以日订单 100 万基线为例：

| 指标 | 当前估值 | 说明 |
|------|---------|------|
| 核心交易链路 TPS | ~1160 TPS | 100万 / 86400s ≈ 11.6 TPS 均值，峰值按 100× 计算 |
| order-core 单机 QPS | ~2000 | 4C8G Pod，Dubbo 协议 |
| payment 单机 QPS | ~1500 | 含外部 HTTP 调用（三方支付延迟增加耗时） |
| inventory 单机 QPS | ~3000 | 纯 Redis 操作，无外部依赖 |
| ES 集群承载 QPS | ~5000 | 当前集群（6 节点）日常查询 ~2000 QPS |
| MQ 生产 TPS | ~2000 | 单 Topic 64 分区，无瓶颈 |
| 数据库连接数上限 | ~100 | 单节点 HikariCP 连接池上限 |

---

## 决策

建立系统化的**容量规划模型**，包含三大部分：

1. **容量计算公式**：从业务目标到资源需求的标准化推导流程，覆盖核心交易链路
2. **压测 SOP**：可复用的压测流程和模板，每次大促按 SOP 执行
3. **弹性伸缩策略**：基于业务指标 + 资源指标的混合 HPA 策略

### 理由

| 维度 | 评估 |
|------|------|
| **效果** | 大促容量缺口从「压测才发现」提前到「需求阶段就明确」，降低线上过载风险 |
| **成本** | 低 — 主要是文档 + 压测脚本 + HPA 配置变更，无基础设施改造成本 |
| **复用性** | 容量模型可复用于所有业务线，压测模板一次建设多次使用 |
| **风险** | 无 — HPA 的 scale-down 有稳定窗口保护，不会引入新风险 |
| **ROI** | 高 — 一次建设投入，每次大促节省 3-5 人天的容量评估 + 紧急扩容时间 |

---

## 详细设计

### 1. 容量模型公式

#### 1.1 核心公式

```
所需资源 = (业务目标量 × 峰值集中系数 × 安全系数) ÷ 单机处理能力
```

**参数说明：**

| 参数 | 含义 | 默认值 | 推导依据 |
|------|------|--------|---------|
| `业务目标量` | 日订单量 / 日活跃用户 / 日查询次数 | 100 万 | 业务运营目标 |
| `峰值集中系数` | 高峰期流量占总量的比例及时间窗口 | 见下表 | 历史流量分布统计 |
| `安全系数` | 预留缓冲容量应对突发流量 | 3（核心链路）/ 2（非核心） | 行业惯例（Google SRE 建议 ×2~×4） |
| `单机处理能力` | 单 Pod 在 P99 达标前提下的最大 QPS | 见 1.3 节 | 压测标定 |

**峰值集中系数计算：**

```
场景：大促流量集中在 2 小时内

  峰值系数 = 高峰流量占比 / 高峰小时数换算为秒

  日订单 100 万，80% 集中在 2 小时：
    高峰 TPS = 1,000,000 × 80% / 7200s ≈ 111 TPS
    峰值集中系数 = 111 / (1,000,000 / 86400) ≈ 9.6

  更严格：按秒级尖峰（秒杀场景）：
    秒杀峰值 TPS = 目标并发数 / 秒杀持续时间
    例如：1 万库存、30 秒抢完 → 333 TPS
    峰值集中系数 = 333 / 11.6 ≈ 28.7
```

**推荐系数矩阵：**

| 场景 | 高峰窗口 | 集中比例 | 峰值集中系数 | 说明 |
|------|---------|---------|-------------|------|
| 日常（平峰） | 全天均匀 | - | 2~3 | 日常流量波动 |
| 日常（早高峰） | 10:00-12:00 | 60% | 4~6 | 上午下单高峰 |
| 日常（晚高峰） | 19:00-22:00 | 70% | 5~8 | 晚间下单高峰 |
| 大促（秒杀） | 30s~5min | 90% | 50~200 | 秒杀/限时抢购 |
| 大促（开门红） | 1h | 80% | 15~20 | 大促首小时 |

#### 1.2 分层容量推导

```
                业务目标
            ┌──────┴──────┐
            │ 日订单 100 万 │
            └──────┬──────┘
                   │
            ┌──────┴──────┐
            │ 目标 TPS 计算 │
            │ 峰值 TPS =   │
            │ 100万×80%/7200│
            │ × 安全系数 3  │
            │ = 333 TPS    │
            └──────┬──────┘
                   │
      ┌────────────┼────────────┬────────────┐
      ▼            ▼            ▼            ▼
  order-core    payment     inventory    order-query
  333 TPS      333 TPS      333 TPS      333 TPS
  × 1.1(内部)   × 1.0        × 3(读多)    × 2(列表+详情)
  ≈ 367 TPS    ≈ 333 TPS    ≈ 1000 TPS   ≈ 667 TPS
      │            │            │            │
      ▼            ▼            ▼            ▼
  单机 2000 QPS  单机 1500 QPS 单机 3000 QPS 单机 800 QPS
  ↓             ↓             ↓             ↓
  ~1 Pod        ~1 Pod        ~1 Pod        ~1 Pod
  保底 4 Pods    保底 4 Pods    保底 2 Pods    保底 4 Pods
```

**各服务单机处理能力基线（4C8G Pod，需实际压测标定）：**

| 服务 | 单机 QPS | 基准测试场景 | 瓶颈点 |
|------|---------|------------|--------|
| **order-core** | ~2000 | Dubbo 创建订单接口（INSERT + MQ 生产） | DB 连接池 → CPU |
| **payment-core** | ~1500 | 支付接口（含外部三方 HTTP 调用，平均 200ms） | 外部依赖延迟 → 连接池 |
| **inventory-core** | ~3000 | 纯 Redis 扣减库存（Lua 脚本） | CPU → 网络带宽 |
| **order-query** | ~800 | ES 列表查询（含解析 + 序列化） | ES 响应 → CPU |
| **fulfillment** | ~1000 | 发货 + 状态流转（多步骤事务） | DB 行锁 → MQ |
| **refund-core** | ~500 | 退款流程（含支付网关回退） | 外部支付网关限频 |

> 以上为初次估算值，实际值需通过 Phase 1 的单链路压测标定。标定后更新此表。

#### 1.3 资源预算计算器

```
输入：
  ├── 日订单目标：1,000,000
  ├── 高峰窗口：2h
  ├── 高峰集中度：80%
  ├── 安全系数：3（核心）
  └── 安全系数：2（非核心）

输出 — 核心服务 Pod 数：

  服务         目标 TPS   单机 QPS  所需 Pod  保底(下限)  推荐配置
  ─────────────────────────────────────────────────────────────
  order-core      367     2000       1         4          4
  payment-core    333     1500       1         4          4
  inventory-core  1000    3000       1         2          2
  order-query     667      800       1         4          4
  fulfillment     333     1000       1         2          2
  refund-core      83*     500       1         2          2

  * 退款 TPS 按订单量的 5-10% 估算

输出 — 基础设施容量：

  资源         计算公式                      所需容量
  ─────────────────────────────────────────────────────────────
  数据库连接数   ∑(服务 Pods × 连接池大小)   ~80 连接
  DB TPS        订单写 TPS × 写入放大(2~3)   ~1000 TPS
  ES QPS        查询 TPS × 2(列表+详情)      ~1334 QPS
  Redis QPS     扣减 TPS × 3(读后写)         ~3000 QPS
  MQ TPS        订单 TPS × 事件数(3~5)       ~1500 TPS
  网络带宽      TPS × 请求体大小(2KB)        ~2 MB/s
```

#### 1.4 容量公式使用流程

```
每次大促/业务目标变更时：

Step 1 — 确定业务目标
  "日订单 200 万" / "日活跃买家 50 万"
  来源：运营 OKR / 大促 GMV 目标

Step 2 — 计算目标 TPS
  峰值 TPS = 日订单量 × 集中比例 / 高峰秒数 × 安全系数

Step 3 — 分层推导资源需求
  按 1.2 节分层推导表，逐个服务计算所需 Pod 数

Step 4 — 比对当前水位
  当前集群剩余容量 vs 需求 → 是否存在缺口？

Step 5 — 输出资源预算
  - 新增 Pod 数
  - 需扩容的基础设施（DB 连接数 / ES 节点 / Redis 分片）
  - 外部依赖需提前沟通的限频提升

Step 6 — 压测验证
  按压测 SOP 执行 → 验证容量模型准确性 → 修正参数
```

### 2. 压测 SOP

#### 2.1 总体流程

```
大促压测三阶段：

Phase A — 单链路压测（大促前 4~3 周）
  ── 每服务独立压测，找到单机瓶颈点
  ── 输出：各服务单机 QPS + P99 延迟数据
  ── 目的：标定容量模型中的"单机处理能力"

Phase B — 全链路压测（大促前 3~2 周）
  ── 模拟大促完整流量，所有服务同时压测
  ── 使用影子库隔离压测数据
  ── 目的：发现链路级瓶颈（依赖超时、连接池耗尽等）

Phase C — 混合场景压测（大促前 2~1 周）
  ── 正常流量 70% + 秒杀 20% + 退款 10%
  ── 模拟突发流量（5 分钟流量翻倍）
  ── 目的：验证弹性伸缩策略和降级预案
```

#### 2.2 单链路压测（Phase A）

**目标**：标定每个服务的「单机处理能力」，更新容量模型基线表。

```yaml
# 压测用例模板 — order-core 创建订单接口
test_case: "order-core_create_order"
target: "order-core:20880"
protocol: "dubbo"
method: "com.omplatform.order.api.OrderService.createOrder"

# 压测数据
data_preparation:
  - users: 10000                    # 虚拟买家
  - products: 500                   # 虚拟商品
  - pre_created_orders: 0           # 前置条件（无需预创建）

# 施压配置
ramp_up:
  duration: "2m"                    # 预热 2 分钟（让 JIT 充分编译）
  initial_users: 10                 # 起始并发
  target_users: 200                 # 目标并发

steady_state:
  duration: "5m"                    # 稳态持续 5 分钟
  target_tps: 2000                  # 目标 TPS

# 成功标准
success_criteria:
  p99_latency: "< 200ms"           # P99 延迟要求
  error_rate: "< 0.1%"             # 错误率要求
  cpu_usage: "< 80%"               # CPU 上限
  heap_usage: "< 75%"              # 堆内存上限
  gc_pause: "< 200ms"              # GC 暂停上限
```

**单链路压测流程：**

```
1. 准备数据
   ├── 脚本：prepare-load-test-data.py (生成虚拟买家/商品/订单)
   └── 产物：压测数据集（SQL 文件导出，可复用于后续压测）

2. 部署目标服务
   ├── 指定版本（发布待验证的版本）
   ├── 4C8G 单 Pod（标准规格）
   └── 关闭非必需依赖（日志采样率 1%、链路追踪采样率 10%）

3. 施压 + 监控
   ├── 压力工具：JMeter / Gatling / Locust
   ├── 从低到高逐步加压（100 → 500 → 1000 → 2000 TPS）
   ├── 每档持续 3min，记录：TPS、P99、CPU、Heap、GC
   └── 找到拐点（P99 突然上升或 CPU > 80%）→ 此为单机上限

4. 输出
   ├── 服务名 + 接口
   ├── 单机 QPS 上限（P99 < 200ms 前提下）
   ├── 单机 CPU 上限（%）
   └── 瓶颈说明（如：DB 连接池先到瓶颈，CPU 仅 60%）
```

#### 2.3 全链路压测（Phase B）

**目标**：验证端到端容量，发现依赖瓶颈。

```
环境要求：
  ── 独立压测 K8s Namespace（与生产隔离）
  ── 影子数据库（OceanBase 影子 schema，表结构与生产一致但数据隔离）
  ── 影子 ES 索引（orders-search-loadtest）
  ── 影子 RocketMQ Topic（order_create_loadtest、payment_loadtest 等）
  ── 影子 Redis DB（不同 DB index 或 key 前缀）

数据流：
  压测流量 → Gateway → order-core(影子) → DB(影子) → MQ(影子)
                                                   │
                                          Canal(影子实例)
                                                   │
                                              ┌─────┴─────┐
                                          ES(影子)    Redis(影子)

压测数据准备：
  ├── 买家数据：10 万虚拟买家（生产脱敏后写入影子 DB）
  ├── 商品数据：2 万虚拟商品（覆盖热点商品 + 普通商品）
  ├── 订单数据：50 万历史订单（模拟存量数据）
  └── 预热数据：压测前预热 ES 和 Redis 缓存（HR 热缓存）

施压模型：
  ├── 阶梯加压：500 → 1000 → 2000 → 3000 TPS
  ├── 每档持续 5min（稳定后记录数据）
  ├── 在每档末尾增加 30s 爆发（2× TPS 持续 30s）
  └── 最大 TPS 持续 15min（验证持续稳定性）

观察点：
  ├── 各服务 CPU / 内存 / GC
  ├── DB 连接数 / 活跃会话 / 慢查询
  ├── ES 查询 / 写入 QPS 和延迟
  ├── MQ 生产 / 消费延迟
  ├── Redis 命令耗时 / 内存增长
  └── 外部依赖（三方支付 mock）的响应时间
```

#### 2.4 混合场景压测（Phase C）

**目标**：验证应对真实大促流量组合的能力。

```
流量模型（模拟 618 大促首小时）：

  时段 0-30min（流量爬坡）：
    ├── 正常流量：线性增长至目标 TPS 的 80%
    ├── 秒杀流量：0（尚未开始）
    └── 退款流量：正常比例 5%

  时段 30-31min（秒杀爆发）：
    ├── 正常流量：目标 TPS 的 80%
    ├── 秒杀流量：目标 TPS 的 50%（30s 脉冲）
    └── 退款流量：正常比例 5%
    └── 预期：触发 HPA 扩容 + 秒杀限流

  时段 31-60min（秒杀后平稳）：
    ├── 正常流量：目标 TPS 的 100%
    ├── 秒杀流量：0
    └── 退款流量：上升至 15%（秒杀后退款增加）
    └── 预期：HPA 缩容回归稳定

  时段 60-90min（突发流量 2×）：
    ├── 突发：流量在 5min 内翻倍（模拟运营活动加码）
    └── 预期：触发降级策略、扩容

成功标准：
  ├── 全链路 P99 < 500ms（核心交易接口 < 200ms）
  ├── 错误率 < 0.5%（含降级降级路径）
  ├── 无级联超时（没有因为一个服务慢导致全线雪崩）
  ├── HPA 扩容在 3min 内完成
  └── 降级预案触发后关键接口仍可用
```

#### 2.5 压测数据管理

```
压测数据生命周期：
  ── Phase A 完成后：导出压测数据集（SQL + JSON），归档至 S3
  ── Phase B 完成后：更新影子库快照，下一次压测直接从快照恢复
  ── Phase C 完成后：清理影子环境，释放资源

数据版本管理：
  压测数据集保存在 git LFS 或 OSS：
  load-test-data/
  ├── v1.0-2026Q1/
  │   ├── buyers_100k.sql.gz         # 10 万买家数据
  │   ├── products_20k.sql.gz        # 2 万商品数据
  │   ├── orders_500k.sql.gz         # 50 万历史订单
  │   └── README.md                  # 数据说明 + 加载流程
  └── v2.0-2026Q2/
      └── ...

压测数据加载脚本：
  # 一键加载压测数据到影子环境
  ./scripts/load-test-data.sh --env=staging --dataset=v1.0-2026Q1
```

### 3. HPA 弹性伸缩策略

#### 3.1 混合指标 HPA

当前仅依赖 CPU 的 HPA 在 Java 服务下存在响应滞后问题（CPU 升高时流量已经堆积）。采用「业务指标 + 资源指标」混合策略：

```yaml
# Core service HPA — order-core
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-core-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-core
  minReplicas: 4
  maxReplicas: 20
  metrics:
    # 指标 1：CPU 利用率
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70

    # 指标 2：Dubbo TPS（自定义指标，由 Prometheus Adapter 提供）
    - type: Pods
      pods:
        metric:
          name: order_dubbo_tps
        target:
          type: AverageValue
          averageValue: 1500

    # 指标 3：GC 频率（GC 频繁说明内存压力大）
    - type: Pods
      pods:
        metric:
          name: jvm_gc_pause_seconds_count
        target:
          type: AverageValue
          averageValue: 10    # 每分钟超过 10 次 GC → 扩容

  behavior:
    scaleDown:
      # 缩容稳定窗口：5 分钟 — 避免流量波动导致频繁伸缩
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1            # 每次最多缩 1 个 Pod
          periodSeconds: 60
    scaleUp:
      # 扩容稳定窗口：0（立即响应）
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100          # 每次最多翻倍扩容
          periodSeconds: 15
        - type: Pods
          value: 4            # 至少扩容 4 个 Pod
          periodSeconds: 15
      selectPolicy: Max       # 取两个策略中的较大值
```

#### 3.2 各服务 HPA 配置建议

| 服务 | min/max | CPU 阈值 | 自定义指标 | 缩容稳定窗口 | 说明 |
|------|---------|---------|-----------|------------|------|
| **order-core** | 4/20 | 70% | Dubbo TPS > 1500 | 300s | 核心交易，快速扩容慢速缩容 |
| **payment-core** | 4/16 | 70% | HTTP 连接池使用率 > 70% | 300s | 外部依赖，需关注连接池 |
| **inventory-core** | 2/10 | 80% | Redis 命令延迟 > 10ms | 300s | 纯内存操作，CPU 利用率可更高 |
| **order-query** | 4/20 | 60% | ES 查询延迟 > 50ms | 300s | 查询服务，关注延迟而非吞吐 |
| **fulfillment** | 2/10 | 70% | MQ 消费延迟 > 1000ms | 300s | 异步处理，对突发容忍度较高 |
| **refund-core** | 2/8 | 70% | 退款队列堆积 > 100 | 300s | 退款少但重要，确保有富余 |

#### 3.3 自定义指标接入

```
K8s 自定义指标 → Prometheus Adapter → HPA

数据流：
  order-core Micrometer → Prometheus (order_dubbo_tps_total)
       │
  Prometheus Adapter 查询
       │
       ▼
  /apis/custom.metrics.k8s.io/v1beta1
       │
       ▼
  HPA 轮询 → 计算期望副本数 → 调整 Deployment replicas

Prometheus Adapter 配置（values.yaml）：
  rules:
    default: false
    custom:
    - seriesQuery: 'order_dubbo_tps_total{namespace="prod",pod!=""}'
      resources:
        overrides:
          namespace: { resource: "namespace" }
          pod: { resource: "pod" }
      metricsQuery: 'sum(rate(<<.Series>>{<<.LabelMatchers>>}[1m])) by (<<.GroupBy>>)'
      name: { as: "order_dubbo_tps" }
```

#### 3.4 缩容保护

为防止因流量短暂下跌导致过度缩容，增加缩容保护机制：

```
缩容保护策略：

1. 稳定窗口（stabilizationWindowSeconds）
   ── 核心服务：300s
   ── 非核心服务：180s
   ── 作用：指标低于阈值后等待 N 秒才执行缩容

2. 最低保底 Pods
   ── 见 3.2 表 minReplicas 列
   ── 保底确保服务即使 0 流量也能快速响应突发

3. 缩容速率限制
   ── 每分钟最多缩 1 个 Pod（核心服务）
   ── 每分钟最多缩 2 个 Pod（非核心服务）
   ── 避免一次缩多个 Pod 导致流量波动

4. 自定义指标保护
   ── 即使 CPU 低，如果 TPS 仍在高位 → 不缩容
   ── 等待两个指标都低于阈值才触发缩容
```

### 4. 容量水位线与告警

#### 4.1 安全水位线定义

| 资源 | 安全水位线 | 警戒水位线 | 危险水位线 | 说明 |
|------|-----------|-----------|-----------|------|
| **CPU** | < 60% | 60-80% | > 80% | CPU 长期 > 70% 应扩容 |
| **Heap 内存** | < 60% | 60-80% | > 80% | 接近 90% 可能触发 OOM |
| **GC 暂停** | < 100ms/min | 100-500ms/min | > 500ms/min | Full GC 频率 > 1/min 需排查 |
| **DB 连接数** | < 60% | 60-80% | > 80% | 连接池耗尽会导致请求排队 |
| **DB 活跃会话** | < 30 | 30-50 | > 50 | 活跃会话 > 50 可能出现锁等待 |
| **ES 查询延迟(P99)** | < 50ms | 50-200ms | > 200ms | ES 抖动可能传导到上游 |
| **ES 写入延迟(P99)** | < 200ms | 200-500ms | > 500ms | 写入延迟高说明索引压力大 |
| **MQ 消费延迟** | < 100ms | 100ms-5s | > 5s | 消费延迟需关注消费者是否正常 |
| **Redis 命令耗时** | < 5ms | 5-20ms | > 20ms | Redis 延迟升高通常因大 key 或网络 |
| **网络带宽** | < 50% | 50-80% | > 80% | 带宽打满导致丢包重传 |

#### 4.2 告警规则

```yaml
# Prometheus 告警规则 — 容量相关
groups:
  - name: capacity
    rules:
      # ── 服务级告警 ──
      - alert: HighCpuUsage
        expr: |
          avg by (pod, namespace) (rate(container_cpu_usage_seconds_total[5m]))
          / on(pod, namespace) kube_pod_container_resource_limits{resource="cpu"}
          > 0.8
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "Pod CPU 使用率超过 80%"
          description: "{{ $labels.pod }} CPU 使用率 {{ $value | humanizePercentage }} 持续 5min"

      - alert: HighHeapUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "JVM 堆内存使用率超过 85%"
          description: "{{ $labels.pod }} 堆内存使用率 {{ $value | humanizePercentage }}"

      - alert: HighGcFrequency
        expr: rate(jvm_gc_pause_seconds_count{action="end of major GC"}[5m]) > 0.5
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "Full GC 频率超过每分钟 0.5 次"
          description: "{{ $labels.pod }} Full GC 频率 {{ $value }}次/秒"

      # ── 基础设施告警 ──
      - alert: DbConnectionExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
        for: 2m
        labels: { severity: P1 }
        annotations:
          summary: "数据库连接池使用率超过 80%"
          description: "{{ $labels.pool }} 当前活跃 {{ $labels.hikaricp_connections_active }}/{{ $labels.hikaricp_connections_max }}"

      - alert: EsQueryLatencyHigh
        expr: histogram_quantile(0.99, rate(elasticsearch_search_query_time_seconds_bucket[5m])) > 0.2
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "ES 查询 P99 延迟超过 200ms"
          description: "当前 {{ $value }}s"

      - alert: MqConsumerLagHigh
        expr: rocketmq_consumer_accumulation > 10000
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "MQ 消费者堆积超过 1 万条"
          description: "Topic {{ $labels.topic }} Group {{ $labels.group }} 堆积 {{ $value }}"

      # ── HPA 告警 ──
      - alert: HpaMaxedOut
        expr: kube_horizontalpodautoscaler_status_current_replicas == kube_horizontalpodautoscaler_spec_max_replicas
        for: 10m
        labels: { severity: P2 }
        annotations:
          summary: "HPA 已打满最大副本数"
          description: "{{ $labels.horizontalpodautoscaler }} 已达到最大副本上限，可能容量不足"

      - alert: HpaScaleUpBlocked
        expr: |
          avg by (horizontalpodautoscaler) (
            rate(container_cpu_usage_seconds_total[5m])
          ) / 0.7 > kube_horizontalpodautoscaler_spec_max_replicas * 2000
        for: 3m
        labels: { severity: P2 }
        annotations:
          summary: "HPA 扩容受阻 — CPU 需求超过最大副本容量"
          description: "当前 CPU 需求超过 HPA maxReplicas 的承载能力，需要调整 limit"
```

### 5. 大促保障 SOP

#### 5.1 大促倒计时时间线

```
T-4 周（需求确定期）
  ├── 确定大促目标（GMV / 日订单量 / DAU）
  ├── 运营输出峰值窗口时间（开门红 / 秒杀批次）
  └── 启动容量模型计算 → 输出资源预算

T-3 周（压测准备期）
  ├── Phase A：单链路压测 → 标定单机 QPS 基线
  ├── 准备压测数据（买家/商品/订单数据集）
  └── 搭建影子环境（DB/ES/MQ/Redis 影子实例）

T-2 周（全链路压测期）
  ├── Phase B：全链路压测 → 发现链路瓶颈
  ├── 修复发现的瓶颈（连接池、超时配置、限流阈值）
  └── 调整 HPA 参数（阈值 / min/max / 稳定窗口）

T-1 周（混合场景 + 预案验证期）
  ├── Phase C：混合场景压测 → 验证弹性伸缩和降级
  ├── 演练降级预案（Redis 宕机 / ES 降级 / 限流触发）
  ├── 确认所有资源已到位（扩容的 Pod / DB 连接 / Redis 分片）
  └── 输出「大促就绪报告」

T-1 天（战前检查）
  ├── 检查 HPA 状态（minReplicas 是否已调整到预期水位）
  ├── 检查各服务日志级别（调整为 WARN，减少磁盘 IO）
  ├── 检查监控大盘（所有面板数据正常）
  ├── 确认值班人员（排班表 + 呼叫链）
  └── 执行「大促战前检查清单」（见 7.4）

T 天（大促当天）
  ├── 大促前 30min：预热所有缓存（HR 热缓存 / Redis / JIT）
  ├── 大促前 15min：停止所有非关键发布
  ├── 大促开始：监控大盘 + 告警响应
  ├── 大促中：每 15min 确认核心指标健康
  └── 大促后：输出「大促复盘报告」

T+1 天（复盘）
  ├── 对比实际流量 vs 容量模型预测 → 修正模型参数
  ├── 识别容量规划的不足之处
  └── 更新压测数据集（加入本次大促的流量特征）
```

#### 5.2 大促期间值班运行图

```
           值班节奏（24h 三班倒）
           
   ╔══════════════════════════════════════════════╗
   ║           值班指挥塔（SRE + 架构师）          ║
   ║         ┌─────────────────────┐              ║
   ║         │  总控 Dashboard    │              ║
   ║         │  - 全链路 TPS      │              ║
   ║         │  - 各服务 P99      │              ║
   ║         │  - 告警聚合        │              ║
   ║         │  - 扩缩容状态      │              ║
   ║         └─────────┬──────────┘              ║
   ║                   │                          ║
   ║    ┌──────────────┼──────────────┐           ║
   ║    ▼              ▼              ▼           ║
   ║  order-core   payment     infrastructure    ║
   ║  值班人        值班人       值班人 (DBA+网络)  ║
   ╚══════════════════════════════════════════════╝

值班响应分层：
  L1（一线响应）：收到告警 → 确认影响面 → 按预案执行降级
  L2（二线处置）：L1 无法处理 → 技术负责人介入 → 执行代码级修复
  L3（三线应急）：L2 无法处理 → 架构师 + 研发负责人 → 决策重启/回滚

值班通信：
  ── 主群：即时消息群（全员 @here 仅限 P0/P1）
  ── 电话：值班人员保持电话畅通，15min 内响应
  ── 交接：每班次结束前 15min 交接（当前状态/未完成事项/关注点）
```

### 6. 资源预算模板

#### 6.1 大促资源预算表

```
大促名称：618 年中大促
目标：日订单 200 万，峰值 TPS 5000
日期：2026-06-18

┌────────────────────────────────────────────────────────────┐
│                    资源预算表                              │
├────────────┬──────────┬──────┬──────┬───────┬────────────┤
│ 服务        │ 当前 Pod │ 需求  │差额  │ 单价   │ 预算/月   │
│             │ (日常)   │ (大促)│      │ (元/Pod)│ (元)      │
├────────────┼──────────┼──────┼──────┼───────┼────────────┤
│ order-core │    4     │  8   │ +4   │  800  │  3,200    │
│ payment    │    4     │  8   │ +4   │  800  │  3,200    │
│ inventory  │    2     │  4   │ +2   │  800  │  1,600    │
│ order-query│    4     │  10  │ +6   │  800  │  4,800    │
│ fulfillment│    2     │  4   │ +2   │  800  │  1,600    │
│ refund-core│    2     │  2   │  0   │  800  │    0      │
├────────────┼──────────┼──────┼──────┼───────┼────────────┤
│ ES 节点    │    6     │  10  │ +4   │ 3000  │ 12,000    │
│ Redis 分片 │    6     │  8   │ +2   │ 2000  │  4,000    │
│ OB 节点    │    6     │  6   │  0   │ 5000  │    0      │
├────────────┼──────────┼──────┼──────┼───────┼────────────┤
│ 总计       │          │      │      │       │ 30,400    │
└────────────┴──────────┴──────┴──────┴───────┴────────────┘

说明：
  - Pod 单价按 4C8G 规格计算（含调度、日志、监控摊销）
  - ES/Redis 单价按节点的 1/2 月度均摊
  - 预算为大促期间额外资源开销（日常资源不计）
```

#### 6.2 容量模型验证报告

```
大促结束后，对比实际数据与容量模型预测：

服务         目标 TPS  实际峰值  模型预测 Pod  实际 Pod   准确率
─────────────────────────────────────────────────────────────
order-core      833      912          5          6        83%
payment-core    833      765          5          5        93%
inventory-core  2500    2100          3          3        84%
order-query     1667    1800          6          7        86%

差异分析：
  order-core：实际峰值比模型预测高 9.5%，且 DB 连接池先成为瓶颈
    → 修正 order-core 单机 QPS 为 1800（原 2000）

修正动作：
  ├── 更新 1.2 节单机 QPS 基线表
  ├── 更新order-core HPA 自定义指标阈值（1500 → 1200）
  └── 更新资源预算模板中的「准确率」列
```

---

## 容量看板

### Prometheus 指标

```java
// 容量相关 Micrometer 指标
@Bean
public MeterRegistry capacityMeterRegistry() {

    // 各服务 TPS（用于 HPA 自定义指标和容量看板）
    Counter.builder("capacity.tps")
        .tag("service", "order-core")
        .tag("method", "createOrder")
        .register(registry);

    // HPA 水位
    Gauge.builder("hpa.replicas", hpaManager, hm -> hm.getCurrentReplicas("order-core"))
        .tag("hpa", "order-core")
        .tag("type", "current")
        .register(registry);

    Gauge.builder("hpa.desired", hpaManager, hm -> hm.getDesiredReplicas("order-core"))
        .tag("hpa", "order-core")
        .tag("type", "desired")
        .register(registry);

    // 容量饱和度（当前负载 / 最大负载）
    Gauge.builder("capacity.saturation", this, cm -> cm.computeSaturation("order-core"))
        .tag("service", "order-core")
        .description("Capacity saturation ratio (0-1), 1=fully saturated")
        .register(registry);

    return registry;
}
```

### Grafana 看板

```
看板：容量运营总控（Capacity Command Center）

Panel 1 — 全链路 TPS 总览（时序图）
  ── 所有核心服务的当前 TPS 叠加
  ── 维度：按服务分色
  ── 阈值线：各服务的目标 TPS（虚线）
  ── 时间范围：最近 1h / 6h / 24h

Panel 2 — 容量饱和度热力图（时序图）
  ── 每服务一张「饱和度 = 当前 TPS / 目标 TPS」
  ── 颜色编码：绿(< 60%) → 黄(60-80%) → 红(> 80%)
  ── 直观发现接近容量上限的服务

Panel 3 — HPA 副本数（时序图）
  ── 各服务 HPA current vs desired 副本数
  ── 标注：扩容事件（annotations）
  ── 阈值线：maxReplicas（红色虚线到达即告警）

Panel 4 — 资源利用率（仪表盘）
  ── 每个服务的 CPU / 内存 / 连接池使用率
  ── 格式：仪表盘（Gauge），绿黄红三段
  ── 异常值高亮

Panel 5 — 依赖延迟（时序图）
  ── DB / ES / Redis / MQ P99 延迟
  ── 维度：按依赖类型分色
  ── 阈值线：各依赖的安全水位线

Panel 6 — 压测进度（状态面板）
  ── 压测当前阶段（准备中 / 执行中 / 已完成）
  ── 各服务压测状态（未开始 / 进行中 / 通过 / 失败）
  ── 压测参数：当前并发 / 目标 TPS / 已持续时长

Panel 7 — 大促就绪状态（Checklist）
  ── 容量模型已计算 / 资源已到位 / 降级预案已验证
  ── 绿色表示就绪，红色表示未就绪
```

---

## 故障场景与处理

| 场景 | 影响 | 检测方式 | 自动处理 | 手动处理 |
|------|------|---------|---------|---------|
| **服务 CPU > 80%** | 响应变慢，P99 上升 | Prometheus 告警 | HPA 自动扩容 | 检查是否达到 maxReplicas，是则调整上限 |
| **DB 连接池耗尽** | 请求排队，超时失败 | HikariCP 指标告警 | 无（HPA 不感知连接池） | 扩容 Pod + 检查慢查询 |
| **ES 查询延迟 > 200ms** | 订单列表/详情变慢 | ES 指标告警 | 无 | 检查 ES 集群负载，扩容节点 |
| **MQ 堆积 > 1 万** | 异步处理延迟 | 消费者堆积告警 | 无 | 扩容消费者实例 + 检查消费者健康 |
| **HPA maxed out** | 无法继续扩容 | HPA 指标告警 | 无 | 调整 maxReplicas 或扩容集群 |
| **Redis 延迟升高** | 缓存/库存操作变慢 | Redis 指标告警 | 无 | 检查大 key 或热 key，拆分 |
| **流量突增超过预期** | 容量不足，过载 | TPS 告警 + 饱和度告警 | 限流触发 | 执行降级预案，非核心功能降级 |
| **外部依赖限频** | 支付/短信等失败 | 三方接口错误率升高 | 无 | 联系三方提升限额，或本地排队缓冲 |

---

## 实施计划

### Phase 1：容量模型搭建（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 单链路压测 — order-core | 0.5d | 订单创建接口单机 QPS 基线 |
| 单链路压测 — payment-core | 0.5d | 支付接口单机 QPS 基线（含三方 mock） |
| 单链路压测 — order-query | 0.5d | ES 查询接口单机 QPS 基线 |
| 容量模型参数标定 | 0.5d | 更新单机 QPS 基表 + 安全系数确认 |

### Phase 2：压测基础建设（2 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| 压测数据集准备脚本 | 0.5d | generate-load-test-data.py + SQL 数据集 |
| 影子环境搭建（DB/ES/MQ/Redis） | 0.5d | 独立命名空间 + 影子资源 |
| 压测脚本模板（JMeter/Locust） | 0.5d | 3 套压测脚本（单链路/全链路/混合） |
| 压测 CI Job 自动化 | 0.5d | Jenkins Pipeline 一键触发压测 |

### Phase 3：HPA 优化（1.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| Prometheus Adapter 部署配置 | 0.5d | 自定义指标接入 K8s HPA |
| 各服务 HPA YAML 配置 | 0.5d | 6 个服务的 HPA 配置（混合指标） |
| 弹性伸缩测试验证 | 0.5d | 压测触发 HPA 扩容/缩容验证 |

### Phase 4：看板 + SOP 落地（1.5 天）

| 任务 | 工时 | 产出 |
|------|------|------|
| Prometheus 容量告警规则 | 0.5d | 10+ 告警规则配置 |
| Grafana 容量看板 | 0.5d | 7-Panel 运营总控看板 |
| 大促 SOP 文档确定 | 0.25d | 倒计时 5 周时间线 + 值班手册 |
| 降级预案演练脚本 | 0.25d | Redis 宕机 / ES 降级 / 限流触发演练 |

**总计：7 人天**

---

## 上线检查清单

### 容量模型
- [ ] 各核心服务单链路压测完成，单机 QPS 基线已标定
- [ ] 容量模型参数（安全系数 / 峰值集中系数）已确认
- [ ] 资源预算模板可按需生成（大促资源预算表）
- [ ] 容量模型验证流程已建立（大促后对比实际 vs 预测）

### 压测环境
- [ ] 影子 DB schema 与生产一致，压测数据已加载
- [ ] 影子 ES 索引已创建，映射与生产一致
- [ ] 影子 MQ Topic 已创建，消费者指向影子实例
- [ ] 影子 Redis 已配置（不同 DB 或 key 前缀）

### HPA
- [ ] Prometheus Adapter 已部署，自定义指标可查询
- [ ] 各服务 HPA YAML 已配置，混合指标可用
- [ ] 扩容测试验证：压测触发 HPA → Pod 数在 3min 内达到预期
- [ ] 缩容保护验证：流量下降后稳定窗口期内不缩容

### 告警
- [ ] 10+ 条容量告警规则已配置并生效
- [ ] 告警通知渠道已配置（即时消息 / 电话 / 邮件）
- [ ] 告警抑制规则已配置（避免告警风暴）
- [ ] 告警静默窗口已配置（大促期间可批量静默已知告警）

### 大促就绪
- [ ] 大促资源预算已审批，额外资源已到位
- [ ] 降级预案已完成演练（Redis 宕机 / ES 降级 / 限流触发）
- [ ] 值班排班表已确定，呼叫链已测试
- [ ] 大促战前检查清单已执行（日志级别 / HPA 水位 / 缓存预热）

---

## 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §5 部署与容量 | 补充容量模型公式和分层推导流程，替换当前的粗略估算 |
| 架构文档 §5.2 弹性伸缩 | 补充 HPA 混合指标配置和缩容保护策略 |
| 架构文档 §5.4 服务水平目标 | 补充 P99 延迟水位线和容量饱和度定义 |
| ADR-012 ES ILM | ES 查询容量纳入全链路容量模型 |
| ADR-013 Canal HA | Canal 所在 MQ 消费延迟纳入容量监控 |
| ADR-014 热缓存 | Redis 热缓存减少 ES QPS，影响容量模型中的 order-query 所需 Pod 数 |
| optimization-opportunities.md §6 | 本 ADR 是对 P2 #6 的详细展开 |
