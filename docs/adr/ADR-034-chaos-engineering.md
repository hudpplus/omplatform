# ADR-034: 混沌工程与弹性验证

## 状态

已接受

## 背景

### 现状分析

架构中已有大量弹性设计：

| 弹性机制 | 涉及组件 | 当前验证方式 |
|---------|---------|------------|
| Sentinel 限流 + 熔断 | Gateway (IGW/EGW) | 无（首次真实故障才触发） |
| HPA 自动扩缩容 | 所有 K8s 服务 | 压测时顺带验证 |
| Saga 降级 Choreography | Saga 事务 | 无 |
| Redis 降级 ES | 热缓存 | 无（从未演练过） |
| AZ 缓存预热 + 切换 | 多 AZ 部署 | 季度演练 |
| 位点四级恢复 | Canal | 无 |
| 灰度自动熔断 | 全链路灰度 | 版本发布前 |
| 死信兜底 + 对账修复 | Canal / Saga / Webhook | 无 |

**核心问题**：

1. **弹性措施从未被验证** — 熔断器在首次真实故障时才触发，如果熔断逻辑有 Bug，故障直接级联
2. **组件降级路径未经过测试** — Redis 宕机时降级 ES 的代码路径从未在 PRE 环境下跑过
3. **没有稳态基线** — 每个实验缺少明确的"什么算正常"的衡量标准
4. **组合故障未覆盖** — 只测试单组件故障，没有覆盖"Redis 宕机 + Canal 重建中"等组合场景
5. **缺少自动化** — 每次演练需要人工准备环境、执行故障注入、验证结果

### 目标

1. 建立混沌实验目录，覆盖所有弹性机制的验证
2. 定义每个实验的稳态指标（Steady State）
3. 选定故障注入工具（Chaos Mesh / chaosblade）
4. 设计 GameDay 季度演练 SOP
5. 最小化爆炸半径：实验在 PRE 环境执行，隔离真实用户流量

## 决策

### 方案对比：故障注入工具

| 维度 | 方案 A：Chaos Mesh | 方案 B：chaosblade | 方案 C：Litmus |
|------|-------------------|-------------------|---------------|
| 平台 | K8s 原生 CRD | 命令行 + HTTP | K8s Operator |
| 故障类型 | Pod Kill / 网络延迟/分区/CPU 压力/IO 压力 | 应用层/系统层/容器层 | Pod Kill / 网络 / 压力 |
| 实验编排 | YAML CRD + Dashboard | Shell 脚本 | Workflow CRD |
| K8s 集成度 | 深（自动管理实验范围） | 中（需手动指定目标） | 深（与 RBAC 集成） |
| 学习曲线 | 中 | 低 | 中 |
| 社区活跃度 | CNCF Incubation | 阿里开源 | CNCF Incubation |
| PRE 环境适用 | ✅ | ✅ | ✅ |

**选择：方案 A（Chaos Mesh）为主 + 方案 B（chaosblade）补充**

**选型理由**：
- Chaos Mesh 与 K8s 深度集成，实验定义 = YAML CRD，版本化管理
- 支持最丰富的故障类型（网络、Pod、压力、IO）
- 实验范围通过 Namespace / Label 精确控制，适合 PRE 环境隔离
- chaosblade 作为补充工具，用于非 K8s 组件（如 OB、ES 中间件本身的故障注入）

### 方案对比：实验环境

| 维度 | 方案 A：PRE 环境 | 方案 B：PROD 环境（影子流量） | 方案 C：独立 Chaos 集群 |
|------|----------------|---------------------------|---------------------|
| 用户影响 | 无 | 极小（影子流量占比 < 1%） | 无 |
| 真实度 | 中（数据量小） | 最高 | 低（与生产差异大） |
| 成本 | 复用 PRE 资源 | 高（需额外容量） | 最高（独立集群） |
| 频率 | 可随时执行 | 季度 | 半年度 |

**选择：方案 A（PRE 环境为主）**

- 所有混沌实验在 PRE 环境自动执行
- 年度 GameDay 在 PRE 环境做全流程演练
- PROD 环境仅做熔断触发测试（通过 Sentinel 规则手动调低阈值触发熔断，验证熔断响应正确）

## 详细设计

