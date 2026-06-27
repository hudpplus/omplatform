# ADR-047：风控集成 (Risk Control Integration)

> **状态**: 已接受  
> **创建日期**: 2026-06-13  
> **影响范围**: order-core（风控预检查）、workflow-service（审核回调）、payment-core（退款风控）、cart-service（下单预检）、aftersale-service
>
> **本文档系统设计订单中台与外部风控平台的集成方案，涵盖同步预检查（下单）、异步审核队列（可疑订单）、HOLD 状态联动、黑/白名单本地缓存、退款风控评分以及降级策略。**

---

## 1. 背景

### 现状分析

当前风控能力仅作为概念在多个 ADR 中被引用，缺乏统一的集成设计：

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| P1 | **无统一集成设计** | 风控能力散落多处引用，无独立 ADR | 集成方式不统一，每个新功能重复对接 |
| P2 | **HOLD 触发不完整** | state-machine.md 有 PENDING_PAY→HOLD 和 PAID→HOLD 但无风控维度的触发逻辑 | 风险订单无法自动挂起 |
| P3 | **无本地缓存** | 黑白名单每次查外部平台，延迟不可控 | 高峰期间外部平台抖动影响下单 |
| P4 | **无降级策略** | 外部风控不可用怎么办未定义 | 风控故障可能导致全站停摆 |
| P5 | **退款风控单一** | ADR-042 仅定义 riskScore 阈值，无完整风控流程 | 退款审核效率低 |

### 现有风控引用分布

| 引用 | 位置 | 内容 |
|------|------|------|
| 风控外部系统 | context-diagram.puml | `System_Ext(risk_control, "风控系统", "订单风控审核、反欺诈")` |
| HOLD→风控 | state-machine.md | `PENDING_PAY --> HOLD: 库存不足/风控` |
| HOLD→风控 | state-machine.md | `PAID --> HOLD: 库存不足/风控` |
| B2B 风控 | ADR-037 §4 | `b2bRiskCheckHandler` 同步 Hook |
| 买家风控等级 | ADR-039 | OrderCreateService 预检查含 buyerRiskLevel |
| 退款风控 | ADR-042 §4.3 | riskScore 阈值判定自动/人工审核 |
| 渠道订单风控 | ADR-036 | 渠道订单接入含风控预检步骤 |
| P2 缺口 | completeness-report §5.1 | 下单风控预检、异步审核队列、风险订单自动 HOLD |

---

## 2. 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | 同步预检查 | 下单时防欺诈检查，P99 ≤ 200ms（含黑名单缓存命中时 ≤ 10ms） |
| G2 | 异步审核队列 | 可疑订单入队列，10min 后重评估，自动 HOLD/放行 |
| G3 | HOLD 集成 | 复用 ADR-039 HOLD 状态机，风控→HOLD→人工审核→RELEASE/CANCEL |
| G4 | 黑白名单缓存 | Redis 本地缓存，P99 ≤ 10ms，60s 刷新 |
| G5 | 退款风控 | 风险评估分类：自动放行/人工审核/拒绝 |
| G6 | 降级策略 | 三级降级（L0/L1/L2）Apollo 可配，不影响核心交易 |

---

## 3. 战术 DDD 设计

### 3.1 聚合根

| 聚合根 | 数据库表 | 标识符 | 生命周期 |
|--------|---------|--------|---------|
| **RiskCheckRecord** (风控检查记录) | risk_check_record | check_id (Long, auto) | 一次检查创建，完成后只读 |
| **RiskReviewRecord** (异步审核记录) | risk_review_record | review_id (Long, auto) | 审核完成即终态 |

### 3.2 Entity vs Value Object

| 类型 | 名称 | 标识 | 原因 |
|------|------|------|------|
| **Entity** | RiskCheckRecord | check_id | 有决策状态（PASS/REVIEW/REJECT），且与外部 trace 关联 |
| **Entity** | RiskReviewRecord | review_id | 审核状态机（PENDING→APPROVED/REJECTED） |
| **Entity** | BlacklistEntry | entity_type + entity_value | 可增删，有生命周期 |
| **Entity** | WhitelistEntry | entity_type + entity_value | 可增删，有生命周期 |
| **Value Object** | RiskScore | score + level + detail | 风控评分快照，不可变 |
| **Value Object** | DegradationConfig | level + strategy | 降级配置，替换即生效 |

