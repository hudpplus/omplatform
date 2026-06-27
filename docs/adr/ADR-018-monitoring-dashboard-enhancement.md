# ADR-018：业务监控大盘增强

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台已有 4 个 Grafana 看板，覆盖了业务概览、接口监控、JVM/GC 和基础设施维度：

```
现有 Grafana 看板矩阵（改造前）：

┌─ 看板 1：业务全景 ─────────────────────┐
│ 订单量(TPS)、成交量、退款率、响应时间      │
│ 按业务线(business_type)聚合              │
└──────────────────────────────────────────┘
┌─ 看板 2：核心接口监控 ───────────────────┐
│ Dubbo 接口 QPS/P99/P999                  │
│ 订单创建/支付/退款/查询各链路耗时          │
└──────────────────────────────────────────┘
┌─ 看板 3：JVM & GC ──────────────────────┐
│ 堆内存、GC 频率、线程池状态               │
│ 各服务(Pod 粒度)                         │
└──────────────────────────────────────────┘
┌─ 看板 4：基础设施 ──────────────────────┐
│ OB 连接数、ES 集群状态、MQ 堆积           │
│ Redis 内存/命中率、Canal 延迟             │
└──────────────────────────────────────────┘
```

这 4 个看板以「技术视角」为主，面向的是研发和运维人员。但订单中台的运营和风控团队最关心的两个领域——**资金链路**和**数据一致性**——缺少专项看板。

结果就是：资金异常（支付失败激增、退款失败）和数据不一致（ES 与数据库不同步）由业务方发现后再通知研发排查，处于「被动发现」状态。

### 存在的问题

**问题 1：资金链路黑盒化**  
财务团队需要每天手动导出交易数据来核对昨日 GMV、支付成功率、退款率等指标，缺少实时可视化的资金看板。支付失败、回调超时等异常事件也缺少集中时间线展示，排查依赖翻日志。

**问题 2：数据一致性被动发现**  
Canal 同步延迟升高、OB↔ES 数据不一致、消息堆积等问题，当前靠巡检脚本 + 人工登录观察。SRE 团队在用户反馈前往往不知道数据已经不一致了。

**问题 3：看板信息密度低，缺少关联分析**  
现有看板按技术领域切分（JVM、ES、OB），但故障排查时需要跨看板关联。例如用户反馈「订单列表不更新」，需要同时看 Canal 延迟、Redis 缓存更新、ES 索引刷新三个面板——分布在 3 个不同的看板上。

**问题 4：业务层面缺少差异化视角**  
财务关心 GMV 和资金对账，风控关心异常交易模式，运营关心退款率和用户投诉趋势。同一份数据需要不同的呈现维度和聚合粒度，当前所有看板以「系统健康」为唯一视角。

**问题 5：历史趋势对比能力弱**  
看板缺少「同比/环比」参考线。高峰值发生时，无法快速判断「这是正常大促波动」还是「异常突发」，造成不必要的告警疲劳和沟通成本。

### 当前数据

| 指标 | 当前监控方式 | 发现周期 |
|------|------------|---------|
| 实时 GMV | 业务方手动导出 | T+1 |
| 支付成功率 | 业务方手动统计 | T+1 |
| 退款失败 | 客诉反馈 | 小时级 |
| 资金对账差异 | 日终对账脚本 | T+1 |
| Canal 延迟 | 命令行查看 | 被动 |
| OB↔ES 一致性 | 无监控 | — |
| Redis 缓存一致 | 无监控 | — |
| 消息堆积 | Grafana 已有 | 分钟级 |
| 死信队列 | 无监控 | — |

---

## 决策

在现有 Grafana + Prometheus 基础设施之上，新增 **2 个专项 Grafana 看板**：

### 看板 1：资金链路专项看板 — 面向财务 & 风控

涵盖实时 GMV、支付链路健康度、退款趋势、资金对账状态、异常事件时间线，目标是将资金链路可视化从「T+1 手动统计」提升到「实时可视化」。

### 看板 2：数据一致性体检看板 — 面向 DBA & SRE

涵盖 Canal 延迟（分区热力图）、OB↔ES 一致性采样、Redis↔OB 延迟、消息堆积与死信、数据修复任务状态，目标是将数据一致性从「被动发现」转为「主动监控」。

同时，为支撑这两个看板，需补充约 **15-20 个 Prometheus 自定义指标**和相关 **Recording Rules**，以及 **8 条新增告警规则**。

---

## 详细设计

### 1. 整体架构