### 1. 实验目录

按弹性机制分组的混沌实验清单。

#### 1.1 Gateway 熔断验证

```yaml
experiments:
  - id: "gateway-001"
    name: "Gateway 慢调用熔断"
    mechanism: "Sentinel 慢调用比例熔断"
    trigger: "注入 100ms 延迟到 order-core 的 dubbo 调用"
    fault: "istio/chaos-mesh: 对 gateay → order-core 注入 200ms 网络延迟"
    duration: "120s（触发熔断 + 验证半开恢复）"
    steady_state:
      - "Gateway 503 比例 = 请求触发熔断后的预期值"
      - "后端服务（order-core）TPS 在熔断后归零（验证熔断有效）"
      - "熔断恢复（HALF_OPEN）后 TPS 回升到正常"
    validation:
      - "omplatform_gateway_circuit_break_total > 0"
      - "熔断期间后端服务零流量"
      - "熔断恢复后 30s 内正常"

  - id: "gateway-002"
    name: "Gateway 异常比例熔断"
    mechanism: "Sentinel 异常比例熔断"
    trigger: "order-core 返回 50% 错误率"
    fault: "注入随机异常到 order-core（50% 请求抛出异常）"
    duration: "120s"
    steady_state:
      - "异常比例 > 30% → 熔断触发 → 503 返回"
      - "后端零错误流量"
    validation:
      - "熔断触发时间 < 15s（1s 统计窗口 + 判断）"
      - "HALF_OPEN 探测流量正确发送到后端"

  - id: "gateway-003"
    name: "Gateway 限流触发"
    mechanism: "Sentinel QPS 限流"
    trigger: "短时间内超量请求"
    fault: "压测工具发送 3x 限流阈值的请求（1000/s → 3000/s）"
    duration: "60s"
    steady_state:
      - "实际通过 Gateway 的 QPS = 限流阈值（不超过）"
      - "HTTP 429 比例 ≈ 65%（2000/3000 被限流）"
    validation:
      - "omplatform_gateway_rate_limited_total 正确计数"
      - "后端服务未感知到超量流量"
```

#### 1.2 缓存降级验证

```yaml
experiments:
  - id: "cache-001"
    name: "Redis 集群完全不可用"
    mechanism: "热缓存自动降级 ES"
    trigger: "Redis Cluster 全部不可用"
    fault: "Chaos Mesh: NetworkLoss 阻断 Redis 端口 (6379)"
    duration: "300s（含恢复后验证）"
    steady_state:
      - "所有接口仍正常返回 HTTP 200"
      - "响应时间 < 500ms（ES 查询耗时）"
      - "降级计数（hotcache_degrade_total）正确递增"
    validation:
      - "omplatform_degrade_component_status{component=\"redis_cache\"} == 1"
      - "ES QPS 峰值 < ES 集群容量上限"
      - "Redis 恢复后 30s 内降级状态归零"

  - id: "cache-002"
    name: "Canal 高延迟"
    mechanism: "缓存降级（Apollo 开关）"
    trigger: "Canal 同步延迟 > 10s"
    fault: "暂停 Canal 消费（chaosblade: process kill）"
    duration: "600s"
    steady_state:
      - "缓存命中率逐渐下降（缓存过期后得不到更新）"
      - "Apollo 开关 hot.cache.degrade-on-high-delay=true → 降级 ES"
    validation:
      - "延迟恢复后 Canal 自动恢复消费"
      - "缓存命中率在 Canal 恢复后 5min 内回到 80%+"

  - id: "cache-003"
    name: "缓存应用层熔断"
    mechanism: "HotCacheTemplate 熔断（连续 50 次异常）"
    trigger: "Redis 反复失败"
    fault: "Redis 返回随机错误（通过 proxy 注入）"
    duration: "180s"
    steady_state:
      - "连续 50 次 Redis 失败后，熔断 30s"
      - "熔断期间跳过 Redis 直读 ES"
      - "30s 窗口到期后尝试恢复"
    validation:
      - "应用层熔断标志 degraded = true"
      - "30s 后自动恢复正常（degraded = false）"
```

#### 1.3 数据同步验证（Canal）

