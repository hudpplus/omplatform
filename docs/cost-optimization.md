# 成本优化策略

> 本文档系统性地梳理订单中台架构中的成本优化机会，覆盖基础设施、中间件、数据存储、网络流量四个维度。定位为跨组件的策略参考，与各 ADR 中的成本相关设计互为补充。

---

## 目录

1. [当前成本分布](#1-当前成本分布)
2. [基础设施层优化](#2-基础设施层优化)
3. [数据存储层优化](#3-数据存储层优化)
4. [中间件层优化](#4-中间件层优化)
5. [网络与流量优化](#5-网络与流量优化)
6. [运营效率优化](#6-运营效率优化)
7. [预算与监控](#7-预算与监控)
8. [汇总与优先级](#8-汇总与优先级)

---

## 1. 当前成本分布

### 成本模型假设（日订单 100 万，3 AZ，20+ 服务）

| 成本类别 | 月度成本估算 | 占比 | 增长因子 |
|---------|-------------|------|---------|
| OceanBase 集群（OB集群5节点(2+2+1跨3AZ)） | ¥80,000-120,000 | 35-40% | 数据量增长 |
| Redis 集群（6 节点 × 3 AZ） | ¥30,000-50,000 | 12-15% | QPS 增长 |
| ES 集群（日常 6 节点） | ¥40,000-60,000 | 15-20% | 索引量增长 |
| K8s 计算（100+ Pods × 3 AZ） | ¥30,000-50,000 | 12-15% | 业务增长 |
| RocketMQ 集群（6节点 × 3 AZ） | ¥15,000-25,000 | 6-8% | Topic 增长 |
| 网络跨 AZ 流量 | ¥8,000-15,000 | 3-5% | 调用量增长 |
| OSS 冷存储 + 备份 | ¥5,000-10,000 | 2-3% | 归档策略 |
| 其他（Nacos/Apollo/SkyWalking/监控） | ¥5,000-8,000 | 2-3% | 平缓 |

**总成本估算**：¥21-34 万/月

### 成本增长瓶颈

```
OB 成本：数据膨胀（日增 ~2GB 基础数据 + binlog）
ES 成本：按月分索引，ILM 365d 删除但未启用温/冷层
Redis 成本：全量缓存策略，大量长期不访问的 key
K8s 成本：resource request 预留偏高，无 HPA 精细化
网络成本：跨 AZ 调用链长（Gateway → order-core → payment → OB）
```

---

## 2. 基础设施层优化

### 2.1 K8s 资源 Request/Limit 治理

**现状问题**：

- `resource.requests` 往往按峰值再 × 安全系数设置，日常利用率仅 20-40%
- 部分服务（cart-service、promotion-service）非核心链路，可接受超卖
- HPA 配置偏保守，minReplicas 偏高

**优化方案**：

| 方法 | 收益 | 风险 | 实施方式 |
|------|------|------|---------|
| 基于 Percentile 的请求量调整 | CPU 请求降低 20-30% | 极端流量可能触发 throttle | VPA 推荐 + 人工审核 |
| 非核心服务超卖（CPU 1:2） | Pod 密度提升 30-50% | 资源争抢 | Burstable QoS + LimitRange |
| HPA 弹性精细化 | 低峰期 Pod 数减少 30-50% | 扩容滞后可能导致性能抖动 | Prometheus Advanced HPA + KP |
| 服务合并（低负载服务） | 减少 2-4 个 Pod | 隔离性下降 | 按调用链合并（如 cart 合并到 order-core） |

**推荐配置模板**：

```yaml
# 核心服务（order-core, payment, inventory）
resources:
  requests:
    cpu: "2"      # 按 P99 峰值预留
    memory: "4Gi"
  limits:
    cpu: "4"      # 允许 burst 到 2x
    memory: "6Gi"

# 非核心服务（promotion, aftersale, logistics）
resources:
  requests:
    cpu: "500m"   # 核心 1/4，允许超卖
    memory: "1Gi"
  limits:
    cpu: "2"      # 宽松上限
    memory: "4Gi"
```

### 2.2 预留实例与 Spot 实例混合部署

**策略**：

```
稳定基础（60-70%）：预留实例（Reserved/Committed Use），覆盖 baseline
弹性增量（20-30%）：Spot 实例（Preemptible VM），覆盖 HPA 扩容部分
关键服务保护：order-core / payment 走预留实例，禁止跑在 Spot 上
```

**收益估算**：

| 云厂商 | 预留实例折扣 | Spot 折扣 | 混合后节省 |
|--------|------------|----------|-----------|
| 阿里云 ECS | 1年 7折 / 3年 5折 | 常规 4-6折 | ~35-45% |
| AWS | 1年 6折 / 3年 4折 | 常规 4-7折 | ~40-50% |

**风险控制**：

- Spot 中断处理：`preStop` hook 优雅退出 + 中断预算（PDB）保障最小副本数
- 核心服务禁用 Spot：通过 `nodeSelector` + `toleration` 隔离

### 2.3 规范化标签与 FinOps

```yaml
# K8s 资源标签规范
labels:
  omplatform/service: order-core       # 服务名
  omplatform/cost-center: core-order   # 成本中心
  omplatform/env: prod                 # 环境
  omplatform/biz-line: ecommerce       # 业务线
  omplatform/owner: team-order         # 所属团队
```

- 按 labels 拆分成本：Grafana + 云厂商 Cost Explorer 按标签聚合
- 每月成本报告：按服务/业务线/团队维度展示费用变化趋势
- 设置预算告警：月消费超预算 80% → 群消息 / 超 100% → P2 告警

---

## 3. 数据存储层优化

### 3.1 OceanBase 成本优化

**OB 成本构成**：

```
OB 成本 ≈ 节点数 × 单价 + 存储量 × 单价
  节点：5 节点 × 32C/64G = 核心成本
  存储：SSD 数据盘 + 日志盘 + 三副本
```

**优化方向**：

| 方法 | 收益估算 | 复杂度 | 说明 |
|------|---------|--------|------|
| 压缩率调优 | 存储降低 30-50% | 低 | OB 默认 zstd 压缩，调整 `compression` 参数 |
| 冷热数据分离 | 热数据保留 90d，冷数据归档 | 中 | 配合 ADR-031 数据生命周期 |
| 历史分区归档（2 年+） | 存储降低 15-25% | 中 | OB 表分区按时间，旧分区转为只读 |
| 日志盘独立规格 | 视配置可降 10-20% | 低 | 日志盘可独立选择低成本存储 |
| 参数优化 `memory_limit` | 性能提升减少资源争抢 | 低 | 避免 OOM 导致节点不稳定 |

**OB 压缩配置示例**：

```sql
-- 核心表（高并发写入）：zstd 压缩，性能最优
ALTER TABLE `order` SET compression = 'zstd_1.0';

-- 归档/历史表（低写入）：启用更激进压缩
ALTER TABLE `order_history` SET compression = 'zstd_1.3.8';

-- 日志类表（append-only）：高压缩比优先
ALTER TABLE `operation_log` SET compression = 'lz4_1.0';
```

### 3.2 ES 成本优化

**ES 成本构成**：

```
ES 成本 ≈ 节点数 × 单价 + 存储 × 副本数
  数据节点：日常 6 节点 = 核心成本
  存储：SSD 热节点 + HDD 温/冷节点
```

**优化方向**：

| 方法 | 收益估算 | 复杂度 | 说明 |
|------|---------|--------|------|
| 温/冷节点分层 | 存储成本降低 40-60% | 中 | 90d 后迁移到 warm（HDD），180d 后 cold（frozen） |
| 副本数动态调整 | 存储降低 33% | 低 | 温/冷索引副本从 1 → 0 |
| 字段存储优化 | 存储降低 20-30% | 低 | `"index": false` + `"doc_values": false`（ADR-012） |
| 生命周期自动化 | 确保数据及时迁移 | 低 | ILM policy 覆盖（ADR-012） |
| 索引压缩编码 | 存储降低 10-15% | 低 | `best_compression` |

**ILM 成本优化补充**：

```json
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0d",
        "actions": {
          "rollover": { "max_size": "30GB", "max_age": "30d" },
          "set_priority": { "priority": 100 }
        }
      },
      "warm": {
        "min_age": "90d",
        "actions": {
          "set_priority": { "priority": 50 },
          "allocate": { "number_of_replicas": 0, "require": { "data_tier": "data_warm" } },
          "forcemerge": { "max_num_segments": 1 },
          "shrink": { "number_of_shards": 1, "max_primary_shard_size": "30gb" }
        }
      },
      "cold": {
        "min_age": "365d",
        "actions": {
          "set_priority": { "priority": 0 },
          "allocate": { "number_of_replicas": 0, "require": { "data_tier": "data_frozen" } }
        }
      },
      "delete": {
        "min_age": "1095d",
        "actions": { "delete": {} }
      }
    }
  }
}
```

**说明**：B2B 业务线采用差异化 ILM 策略，cold 阶段保留期及删除策略根据业务需求调整，详情见 ADR-031。

### 3.3 Redis 成本优化

| 方法 | 收益估算 | 复杂度 | 说明 |
|------|---------|--------|------|
| 热点数据 TTL 差异化 | 内存降低 15-25% | 低 | 终态订单 TTL 缩短至 1h |
| Value 结构优化 | 内存降低 10-20% | 低 | 使用 Hash 替代 String、整数压缩 |
| key 命名瘦身 | 内存降低 1-3% | 低 | 缩短前缀 `omplatform:order:detail:` → `o:od:` |
| 驱逐策略调优 | 减少 OOM 风险 | 低 | `allkeys-lru` 替代 `noeviction` |
| 集群缩容评估 | 视利用率可减 2-3 节点 | 中 | 需验证实际内存水位 |

**Value 优化对比**：

```java
// Bad: String value 存储完整 JSON
redisTemplate.opsForValue().set(
    "omplatform:order:detail:" + orderId, 
    JSON.toJSONString(order), 
    24, TimeUnit.HOURS
);

// Good: Hash 按字段存储（内存优化 ~40%）
redisTemplate.opsForHash().putAll(
    "omplatform:order:" + orderId,
    Map.of("status", order.getStatus().name(),
           "amount", order.getAmount().toString(),
           "buyerId", order.getBuyerId().toString())
);
redisTemplate.expire("omplatform:order:" + orderId, 1, TimeUnit.HOURS);
```

### 3.4 数据生命周期成本（与 ADR-031 协同）

| 数据分类 | 当前策略 | 优化后策略 | 存储成本变化 |
|---------|---------|-----------|------------|
| OB 订单主表 | 永久保留 | 热 90d → 历史归档表 | -40% |
| ES 订单索引 | 365d 删除 | 90d hot → 180d warm → 365d cold → 1095d 删除 | -50% |
| Redis 缓存 | 24h TTL | 活跃订单 1h → 终态订单 5min | -20% |
| 事件归档表 | 180d 删除 | 180d OB → 365d OSS Parquet → 删除 | -30% |
| 监控数据 | 30d | 30d Prometheus → 1y OSS（Grafana 仍可查询） | -60% |

**说明**：OB 订单主表 90d 热数据阈值适用于电商业务线。B2B 业务线订单主表需保留 7 年（见 ADR-017/ADR-031），采用独立分区策略，不适用 90d 归档阈值。

---

## 4. 中间件层优化

### 4.1 RocketMQ

| 方法 | 收益估算 | 复杂度 | 说明 |
|------|---------|--------|------|
| Topic 合并 | 减少 Broker 节点 | 低 | 内部事件可共用一个 Topic，tag 区分 |
| 消息体压缩 | 网络带宽降低 40-60% | 低 | gzip 压缩 > 4KB 的消息体 |
| 保留时间调优 | 存储降低 | 低 | 消费确认后 72h 自动删除 |
| 批量发送 | TPS 不变时减少连接 | 低 | 非实时场景合并发送 |

### 4.2 Canal

| 方法 | 收益估算 | 复杂度 | 说明 |
|------|---------|--------|------|
| 仅同步必要表 | 减少 Canal 节点数 | 低 | 当前可能同步了所有表 |
| 过滤非关键 binlog | 减少 OB 到 Canal 流量 | 低 | DDL / 非关键表的更新过滤 |
| 合并数据通道 | 减少 Canal 实例 | 中 | ES 同步 + 缓存刷新共用通道 |

### 4.3 监控体系成本

| 方法 | 收益估算 | 说明 |
|------|---------|------|
| Prometheus 指标降维 | 存储降低 30-50% | 删除不必要的高基数 label |
| 告警规则合并 | 计算资源降低 | 类似指标的告警合并为 recording rule |
| 日志采样降级 | 存储降低 60-80% | 非核心服务 DEBUG 日志不采集 |
| SkyWalking 采样率优化 | 存储降低 40% | 核心服务 100%，非核心 10% |

---

## 5. 网络与流量优化

### 5.1 跨 AZ 流量优化

**当前问题**：OB Paxos 同步、Dubbo 调用链跨 AZ 产生大量网络费用。

**优化方案**：

| 方法 | 收益 | 复杂度 |
|------|------|--------|
| Dubbo 同 AZ 优先路由 | 跨 AZ 流量降低 40-60% | 中 |
| 服务间亲和性调度 | 跨 AZ 流量降低 30% | 中 |
| OB 流量本地读优先 | 网络成本降低 20% | 低 |
| 数据本地化缓存 | 跨 AZ 查询减少 50% | 中 |

**Dubbo 同 AZ 优先路由实现**：

```yaml
# dubbo 同 AZ 优先路由
dubbo:
  consumer:
    parameters:
      # 同 AZ 优先，fallback 到其他 AZ（配合 ADR-016 多 AZ 策略）
      az-preference: true
    cluster: "available"  # 优先可用，不强制 same AZ
```

```yaml
# K8s Pod 反亲和性：相同服务 Pod 均匀分布到不同 AZ
# 但 caller 与 callee 尽量调度到同 AZ
affinity:
  podAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      topologyKey: "topology.kubernetes.io/zone"
      labelSelector:
        matchLabels:
          omplatform/cluster: order-group  # order-core + payment + inventory 调度到同 AZ
```

### 5.2 请求合并与批量操作

| 场景 | 当前 | 优化后 | 收益 |
|------|------|--------|------|
| 订单列表查询 | 按页多次 DB query | 一次查询返回更多字段 | 连接数降低 |
| 库存批量查询 | 循环调用 | Batch dubbo 接口 | 网络往返减少 |
| 消息发送 | 逐条发送 | 批量发送（buffer 100ms） | 网络吞吐提升 |
| 日志上报 | 每行一条 HTTP | 批量聚合上报 | 连接数降低 |

---

## 6. 运营效率优化

### 6.1 环境成本控制

| 环境 | 当前策略 | 优化策略 | 年节省估算 |
|------|---------|---------|-----------|
| DEV | 节点数等同于生产 10% | 非工作时间 HPA scale to 0 | ¥3-5 万 |
| PRE | 全量部署（3 AZ） | 单 AZ + 按需启动非核心服务 | ¥8-12 万 |
| STAGING | 全量部署 | 缩减为单 AZ、单副本 | ¥6-10 万 |
| 测试环境 | 独立 OB 集群 | 共享 OB + Schema 隔离 | ¥3-5 万 |

### 6.2 自动化与人力成本

| 优化项 | 人天/年 | 说明 |
|-------|---------|------|
| 自动化容量评估（ADR-015） | 减少 30 人天 | 避免人工逐服务评估 |
| 自动化 SQL Review（ADR-024） | 减少 20 人天 | 减少 DBA 人工审查 |
| 数据生命周期自动化（ADR-031） | 减少 15 人天 | 避免手工归档和清理 |
| 混沌工程自动化（ADR-034） | 减少 25 人天 | 降低故障演练人力 |

---

## 7. 预算与监控

### 7.1 成本监控指标

```java
@Configuration
public class CostMetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> costMetrics() {
        return registry -> {
            // 按服务的资源使用量
            Gauge.builder("omplatform_cost_cpu_requests_total", this, c -> c.getTotalCpuRequests())
                .tags("service", "all")
                .register(registry);

            Gauge.builder("omplatform_cost_memory_requests_total", this, c -> c.getTotalMemoryRequests())
                .tags("service", "all")
                .register(registry);

            // 按服务的实际利用率
            Gauge.builder("omplatform_cost_cpu_utilization_ratio", this, c -> c.getCpuUtilization())
                .tags("service", "order-core")
                .register(registry);

            // OB 存储量（按业务线）
            Gauge.builder("omplatform_cost_ob_storage_bytes", this, c -> c.getObStorageBytes())
                .tags("biz_line", "ecommerce")
                .register(registry);

            // Redis 内存使用率
            Gauge.builder("omplatform_cost_redis_memory_usage_bytes", this, c -> c.getRedisMemoryBytes())
                .tags("cluster", "main")
                .register(registry);
        };
    }
}
```

### 7.2 预算告警阈值

| 指标 | 警告线 | 告警线 | 动作 |
|------|-------|--------|------|
| 月度总成本 | 预算 80% | 预算 100% | 群消息 → 邮件 |
| 单服务成本环比增长 | +10% | +20% | 通知服务 owner |
| 资源利用率 < 20% | 持续 7 天 | 持续 14 天 | 触发降配任务 |
| ES 存储增长速率 | > 20GB/天 | > 50GB/天 | 审查索引策略 |

### 7.3 成本优化检查清单（每月）

- [ ] 检查每个服务的 CPU/Memory 实际利用率（低于 30% 标记优化）
- [ ] 检查 ES 索引增长速率，评估是否需要合并 shard
- [ ] 检查 Redis 内存水位，识别长期未访问的大 key
- [ ] 检查 OB 存储增长，评估历史分区是否需要归档
- [ ] 检查 Spot 实例中断率（超过 5% 调整比例）
- [ ] 检查 Prometheus 指标基数，降维高基数 label
- [ ] 检查非生产环境是否运行了不必要的节点

---

## 8. 汇总与优先级

### 收益总表

| 优先级 | 优化项 | 类别 | 月节省估算 | 人天 | 复杂度 |
|--------|-------|------|-----------|------|--------|
| **P0** | K8s Request 调整 | 基础设施 | ¥5,000-10,000 | 2d | 低 |
| **P0** | ES 温/冷分层 | 数据存储 | ¥8,000-15,000 | 3d | 中 |
| **P1** | OB 压缩 + 历史归档 | 数据存储 | ¥10,000-20,000 | 5d | 中 |
| **P1** | Redis TTL + 结构优化 | 数据存储 | ¥3,000-8,000 | 2d | 低 |
| **P1** | 非核心服务超卖 | 基础设施 | ¥3,000-6,000 | 1d | 低 |
| **P1** | 预留实例 + Spot 混合 | 基础设施 | ¥8,000-15,000 | 3d | 中 |
| **P2** | 环境成本控制 | 运营效率 | ¥5,000-10,000 | 2d | 中 |
| **P2** | Dubbo 同 AZ 优先路由 | 网络 | ¥2,000-5,000 | 3d | 中 |
| **P2** | 监控体系降维 | 中间件 | ¥2,000-5,000 | 1d | 低 |
| **P3** | RocketMQ Topic 合并 | 中间件 | ¥1,000-3,000 | 1d | 低 |
| **P3** | 服务合并（低负载） | 基础设施 | ¥2,000-5,000 | 5d | 高 |

**预期总降幅**：¥46,000-102,000/月（当前成本的 20-35%）

### 实施路线

```
Phase 1（1-2 月）：P0 快速见效
  → K8s Request 合理化（2d）
  → ES 温冷分层 ILM（3d）
  → 预估节省：¥13,000-25,000/月

Phase 2（3-4 月）：P1 核心优化
  → OB 压缩 + 历史归档（5d）
  → Redis TTL + 结构（2d）
  → 非核心服务超卖（1d）
  → 预留 + Spot 混合（3d）
  → 预估节省：¥24,000-49,000/月

Phase 3（5-6 月）：P2-P3 持续优化
  → 环境成本控制（2d）
  → 网络优化（3d）
  → 监控降维 + MQ 优化（2d）
  → 预估额外节省：¥9,000-28,000/月
```

### 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-015**（容量规划） | 容量模型为资源需求提供基线输入 |
| **ADR-031**（数据生命周期） | 冷热数据分离、归档策略是成本优化的核心 |
| **ADR-012**（ES ILM） | ES 温/冷分层、shard 优化 |
| **ADR-016**（多 AZ 缓存） | 缓存策略影响 Redis 内存效率 |
| **ADR-027**（可观测性） | 监控成本控制、指标降维 |
| **ADR-034**（混沌工程） | 成本优化后需验证降配不影响稳定性 |