```
监控指标采集与看板架构（改造后）：

                    业务服务 (order-core/payment/...)
                  ┌─ Micrometer + Prometheus client ─┐
                  │  order.tx.amount (GMV 计数)      │
                  │  order.payment.status             │
                  │  order.refund.count               │
                  │  order.consistency.diff           │
                  └──────────────┬───────────────────┘
                                 │
                  ┌──────────────┴───────────────────┐
                  │         Prometheus Server         │
                  │  ┌─────────────────────────────┐  │
                  │  │ Recording Rules (预聚合)     │  │
                  │  │  - gmv:rate5m               │  │
                  │  │  - payment:success_rate     │  │
                  │  │  - canal:max_lag            │  │
                  │  │  - consistency:diff_count   │  │
                  │  └─────────────────────────────┘  │
                  └──────┬──────────────┬─────────────┘
                         │              │
          ┌──────────────┴──┐   ┌───────┴──────────────┐
          │  Grafana        │   │  AlertManager         │
          │  ┌────────────┐ │   │  ┌─────────────────┐  │
          │  │ 资金链路    │ │   │  │ 支付成功率 < 99% │  │
          │  │ 一致性体检  │ │   │  │ 对账差异 > 0     │  │
          │  │ 原有 4 看板│ │   │  │ Canal 延迟 > 10s │  │
          │  └────────────┘ │   │  │ 一致性检测失败   │  │
          └─────────────────┘   └──────────────────────┘
```

### 2. 资金链路专项看板（Fund Flow Dashboard）

#### 2.1 Panel 规划

| # | 面板 | 图表类型 | PromQL | 刷新频率 |
|---|------|---------|--------|---------|
| 1 | **今日实时 GMV** | Stat（大数字） | `sum(rate(order_tx_amount_total{business_type=~"$business"}[5m]))` | 30s |
| 2 | **GMV 同比/环比** | Stat + 趋势箭头 | 与昨日/上周同时间段对比 | 30s |
| 3 | **GMV 趋势（分钟级）** | Time Series | `sum(rate(order_tx_amount_total[1m])) by (business_type)` | 15s |
| 4 | **支付成功率（每分钟）** | Time Series + Threshold | `sum(rate(order_payment_success_total[1m])) / sum(rate(order_payment_total[1m])) * 100` | 15s |
| 5 | **支付失败原因分布** | Pie / Bar | `sum(rate(order_payment_fail_total[1m])) by (fail_reason)` | 30s |
| 6 | **退款金额趋势** | Time Series | `sum(rate(order_refund_amount_total[1m])) by (business_type)` | 30s |
| 7 | **退款率（日累计）** | Stat | `sum(increase(order_refund_count_total[24h])) / sum(increase(order_created_total[24h])) * 100` | 60s |
| 8 | **资金对账状态** | Table | `order_reconciliation_difference{status="unmatched"}` | 60s |
| 9 | **资金对账差异详情** | Table | `order_reconciliation_detail` | 60s |
| 10 | **交易异常事件时间线** | State Timeline | `order_tx_anomaly{type=~"$anomaly_type"}` | 30s |

#### 2.2 自定义指标

```java
// 需要在 order-core / payment-core 中新增的 Micrometer 指标

// === 交易金额类 ===
// 订单金额（GMV 计算用）
Counter.builder("order.tx.amount")
    .tag("business_type", order.getBusinessType())
    .tag("currency", "CNY")
    .register(meterRegistry)
    .increment(order.getTotalAmount().doubleValue());

// 支付成功/失败计数
Counter.builder("order.payment.status")
    .tag("result", "success")   // success / fail
    .tag("channel", payment.getChannel())  // wechat / alipay / unionpay
    .register(meterRegistry)
    .increment();

// 支付失败原因（细化）
Counter.builder("order.payment.fail_reason")
    .tag("reason", exception.getReason())  // balance_insufficient / timeout / risk_blocked / channel_error
    .register(meterRegistry)
    .increment();

// 退款金额
Counter.builder("order.refund.amount")
    .tag("business_type", order.getBusinessType())
    .tag("reason", refund.getReason())
    .register(meterRegistry)
    .increment(refund.getAmount().doubleValue());

// 退款计数
Counter.builder("order.refund.count")
    .tag("business_type", order.getBusinessType())
    .register(meterRegistry)
    .increment();

// === 异常事件类 ===
// 支付超时
Counter.builder("order.tx.anomaly")
    .tag("type", "payment_timeout")
    .tag("severity", "warning")
    .register(meterRegistry)
    .increment();

// 重复回调
Counter.builder("order.tx.anomaly")
    .tag("type", "duplicate_callback")
    .tag("severity", "critical")
    .register(meterRegistry)
    .increment();

// 退款失败
Counter.builder("order.tx.anomaly")
    .tag("type", "refund_failure")
    .tag("severity", "error")
    .register(meterRegistry)
    .increment();

// 资金对账差异计数
Gauge.builder("order.reconciliation.difference", reconciliationService,
        service -> service.getUnmatchedCount())
    .tag("type", "payment")  // payment / refund / settlement
    .register(meterRegistry);
```

#### 2.3 看板布局（JSON Model 结构示意）