### 3.3 领域事件

| 事件 | 触发点 | 消费者 |
|------|--------|--------|
| `RiskCheckPassed` | 预检查通过 | order-core（正常创建订单） |
| `RiskCheckRejected` | 预检查拒绝 | order-core（返回错误） |
| `RiskReviewApproved` | 异步审核通过 | order-core（释放 HOLD） |
| `RiskReviewRejected` | 异步审核拒绝 | order-core（维持 HOLD → CANCEL） |
| `RiskDegradationChanged` | 降级等级变化 | all services（调整风控调用策略） |

### 3.4 不变条件（Invariants）

| 规则 | 约束 | 保障方式 |
|------|------|---------|
| REVIEW 决策必有审核记录 | check_record.decision=REVIEW → 对应 review_record 存在 | 对账任务每日校验 |
| 订单 HOLD 与风控决策一致 | review_record.REJECTED → order.status=HOLD | 状态机守卫校验 |
| 降级不降安全 | L2 跳过时记录降级日志，不造假 PASS | 日志审计 |
| 黑白名单不重叠 | 同一 entity 不同时出现在白名单和黑名单 | 应用层校验 |

### 3.5 Repository 模式

```
RiskCheckRepository (Interface)
  └── RiskCheckDBRepository (OB)

BlacklistRepository (Interface)
  ├── BlacklistCacheRepository (Redis, 读缓存, TTL 60s)
  └── BlacklistDBRepository (OB, 读写)

WhitelistRepository (Interface)
  ├── WhitelistCacheRepository (Redis, 读缓存, TTL 60s)
  └── WhitelistDBRepository (OB, 读写)
```

---

## 4. 决策

### 决策 1：同步预检查模式

| 方案 | 评估 |
|------|------|
| **完全 MQ 异步** | 无延迟但下单后才检查，已产生订单数据，回滚成本高 |
| **Dubbo 同步调用 500ms 超时 + 熔断** | ✅ **选中** — 下单前完成风控判定，保证完整性；500ms 超时搭配熔断器，降级策略 Apollo 可配（fail-open/fail-close） |

**调用链路**：
```
order-core.createOrder()
  → RiskPreCheckInterceptor
      → 1. 本地黑名单检查（Redis, ~5ms）
          → HIT → REJECT（立即返回）
      → 2. 本地白名单检查（Redis, ~5ms）
          → HIT → PASS（跳过外部）
      → 3. 外部风控平台（Dubbo, ~100-300ms）
          → PASS → 继续下单
          → REVIEW → 下单+入异步审核队列
          → REJECT → 返回错误码
      → 4. 超时/异常 → 根据降级等级处理
```

### 决策 2：异步审核队列

| 方案 | 评估 |
|------|------|
| **XXL-Job 轮询审核表** | 简单但轮询间隔不可控，实时性差 |
| **RocketMQ 有序消息（按 orderId 分片）** | ✅ **选中** — 同订单有序处理，延迟 10min 后自动重评估；审核事件实时驱动 |

**审核流程**：
```
下单时外部平台返回 REVIEW
  → 发送延迟消息到 risk.review topic（10min 延迟）
  → 10min 后消费
      → 重新调用外部风控查询
      → APPROVED → 修改 risk_review_record 状态 = APPROVED，订单自动放行
      → REJECTED → 修改 risk_review_record 状态 = REJECTED，触发 HOLD
```

### 决策 3：黑/白名单存储

| 方案 | 评估 |
|------|------|
| **每次查询外部平台** | 实时性最好但依赖外部可用性，高峰延迟不可控 |
| **Redis 本地缓存 + 60s XXL-Job 刷新** | ✅ **选中** — 极低延迟（P99 10ms）；缓存可在外部平台不可用时独立工作；Apollo TTL 可配 |