```yaml
experiments:
  - id: "canal-001"
    name: "Canal Server 宕机 + 位点恢复"
    mechanism: "ZK HA 自动切换 + 位点四级恢复"
    trigger: "Canal Server 进程终止"
    fault: "chaosblade: `blade kill --process canal` 或 kubectl delete pod"
    duration: "300s"
    steady_state:
      - "Canal 故障后 30s 内从节点接管"
      - "位点恢复：Redis 持久化位点 > RocketMQ offset > 时间戳 > 1h 前"
      - "ES 数据不丢失（位点准确恢复）"
    validation:
      - "RTO < 20s（ZK 会话超时 + 位点恢复 + 连接建立）"
      - "RPO = 0（位点持久化在 Redis）"
      - "ES 与 OB 最终一致"

  - id: "canal-002"
    name: "Canal 消费死信路径"
    mechanism: "重试 3 次 → retry topic 5 次 → DLQ → XXL-Job 扫描"
    trigger: "ES 写入连续失败"
    fault: "ES 暂停索引写入（阻塞 ES 写入端口）"
    duration: "600s"
    steady_state:
      - "前 3 次立即重试"
      - "后续 5 次延迟重试（5s/30s/5min/30min/2h）"
      - "进入 DLQ 后 XXL-Job 兜底修复"
    validation:
      - "CANAL_RETRY_COUNT 正确递增"
      - "DLQ 记录可被 XXL-Job 扫描并重放"
```

#### 1.4 灰度熔断验证

```yaml
experiments:
  - id: "gray-001"
    name: "灰度自动熔断（错误率超限）"
    mechanism: "错误率 > 1% 自动熔断"
    trigger: "灰度版本返回 5% 错误"
    fault: "在灰度实例上注入随机 500 错误（50 个请求中 3 个报错）"
    duration: "300s"
    steady_state:
      - "灰度错误率 > 1%（5min 窗口）→ Apollo grayPercentage 自动设为 0"
      - "灰度流量切回稳定版，功能不受影响"
      - "gray_route_degradation_total 正确递增"
    validation:
      - "Apollo grayPercentage 值在错误率超限后 1min 内归零"
      - "灰度用户降级到稳定版后体验正常（无 500 错误）"
      - "灰度版本健康后手动恢复灰度比例"

  - id: "gray-002"
    name: "灰度 P99 超限熔断"
    mechanism: "P99 > 2x 基线自动熔断"
    trigger: "灰度实例响应变慢"
    fault: "灰度实例注入 200ms 延迟（使 P99 从 50ms → 250ms 超过 2x）"
    duration: "300s"
    steady_state:
      - "P99 > 100ms（2x 基线）→ 熔断触发"
      - "灰度流量自动降级到稳定版"
    validation:
      - "熔断触发时间 < 2min（5min 窗口内检测到退化）"
```

#### 1.5 Saga 降级验证

```yaml
experiments:
  - id: "saga-001"
    name: "Saga 协调器宕机 → 降级 Choreography"
    mechanism: "Saga Orchestrator → Choreography 降级"
    trigger: "Saga 协调器全部不可用"
    fault: "Kill Saga 协调器 Pod"
    duration: "300s"
    steady_state:
      - "订单创建仍可用（降级为 Choreography 事件驱动模式）"
      - "订单最终状态正确（Choreography 仍能完成流程）"
      - "Saga 恢复后历史事务可重建"
    validation:
      - "Choreography 模式订单状态正确"
      - "无资金/库存不一致（对账通过）"
```

#### 1.6 AZ 切换验证

```yaml
experiments:
  - id: "az-001"
    name: "AZ-A 网络中断"
    mechanism: "GSLB AZ 切换 + 缓存预热"
    trigger: "AZ-A 网络全部阻断"
    fault: "Chaos Mesh: NetworkLoss 100% AZ-A 所有 Pod 的出入口流量"
    duration: "600s"
    steady_state:
      - "GSLB 检测到 AZ-A 不可用 → 切换流量到 AZ-B"
      - "AZ-B 缓存自动预热（或降级 ES）"
      - "核心 API 可用（订单查询 + 创建 + 支付）"
    validation:
      - "AZ 切换 RTO < 5min"
      - "AZ-B 缓存命中率 > 50%（预热或有降级）"
      - "数据一致性对账通过"
```