```json
{
  "title": "资金链路专项看板",
  "uid": "order-fund-flow",
  "tags": ["order-platform", "finance", "fund-flow"],
  "time": { "from": "now-6h", "to": "now" },
  "panels": [
    {
      "title": "今日实时 GMV",
      "type": "stat",
      "gridPos": { "h": 4, "w": 4, "x": 0, "y": 0 },
      "datasource": "Prometheus",
      "targets": [{
        "expr": "sum(rate(order_tx_amount_total{business_type=~\"$business\"}[5m]))",
        "legendFormat": "GMV (元/秒)"
      }],
      "thresholds": [
        { "value": null, "color": "green" },
        { "value": 1000000, "color": "yellow" },
        { "value": 5000000, "color": "red" }
      ]
    },
    {
      "title": "支付成功率",
      "type": "timeseries",
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 },
      "fieldConfig": {
        "thresholds": [
          { "value": null, "color": "red" },
          { "value": 99, "color": "yellow" },
          { "value": 99.9, "color": "green" }
        ]
      },
      "targets": [{
        "expr": "sum(rate(order_payment_success_total[1m])) / sum(rate(order_payment_total[1m])) * 100",
        "legendFormat": "成功率 %"
      }]
    },
    {
      "title": "交易异常事件时间线",
      "type": "state-timeline",
      "gridPos": { "h": 6, "w": 24, "x": 0, "y": 18 },
      "targets": [{
        "expr": "order_tx_anomaly{severity=~\"$severity\"}",
        "legendFormat": "{{type}}"
      }]
    }
  ],
  "variables": [
    { "name": "business", "type": "query", "query": "label_values(order_tx_amount_total, business_type)" },
    { "name": "severity", "type": "custom", "options": ["all", "critical", "error", "warning"], "default": "critical" },
    { "name": "anomaly_type", "type": "custom", "options": ["all", "payment_timeout", "duplicate_callback", "refund_failure", "reconciliation_diff"], "default": "all" }
  ]
}
```

#### 2.4 同比/环比计算 (Prometheus Recording Rules)

```yaml
# recording/gmv_comparison.yml
groups:
  - name: gmv_comparison
    interval: 60s
    rules:
      # 今日 GMV（累计，每天 00:00 重置）
      - record: gmv:today
        expr: sum(increase(order_tx_amount_total[24h]))

      # 昨日 GMV（固定昨日全天）
      - record: gmv:yesterday
        expr: sum(increase(order_tx_amount_total[24h] offset 24h))

      # 上周同日 GMV
      - record: gmv:last_week
        expr: sum(increase(order_tx_amount_total[24h] offset 168h))

      # GMV 环比增长率（%）
      - record: gmv:change_rate_vs_yesterday
        expr: (gmv:today - gmv:yesterday) / gmv:yesterday * 100
```

#### 2.5 资金对账面板详细设计

对账数据来源为 `order-reconciliation-service`，该服务每日凌晨执行对账任务：

```
对账流程与指标采集：

  订单支付记录(OB)          三方支付账单(文件)
         │                        │
         ▼                        ▼
   ┌──────────────────────────────┐
   │  order-reconciliation-service │
   │  (XXL-Job, 每日 02:00)        │
   │                               │
   │  1. 加载 OB 昨日支付记录       │
   │  2. 解析三方支付对账单文件      │
   │  3. 逐笔匹配（order_id + 金额） │
   │  4. 标记差异记录                │
   └──────────┬────────────────────┘
              │
              ├──→ order_reconciliation_difference{type="payment"}  Gauge
              ├──→ order_reconciliation_difference{type="refund"}   Gauge
              └──→ order_reconciliation_detail{status="unmatched"}  Info-style metric

  看板展示：
  ┌─ 资金对账概览 ───────────────────┐
  │  ✓ 昨日对账完成                   │
  │  总笔数：125,832                  │
  │  一致：125,830                    │
  │  差异：2   ← 标红告警             │
  └──────────────────────────────────┘
  ┌─ 差异详情（表格） ───────────────┐
  │ order_id   OB金额  三方金额  状态 │
  │ ORD12345   99.00    0.00    OB有 │
  │ ORD67890   0.00    99.00   三方有│
  └──────────────────────────────────┘
```

### 3. 数据一致性体检看板（Data Consistency Dashboard）

#### 3.1 Panel 规划

| # | 面板 | 图表类型 | PromQL | 刷新频率 |
|---|------|---------|--------|---------|
| 1 | **Canal 同步延迟（全局）** | Stat（最大/平均/P99） | `max(canal_instance_lag{job="canal"})` | 15s |
| 2 | **Canal 延迟热力图（按分区）** | Heatmap | `canal_instance_lag{partition=~".+"}` | 30s |
| 3 | **OB↔ES 一致性校验结果** | Stat + Table | `order_consistency_diff_count{type="ob_vs_es"}` | 60s |
| 4 | **一致性差异趋势** | Time Series | `sum(rate(order_consistency_diff_total[5m])) by (type)` | 30s |
| 5 | **Redis↔OB 延迟** | Time Series | `order_cache_lag_seconds` | 15s |
| 6 | **消息堆积深度（按 Topic）** | Time Series | `sum(rocketmq_consumer_offset_lag) by (topic, consumer_group)` | 15s |
| 7 | **死信队列概览** | Stat + Table | `sum(rocketmq_dlq_count) by (topic)` | 30s |
| 8 | **死信队列处理状态** | State Timeline | `rocketmq_dlq_status` | 60s |
| 9 | **数据修复任务执行状态** | Table（Progress） | `xxl_job_result{job_name=~"consistency_repair.*"}` | 30s |
| 10 | **一致性健康评分** | Stat（0-100） | 聚合评分公式（见下文） | 60s |