**缓存结构**：
```
Redis Key                           Type     Value
risk:blacklist:user:{userId}        String   reason + createdAt
risk:blacklist:device:{deviceId}    String   reason + createdAt
risk:blacklist:ip:{ip}              String   reason + createdAt
risk:whitelist:user:{userId}        String   reason + createdAt
risk:rule:blacklist_version         String   version（用于增量更新）
risk:rule:whitelist_version         String   version
```

### 决策 4：HOLD 状态集成

| 方案 | 评估 |
|------|------|
| **新定义风控 HOLD 状态** | 与现有 HOLD 状态重复（库存不足/风控），状态机复杂化 |
| **复用 ADR-039 HOLD 状态机** | ✅ **选中** — HOLD 单状态覆盖库存不足和风控两种原因；通过 `hold_reason` 区分；复用已有 HOLD→PENDING_PAY/PAID→CANCEL 转换 |

**HOLD 集成点**：
- 风控导致的 HOLD 复用 ADR-039 §3.3 HOLD 生命周期
- `hold_reason = RISK_CONTROL`（区别于 `hold_reason = INSUFFICIENT_STOCK`）
- 审核通过 → RELEASE（回到原有状态）
- 审核拒绝 → CANCEL（走订单取消流程）

### 决策 5：退款风控评分

| 方案 | 评估 |
|------|------|
| **所有退款走人工审核** | 安全但效率低，低风险退款延迟大 |
| **复用 ADR-042 §4.3 riskScore + 三级分档** | ✅ **选中** — 三档：AUTO_APPROVE（≤ 200 元 + low risk）、MANUAL_REVIEW（medium risk）、REJECT（high risk） |

**分档阈值**（Apollo 可配置）：

| 档位 | 条件 | 处理方式 |
|------|------|---------|
| ✅ 自动放行 | 退款金额 ≤ ¥200 + riskScore < 30 + 订单未发货 | 自动审核通过 |
| 🔍 人工审核 | 30 ≤ riskScore < 70 或 退款金额 > ¥200 | 入退款审核工单 |
| 🚫 拒绝 | riskScore ≥ 70 或 用户命中黑名单 | 直接拒绝退款 |

### 决策 6：降级策略

| 方案 | 评估 |
|------|------|
| **无降级** | 风控故障 = 全站无法下单，不符合业务连续性要求 |
| **三级降级（L0/L1/L2）Apollo 可配** | ✅ **选中** — 运营可根据大促/大促期/风控故障等场景切换等级 |

| 等级 | 名称 | 行为 |
|------|------|------|
| **L0** | 全量防护 | 外部平台 + 黑白名单缓存 + 异步审核（默认） |
| **L1** | 降级防护 | 仅黑白名单缓存（跳过外部平台），同步预检只走本地 |
| **L2** | 跳过 | 跳过所有风控检查，仅记录日志用于事后审计 |

---