#### 1.7 资源压力验证

```yaml
experiments:
  - id: "resource-001"
    name: "CPU 打满 → HPA 扩容"
    mechanism: "K8s HPA 基于 CPU 自动扩容"
    trigger: "CPU 使用率 > 70% 持续 3min"
    fault: "stress-ng 在每个 Pod 消耗 200% CPU（1 个核心）"
    duration: "600s"
    steady_state:
      - "CPU > 70% → HPA 触发 → 副本数增加"
      - "扩容完成后 CPU 回落到 < 70%"
      - "P99 响应无显著退化（平稳过渡）"
    validation:
      - "HPA 扩容触发时间 < 3min（30s 稳定窗口）"
      - "副本数从 N → N+1 或更多"
      - "cpu-saturation 告警触发 → 恢复"

  - id: "resource-002"
    name: "OOM 场景"
    mechanism: "K8s OOMKill + 自动重启"
    trigger: "Pod 内存超限"
    fault: "注入内存泄漏（持续分配 500MB/s）"
    duration: "300s"
    steady_state:
      - "Pod OOMKill → K8s 自动重启"
      - "重启期间流量由其他副本接管"
      - "重启后注册到 Nacos → 恢复流量"
    validation:
      - "Pod 重启时间 < 30s"
      - "无 5xx 错误（其他副本接管流量）"
      - "重启后业务正常"
```

### 2. 实验平台集成

#### 2.1 Chaos Mesh CRD 示例

```yaml
# chaos/gateway-slow-call.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: gateway-slow-call
  namespace: omplatform-chaos
spec:
  action: delay
  mode: all
  selector:
    namespaces:
      - omplatform-pre
    labelSelectors:
      app: order-core
  delay:
    latency: "200ms"
    correlation: "100"
    jitter: "0ms"
  duration: "120s"
  scheduler:
    cron: "@at 2026-07-01T14:00:00"  # 定时执行
```

```yaml
# chaos/redis-kill.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: redis-kill
  namespace: omplatform-chaos
spec:
  action: pod-kill
  mode: all
  selector:
    namespaces:
      - middleware
    labelSelectors:
      app: redis
  duration: "300s"
  scheduler:
    cron: "@at 2026-07-01T14:30:00"
```

#### 2.2 实验编排 Workflow

```yaml
# chaos/gameday-q3-2026.yaml — 季度 GameDay 编排
apiVersion: chaos-mesh.org/v1alpha1
kind: Workflow
metadata:
  name: gameday-q3-2026
  namespace: omplatform-chaos
spec:
  entry: main
  templates:
    - name: main
      templateType: Serial
      children:
        - redis-kill
        - gateway-slow-call
        - canal-kill
        
    - name: redis-kill
      templateType: PodChaos
      deadline: "300s"
      podChaos:
        action: pod-kill
        mode: all
        selector:
          namespaces: ["middleware"]
          labelSelectors:
            app: redis
        duration: "300s"
        
    - name: gateway-slow-call
      templateType: NetworkChaos
      deadline: "120s"
      networkChaos:
        action: delay
        mode: all
        selector:
          namespaces: ["omplatform-pre"]
          labelSelectors:
            app: order-core
        delay:
          latency: "200ms"
        duration: "120s"
        
    - name: canal-kill
      templateType: PodChaos
      deadline: "300s"
      podChaos:
        action: pod-kill
        mode: one
        selector:
          namespaces: ["middleware"]
          labelSelectors:
            app: canal
        duration: "300s"
```

#### 2.3 K8s 资源预置

```yaml
# 混沌工程专用命名空间和 RBAC
apiVersion: v1
kind: Namespace
metadata:
  name: omplatform-chaos
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: chaos-admin
  namespace: omplatform-chaos
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: chaos-mesh-operator
rules:
  - apiGroups: [""]
    resources: ["pods", "services", "namespaces"]
    verbs: ["get", "list", "watch", "delete"]
  - apiGroups: ["chaos-mesh.org"]
    resources: ["*"]
    verbs: ["*"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: chaos-mesh-operator-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: chaos-mesh-operator
subjects:
  - kind: ServiceAccount
    name: chaos-admin
    namespace: omplatform-chaos
```