#### 3.2 自定义指标

```java
// === Canal 延迟指标（已在 ADR-013 中定义，补充维度） ===
// canal_instance_lag{instance="canal-1", destination="order", partition="0"}
// ↑ 已有指标，确保 partition 标签存在即可

// === 一致性校验 ===
// 由 order-consistency-checker（XXL-Job 调度，每小时执行）提供
// OB ↔ ES 逐主键对比 count(*) + 随机采样 1000 条详情
Gauge.builder("order.consistency.diff_count", checker,
        c -> c.getLastDiffCount())
    .tag("type", "ob_vs_es")         // ob_vs_es / redis_vs_ob / ob_vs_mq
    .tag("checker_instance", "main")
    .register(meterRegistry);

// 一致性校验总计数（累计，用于 rate 计算）
Counter.builder("order.consistency.checked_total")
    .tag("type", "ob_vs_es")
    .register(meterRegistry)
    .increment(sampleSize);

// 差异发现计数
Counter.builder("order.consistency.diff_total")
    .tag("type", "ob_vs_es")
    .tag("severity", "critical")  // critical / warn
    .register(meterRegistry)
    .increment(diffCount);

// === 缓存延迟指标 ===
// Canal cache-writer 记录 binlog 时间 → Redis 写入完成时间的差值
Gauge.builder("order.cache.lag_seconds", cacheWriter,
        w -> w.getCurrentLagSeconds())
    .tag("cache_type", "hot")   // hot / full
    .register(meterRegistry);

// === MQ 死信指标（RocketMQ Exporter 已有，补充自定义维度） ===
// 死信消息已达次数
Gauge.builder("order.dlq.death_count", dlqService,
        s -> s.getMessageDeathCount(topic))
    .tag("topic", topic)
    .register(meterRegistry);

// === 数据修复任务进度 ===
Gauge.builder("order.repair.job_progress", repairJob,
        j -> j.getProgressPercent())
    .tag("job_name", jobName)
    .tag("phase", "incremental")  // full / incremental / verify
    .register(meterRegistry);
```

#### 3.3 一致性健康评分公式

```yaml
# recording/consistency_score.yml
groups:
  - name: consistency_score
    interval: 60s
    rules:
      # 各维度评分 (0-100)，越低越差
      - record: consistency:score_canal
        expr: clamp_max(clamp_min((100 - (canal_instance_lag / 10 * 100)), 0), 100)

      - record: consistency:score_es_diff
        expr: clamp_max(clamp_min((100 - (order_consistency_diff_count{type="ob_vs_es"} * 10)), 0), 100)

      - record: consistency:score_cache_lag
        expr: clamp_max(clamp_min((100 - (order_cache_lag_seconds * 5)), 0), 100)

      - record: consistency:score_mq_backlog
        expr: clamp_max(clamp_min((100 - (rocketmq_consumer_offset_lag / 1000 * 100)), 0), 100)

      # 综合评分（加权平均）
      - record: consistency:score_overall
        expr: |
          (consistency:score_canal * 0.3 +
           consistency:score_es_diff * 0.3 +
           consistency:score_cache_lag * 0.2 +
           consistency:score_mq_backlog * 0.2)
```

#### 3.4 一致性校验器设计

```java
/**
 * 一致性校验器 —— 由 XXL-Job 每小时调度一次
 * 比较 OB 数据库与 ES 索引之间的数据一致性
 */
@Component
public class ObEsConsistencyChecker {

    private static final int SAMPLE_SIZE_PER_ROUND = 1000;
    private static final double DIFF_THRESHOLD = 0.001; // 0.1% 差异率触发告警

    /**
     * 执行一致性校验
     * 1. Count 比对：SELECT COUNT(*) vs ES _count
     * 2. 采样比对：从 OB 抽取 1000 条，逐条到 ES 查询比对
     * 3. 时间窗口：最近 1h 有变更的订单（避免扫描全表）
     */
    @XxlJob("consistencyCheckJob")
    public ReturnT<String> execute() {
        // 阶段 1：总量差异检测
        long obCount = orderRepository.countRecentOrders();
        long esCount = esClient.countRecentDocuments();
        long countDiff = Math.abs(obCount - esCount);

        if (countDiff > 0) {
            // 更新 Gauge：order_consistency_diff_count{type="ob_vs_es_count"}
            metricsRecorder.recordCountDiff("ob_vs_es", countDiff);
        }

        // 阶段 2：采样比对
        List<Order> samples = orderRepository.sampleRecentOrders(SAMPLE_SIZE_PER_ROUND);
        int diffCount = 0;

        for (Order order : samples) {
            EsOrderDoc esDoc = esClient.findById(order.getOrderId());
            if (esDoc == null || !esDoc.matches(order)) {
                diffCount++;
                // 记录差异详情（写入日志 + 差异明细表）
                consistencyDiffRepository.insert(ConsistencyDiff.builder()
                    .orderId(order.getOrderId())
                    .diffType("ob_vs_es")
                    .obSnapshot(JsonUtils.toJson(order))
                    .esSnapshot(esDoc != null ? JsonUtils.toJson(esDoc) : "null")
                    .detectTime(Instant.now())
                    .status("unresolved")
                    .build());
            }
            metricsRecorder.recordCheckOne();
        }

        metricsRecorder.recordDiffCount("ob_vs_es", diffCount);
        metricsRecorder.recordCheckedCount("ob_vs_es", SAMPLE_SIZE_PER_ROUND);

        double diffRate = (double) diffCount / SAMPLE_SIZE_PER_ROUND;
        if (diffRate > DIFF_THRESHOLD) {
            // 触发告警 → 自动触发数据修复任务
            repairTaskDispatcher.dispatchRepair("ob_vs_es", diffRate);
            return ReturnT.FAIL("差异率 %.2f%% 超过阈值 %.2f%%", diffRate * 100, DIFF_THRESHOLD * 100);
        }

        return ReturnT.SUCCESS("校验完成，采样 %d 条，差异 %d 条", SAMPLE_SIZE_PER_ROUND, diffCount);
    }
}
```