## 4. 系统架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            API Gateway Layer                              │
│     IGW Buyer / IGW Admin                                                  │
│     下单/退款 → order-core → 触发风控预检查                                │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────┐
│                     risk-integration（订单平台内部组件，非独立服务）         │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 1: Pre-Check Interceptor 预检查拦截器                        │    │
│  │  ┌──────────────────┐ ┌────────────────┐ ┌────────────────────┐  │    │
│  │  │ BlacklistChecker  │ │ WhitelistChecker│ │ ExternalRiskCaller │  │    │
│  │  │ (Redis 缓存检查)  │ │ (Redis 缓存检查)│ │ (Dubbo→外部平台)  │  │    │
│  │  └──────────────────┘ └────────────────┘ └────────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 2: Review Queue 异步审核队列                                 │    │
│  │  ┌──────────────────────┐ ┌──────────────┐ ┌──────────────────┐  │    │
│  │  │ ReviewMessageProducer│ │ ReviewConsumer │ │ ReviewCallback   │  │    │
│  │  │ (RocketMQ 延迟消息)  │ │ (10min 消费)  │ │ (审核结果处理)   │  │    │
│  │  └──────────────────────┘ └──────────────┘ └──────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 3: HOLD Manager + Refund Scorer                             │    │
│  │  ┌────────────────────┐ ┌──────────────────┐ ┌────────────────┐  │    │
│  │  │ HoldTriggerService  │ │ RefundRiskScorer  │ │ OrderGuard     │  │    │
│  │  │ (风控→HOLD 触发)   │ │ (退款风险评估)   │ │ (状态守卫)     │  │    │
│  │  └────────────────────┘ └──────────────────┘ └────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 4: Cache Sync + Audit 缓存同步与审计                         │    │
│  │  ┌──────────────────┐ ┌────────────────┐ ┌────────────────────┐  │    │
│  │  │ BlacklistSyncJob  │ │ WhitelistSyncJob│ │ RiskAuditLogger    │  │    │
│  │  │ (XXL-Job 60s)    │ │ (XXL-Job 60s)  │ │ (风控审计日志)     │  │    │
│  │  └──────────────────┘ └────────────────┘ └────────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
   ┌─────▼──────┐      ┌──────▼───────┐     ┌───────▼──────────┐
   │ Redis      │      │  OceanBase   │     │  External        │
   │ Cluster    │      │  6 张表      │     │  Risk Platform   │
   │ 黑白名单   │      │  审核记录    │     │  (Dubbo/mTLS)    │
   │ 缓存       │      │  日志        │     │                  │
   └────────────┘      └──────────────┘     └──────────────────┘
```

---

## 5. 核心流程

### 5.1 下单风控预检流程

```
用户提交订单
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│ RiskPreCheckInterceptor                                      │
│                                                             │
│  Step 1: 查询降级等级 (Apollo)                               │
│  ├─ L2 (跳过) → 记录日志, PASS, 继续下单                     │
│  └─ L0/L1 → 继续                                            │
│                                                             │
│  Step 2: 黑名单检查 (Redis)                    ← 10ms P99   │
│  ├─ HIT → REJECT, 返回错误码 "RISK_REJECTED"                │
│  └─ NOT HIT → 继续                                          │
│                                                             │
│  Step 3: 白名单检查 (Redis)                    ← 10ms P99   │
│  ├─ HIT → PASS, 跳过后续外部检查                             │
│  └─ NOT HIT → 继续 (L1 到此为止)                            │
│                                                             │
│  Step 4: 外部风控平台 (Dubbo)                  ← 200ms P99  │
│  ├─ PASS → 继续下单                                         │
│  ├─ REVIEW → 下单 + 入异步审核队列                           │
│  ├─ REJECT → 返回错误码 "RISK_REJECTED"                     │
│  └─ 超时/异常 → 按降级策略处理                              │
│      ├─ fail-open → PASS (以可用性优先)                     │
│      └─ fail-close → REJECT (以防风险优先)                  │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
 继续下单 / 返回错误
```

### 5.2 异步审核 + HOLD 流程

```
下单时外部风控返回 REVIEW
    │
    ▼
┌────────────────────────────────────────────┐
│ 1. 创建 risk_check_record (status=REVIEW)  │
│ 2. 发送 RocketMQ 延迟消息 (10min)          │
│    topic: risk.review                      │
│    tag: AUTO_REVIEW                        │
│    key: orderNo                            │
└────────────────────────────────────────────┘
    │ 10min 后
    ▼
┌────────────────────────────────────────────┐
│ 3. 消费 ReviewMessage                      │
│ 4. 重新调用外部风控平台查询                 │
│                                            │
│    ├─ APPROVED →                            │
│    │   update risk_review_record=APPROVED   │
│    │   订单正常流转                         │
│    │                                        │
│    └─ REJECTED →                            │
│        update risk_review_record=REJECTED   │
│        → 触发 HOLD                          │
│            ├─ PENDING_PAY→HOLD              │
│            ├─ PAID→HOLD                     │
│            └─ 发 risk.order_hold 事件       │
└────────────────────────────────────────────┘
    │
    ▼
