# 降级策略总图

> 跨 ADR 的降级决策中心。定义了降级等级、组件级降级行为、优先级矩阵和最小可用系统。

---

## 目录

1. [降级等级定义](#1-降级等级定义)
2. [降级优先级矩阵](#2-降级优先级矩阵)
3. [最小可用系统](#3-最小可用系统)
4. [组件降级策略总表](#4-组件降级策略总表)
5. [降级触发与恢复模式](#5-降级触发与恢复模式)
6. [Apollo 降级配置命名空间](#6-apollo-降级配置命名空间)
7. [降级监控指标](#7-降级监控指标)
8. [降级演练与验证](#8-降级演练与验证)
9. [与现有 ADR 的关联](#9-与现有-adr-的关联)

---

## 1. 降级等级定义

将系统降级划分为 **4 个等级**，对应不同的触发条件和影响范围。

| 等级 | 名称 | 含义 | 典型场景 | 影响 |
|------|------|------|---------|------|
| **L0** | 正常运行 | 所有功能正常，全部中间件可用 | 日常 | 无 |
| **L1** | 性能降级 | 非核心组件不可用，性能受损但功能完整 | Redis 宕机、Canal 中断 | 响应变慢 2-5x，功能无缺失 |
| **L2** | 功能受限 | 非核心功能关闭，仅核心链路可用 | 灰度降级、Saga 降 Choreography | 辅助功能（通知、对账）不可用 |
| **L3** | 核心仅存 | 仅保留订单 + 支付核心链路 | Gateway 熔断、部分服务宕机 | 管理端、报表、导出不可用 |
| **L4** | 保护模式 | 数据只读或写入受限，防止数据不一致 | 存储层异常、无法保证一致性 | 写操作受限，仅可查询 |

### 等级间跃迁规则

```
L0 (正常)
  │
  ├── Redis 宕机/缓存失效 ───────────────────→ L1 (性能降级)
  ├── 灰度异常/实例不可用 ──────────────────→ L2 (功能受限)
  ├── Gateway 熔断 / 核心服务宕机 ──────────→ L3 (核心仅存)
  └── 数据层异常/多 AZ 故障 ────────────────→ L4 (保护模式)

各等级回退到 L0 需满足：
  L1 → L0：Redis 恢复 + 缓存重建完成
  L2 → L0：灰度清理完成 / SagaLog 恢复
  L3 → L0：所有核心服务恢复 + 限流熔断关闭
  L4 → L0：存储层完全恢复 + 数据一致性验证通过
```

---

## 2. 降级优先级矩阵

定义在容量不足时，**优先降解什么、保留什么**。

### 保留顺序（从高到低）

| 优先级 | 功能域 | 说明 | 关联组件 |
|--------|--------|------|---------|
| **P0-保留** | 订单创建 + 支付 | 资金流转核心链路，不可降级 | order-core, payment |
| **P0-保留** | 订单查询（核心字段） | 买家和卖家必须能看到订单状态 | order-query, ES |
| **P1-保留** | 库存扣减与回滚 | 库存准确性影响商家运营 | inventory |
| **P1-保留** | 退款流程 | 用户资金安全 | refund |
| **P2-可降级** | 通知推送（站内信/SMS） | 用户暂时收不到通知，无资金损失 | notification, webhook |
| **P2-可降级** | 数据分析/报表 | 商家后台报表延迟生成 | data-report |
| **P3-可降级** | 数据导出 | 批量导出功能暂停 | async-job (export) |
| **P3-可降级** | 管理系统操作日志 | 后台操作记录延迟写入 | audit-log |

### 降级顺序（从先到后）

当系统触发容量预警时，按以下顺序自动执行降级：

```
Step 1: 关闭异步非关键功能
  ─ 数据导出 Job
  ─ 定时报表生成
  ─ 非关键审计日志（降级为本地文件 → 事后归集）

Step 2: 关闭通知类功能
  ─ Webhook 投递（消息入 MQ 但暂停 HTTP POST）
  ─ SMS/邮件通知
  ─ 站内信推送

Step 3: Gateway 限流收紧
  ─ 外部 API 限流阈值下调至 50%
  ─ 管理端 API 限流阈值下调至 30%
  ─ 非核心服务路由熔断阈值降低

Step 4: 缓存层降级
  ─ 非关键缓存提前过期
  ─ 热点缓存写入暂停（只读现有缓存）
  ─ Canal 数据同步暂停（仅保留 binlog 位点）

Step 5: 只读模式
  ─ 所有写操作排队或拒绝
  ─ 仅开放订单查询
```

---

## 3. 最小可用系统

定义系统在极端情况下（L4）必须保留的能力。

### 最小可用功能集

```yaml
must_keep:
  # ── 订单查询 ──
  - api: GET /api/v1/orders/{id}
    desc: 订单详情查询（含基础支付状态）
    storage: OB 直读（跳过 ES 和缓存）
    degrade_path: OB 主键查询（最简路径）
    
  - api: GET /api/v1/orders
    desc: 订单列表查询（限 buyer_id + 时间范围）
    storage: OB SQL（跳过 ES 全文检索）
    degrade_path: 去掉全文检索条件 + 分页降级

  # ── 支付 ──
  - api: POST /api/v1/payments
    desc: 支付发起（含回调接收）
    storage: OB + Redis（需保证一致性）
    degrade_path: 不允许降级（L4 时整个支付链路暂停）

  # ── 退款 ──
  - api: POST /api/v1/refunds
    desc: 退款发起
    storage: OB
    degrade_path: 不允许降级

may_degrade:
  # ── 可以关闭的功能 ──
  - api: GET /api/v1/export
    action: 关闭，返回 "功能暂不可用"
  - api: POST /api/v1/webhooks
    action: 暂停投递，消息保留在 MQ
  - api: GET /api/v1/reports
    action: 关闭，返回 "功能暂不可用"
```

### 中间件降级依赖

```
正常路径                    降级路径
──────────────────────────────────────────────────
Redis 热缓存 → ES           Redis SET → DB 唯一索引
Gateway 限流 → 503           SkyWalking → 本地日志
RocketMQ → 同步调用兜底        Apollo → 本地配置文件
Nacos 注册 → 本地地址列表      Vault → 本地密钥缓存
```

---

## 4. 组件降级策略总表

按组件列出所有降级场景，附触发条件、动作和关联 ADR。

### 4.1 Gateway 层（Internal + External）

> Sentinel 阈值为 per-service 独立配置（参考 ADR-040 Part B §2），通过 Apollo 命名空间 `gateway.sentinel-rules` 动态下发。

| 服务资源 | Sentinel 阈值 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|-------------|---------|---------|------|-----|
| **order-core.createOrder** | QPS > 5000 / RT > 200ms / 并发 > 200 | HTTP 429 + MQ 排队降级 | Sentinel HALF_OPEN 全自动 | L2 | 029,040 |
| **order-core.cancelOrder** | QPS > 1000 / RT > 300ms / 并发 > 100 | HTTP 429 + 拒绝 | Sentinel HALF_OPEN 全自动 | L2 | 029,040 |
| **order-core.payCallback** | QPS > 5000 / RT > 100ms / 并发 > 200 | HTTP 429 + 排队 | Sentinel HALF_OPEN 全自动 | L2 | 029,040 |
| **payment-core.charge** | QPS > 2000 / RT > 1000ms / 并发 > 100 | HTTP 429 + retry hint | Sentinel HALF_OPEN 全自动 | L3 | 029,040 |
| **payment-core.refund** | QPS > 500 / RT > 2000ms / 并发 > 50 | HTTP 429 | Sentinel HALF_OPEN 全自动 | L3 | 029,040 |
| **inventory-core.deduct** | QPS > 5000 / RT > 50ms / 并发 > 200 | HTTP 429 | Sentinel HALF_OPEN 全自动 | L2 | 029,040 |
| **order-query.listByBuyer** | QPS > 10000 / RT > 100ms / 并发 > 300 | HTTP 503（ES overload） | Sentinel HALF_OPEN 全自动 | L1 | 029,040 |
| **order-query.getById** | QPS > 20000 / RT > 50ms / 并发 > 500 | HTTP 503 | Sentinel HALF_OPEN 全自动 | L1 | 029,040 |
| **AppKey 超配额** | 日配额/秒配额超限 | HTTP 429 + 配额耗尽提示 | 次日重置 / 配额调整 | L2 | 025 |
| **后端服务全部不可用** | 健康检查全部失败 | HTTP 503 + 降级提示 | K8s 自动恢复 | L3 | 029 |

### 4.2 缓存层（Redis）

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| Redis 不可用 | 连接异常/超时/命令失败 | 回退 ES 查询 + degrade 计数 | Redis 恢复后自动重连 | L1 | 014 |
| 高延迟 | Canal 延迟 > 10s | Apollo 开关触发，主动缓存失效 | 延迟恢复后关闭开关 | L1 | 014 |
| 熔断 | 连续 50 次 Redis 异常（参见 ADR-014） | 应用层 30s 跳过 Redis 直读 ES | 30s 窗口到期自动重试 | L1 | 014 |
| 多 AZ 缓存未预热 | AZ 切换时预热未完成 | 不等预热即切换，miss 走 ES | 后台继续预热 | L1 | 016 |
| MultiLock 故障 | 跨 AZ 锁获取失败 | 降级为单 AZ 锁 | 下次调用自动重试 | L2 | 016 |

### 4.3 数据同步层（Canal）

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| Canal Server 宕机 | 无心跳 > 30s | Kafka 日志堆积 → 恢复后追赶 | K8s 自动重启 + 位点恢复 | L1 | 013 |
| 位点丢失 | Redis 位点数据丢失 | 逐级降级：RocketMQ offset → 时间戳 → 1h 前位点 | XXL-Job 全量对账修复 | L1 | 013 |
| 消费失败 | 3 次重试仍失败 | 入 retry topic 5 次 → DLQ → XXL-Job 扫描 | SRE 排查修复后重放 | L2 | 013 |
| 全量对账 | 每日定时 Job | OB ↔ ES 全量比对 + 自动修复不一致 | 自动 | L0 (例行) | 013 |

### 4.4 灰度发布层

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| 灰度错误率 > 1% | 5min 窗口 | Apollo grayPercentage=0 | 问题修复后手动恢复 | L2 | 022 |
| 灰度 P99 > 2x 基线 | 5min 窗口 | 自动熔断：灰度比例归零 | 问题修复后手动恢复 | L2 | 022 |
| 灰度实例不可用 | 路由无匹配实例 | GrayTagRouter 降级到稳定版 | 灰度 Pod 扩容后自动恢复 | L1 | 022 |
| 灰度数据异常 | 灰度版本写入了错误数据 | 数据回滚（gray_tag 过滤）+ 灰度熔断 | SRE 数据清理 | L3 | 022 |

### 4.5 分布式事务层（Saga）

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| Saga 协调器不可用 | 节点宕机 | 降级为 Choreography 事件驱动模式 | 节点恢复后切回 | L2 | 020 |
| SagaLog 写失败 | DB 异常 | 降级 Choreography + 本地日志记录 | DB 恢复后重建 Saga 状态 | L2 | 020 |
| 补偿执行失败 | 重试 3-5 次仍失败 | 入死信队列 → SRE 人工处理 | SRE 排查修复 | L3 | 020 |
| 幂等记录表写失败 | Redis+DB 均不可用 | 跳过幂等检查或降级乐观锁 | 中间件恢复后自动复原 | L2 | 030 |

### 4.6 消息队列层（RocketMQ）

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 |
|---------|---------|---------|---------|------|
| RocketMQ 不可用 | Broker 宕机 | 同步调用兜底（RPC 替代异步） | 集群恢复后切回异步 | L2 |
| 关键消息堆积 | 消费延迟 > 5min | 跳过非关键消息 | 消费恢复后追赶上 | L2 |

### 4.7 可观测性层

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| 日志采样降级 | TPS > 100 | 日志降级为 10% 采样 | TPS 回落后恢复 | L1 | 027 |
| SkyWalking 不可用 | Agent 连接失败 | 降级为本地日志 + 采样缓存 | Agent 恢复后重新连接 | L1 | 027 |
| Prometheus 不可用 | 抓取失败 | 本地指标缓存 → 恢复后补推 | Prometheus 恢复后自动 | L1 | 027 |

### 4.8 数据层（OB / ES）

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| OB 分区 Leader 迁移 | 节点故障 | Paxos 自动选主 30s 内完成 | 自动 | L1 | — |
| ES 集群压力过大 | Circuit breaker / GC | 关闭非核心查询 + 降低 shard | 负载恢复后开启 | L2 | 012 |
| 冷数据跨度过大 | 查询超过 180 天 | 拒绝或提示"请缩小时间范围" | 用户调整查询条件 | L4 | 031 |

### 4.9 安全与脱敏层

| 降级场景 | 触发条件 | 降级动作 | 恢复方式 | 等级 | ADR |
|---------|---------|---------|---------|------|-----|
| 数据脱敏降级 | Apollo 开关关闭 masking.enabled | 敏感字段明文返回（脱敏关闭） | Apollo 开关重新开启 | L2/L3 | 023 |

---

## 5. 降级触发与恢复模式

### 5.1 触发方式

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| **自动触发** | Sentinel 规则 / Redis 异常捕获 / 熔断器 | 限流、熔断、缓存降级 |
| **Apollo 开关** | 运维人员推送或自动规则触发 | 灰度熔断、Canal 降级、大促预判 |
| **K8s HPA** | 自动扩缩容 | 资源不足 |
| **手动执行** | SRE 执行降级脚本 | AZ 切换回滚、DB 降级 |

### 5.2 恢复方式

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| **自动恢复** | 条件满足后自动回到正常状态 | Sentinel HALF_OPEN、Redis 重连、HPA 扩容 |
| **定时恢复** | 固定窗口后尝试恢复 | 应用层熔断 30s 窗口、限流阈值周期 |
| **手动恢复** | 需要 SRE 确认后执行 | AZ 回滚、灰度数据清理、DLQ 重放 |
| **SRE 审批** | 高风险操作需审批后执行 | L3→L0 跃迁、数据层恢复 |

### 5.3 降级自动化的分级策略

```yaml
degradation_automation_levels:
  L0->L1: 全自动（不需人工确认）
  L0->L2: 全自动（非关键功能）
  L0->L3: 半自动（Apollo 一键触发，建议人工确认）
  L0->L4: 手动（需 SRE 审批 + 钉钉群确认）
  
recovery_automation_levels:
  L1->L0: 全自动（条件满足即恢复）
  L2->L0: 全自动（中间件恢复后自动复原）
  L3->L0: 半自动（需 SRE 确认所有服务健康）
  L4->L0: 手动（需数据一致性验证）
```

---

## 6. Apollo 降级配置命名空间

所有降级开关统一管理，集中在一个 Apollo Namespace。

```yaml
# Namespace: degrade-control
# 用途：统一降级策略配置，所有降级开关在此管理

# ── 全局开关 ──
degrade:
  global:
    enabled: true                    # 全局降级功能总开关
    level: L0                        # 当前降级等级（L0/L1/L2/L3/L4）
                                     # 设为 L3 时，所有 L3 及以上降级策略生效

# ── 缓存层降级 ──
hot-cache:
  enabled: true                      # 热缓存总开关（关闭后直读 ES）
  degrade-on-high-delay: false       # Canal 延迟高时触发降级
  circuit-breaker-threshold: 50      # 连续异常次数触发熔断
  circuit-breaker-window: 30         # 熔断窗口（秒）

# ── 灰度降级 ──
gray:
  percentage: 10                     # 灰度比例（设为 0 = 熔断）
  max-percentage: 50                 # 最大灰度比例（安全护栏）
  auto-circuit-break: true           # 错误率超限自动熔断
  error-rate-threshold: 0.01         # 自动熔断错误率阈值（1%）
  p99-multiplier: 2.0                # P99 超过基线倍数时熔断

# ── Gateway 降级 ──
gateway:
  external-rate-limit-percent: 100   # 外部限流阈值百分比（%）
  internal-rate-limit-percent: 100   # 内部限流阈值百分比（%）
  circuit-breaker-enabled: true      # Sentinel 熔断总开关

# ── 非核心功能降级 ──
feature:
  webhook-enabled: true              # Webhook 投递开关
  export-enabled: true               # 数据导出开关
  report-enabled: true               # 报表生成开关
  notification-enabled: true         # 通知推送开关

# ── 日志降级 ──
logging:
  sample-rate: 100                   # 日志采样率（%），L1 时降至 10

# ── 数据层降级 ──
data-layer:
  es-read-only: false                # ES 只读模式（L4 时自动设为 true）
  ob-read-only: false                # OB 只读模式（L4 时自动设为 true）
  archive-paused: false              # 数据归档暂停
```

### 配置变更审批

```yaml
# degrade-control 命名空间配置变更审批规则
approval_rules:
  - field: degrade.global.level
    action: L0 → L3/L4 或 L3/L4 → L0
    approver: SRE 负责人
    channel: 钉钉审批（需要在线确认）

  - field: "*.enabled"
    action: 开启 → 关闭（影响核心功能）
    approver: 值班 SRE
    channel: 钉钉群 @值班人

  - field: gray.percentage
    action: 设为 0（熔断）
    approver: 无（自动熔断）
    note: 自动/手动熔断均不需要审批

  - field: degrade.global.level
    action: 任意等级变更
    audit: 自动记录到操作审计日志
    ttl: 保留 180 天
```

---

## 7. 降级监控指标

### Prometheus 指标

```java
@Configuration
public class DegradeMetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> degradeMetrics() {
        return registry -> {
            // 当前降级等级
            Gauge.builder("omplatform_degrade_level", () -> getCurrentLevel())
                .description("当前系统降级等级 (0-4)")
                .register(registry);
            
            // 各组件降级状态（1=降级中, 0=正常）
            Gauge.builder("omplatform_degrade_component_status")
                .tags("component", "redis_cache")
                .description("组件降级状态")
                .register(registry);
            
            // 降级触发次数
            Counter.builder("omplatform_degrade_trigger_total")
                .tags("component", "unknown", "reason", "unknown")
                .description("降级触发次数")
                .register(registry);
            
            // 降级持续时间
            Histogram.builder("omplatform_degrade_duration_seconds")
                .tags("component", "unknown")
                .description("降级持续时间（秒）")
                .register(registry);
        };
    }
}
```

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `omplatform_degrade_level` | Gauge | — | 当前系统降级等级 0-4 |
| `omplatform_degrade_component_status` | Gauge | component | 各组件降级状态（0=正常, 1=降级） |
| `omplatform_degrade_trigger_total` | Counter | component, reason | 降级触发次数累计 |
| `omplatform_degrade_duration_seconds` | Histogram | component | 降级持续时间分布 |
| `omplatform_degrade_auto_recover_total` | Counter | component | 自动恢复次数 |

### 告警规则

```yaml
groups:
  - name: degrade-alerts
    rules:
      # P0: L3 及以上降级
      - alert: DegradeLevelCritical
        expr: omplatform_degrade_level >= 3
        for: 1m
        labels: { severity: P0 }
        annotations:
          summary: "系统进入 L{{ $value }} 降级状态"
          description: "当前降级等级 {{ $value }}，请立即确认影响面"

      # P1: L1-L2 降级
      - alert: DegradeLevelWarning
        expr: omplatform_degrade_level >= 1
        for: 5m
        labels: { severity: P1 }
        annotations:
          summary: "系统进入 L{{ $value }} 降级状态"

      # P1: 降级触发频繁
      - alert: DegradeFrequent
        expr: rate(omplatform_degrade_trigger_total[10m]) > 10
        labels: { severity: P1 }
        annotations:
          summary: "降级触发频繁（10min {{ $value }} 次）"

      # P2: 组件持续降级 > 30min
      - alert: DegradeStuck
        expr: omplatform_degrade_component_status == 1
        for: 30m
        labels: { severity: P2 }
        annotations:
          summary: "组件 {{ $labels.component }} 降级已持续 30min"
```

### Grafana 看板

建议在原有看板中新增 **"系统降级总览" Panel**：

```
+---------------------------------------------------------------+
| 降级总览                         当前等级: L1  (降级中 23m)     |
+---------------------------------------------------------------+
| 组件降级状态:                                                 |
|   Redis Cache   ████████████████████████████  降级中 23m      |
|   Canal Sync    ████████████████████████████  降级中 23m      |
|   Webhook       ████████████████████████████  正常            |
|   Gray Release  ████████████████████████████  正常            |
+---------------------------------------------------------------+
| 降级趋势 (24h):                                               |
|   ╭────────╮         ╭──────╮                                |
| ──╯        ╰─────────╯      ╰──                              |
|   L0        L1               L0                               |
+---------------------------------------------------------------+
```

---

## 8. 降级演练与验证

### 8.1 常态化演练计划

| 演练场景 | 频率 | 验证点 | 工具 |
|---------|------|--------|------|
| Redis 集群宕机 | 季度 | 缓存自动降级 ES、恢复后自动重连 | chaosblade / gameday |
| Gateway 熔断 | 季度 | Sentinel 熔断触发、HALF_OPEN 恢复 | chaosblade / 慢调用注入 |
| Canal Server 宕机 | 季度 | 位点恢复正确性、XXL-Job 对账修复 | chaosblade / 进程 kill |
| 灰度熔断 | 版本发布前 | 错误率超限自动熔断、降级到稳定版 | 灰度压测 |
| AZ 整体故障 | 半年 | GSLB 切换 + 缓存预热 + 全链路可用 | gameday 剧本 |
| ES 集群不可用 | 季度 | 查询降级到 OB、非核心查询关闭 | chaosblade |
| Saga 协调器宕机 | 季度 | 自动降级 Choreography、恢复后重建 | chaosblade |
| RocketMQ 不可用 | 季度 | 同步调用兜底是否生效 | chaosblade |

### 8.2 演练验证清单

```yaml
validation_items:
  - scenario: "Redis 宕机"
    checks:
      - "所有接口仍正常返回（降级 ES）"
      - "响应时间保持在 < 500ms（ES 平均耗时）"
      - "omplatform_degrade_component_status{component=\"redis_cache\"} == 1"
      - "omplatform_degrade_trigger_total{component=\"redis_cache\"} 正确累加"
      - "Redis 恢复后 30s 内自动回到正常状态"
      
  - scenario: "Gateway 熔断"
    checks:
      - "熔断触发时返回 HTTP 503 + JSON 降级提示"
      - "熔断期间后端服务零流量（DB TPS 降为零）"
      - "HALF_OPEN 探测成功后全自动恢复"
      - "omplatform_gateway_circuit_break_total 正确累加"
      
  - scenario: "灰度熔断"
    checks:
      - "错误率 > 1% 后 Apollo grayPercentage 自动设为 0"
      - "灰度请求降级到稳定版后功能仍正常"
      - "gray_route_degradation_total 正确累加"
```

---

## 9. 与现有 ADR 的关联

| 文档 | 关系 |
|------|------|
| **ADR-029** (内部 Gateway) | Sentinel 限流 + 熔断规则定义；DegradeFallbackFilter 降级响应 |
| **ADR-025** (外部 Gateway) | 外部 API 限流配额降级 |
| **ADR-014** (热缓存加速) | Redis 不可用/高延迟/高基数触发的四级降级策略 |
| **ADR-016** (多 AZ 缓存) | AZ 切换时缓存预热降级 + MultiLock 降级 |
| **ADR-022** (全链路灰度) | 五层灰度熔断机制 + GrayTagRouter 降级路由 |
| **ADR-023** (数据脱敏) | Apollo 开关关闭 masking.enabled 触发数据脱敏降级 |
| **ADR-020** (Saga 事务) | Saga 协调器降级到 Choreography |
| **ADR-013** (Canal 高可用) | 位点丢失的四级降级 + 消费失败死信兜底 |
| **ADR-030** (幂等框架) | Redis 不可用时降级 DB 唯一索引 |
| **ADR-026** (认证授权) | Token 校验降级（灰度期间兼容旧版本） |
| **ADR-027** (可观测性) | 日志采样降级 + SkyWalking 降级 |
| **ADR-031** (数据生命周期) | 冷数据跨度过大时查询拒绝 |
| **ADR-032** (本地开发环境) | Apollo 不可用时降级本地配置文件 |
| **ADR-040** (高性能高可用) | per-service Sentinel 阈值矩阵（取代通用降级条件）；断路器三层体系；可用性目标分解 |
| **ADR-015** (容量规划) | HPA 弹性伸缩作为容量层面的降解手段 |
| **canary-release.md** | 5 批灰度发布 + 自动回滚规则 |
| **deployment.puml** | 同城三中心物理部署 — AZ 级故障不降级，靠冗余 |
| **security.puml** | 安全架构四层防御 — 安全组件降级需审批 |