### 3. GameDay SOP

#### 3.1 GameDay 季度执行流程

```
T-2W： 宣布 GameDay 日期 + 选择实验清单
         ── 从实验目录中挑选 3-5 个实验
         ── 通知所有相关团队（开发 / SRE / 测试）
         ── 确认 PRE 环境可用

T-1W： 准备实验配置 + 审批
         ── 编写 Chaos Mesh Workflow YAML
         ── 架构评审：实验范围、稳态指标、回滚预案
         ── 确认实验期间无其他发布任务

T-0：   执行 GameDay
         ── 14:00 开始实验
         ── 逐个执行故障注入
         ── 记录每个实验的恢复时间、稳态偏差
         ── 如果实验中系统无法自动恢复 → 执行回滚脚本

T+2D： 复盘会
         ── 每个实验结果展示
         ── 未通过的实验 → 责任人 + 修复 DDL
         ── 改进 Runbook → 更新 ADR-034 实验目录
```

#### 3.2 实验执行命令

```bash
# 执行单个实验
kubectl apply -f chaos/gateway-slow-call.yaml

# 观察实验状态
kubectl get networkchaos gateway-slow-call -n omplatform-chaos -o yaml

# 验证稳态指标
curl -s "http://prometheus:9090/api/v1/query?query=rate(omplatform_gateway_circuit_break_total[5m])"

# 取消实验
kubectl delete -f chaos/gateway-slow-call.yaml

# 执行 GameDay Workflow（自动串行执行多个实验）
kubectl apply -f chaos/gameday-q3-2026.yaml
```

#### 3.3 回滚预案

```yaml
rollback_procedures:
  - scenario: "故障注入范围超出预期（影响其他服务）"
    action: "kubectl delete -f chaos/chaos.yaml — 立即停止所有实验"
    
  - scenario: "系统无法自动恢复"
    action: "手动执行恢复脚本（如重启服务、重置配置）"
    
  - scenario: "稳态指标严重偏离"
    action: "停止实验 + 执行 degradation-strategy.md 中对应等级的降级预案"
    
  - scenario: "PRE 环境数据损坏"
    action: "从备份恢复 PRE 数据库 + 重建索引"
```

### 4. 稳态指标定义

每个实验的稳态指标包括：

```yaml
steady_state_indicators:
  # 响应面指标
  - metric: "API 错误率"
    query: "rate(http_requests_total{status=~\"5..\"}[2m])"
    threshold: "< 0.1%"    # 实验期间不可接受 > 0.1% 5xx
    action: "abort_experiment"

  - metric: "API P99 响应时间"
    query: "histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[2m]))"
    threshold: "< 基线 + 500ms"  # 允许实验导致的性能退化
    action: "log_warning"
    
  # 业务指标（核心）
  - metric: "订单创建成功率"
    query: "rate(order_create_total{status=\"success\"}[2m]) / rate(order_create_total[2m])"
    threshold: ">= 99.9%"
    action: "abort_experiment"
    
  - metric: "支付成功率"
    query: "rate(payment_total{status=\"success\"}[2m]) / rate(payment_total[2m])"
    threshold: ">= 99.9%"
    action: "abort_experiment"
    
  # 数据一致性指标
  - metric: "OB ↔ ES 一致率"
    query: "reconciliation_pass_rate"
    threshold: "= 100%"
    action: "run_post_experiment_reconciliation"

  # 中间件指标
  - metric: "MQ 积压"
    query: "rocketmq_consumer_lag"
    threshold: "< 1000"
    action: "log_warning"
    
  - metric: "ES GC 频率"
    query: "rate(es_gc_collection_seconds_count[2m])"
    threshold: "< 基线 + 2x"
    action: "log_warning"
```

### 5. 实验频率与职责