#### 3.5 看板布局

```
数据一致性体检看板布局：

┌──────────────────────┬──────────────────────────────────────────────────┐
│  一致性健康评分       │  Canal 同步延迟（全局）                          │
│  ┌────────┐          │  P99: 2.3s    P50: 0.8s    Max: 5.1s            │
│  │  87    │          │  ┌──────────────────────────────────────────┐    │
│  │  /100  │          │  │ ████████████░░░░░░░░░░░░ 2.3s            │    │
│  └────────┘          │  └──────────────────────────────────────────┘    │
├──────────────────────┴──────────────────────────────────────────────────┤
│  Canal 延迟热力图（按分区）                                              │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │ 分区 0  ████████░░░░░░░░░░ 1.2s                                    │ │
│  │ 分区 1  ██████████░░░░░░░░ 1.8s                                    │ │
│  │ 分区 2  ██████████████░░░░ 2.7s  ← 延迟偏高                        │ │
│  │ ...                                                                │ │
│  └───────────────────────────────────────────────────────────────────┘ │
├──────────────────────┬──────────────────────────────────────────────────┤
│  OB↔ES 一致性        │  消息堆积 + 死信                                │
│  最近校验：10:32      │  ┌─ Topic ──────┬─ 堆积 ─┬─ DLQ ─┐            │
│  采样 1,000 条        │  │ order.event  │ 1,234  │ 12    │            │
│  差异：2 (0.2%) ← 标红│  │ order.pay    │ 0      │ 0     │            │
│  差异趋势：持平        │  │ order.refund │ 567    │ 3     │            │
├──────────────────────┴──────────────────────────────────────────────────┤
│  数据修复任务执行状态                                                    │
│  ┌─ 任务名 ──────────┬─ 进度 ─┬─ 状态 ─┬─ 详情 ───────────────┐       │
│  │ consistency_fix   │ 87%    │ 执行中 │ 15,832/18,200 条     │       │
│  │ es_backfill       │ 100%   │ ✅完成 │                      │       │
│  │ redis_sync        │ 100%   │ ✅完成 │                      │       │
│  └───────────────────┴────────┴────────┴──────────────────────┘       │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4. 新增告警规则

```yaml
# alerts/fund_flow_alerts.yml
groups:
  - name: fund_flow_alerts
    interval: 30s
    rules:
      # --- P1 告警 ---
      # 支付成功率低于 99%
      - alert: PaymentSuccessRateLow
        expr: |
          (sum(rate(order_payment_success_total[5m])) / sum(rate(order_payment_total[5m]))) * 100 < 99
        for: 3m
        labels:
          severity: critical
          team: payment
        annotations:
          summary: "支付成功率低于 99%"
          description: "当前 {{ $value | humanize }}%，已持续 3 分钟"
          runbook: "https://wiki.internal/payment-failure-runbook"

      # GMV 同比骤降 > 50%（与昨日同时段对比）
      - alert: GmvDropAbnormal
        expr: |
          (sum(rate(order_tx_amount_total[30m])) - sum(rate(order_tx_amount_total[30m] offset 24h)))
          / sum(rate(order_tx_amount_total[30m] offset 24h)) * 100 < -50
        for: 5m
        labels:
          severity: critical
          team: business
        annotations:
          summary: "GMV 同比骤降 > 50%"
          description: "30min GMV 较昨日同时段下降 {{ $value | humanize }}%"

      # --- P2 告警 ---
      # 退款失败激增
      - alert: RefundFailureSpike
        expr: |
          sum(rate(order_tx_anomaly{type="refund_failure"}[5m])) > 10
        for: 5m
        labels:
          severity: warning
          team: payment
        annotations:
          summary: "退款失败每分钟超过 10 笔"
          description: "退款失败 rate: {{ $value | humanize }}/min"

      # 资金对账出现差异
      - alert: ReconciliationDifference
        expr: order_reconciliation_difference > 0
        for: 0m
        labels:
          severity: warning
          team: finance
        annotations:
          summary: "资金对账出现差异"
          description: "{{ $labels.type }} 类型对账有 {{ $value }} 笔差异"