┌────────────────────────────────────────────┐
│ 5. 人工审核 (风控运营团队)                  │
│     Admin API: POST /v1/admin/risk/review   │
│                                            │
│    ├─ 审核通过 → RELEASE                   │
│    │   HOLD→原状态(PAID/PENDING_PAY)       │
│    │   发 risk.order_released 事件          │
│    │                                        │
│    └─ 审核拒绝 → CANCEL                    │
│        HOLD→CANCELLED                      │
│        发 order.cancelled 事件              │
│        → 触发 Saga 补偿                    │
└────────────────────────────────────────────┘
```

### 5.3 退款风控流程

```
用户提交退款请求
    │
    ▼
┌──────────────────────────────────────────────────┐
│ RefundRiskScorer                                  │
│                                                   │
│  Step 1: 计算 riskScore                           │
│  输入: 退款金额、用户历史退款率、订单状态、       │
│        用户等级、设备指纹、ip                     │
│  输出: score (0-100)                              │
│                                                   │
│  Step 2: 三档分档 (Apollo 阈值可配)               │
│  ├─ score < 30 + 金额 ≤ 200 + 未发货             │
│  │  → AUTO_APPROVE (自动放行, 不走人工)           │
│  │                                                   │
│  ├─ 30 ≤ score < 70 或 金额 > 200                │
│  │  → MANUAL_REVIEW (入审核工单)                  │
│  │  → 通知风控运营团队                             │
│  │                                                   │
│  └─ score ≥ 70 或 用户命中黑名单                  │
│     → REJECT (直接拒绝退款)                       │
│     → 发 risk.refund_rejected 事件                │
└──────────────────────────────────────────────────┘
```

---

## 6. 数据模型

```sql
-- 风控预检查记录表
CREATE TABLE risk_check_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no        VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,
    check_type      VARCHAR(20) NOT NULL COMMENT 'PRE_CHECK(下单预检)/REFUND_CHECK(退款风控)',
    risk_level      VARCHAR(10) COMMENT 'LOW/MEDIUM/HIGH',
    decision        VARCHAR(20) NOT NULL COMMENT 'PASS/REVIEW/REJECT',
    score           INT COMMENT '风控评分 0-100',
    check_detail    JSON COMMENT '检查详情: 黑白名单结果/外部平台响应',
    external_trace_id VARCHAR(64) COMMENT '外部风控平台追踪 ID',
    degrade_level   VARCHAR(5) COMMENT '执行时的降级等级 L0/L1/L2',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_no),
    INDEX idx_user (user_id),
    INDEX idx_created (created_at)
);

-- 异步审核记录表
CREATE TABLE risk_review_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    check_record_id BIGINT NOT NULL,
    order_no        VARCHAR(64) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    review_type     VARCHAR(20) NOT NULL COMMENT 'AUTO(系统自动)/MANUAL(人工)',
    risk_level      VARCHAR(10) COMMENT '当前风险等级',
    reviewer        VARCHAR(50) COMMENT '审核人（人工审核时）',
    review_comment  VARCHAR(500) COMMENT '审核意见',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at     DATETIME COMMENT '审核完成时间',
    
    INDEX idx_order (order_no),
    INDEX idx_status (status)
);

-- 规则缓存表（本地镜像）
CREATE TABLE risk_rule_cache (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_type       VARCHAR(30) NOT NULL COMMENT 'BLACKLIST/WHITELIST/SCORE_THRESHOLD',
    entity_type     VARCHAR(20) COMMENT 'USER/DEVICE/IP',
    entity_value    VARCHAR(128) COMMENT '实体值',
    rule_config     JSON COMMENT '规则配置',
    effective_from  DATETIME NOT NULL,
    effective_to    DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_type_entity (rule_type, entity_type, entity_value),
    INDEX idx_effective (effective_from, effective_to)
);

-- 黑名单表
CREATE TABLE risk_blacklist (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type     VARCHAR(20) NOT NULL COMMENT 'USER/DEVICE/IP/PHONE',
    entity_value    VARCHAR(128) NOT NULL COMMENT '用户ID/设备指纹/IP/手机号',
    reason          VARCHAR(200) COMMENT '拉黑原因',
    source          VARCHAR(50) COMMENT '来源: MANUAL/SYSTEM/EXTERNAL',
    expire_at       DATETIME COMMENT '过期时间（NULL=永久）',
    created_by      VARCHAR(50),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_entity (entity_type, entity_value)
);