| 实验 | 频率 | 执行方 | 验证方 |
|------|------|--------|--------|
| Gateway 慢调用熔断 | 月 | SRE | Gateway 负责开发 |
| Gateway 异常比例熔断 | 月 | SRE | Gateway 负责开发 |
| Gateway 限流触发 | 月 | SRE | Gateway 负责开发 |
| Redis 集群宕机 | 季度 | SRE | 数据层负责开发 |
| Canal 宕机 + 位点恢复 | 季度 | SRE | Canal 负责开发 |
| Canal 消费死信路径 | 季度 | SRE | Canal 负责开发 |
| 灰度自动熔断 | 版本发布前 | 测试 | 灰度负责开发 |
| Saga 降级 Choreography | 季度 | SRE | Saga 负责开发 |
| AZ-A 网络中断 | 半年 | SRE + 全链路 | 全链路开发 |
| CPU 打满 HPA 扩容 | 月 | SRE | 基础设施团队 |
| 全量 GameDay（5-8 个实验） | 季度 | SRE + 全团队 | 全团队 |

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | Chaos Mesh 部署到 PRE 集群 + RBAC 配置 | 1d | Chaos Mesh Operator 运行中 |
| P2 | 实验目录 CRD YAML 编写（8-10 个） | 2d | chaos/ 目录下的实验定义 |
| P3 | 稳态指标 Prometheus 查询 + 自动验证脚本 | 1d | 实验通过/失败判定脚本 |
| P4 | GameDay Workflow 编排 + PRE 环境集成 | 1d | season-game-day.yaml |
| P5 | CI 集成：实验作为发布流水线的一步（可选） | 1d | 流水线 + 阀门配置 |
| P6 | 文档 + 团队培训 + 首次 GameDay | 1.5d | README + 培训 + 演练录像 |

**合计**：7.5 人天

## 上线检查清单

- [ ] 基础设施：Chaos Mesh 部署到 PRE 集群
- [ ] 基础设施：RBAC 配置（chaos-admin SA + ClusterRole）
- [ ] 基础设施：实验专用 Namespace `omplatform-chaos`
- [ ] 实验：Gateway 熔断实验 CRD（gateway-001/002/003）
- [ ] 实验：缓存降级实验 CRD（cache-001/002/003）
- [ ] 实验：Canal 容灾实验 CRD（canal-001/002）
- [ ] 实验：灰度熔断实验 CRD（gray-001/002）
- [ ] 实验：Saga 降级实验 CRD（saga-001）
- [ ] 实验：资源压力实验 CRD（resource-001/002）
- [ ] 验证：稳态指标 Prometheus 查询可正常拉取
- [ ] 验证：每个实验的回滚预案已定义
- [ ] 验证：PRE 环境隔离生效（实验不影响其他环境）
- [ ] 文档：GameDay SOP 定稿
- [ ] 流程：季度 GameDay 排入日历

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **degradation-strategy.md** | 混沌实验验证降级策略中的每个降级路径是否生效 |
| **disaster-recovery-plan.md** | AZ 切换实验验证 DR 计划的切换 RTO/RPO 目标 |
| **ADR-029** (内部 Gateway) | 验证 Sentinel 熔断 + 限流规则是否正确触发（gateway-* 实验） |
| **ADR-014** (热缓存加速) | 验证 Redis 降级 ES 路径（cache-* 实验） |
| **ADR-013** (Canal 高可用) | 验证 Canal HA 故障切换 + 位点恢复（canal-* 实验） |
| **ADR-016** (多 AZ 缓存) | 验证 AZ 切换缓存预热策略（az-* 实验） |
| **ADR-022** (全链路灰度) | 验证灰度自动熔断机制（gray-* 实验） |
| **ADR-020** (Saga 事务) | 验证 Saga 降级 Choreography（saga-* 实验） |
| **ADR-015** (容量模型) | 验证 HPA 扩容策略（resource-* 实验） |
| **canary-release.md** | 灰度发布的熔断机制验证 |
| **deployment.puml** | AZ 切换实验验证部署架构的容灾能力 |

## 备选方案评估

### 方案 B：chaosblade 为主

- **优点**：轻量、学习成本低、支持非 K8s 组件
- **缺点**：实验管理散落在 Shell 脚本中，难以版本化和复用
- **适用场景**：作为 Chaos Mesh 的补充（非 K8s 组件故障注入）

### 方案 C：Litmus

- **优点**：ChaosHub 可复用社区实验，GitOps 集成好
- **缺点**：实验模板不如 Chaos Mesh 丰富，团队无使用经验
- **适用场景**：如后续社区 Litmus 成熟度显著提升可迁移