```

```yaml
# alerts/consistency_alerts.yml
groups:
  - name: consistency_alerts
    interval: 30s
    rules:
      # --- P1 告警 ---
      # Canal 延迟超过 30s
      - alert: CanalLagHigh
        expr: max(canal_instance_lag) > 30
        for: 2m
        labels:
          severity: critical
          team: sre
        annotations:
          summary: "Canal 同步延迟超过 30s"
          description: "当前最大延迟: {{ $value }}s，持续 2 分钟"
          runbook: "https://wiki.internal/canal-lag-runbook"

      # OB↔ES 一致性差异超过阈值
      - alert: ObEsConsistencyBreach
        expr: |
          (sum(increase(order_consistency_diff_total{type="ob_vs_es"}[1h])) 
           / sum(increase(order_consistency_checked_total{type="ob_vs_es"}[1h]))) * 100 > 0.1
        for: 1h  # 持续 1 小时差异超阈值才告警（避免采样波动）
        labels:
          severity: critical
          team: sre
        annotations:
          summary: "OB↔ES 一致性差异率超过 0.1%"
          description: "当前差异率 {{ $value | humanize }}%"

      # --- P2 告警 ---
      # Redis 缓存延迟超过 10s
      - alert: CacheLagHigh
        expr: order_cache_lag_seconds > 10
        for: 3m
        labels:
          severity: warning
          team: sre
        annotations:
          summary: "Redis 缓存更新延迟 > 10s"
          description: "当前延迟 {{ $value }}s"

      # 消息堆积持续增长
      - alert: MqBacklogGrowing
        expr: |
          sum(rocketmq_consumer_offset_lag) - sum(rocketmq_consumer_offset_lag offset 10m) > 0
        for: 10m
        labels:
          severity: warning
          team: sre
        annotations:
          summary: "消息堆积持续增长"
          description: "10 分钟内堆积未下降，当前 {{ $value }} 条"

      # 死信队列新增消息
      - alert: DlqMessageArrived
        expr: sum(increase(rocketmq_dlq_count[15m])) > 0
        for: 0m
        labels:
          severity: warning
          team: sre
        annotations:
          summary: "死信队列新增消息"
          description: "Topic {{ $labels.topic }} 新增死信消息"
```

### 5. 看板变量与模板化

两个看板均通过 Grafana 模板变量实现多维度筛选，避免为每个业务线重复建看板：

```json
{
  "commonVariables": [
    {
      "name": "business_type",
      "type": "query",
      "query": "label_values(order_tx_amount_total, business_type)",
      "includeAll": true,
      "default": "All",
      "description": "按业务线筛选"
    },
    {
      "name": "time_range",
      "type": "interval",
      "query": "5m,15m,30m,1h,3h,6h,12h,24h",
      "default": "30m",
      "description": "聚合时间窗口"
    },
    {
      "name": "service",
      "type": "query",
      "query": "label_values({__name__=~\"order_.*\"}, service)",
      "includeAll": true,
      "default": "All",
      "description": "按服务筛选（一致性看板专用）"
    }
  ]
}
```

### 6. 数据源与集成

| 数据源 | 用途 | 集成方式 |
|--------|------|---------|
| **Prometheus** | 所有时序指标 | 已有，新增自定义指标 |
| **MySQL/OceanBase** | 对账差异详情表 | Grafana MySQL Data Source（只读） |
| **Elasticsearch** | 一致性差异索引 | Grafana ES Data Source（只读） |
| **XXL-Job API** | 数据修复任务状态 | HTTP API → Prometheus exporter |

对于需要从数据库查询的明细数据（对账差异、一致性差异明细），采用 **Grafana 混合数据源**方式：面板主体基于 Prometheus 做聚合展示，点击 Drill-down 链接跳转到 MySQL/ES 数据源的明细面板。

### 7. 看板引导与 Drill-down

为避免信息过载，两个看板均采用「概览 → 下钻」的分层设计：

```
资金链路看板导航模式：

  顶层（默认）：实时概览
  ┌─ GMV 大数字 ─┬─ 支付成功率 ─┬─ 退款趋势 ─┬─ 异常时间线 ┐
  │ 2,356,789 元 │   99.3%      │ ▼2.1%     │ 0 异常     │
  └──────────────┴──────────────┴────────────┴────────────┘
        │                │             │           │
        ▼                ▼             ▼           ▼
  第二层：GMV 明细   支付明细      退款明细    异常详情
  ┌─ 按业务线 ─┐  ┌─ 按渠道 ─┐  ┌─ 按原因 ─┐  ┌─ 事件列 ─┐
  │ 电商 80%  │  │ 微信 60%│  │ 用户申  │  │ 时间/   │
  │ 本地 15% │  │ 支付宝  │  │ 请 80%  │  │ 类型/   │
  │ B2B 5%   │  │ 35%     │  │ 超时    │  │ 影响金  │
  └──────────┘  └─────────┘  └─────────┘  └────────────────┘
        │                │             │           │
        ▼                ▼             ▼           ▼
  第三层：下钻到        下钻到         下钻到      下钻到
  订单列表 (ES)       支付流水        退款记录    日志 (Loki)