-- 白名单表
CREATE TABLE risk_whitelist (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type     VARCHAR(20) NOT NULL COMMENT 'USER/DEVICE/IP/PHONE',
    entity_value    VARCHAR(128) NOT NULL,
    reason          VARCHAR(200),
    source          VARCHAR(50) DEFAULT 'MANUAL',
    expire_at       DATETIME,
    created_by      VARCHAR(50),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_entity (entity_type, entity_value)
);

-- 退款风控评分表
CREATE TABLE risk_refund_score (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no        VARCHAR(64) NOT NULL,
    refund_no       VARCHAR(64) COMMENT '退款单号',
    user_id         BIGINT NOT NULL,
    score           INT NOT NULL COMMENT '风控评分 0-100',
    decision        VARCHAR(20) NOT NULL COMMENT 'AUTO_APPROVE/MANUAL_REVIEW/REJECT',
    score_detail    JSON COMMENT '评分详情',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_order (order_no),
    INDEX idx_user (user_id)
);
```

---

## 7. API 设计

### Internal API（服务间调用）

| 方法 | 端点 | 调用方 | 说明 |
|------|------|--------|------|
| 预检查 | `POST /api/backend/v1/risk/pre-check` | order-core | 下单前调用，返回 PASS/REVIEW/REJECT |
| 审核回调 | `POST /api/backend/v1/risk/review/callback` | MQ consumer | 异步审核结果处理 |
| 退款评分 | `POST /api/backend/v1/risk/refund-score` | payment-service | 退款前计算风控评分 |
| 查询黑白名单 | `GET /api/backend/v1/risk/blacklist/check?entityType=USER&value={id}` | pre-check | 黑名单查询 |
| 查询风控记录 | `GET /api/backend/v1/risk/check-record?orderNo={orderNo}` | order-core | 查询订单风控记录 |

### Admin API

| 方法 | 端点 | 说明 |
|------|------|------|
| 黑名单 CRUD | `POST /api/admin/v1/risk/blacklist` | 黑名单增删改查 |
| 白名单 CRUD | `POST /api/admin/v1/risk/whitelist` | 白名单增删改查 |
| 审核队列 | `GET /api/admin/v1/risk/reviews?status=PENDING` | 待审核队列查看 |
| 人工审核 | `POST /api/admin/v1/risk/reviews/{id}/approve` | 人工审核通过 |
| 人工拒绝 | `POST /api/admin/v1/risk/reviews/{id}/reject` | 人工审核拒绝 |
| HOLD 队列 | `GET /api/admin/v1/risk/holds` | 查看 HOLD 中的风险订单 |
| 降级配置 | `PUT /api/admin/v1/risk/degrade-level` | 设置降级等级 L0/L1/L2 |

---

## 8. 事件定义

| 事件 | 生产者 | 消费者 | 触发条件 |
|------|--------|--------|---------|
| `risk.order_hold` | risk-integration | order-core, notification | 风控审核拒绝 → HOLD |
| `risk.order_released` | risk-integration | order-core, notification | HOLD 审核通过 → 放行 |
| `risk.review_required` | risk-integration | workflow-service | 风控 REVIEW 需要人工审核 |
| `risk.refund_rejected` | risk-integration | payment-service, notification | 退款被风控拒绝 |
| `risk.refund_auto_approved` | risk-integration | payment-service | 退款自动审核通过 |
| `risk.alert` | risk-integration | notification (运营团队) | 风控异常告警（如外部平台不可用） |

---

## 9. 非功能设计

| 指标 | 目标 | 实现 |
|------|------|------|
| 预检查 P99 | ≤ 200ms | 黑白名单 Redis (10ms) + 外部 Dubbo (200ms 超时) |
| 黑白名单查询 P99 | ≤ 10ms | 纯 Redis 读，无 DB，60s TTL 自动刷新 |
| 黑名单同步延迟 | ≤ 60s | XXL-Job 60s 轮询刷新本地缓存 |
| 外部平台不可用 | 不影响下单 | L1 降级仅用本地缓存 + L2 跳过 |
| 熔断器 | 50% 错误率 → 熔断 30s | Sentinel circuit breaker |
| 异步审核延迟 | 10min ± 1min | RocketMQ 延迟消息 10min |
| 风控审计日志 | 100% 记录 | 所有检查记录写入 risk_check_record + 日志采集 |

---

## 10. 安全设计

| 安全维度 | 措施 |
|---------|------|
| 外部风控通信 | Dubbo + mTLS + 签名校验（context-diagram.puml 已定义） |
| 黑白名单鉴权 | Admin API 仅 IGW Admin 可访问，操作审计日志 |
| 预检查 IDOR 防护 | 检查 userId 必须等于当前登录用户 |
| 审核回调防重放 | request_id 幂等（ADR-030）+ 回调接口签名校验 |
| 风控评分信息保护 | 评分详情仅风控团队 /admin 接口可查看 |

---

## 11. 实施计划

| 阶段 | 内容 | 人天 |
|------|------|------|
| Phase 1: 黑白名单缓存 | BlacklistChecker + WhitelistChecker + Redis 缓存 + XXL-Job 60s 刷新 + Admin CRUD | 2d |
| Phase 2: 同步预检查 | RiskPreCheckInterceptor + 外部平台 Dubbo 集成 + Sentinel 熔断 + 降级策略 | 1.5d |
| Phase 3: 异步审核队列 | RocketMQ 延迟消息 + ReviewConsumer + ReviewCallback + HOLD 触发 | 2d |
| Phase 4: 退款风控 | RefundRiskScorer + 三级分档 + 自动/人工/拒绝处理 | 1.5d |
| Phase 5: 事件 + Admin API + 文档 | 事件定义 + Admin 审核/HOLD 管理 + 交叉引用更新 | 1d |

**总计：~8 人天**

---

## 12. 交叉引用矩阵

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-039** §3.3 | HOLD 状态机 | 风控触发的 HOLD 复用已有状态机，通过 hold_reason 区分 |
| **ADR-039** | 下单预检查 | OrderCreateService 中集成 RiskPreCheckInterceptor |
| **ADR-037** §4 | B2B 风控 Hook | b2bRiskCheckHandler 同步检查适配到预检查流程 |
| **ADR-042** §4.3 | 退款 riskScore | 复用 riskScore 阈值 + 扩展为三档分档 |
| **ADR-020** | Saga 补偿 | 风控 REJECT 触发 cancelOrder Saga 补偿 |
| **ADR-030** | 幂等框架 | 审核回调接口 request_id 幂等 |
| **ADR-040** | Sentinel 熔断 | 外部风控调用 500ms 超时 + 50% 错误率熔断 |
| **ADR-036** | 渠道风控 | 渠道订单接入包含风控预检步骤 |
| **ADR-038** | 事件规范 | 所有事件走 event center |
| **ADR-038** | API 规范 | ApiResult\<T\> 标准返回 |
| **ADR-044** | 购物车 | 购物车也有风控预检入口 |

---

## 13. 风险矩阵

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 外部风控平台不可用 | 中 | 高（全站无法下单） | L1/L2 降级策略 + Sentinel 熔断 + fail-open 默认值 |
| 黑白名单缓存过期导致漏放 | 低 | 高 | 60s 刷新 + 黑名单变更实时事件刷新 + 审计日志追溯 |
| 误杀正常用户（false positive） | 中 | 中 | REVIEW 模式（非直接 REJECT）+ 人工审核通道 + 白名单豁免 |
| 异步审核队列积压 | 低 | 中 | RocketMQ 消息堆积告警 + 消费者扩容 + TTL 超时后自动放行 |
| 退款风控评分不准 | 中 | 中 | 评分阈值 Apollo 可配置 + 人工审核兜底 + 评分模型定期优化 |
