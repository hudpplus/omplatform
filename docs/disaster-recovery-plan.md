# 容灾与演练计划

> 定义系统级容灾策略、RTO/RPO 目标、AZ 切换 SOP 和常态化演练计划。与 `degradation-strategy.md` 降级策略总图互补，本文档聚焦**故障切换与恢复**。

---

## 目录

1. [RTO / RPO 总表](#1-rto--rpo-总表)
2. [同城三中心拓扑与故障模型](#2-同城三中心拓扑与故障模型)
3. [AZ 切换流程](#3-az-切换流程)
4. [组件级故障恢复](#4-组件级故障恢复)
5. [数据一致性验证](#5-数据一致性验证)
6. [DR 演练计划](#6-dr-演练计划)
7. [通信与升级流程](#7-通信与升级流程)
8. [DR Runbook 速查表](#8-dr-runbook-速查表)
9. [与现有 ADR 的关联](#9-与现有-adr-的关联)

---

## 1. RTO / RPO 总表

### 1.1 按故障场景

> **说明**：当前架构升级为 Dual-Active 模式（参见 §2.4），AZ-B 持续预热并承接 30% 读流量，故障切换从冷启动变为预热切换。RTO 大幅缩短。

| 故障场景 | RTO 目标 | RPO 目标 | 自动程度 | 涉及服务 |
|---------|---------|---------|---------|---------|
| **AZ-A 整体宕机** | < 60s | < 5s | 半自动 | 全链路 |
| **OB Leader 宕机** | < 30s | 0（Paxos） | 全自动 | OB 自身 |
| **Redis Cluster 宕机** | < 30s（降级 ES） | N/A（缓存可重建） | 全自动 | 查询服务 |
| **ES 集群宕机** | < 5min | N/A（可重建） | 半自动 | 搜索/查询 |
| **RocketMQ Broker 宕机** | < 2min | < 5s | 全自动 | 消息驱动服务 |
| **Gateway 实例宕机** | < 10s | 0 | 全自动 | 入口流量 |
| **Canal Server 宕机** | < 20s（位点恢复） | 0（Redis 持久化位点） | 全自动 | ES 数据同步 |
| **Nacos 注册中心宕机** | < 1min（本地缓存） | 0 | 全自动 | 服务发现 |
| **Apollo 配置中心宕机** | < 1min（本地缓存） | 0 | 全自动 | 配置依赖服务 |
| **Vault 密钥管理宕机** | < 15min | 0（本地缓存密钥） | 半自动 | 安全相关 |

### 1.2 按数据等级

| 数据类型 | RPO 目标 | 说明 |
|---------|---------|------|
| 订单核心数据（OB） | 0（Paxos 多数派） | 同城三中心，3/5 仲裁 |
| 缓存数据（Redis） | 可丢失 | 缓存可从 OB/ES 重建 |
| 消息数据（RocketMQ） | < 5s（同步刷盘） | 同步复制+自动重试 |
| 搜索引擎数据（ES） | < 5s（Canal 实时同步） | 可接受秒级延迟，可从 OB 重建 |
| 审计日志 | < 1min | 本地缓存 + 异步写入 |
| 操作日志 / 事件归档 | 可容忍少量丢失 | 非关键数据 |

### 1.3 恢复优先级

```
第一优先（P0 — 必须立刻恢复）：
  ├── OB 写入路径（订单创建/支付/退款）
  ├── Gateway 入口（流量入口）
  └── Redis 缓存（查询性能）

第二优先（P1 — 5min 内恢复）：
  ├── ES 查询（订单搜索/对账）
  ├── RocketMQ 消息（异步流程）
  └── Apollo 配置（动态配置）

第三优先（P2 — 30min 内恢复）：
  ├── Canal 同步（ES 数据一致性）
  ├── 定时任务（XXL-Job）
  └── 非核心通知服务
```

---

## 2. 同城三中心拓扑与故障模型

### 2.1 物理拓扑

```
                    GSLB (DNS 全局负载均衡)
                  /          |          \
             AZ-A          AZ-B          AZ-C
           (主)            (从)          (仲裁)
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ K8s Pool │  │ K8s Pool │  │ OBServer │
        │ igw/egw  │  │ igw/egw  │  │ (Arbiter)│
        │ 服务实例   │  │ 服务实例   │  │ ES 集群   │
        │ OBProxy  │  │ OBProxy  │  │          │
        ├──────────┤  ├──────────┤  └──────────┘
        │ OBServer │  │ OBServer │
        │ (Leader) │  │ (Follower│
        │ OBServer │  │ OBServer │
        │(Follower)│  │(Follower)│
        ├──────────┤  ├──────────┤
        │ Redis    │  │ Redis    │
        │RocketMQ  │  │RocketMQ  │
        └──────────┘  └──────────┘
```

### 2.2 Paxos 权重

| AZ | OBServer | Paxos 权重 | 角色 | 说明 |
|----|---------|-----------|------|------|
| AZ-A | OBServer-1, OBServer-2 | 2 票 | Leader + Follower | 主 AZ，Leader 在此 |
| AZ-B | OBServer-3, OBServer-4 | 2 票 | Follower × 2 | 从 AZ，仅投票 |
| AZ-C | OBServer-5 (Arbiter) | 1 票 | 仲裁 | 仅投票，不存数据 |
| **合计** | 5 节点 | 5 票 | 多数派 = **3/5** | 容忍 2 节点故障 |

### 2.3 架构模式：Dual-Active（双活读）

从传统的「主备模式」升级为 **Dual-Active 模式**，AZ-B 持续承接读流量并预热缓存：

```yaml
流量分发:
  写入: AZ-A 独占（order-core/payment-core 写接口始终路由到 AZ-A）
  读取: AZ-A 70% + AZ-B 30%（order-query 两 AZ 均可处理）

AZ-B 预热策略（增强 ADR-016）:
  mode: continuous              # 持续预热（非故障时才启动）
  source-traffic-ratio: 0.3     # AZ-B 承接 30% 读流量，天然预热
  target-hit-rate: 0.85         # 稳态目标命中率 85%
  ttl-standby: 120              # AZ-B 缓存 TTL 较长，减少刷新频率

AZ 切换提升:
  旧模式: AZ-A 故障 → 冷启动 AZ-B → RTO 5min
  新模式: AZ-A 故障 → AZ-B 已有缓存 → RTO < 60s

跨 AZ 缓存设计详见 ADR-016（多 AZ 缓存优化）和 ADR-040（高性能高可用架构）。
```

### 2.4 故障模型与影响

| 故障模型 | 数据影响 | 服务影响 | 需切换 AZ？ |
|---------|---------|---------|-----------|
| AZ-A 单 OBServer 宕机 | 无（剩余副本可读） | 无（OBProxy 自动路由） | 否 |
| AZ-A 双 OBServer 宕机 | 无（多数派仍在 AZ-B+C） | 写入正常（Leader 自动切换到 AZ-B） | 否（Leader 迁移即可） |
| **AZ-A 整体宕机**（网络/电源/全部） | 无（数据副本在 AZ-B+C） | 全部影响 | ✅ **是，需切换** |
| AZ-B 单 OBServer 宕机 | 无 | 无 | 否 |
| AZ-B 双 OBServer 宕机 | 无（但是权重 2/5 丢票） | 无（AZ-A 仍服务） | 否 |
| AZ-C 仲裁宕机 | 无 | 无（仅丢 1 票，仍可形成 2/3 多数） | 否 |
| **AZ-A+B 同时故障** | **数据丢失**（只有 AZ-C 仲裁无数据） | 全部不可用 | ✅ **Region 级灾难** |

> **注意**：同城三中心架构能容忍任意单 AZ 故障或双节点故障，但**不能容忍双 AZ 同时故障**，因为唯一有数据的 AZ-C 只有仲裁节点，没有数据副本。这是同城双活（非三地五中心）的固有局限，需在架构决策中明确。
>
> **架构参考**：本文的物理部署拓扑见 `deployment.puml` 的 3-AZ 部署模型，多 AZ 两地三中心架构设计详见 ADR-016（多 AZ 缓存优化）。

---

## 3. AZ 切换流程

### 3.1 计划内切换（Scheduled Switch）

用于机架维护、版本升级、容量扩展等场景。

```
Phase 1: 切换前准备（T-30min）
  ── 确认 AZ-B 所有服务健康
  ── 确认 OB 副本同步延迟 < 1s（无积压）
  ── 启动 AZ-B 缓存预热（30min 窗口，目标命中率 > 85%）
  ── 确认 AZ-B 的 Canal cache-writer 正常运行
  ── 在钉钉群公告切换计划
  
Phase 2: 流量切换（T-0）
  ── GSLB 配置更新：将 AZ-A 权重从 100 降为 0
  ── AZ-B 权重从 0 升为 100
  ── 等待 DNS TTL 传播（60s，建议设置 TTL=60s）
  ── 确认新流量全部到达 AZ-B
  
Phase 3: 切换后验证（T+5min）
  ── 验证 Gateway 健康：igw_b + egw_b 流量正常
  ── 验证 OB 连接：所有请求路由到 AZ-B OBServer
  ── 验证 Redis 命中率 > 80%
  ── 验证 ES 查询正常
  ── 验证 RocketMQ 消费正常
  ── 运行自动化测试用例（30 个核心 API）

Phase 4: 稳态观察（T+30min）
  ── 观察错误率 < 0.1%
  ── 观察 P99 响应时间 < 基线 + 20ms
  ── 钉钉群公告切换完成
```

### 3.2 故障切换（Unplanned Failover）

用于 AZ 整体宕机等紧急场景。**不等预热完成**，走快速路径。

```
Phase 1: 故障确认（T+0 ~ T+1min）
  ── 监控告警触发（AZ-A 整体不可用）
  ── SRE 确认：确实是 AZ 级故障（而非单组件）
  ── 确认 AZ-B + AZ-C 健康
  ── 钉钉群 @所有人 发故障公告

Phase 2: 快速切换（T+1min ~ T+3min）
  ── GSLB 配置更新：AZ-A 权重降为 0
  ── AZ-B 权重升为 100
  ── OB Leader 选举确认（已在 AZ-B 或自动迁移）
  ── 不等预热完成，直接切换（缓存降级 ES 兜底）
  ── 确认 Gateway 收到流量
  
Phase 3: 切换后恢复（T+3min ~ T+10min）
  ── 缓存自动回填（请求驱动，逐步提升命中率）
  ── RocketMQ 积压消费追赶
  ── Canal 从最新位点恢复同步
  ── 检查 XXL-Job 任务是否在 AZ-B 正常触发
  ── 确认分布式锁正常（MultiLock 降级单 AZ 锁）

Phase 4: 故障复盘（T+24h）
  ── 根本原因分析
  ── 数据一致性对账（OB ↔ ES ↔ 事件归档）
  ── 更新 Runbook / 改进自动切换
```

### 3.3 AZ 切换决策树

```
AZ 级故障告警
  │
  ├── 计划内维护？
  │   └──→ 走计划内切换流程（30min 预热）
  │
  ├── AZ 整体不可用（网络/电源）？
  │   ├── 确认 AZ-B+C 健康 → 走故障切换（3min）
  │   └── AZ-B 也不可用 → 升级为 Region 级灾难
  │
  ├── OB 集群不可用？
  │   ├── 单节点：自动切换，无需干预
  │   ├── 双节点 + 多数派丢失 → 等恢复（不可写入）
  │   └── 全部不可用 → 数据层灾难，DB 恢复专家介入
  │
  ├── Redis 集群不可用？
  │   └──→ 自动降级 ES（不切换 AZ）
  │
  ├── ES 集群不可用？
  │   └──→ 降级 OB 查询（不切换 AZ）
  │
  └── 不确定？
      └──→ 按「最坏情况」走故障切换流程
```

---

## 4. 组件级故障恢复

### 4.1 OB 节点故障

```yaml
detection:
  - OBProxy 感知连接断开
  - OBServer 心跳超时（10s）
  - OB 集群自愈：Paxos 自动选主（< 30s）

failover:
  - OBProxy 自动路由到可用节点
  - Leader 节点宕机 → 自动选举新 Leader
  - 客户端无感知（连接池自动迁移）

limitations:
  - 写入可用性：需 3/5 多数派存活（最多 2 节点宕机）
  - 如需切换 AZ（AZ-A 整体宕机），GSLB 秒级生效
  - 三中心部署架构见 deployment.puml，OB 的 Paxos 自动选主依赖于三中心多数派仲裁
```

### 4.2 Redis 集群故障

```yaml
detection:
  - 应用层 Redis 连接异常 / 命令超时 / 响应错误
  - 连续 N 次异常触发熔断

action:
  - 全自动：应用层降级到 ES 查询
  - 熔断 30s 后自动恢复探测
  - 缓存写入暂停，仅读现有缓存

recovery:
  - Redis 集群恢复后，应用自动重连
  - 缓存 TTL 驱动逐步重建热点数据
  - Canal cache-writer 自动恢复写入

rto: < 30s（降级时间）
rpo: N/A（缓存可重建）
```

### 4.3 ES 集群故障

```yaml
detection:
  - ES 连接异常 / 查询超时 (> 5s)
  - ES Cluster health: red
  - 监控告警：ES 查询错误率 > 10%

action:
  - 订单查询降级：走 OB 主键/索引查询（功能受限）
  - 搜索/聚合功能关闭（管理后台搜索不可用）
  - Canal 同步暂停（保留 binlog 位点）

recovery:
  - ES 恢复后启动全量重建（XXL-Job 全量索引）
  - Canal 从恢复点继续增量同步
  - 搜索功能逐步恢复

rto: < 5min（降级后重建时间取决于数据量）
rpo: < 5s（Canal 暂停前的最后位点）
```

### 4.4 RocketMQ 集群故障

```yaml
detection:
  - Broker 连接失败
  - 消息发送超时 / NACK
  - 消费端收不到新消息

action:
  - 生产者同步降级：写操作改为同步 RPC 调用
  - 生产者异步降级：非关键消息丢弃或落本地文件
  - 消费端暂停消费（消息积压，恢复后追赶）

recovery:
  - Broker 恢复后生产者切回异步模式
  - 消费端从断点继续消费
  - 积压消息自动追赶（通过 RocketMQ 限流控制）

rto: < 2min
rpo: < 5s（同步刷盘）
```

### 4.5 Gateway 实例故障

```yaml
detection:
  - K8s Readiness 探测失败
  - 负载均衡器健康检查失败

action:
  - K8s 自动重启故障 Pod
  - 负载均衡器自动剔除故障实例
  - 其他实例接管流量（无感）

recovery:
  - 新 Pod 启动后自动注册到 Nacos
  - 负载均衡器健康检查通过后恢复流量

rto: < 10s（K8s 重新调度 + 启动，秒级）
rpo: 0（Gateway 无状态）
```

### 4.6 Apollo / Nacos 故障

```yaml
apollo:
  action:
    - Apollo Client 使用本地缓存快照
    - 配置变更无法推送（静置在最后一次有效配置）
  recovery:
    - Apollo 恢复后 Client 重新连接
    - 在离线期间错过的配置变更：手动检查或通过 ConfigChangeListener 补推
  rto: < 1min
  rpo: 0（本地缓存）

nacos:
  action:
    - 服务消费者使用本地缓存的地址列表
    - 新服务上线无法被发现（依赖 Nacos）
  recovery:
    - Nacos 恢复后注册数据自动同步
    - 本地缓存 TTL 过期后从 Nacos 重新拉取
  rto: < 1min
  rpo: 0（本地缓存）
```

---

## 5. 数据一致性验证

AZ 切换后，必须验证关键数据的最终一致性。

### 5.1 自动化验证项

```sql
-- 验证 1：OB 主键查询一致性（在切换前后各执行一次）
SELECT COUNT(*) FROM `order` WHERE gmt_create >= NOW() - INTERVAL 10 MINUTE;
-- 切换后查询结果应 ≥ 切换前（允许新订单增加）

-- 验证 2：订单详情一致性（随机抽样 100 单）
SELECT id, order_status, total_amount, gmt_modified
FROM `order` ORDER BY RAND() LIMIT 100;
-- 切换前后核心字段应一致

-- 验证 3：支付记录完整性
SELECT COUNT(*) FROM payment
WHERE gmt_create >= NOW() - INTERVAL 30 MINUTE;
-- 切换前后金额总和一致
```

### 5.2 业务验证脚本

```yaml
post_switch_checks:
  # ── API 层面验证 ──
  - name: "基础订单查询"
    api: GET /api/v1/orders/{id}
    sample: 50 笔已知订单
    success_rate: 100%
    max_p99: 200ms

  - name: "订单创建"
    api: POST /api/v1/orders
    count: 20 笔
    success_rate: 100%
    max_p99: 500ms

  - name: "订单搜索"
    api: GET /api/v1/orders?keyword=xxx
    sample: 10 个关键词
    success_rate: >= 95%
    max_p99: 500ms

  # ── 数据层面验证 ──
  - name: "OB ↔ ES 对账"
    matcher: "order_no"
    tolerance: 0
    sample: 1000 笔最近订单
    expected: "全部一致"

  - name: "MQ 消费进度"
    check: "无堆积（lag < 100）"
    sample: "所有消费者组"
```

### 5.3 对账失败的处理

```yaml
reconciliation_failures:
  - issue: "OB 有数据，ES 无数据"
    severity: HIGH
    action: "Canal 增量同步追赶，必要时触发全量索引"

  - issue: "OB 和 ES 数据不一致（金额/状态）"
    severity: CRITICAL
    action: "以 OB 为准，修复 ES 索引 + 排查原因（数据流问题）"

  - issue: "MQ 消息丢失（发送成功但未消费）"
    severity: HIGH
    action: "从事件归档表重建消息，重新投递"
```

---

## 6. DR 演练计划

### 6.1 演练频率与范围

| 演练类型 | 频率 | 范围 | 参与方 | 时长 |
|---------|------|------|--------|------|
| **组件级故障注入** | 每月 | 单组件故障 + 自动恢复 | SRE + 开发值班 | 30min |
| **AZ 计划内切换** | 每季度 | 全链路 AZ 切换 | SRE + 全链路开发 | 2h |
| **AZ 故障切换（GameDay）** | 每半年 | 模拟 AZ 宕机 + 真实切换 | 全团队（包括 oncall） | 4h |
| **全流程混沌演练** | 每年 | 多组件故障 + 组合故障 | 全团队 + 架构组 | 8h |

### 6.2 组件级故障注入（月度 — 30min）

```yaml
schedule: "每月第一个周三 14:00-14:30（避免业务高峰期）"

scenarios:
  - name: "Redis 宕机"
    tool: "chaosblade: `blade create docker kill --container omplatform-redis`"
    validation:
      - "应用正确降级 ES"
      - "接口 P99 < 500ms"
      - "降级计数正确上报"
    recovery: "Redis 自动重启 (docker compose up -d redis)"
    
  - name: "Canal Server Kill"
    tool: "chaosblade / kubectl delete pod canal-server"
    validation:
      - "ZK 感知断开 -> 从节点接管"
      - "RTO < 30s"
      - "位点恢复正常（无数据重复/丢失）"
    recovery: "自动"

  - name: "Gateway 实例 Kill"
    tool: "kubectl delete pod internal-gateway-xxx"
    validation:
      - "K8s 重新调度新 Pod"
      - "负载均衡器剔除故障实例"
      - "请求无感知"
    recovery: "自动"

  - name: "OBServer 进程 Kill"
    tool: "chaosblade: kill OBServer 进程"
    validation:
      - "OBProxy 自动路由到其他节点"
      - "10s 内 Paxos 完成选主"
      - "写入不受影响"
    recovery: "自动"
```

### 6.3 AZ 计划内切换（季度 — 2h）

```yaml
schedule: "每季度第一周某工作日 22:00-00:00（低峰期）"

steps:
  1. 事前准备 (30min):
     - 检查 AZ-B 所有服务健康
     - 确认 OB 副本同步延迟
     - 启动缓存预热
     - 通知所有相关团队
  
  2. 切换执行 (15min):
     - GSLB 权重调整监控
     - 验证流量迁移
     - Runbook 逐条执行
  
  3. 验证 (45min):
     - 核心 API 回归
     - 数据一致性对账
     - 性能基线对比
  
  4. 回退预案 (30min):
     - 如有问题立即切回 AZ-A
     - 切换后数据校验

success_criteria:
  - P99 退化 < 基线 + 20ms
  - 错误率 < 0.1%
  - 数据一致率 = 100%
```

### 6.4 AZ 故障 GameDay（半年 — 4h）

```yaml
schedule: "每半年一次，提前 2 周通知"

format:
  - 不预先通知具体故障时间
  - 仅通知 "$DATE 本周某天会有故障注入"
  - SRE 按照真实故障流程响应

scenarios (随机选 1-2 个):
  - "AZ-A 网络完全中断（模拟光缆问题）"
  - "AZ-A 所有服务不可用（模拟电力故障）"
  - "AZ-A + AZ-B 间网络中断（网络分区）"

validation:
  - SRE 响应时间
  - 故障确认 + 切换决策时间
  - 切换执行 + 流量恢复时间
  - 通知 Communication 流程
  - 切换后功能验证
  
post_game_day:
  - 复盘会（48h 内）
  - Runbook 更新
  - 自动化改进项（如有）
  - 评分与改进计划
```

---

## 7. 通信与升级流程

### 7.1 故障等级与通知

```yaml
severity_levels:
  P0:
    label: "严重故障"
    definition: "核心功能（订单/支付）不可用，影响面超过 10% 用户"
    notification: "电话 + 钉钉 @所有人"
    response_time: "5min 内响应"
    escalation: "15min 未恢复 → 通知技术负责人"
    
  P1:
    label: "高优故障"
    definition: "非核心功能不可用或单 AZ 故障已切换"
    notification: "钉钉群 @值班 SRE"
    response_time: "15min 内响应"
    escalation: "30min 未恢复 → 通知技术负责人"
    
  P2:
    label: "一般故障"
    definition: "单组件降级（Redis/ES 降级）、性能退化 < 2x"
    notification: "钉钉群通知（不 @人）"
    response_time: "1h 内响应"
    escalation: "4h 未恢复 → 通知团队负责人"

  P3:
    label: "告警"
    definition: "无用户影响，仅需要关注"
    notification: "监控告警（不通知）"
    response_time: "下一个工作日"
    escalation: "无需升级"
```

### 7.2 故障通信模板

```
[故障等级] [组件名] [故障描述]

时间：2026-06-13 14:30:00
影响面：订单查询 P99 从 50ms 上升到 500ms（10x）
状态：⏳ 排查中 / 🔧 修复中 / ✅ 已恢复

当前动作：
  ├── 已确认：Redis 集群 AZ-A 节点宕机
  ├── 已自动降级：ES 兜底查询
  └── 处理中：Redis 节点自动重建（预计 5min）

负责人：@张三
```

### 7.3 升级流程

```
P0/P1 故障升级路径：

  Layer 1 (0-15min): 值班 SRE
    ── 确认故障 → 执行 Runbook → 尝试恢复
  
  Layer 2 (15-30min): 技术负责人
    ── 协调多团队 → 复杂恢复决策 → 人工干预
  
  Layer 3 (30-60min): 架构师 + 管理层
    ── 数据修复决策 → 业务影响评估 → 对外公告
  
  Layer 4 (> 60min): 应急指挥部
    ── 全量资源投入 → 业务连续性启用
```

---

## 8. DR Runbook 速查表

### AZ-A 整体宕机

```yaml
title: "AZ-A 整体宕机 Runbook"
trigger: "AZ-A 全部服务不可用"

steps:
  1. "确认 AZ-A 不可用（钉钉/监控/GSLB 检测）"
     command: "curl -s -o /dev/null -w '%{http_code}' https://igw-a.omplatform.com/health"
  
  2. "确认 AZ-B + AZ-C 健康"
     command: "curl -s https://igw-b.omplatform.com/health | jq .status"
  
  3. "GSLB 切换流量到 AZ-B"
     command: "az-switch.sh --from=az-a --to=az-b --force"
  
  4. "确认 OB Leader 在 AZ-B"
     command: "obclient -h obproxy-b -P2883 -e 'SELECT svr_ip, role FROM oceanbase.__all_server'"

  5. "验证核心服务"
     command: "run-dr-check.sh --mode=full --target=az-b"
  
  6. "钉钉群公告切换完成"
```

### Redis 集群宕机

```yaml
title: "Redis 集群宕机 Runbook"
trigger: "缓存命中率骤降 / Redis 连接告警"

steps:
  1. "确认降级生效（降级 ES 查询自动触发）"
     command: "curl -s https://igw.omplatform.com/health | jq .degrade_status"
  
  2. "检查 Redis 集群状态"
     command: "redis-cli -h redis-a -p 6379 cluster info | grep cluster_state"
  
  3. "如果集群状态异常：等待自动恢复（无需手动切换）"
  
  4. "如果 10min 仍未恢复 → 人工介入排查"
     command: "kubectl logs -n middleware redis-0 --tail=100"
  
  5. "Redis 恢复后确认缓存自重建"
     command: "curl -s http://prometheus:9090/api/v1/query?query=hotcache_hit_rate"
```

### ES 集群宕机

```yaml
title: "ES 集群宕机 Runbook"
trigger: "ES 查询错误率 > 10%"

steps:
  1. "确认 ES 集群状态"
     command: "curl -s http://es:9200/_cluster/health | jq .status"
  
  2. "如果 status=red → 等待自动恢复或重启 ES 节点"
  
  3. "通知 Canal 同步暂停（保留位点）"
     # Canal 自动暂停（连接断开后自动停止）
  
  4. "订单查询降级为 OB 查询（自动，应用层配置）"
  
  5. "ES 恢复后启动全量重建"
     command: "xxl-job-trigger.sh --jobId=es-full-rebuild"
  
  6. "验证 ES 数据一致性"
     command: "xxl-job-trigger.sh --jobId=ob-es-reconciliation"
```

### RocketMQ Broker 宕机

```yaml
title: "RocketMQ Broker 宕机 Runbook"
trigger: "RocketMQ 发送/消费告警"

steps:
  1. "检查 Broker 状态"
     command: "kubectl get pods -n middleware | grep rocketmq"
  
  2. "如果单节点宕机 → RocketMQ 集群自动切换（需部署主备）"
     # 需要确认：当前架构单 AZ 内 RocketMQ 是否支持主备
  
  3. "如果整个集群不可用 → 通知开发团队"
     # 生产者逐步降级为同步 RPC 调用
```

---

## 9. 与现有 ADR 的关联

| 文档 | 关系 |
|------|------|
| **ADR-016** (多 AZ 缓存优化) | 提供两地三中心架构下的跨 AZ 缓存预热策略、MultiLock 分布式锁、AZSwitchCoordinator 协调器 |
| **ADR-013** (Canal 高可用) | 定义 Canal 故障切换 RTO 15-20s、位点四级恢复策略 |
| **ADR-015** (容量规划) | HPA 弹性伸缩与容量保障，DR 切换后的资源评估 |
| **ADR-028** (密钥管理) | Vault 不可用时的本地缓存降级（RTO < 15min） |
| **ADR-014** (CQRS 热缓存加速) | Redis 降级 ES 的自动恢复机制（CQRS 缓存层，不涉及数据库高可用） |
| **ADR-022** (全链路灰度) | 灰度发布作为变更容灾手段，异常时自动熔断回滚 |
| **degradation-strategy.md** | 降级策略总图 — 本文的组件级故障恢复基于其中定义的降级等级 |
| **deployment.puml** | 同城三中心物理拓扑 — 本文的 AZ 切换基于此部署模型 |
| **ADR-012** (ES ILM) | ES 自动快照与恢复机制 |
| **ADR-031** (数据生命周期) | 冷数据 OSS 归档的异地容灾 |
| **ADR-040** (高性能高可用) | 定义 Dual-Active 模式、99.99% 可用性分解、缓存预热策略 |


---

## 附录 A：切换脚本核心逻辑

```bash
#!/bin/bash
# az-switch.sh — AZ 切换脚本

set -euo pipefail

FROM_AZ=${1:-}
TO_AZ=${2:-}
FORCE=${3:-false}

# 1. 前置健康检查
echo "[1/6] 健康检查目标 AZ..."
bash check-az-health.sh "$TO_AZ" || {
    [ "$FORCE" = "true" ] && echo "WARN: 强制切换，跳过健康检查"
}

# 2. OB 副本延迟检查（计划内切换）
if [ "$FORCE" != "true" ]; then
    echo "[2/6] 检查 OB 同步延迟..."
    LAG=$(obclient -e "SELECT MAX(sync_lag) FROM oceanbase.__all_server_health")
    if [ "$LAG" -gt 1 ]; then
        echo "ERROR: OB 同步延迟 $LAG s > 1s，请等待后再切换"
        exit 1
    fi
fi

# 3. GSLB 切换
echo "[3/6] GSLB 流量切换..."
gslb-api --set-weight "$FROM_AZ" 0
gslb-api --set-weight "$TO_AZ" 100
sleep 60  # 等待 DNS TTL 传播

# 4. 切换后验证
echo "[4/6] 切换后验证..."
bash run-dr-check.sh --mode=quick --target="$TO_AZ" || {
    echo "ERROR: 切换后验证失败，启动回滚..."
    bash az-rollback.sh "$FROM_AZ" "$TO_AZ"
    exit 1
}

# 5. 稳态观察
echo "[5/6] 稳态观察(60s)..."
sleep 60
bash run-dr-check.sh --mode=steady --target="$TO_AZ"

# 6. 完成
echo "[6/6] AZ 切换完成: $FROM_AZ → $TO_AZ"
bash notify.sh "AZ 切换完成: $FROM_AZ → $TO_AZ"
```