```

每个面板的标题配置为 Drill-down 链接：

```json
{
  "title": "今日实时 GMV",
  "links": [
    {
      "title": "查看订单明细",
      "url": "/explore?orgId=1&left=%5B%22now-1h%22,%22now%22,%22Loki%22,%7B%22expr%22:%22%7Bservice%3D%5C%22order-core%5C%22%7D%20%7C%3D%20%5C%22createOrder%5C%22%22%7D%5D"
    }
  ]
}
```

### 8. 实施计划

| 阶段 | 任务 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | 应用层埋点：在 order-core/payment-core 中新增 10+ Micrometer 指标 | 代码 PR + 本地验证 | 2 |
| 1.1 | 交易金额/支付成功失败/退款等 Counter 埋点 | order-core PR #1 | 0.5 |
| 1.2 | 异常事件/对账差异 Gauge 埋点 | order-core PR #2 | 0.5 |
| 1.3 | consistency-checker 模块开发（XXL-Job + 指标） | consistency-checker 模块 | 1 |
| **Phase 2** | Prometheus 配置：Recording Rules + Alert Rules | YAML 配置文件 | 0.5 |
| 2.1 | GMV/一致性 Recording Rules | `recording/` YAML | 0.3 |
| 2.2 | 资金/一致性告警规则 | `alerts/` YAML | 0.2 |
| **Phase 3** | 资金链路看板：Panel 设计 + JSON Model 编写 + 测试 | Dashboard JSON | 1.5 |
| 3.1 | 核心面板（GMV/支付成功率/退款） | 6 panels | 0.5 |
| 3.2 | 对账面板 + 异常事件时间线 | 4 panels | 0.5 |
| 3.3 | 模板变量 + Drill-down 链接 + 测试 | 完整看板 | 0.5 |
| **Phase 4** | 一致性看板：Panel 设计 + JSON Model 编写 + 测试 | Dashboard JSON | 1.5 |
| 4.1 | Canal 延迟面板（Stat + Heatmap） | 2 panels | 0.3 |
| 4.2 | OB↔ES 一致性面板 | 3 panels | 0.4 |
| 4.3 | 消息堆积 + 死信面板 | 3 panels | 0.3 |
| 4.4 | 修复任务面板 + 健康评分 + 测试 | 3 panels | 0.5 |
| **Phase 5** | 集成测试 + 文档 + 上线 | 验证报告 + Wiki | 0.5 |
| 5.1 | 模拟故障验证告警触发 | 测试报告 | 0.3 |
| 5.2 | 操作手册 + 团队培训 | Wiki 文档 | 0.2 |

**合计：6 人天**

### 9. 上线检查清单

#### 代码与配置
- [ ] order-core 新增 Micrometer 指标埋点（PR 已合并）
- [ ] payment-core 新增支付/退款指标埋点（PR 已合并）
- [ ] consistency-checker 模块开发完成并发布
- [ ] Prometheus Recording Rules 配置已生效
- [ ] Prometheus Alert Rules 配置已生效
- [ ] AlertManager 接收测试告警成功

#### 看板 
- [ ] 资金链路看板 JSON 已导入 Grafana（Dashboard UID: `order-fund-flow`）
- [ ] 一致性体检看板 JSON 已导入 Grafana（Dashboard UID: `order-consistency-check`）
- [ ] 看板变量（business_type/time_range/service）正常工作
- [ ] Drill-down 链接可正常跳转
- [ ] 各面板数据源连接正常，无 `N/A` 面板
- [ ] 权限配置：资金看板仅财务/运营/SRE 可查看

#### 告警
- [ ] PaymentSuccessRateLow 告警已启用（阈值 99%，for 3m）
- [ ] GmvDropAbnormal 告警已启用（阈值 -50%，for 5m）
- [ ] CanalLagHigh 告警已启用（阈值 30s，for 2m）
- [ ] ObEsConsistencyBreach 告警已启用（阈值 0.1%，for 1h）
- [ ] 告警通知已接入企业微信/钉钉
- [ ] 告警静默期配置（大促期间免打扰窗口已设置）

#### 演练
- [ ] 模拟支付失败激增 → 验证告警触发 + 看板面板变化
- [ ] 模拟 Canal 断连 → 验证延迟告警 + 一致性评分下降
- [ ] 手动制造 ES 差异 → 验证一致性校验器检出 + 看板更新
- [ ] 演练后恢复，确认指标回归基线

#### 文档
- [ ] 看板使用说明已更新至 Wiki（含截图 + 数据源说明）
- [ ] 告警 Runbook 已更新（PaymentSuccessRateLow + CanalLagHigh）
- [ ] 值班交接文档已同步

### 10. 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §8.2 监控体系 | 补充资金链路 & 一致性维度的看板描述 |
| ADR-013 Canal 高可用 | `canal_instance_lag` 指标复用于一致性看板延迟热力图 |
| ADR-015 容量规划模型 | 容量看板（Capacity Command Center）与资金/一致性看板互补，前者聚焦资源，后者聚焦业务与数据质量 |
| ADR-017 业务线物理隔离 | 业务线拆分后，资金和一致性指标按 `business_type` 标签聚合，看板变量实现多业务线筛选 |

---

## 备选方案评估

### 方案 A（选定）：Grafana + Prometheus + 应用层新增指标

**优点**：基础设施已存在，仅需补充代码埋点和看板 JSON；学习成本低；与现有监控体系无缝集成

**缺点**：Grafana 对大屏展示和复杂下钻交互支持较弱；资金明细需要依赖 MySQL 数据源

### 方案 B：自建监控前端 + 时序数据库

**优点**：定制化程度高，可嵌入业务系统（如运营后台）；支持复杂权限控制

**缺点**：开发成本高（至少 20 人天）；维护负担重；短时间内无法落地

**结论**：当前阶段方案 A 足够支撑。如果未来需要嵌入运营后台，可将 Grafana Panel 通过 iframe 嵌入，无需自建。

### 方案 C：商业 APM 产品（Datadog/Dynatrace）

**优点**：开箱即用的业务看板和 APM 能力；SLA 由厂商保障

**缺点**：成本高（百万级/年）；数据合规（交易数据出公网）；定制灵活性低于自建

**结论**：短期不考虑。若公司统一采购 APM 产品，未来的看板可平滑迁移。

---

## 附录

### 附录 A：一致性校验 XXL-Job 配置

```yaml
# xxl-job 任务配置
job:
  consistency-check:
    cron: "0 0 * * * ?"          # 每小时整点执行
    jobHandler: "consistencyCheckJob"
    params: '{"sampleSize":1000,"timeWindowMin":60}'
    blockStrategy: "DISCARD_LATER"  # 上次未完成则跳过本次
    timeout: 600                    # 10 分钟超时

  consistency-detail-cleanup:
    cron: "0 0 3 * * ?"          # 每天凌晨 3 点清理
    jobHandler: "consistencyDiffCleanupJob"
    params: '{"retentionDays":30}'
```

### 附录 B：Prometheus 指标总表

#### 新增指标（17 个）

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `order_tx_amount_total` | Counter | business_type, currency | GMV 金额累计 |
| `order_payment_total` | Counter | business_type, channel | 支付总次数 |
| `order_payment_success_total` | Counter | business_type, channel | 支付成功次数 |
| `order_payment_fail_total` | Counter | business_type, fail_reason | 支付失败次数 |
| `order_refund_amount_total` | Counter | business_type, reason | 退款金额累计 |
| `order_refund_count_total` | Counter | business_type | 退款次数 |
| `order_tx_anomaly` | Counter | type, severity | 交易异常事件 |
| `order_reconciliation_difference` | Gauge | type | 对账差异笔数 |
| `order_consistency_diff_count` | Gauge | type, checker_instance | 一致性差异条数 |
| `order_consistency_checked_total` | Counter | type | 一致性已检查条数 |
| `order_consistency_diff_total` | Counter | type, severity | 一致性差异发现条数 |
| `order_cache_lag_seconds` | Gauge | cache_type | 缓存延迟秒数 |
| `order_dlq_death_count` | Gauge | topic | 死信消息已投递次数 |
| `order_repair_job_progress` | Gauge | job_name, phase | 数据修复任务进度 |

#### 复用已有指标（5 个）

| 指标名 | 来源 | 看板用途 |
|--------|------|---------|
| `canal_instance_lag` | ADR-013 Canal Exporter | 一致性看板延迟热力图 |
| `rocketmq_consumer_offset_lag` | RocketMQ Exporter | 一致性看板堆积深度 |
| `rocketmq_dlq_count` | RocketMQ Exporter | 一致性看板死信队列 |
| `xxl_job_result` | XXL-Job Exporter | 一致性看板修复任务状态 |
| `jvm_memory_used_bytes` | JVM Micrometer | 资金看板（服务健康关联） |

### 附录 C：Grafana Dashboard 导入脚本

```bash
#!/bin/bash
# import_dashboard.sh — 导入 Grafana Dashboard
# 使用 Grafana HTTP API 导入看板 JSON

GRAFANA_URL=${GRAFANA_URL:-"http://grafana:3000"}
API_KEY=${GRAFANA_API_KEY:-""}

import_dashboard() {
  local json_file=$1
  local payload=$(cat $json_file | jq '{dashboard: ., overwrite: true}')

  curl -s -X POST "$GRAFANA_URL/api/dashboards/db" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d "$payload" | jq '.title + ": " + .status'
}

import_dashboard "dashboards/fund-flow-dashboard.json"
import_dashboard "dashboards/consistency-dashboard.json"
```
