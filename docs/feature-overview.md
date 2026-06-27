# 订单中台功能全景

> 本文档系统性地梳理订单中台的完整功能清单与架构概要，覆盖所有业务域和技术域。
> 基于全部架构决策记录（ADR 000-041）及设计文档整理。

---

## 目录

1. [全渠道订单接入与分发](#1-全渠道订单接入与分发)
2. [订单核心能力](#2-订单核心能力)
3. [支付与资金](#3-支付与资金)
4. [售后与物流](#4-售后与物流)
5. [分布式编排与可靠性](#5-分布式编排与可靠性)
6. [安全与治理](#6-安全与治理)
7. [数据与生命周期](#7-数据与生命周期)
8. [DevOps 与基础设施](#8-devops-与基础设施)
9. [多业务线与多租户](#9-多业务线与多租户)
10. [架构概要](#10-架构概要)
11. [相关文档索引](#11-相关文档索引)
12. [开放与集成能力](#12-开放与集成能力)

---

## 1. 全渠道订单接入与分发

### 支持的销售渠道

| 渠道 | 接入模式 | 认证方式 | 状态同步 | 实现状态 |
|------|---------|---------|---------|---------|
| **天猫** | Webhook Push | AppKey + HMAC-SHA256 | ✅ | SPI 适配器 |
| **京东** | Webhook Push | AppKey + Secret + Timestamp | ✅ | SPI 适配器 |
| **拼多多** | Pull（定时拉取） | OAuth2 AccessToken | ✅ | Pull Job |
| **抖音** | Webhook Push | OAuth2 Client Credentials | ✅ | SPI 适配器 |
| **微信小程序** | Webhook Push | SessionKey + 消息体签名 | ❌ | SPI 适配器 |
| **线下 POS** | Pull（定时拉取） | API Key + IP 白名单 | ✅ | Pull Job |

### 架构层次

```
外部销售渠道（Push / Pull）
         │
  ┌──────▼──────┐
  │ Channel GW  │  独立 Gateway，渠道级认证 + 限流
  └──────┬──────┘
         │
  ┌──────▼──────┐
  │ Channel     │  SPI 适配器框架，每个渠道一个实现
  │ Adapter     │  字段映射、签名认证、状态枚举映射
  └──────┬──────┘
         │
  ┌──────▼──────────┐
  │ 订单标准化引擎    │  字段映射 → 数据补全 → 规则校验 → 业务线路由
  └──────┬──────────┘
         │
  ┌──────▼──────┐
  │ 订单中台核心  │  oms-trade / oms-finance / oms-fulfillment / oms-marketing / ...
  └─────────────┘
         │
  ┌──────▼──────┐
  │ 渠道状态同步   │  MQ 事件驱动异步回写 + XXL-Job 兜底对账
  └─────────────┘
```

### 核心组件

| 组件 | 职责 | 技术选型 |
|------|------|---------|
| **Channel Gateway** | 渠道流量入口，独立于 IGW/Ext GW | Spring Cloud Gateway |
| **channel-adapter** | SPI 插件框架 + 标准化引擎 | Spring Boot + Dubbo |
| **ChannelAdapter SPI** | 渠道差异逻辑隔离接口 | Java SPI + Spring @Component |
| **ChannelAuthProvider** | 渠道认证策略接口 | Java SPI |
| **订单标准化引擎** | 字段映射、数据补全、规则校验、业务线路由 | 职责链模式 |
| **渠道注册表** | 渠道定义、路由规则、映射配置 | Apollo 配置驱动 |
| **ChannelStatusSync** | 异步状态回写 + 重试 + 对账 | RocketMQ + XXL-Job |

### 数据模型

#### 渠道原始订单表（`channel_raw_order`）

```sql
channel_type       VARCHAR(32)    -- 渠道类型（tmall/jd/pdd/...）
channel_order_no   VARCHAR(128)   -- 渠道侧订单号
channel_shop_id    VARCHAR(64)    -- 渠道店铺 ID
order_no           VARCHAR(32)    -- 标准化后的中台订单号
raw_data           JSON           -- 渠道原始请求数据（全量）
data_hash          VARCHAR(64)    -- 原始数据 SHA256 摘要
normalize_status   VARCHAR(16)    -- PENDING / SUCCESS / FAILED
```

#### order 表新增字段

```sql
channel_type       VARCHAR(32)    -- 渠道类型（新增）
channel_order_no   VARCHAR(128)   -- 渠道侧订单号（新增）
channel_shop_id    VARCHAR(64)    -- 渠道店铺 ID（新增）
```

### 标准化流程

```
原始渠道订单 → ① SPI 字段映射 → ② 商品信息补全（从商品中心）
  → ③ 规则校验（必填/金额一致性/库存） → ④ 业务线路由（渠道+类目→business_type）
  → ⑤ 保存原始数据（channel_raw_order） → ⑥ 创建标准化订单（order 表）
```

### 状态同步

```
订单状态变更 → OrderStatusChangedEvent → RocketMQ
  → ChannelStatusSyncConsumer → 调用对应 Adapter.syncStatus()
    → 成功：更新 channel_sync_log
    → 失败：指数退避重试（5 次）→ DLQ → XXL-Job 每小时兜底
```

### 渠道配置（Apollo）

渠道定义、路由规则、字段映射和限流阈值全部存储在 Apollo 配置中心，支持热生效：

```yaml
# channel.config — 启用/禁用渠道、配置限流阈值
# channel.route — 渠道 + 类目 → business_type 路由映射
# channel.shop — 各渠道店铺认证凭证
```

> 📄 详细设计文档：[ADR-036 全渠道订单接入与分发](adr/ADR-036-omni-channel-order-ingestion.md)  
> 📊 时序图：[渠道订单接入流程](diagrams/sequence/channel-order-ingestion.puml)

---

## 2. 订单核心能力

### 订单全生命周期管理

13 态状态机覆盖订单从创建到完成/退款/异常的完整流转，由状态机引擎统一校验合法性。

**状态分类**：

```
正向流转：
  PENDING_PAY → PAID → TO_SHIP → SHIPPED → DELIVERED → COMPLETED

逆向流转：
  PAID / TO_SHIP → REFUNDING → REFUNDED            （仅退款）
  TO_SHIP / SHIPPED / DELIVERED / COMPLETED → RETURNING → REFUNDED  （退货退款）

终止态：
  PENDING_PAY → CANCELLED               （取消）
  PENDING_PAY → CLOSED                  （超时关闭）

异常态：
  PENDING_PAY / PAID → HOLD              （库存不足/风控挂起）
  任意非终态 → FROZEN                    （管理员冻结）
  HOLD → PENDING_PAY / PAID              （解除挂起）
  FROZEN → 原态                          （解冻）
```

| 状态 | 含义 | 是否终态 | 超时时间 | 触发动作 |
|------|------|---------|---------|---------|
| `PENDING_PAY` | 待支付 | 否 | 30 min | 提交订单 |
| `PAID` | 已支付 | 否 | 24 h | 支付成功回调 |
| `TO_SHIP` | 待发货 | 否 | 72 h | 支付完成（需商家发货） |
| `SHIPPED` | 已发货 | 否 | 7 d | 商家发货 |
| `DELIVERED` | 已签收 | 否 | 7 d | 确认收货 |
| `COMPLETED` | 已完成 | ✅ 是 | — | 自动完成（签收后 7 天） |
| `CANCELLED` | 已取消 | ✅ 是 | — | 用户/商家取消 |
| `CLOSED` | 超时关闭 | ✅ 是 | — | 支付超时（30 分钟） |
| `REFUNDING` | 退款中 | 否 | 72 h | 申请退款 |
| `RETURNING` | 退货中 | 否 | 15 d | 申请退货 |
| `REFUNDED` | 已退款 | ✅ 是 | — | 退款完成 |
| `HOLD` | 挂起 | 否 | 48 h | 库存不足/风控触发 |
| `FROZEN` | 冻结 | 否 | 无限制 | 管理员人工锁定 |

所有非法转换（如 `PAID → PENDING_PAY`、`COMPLETED → REFUNDING`、`CANCELLED → PAID`）由状态机引擎通过 **N×N 转换矩阵** + **守卫条件（Guard）** 强制拦截。

### 状态机引擎

状态机引擎是订单生命周期的核心约束层，提供以下能力：

| 能力 | 说明 | 技术方案 |
|------|------|---------|
| **转换矩阵校验** | 13×13 转换矩阵明确定义每组 (当前态→目标态) 是否合法 | `EnumMap<OrderStatus, Set<OrderStatus>>` |
| **守卫条件（Guard）** | 转换前的业务条件判断（如支付金额校验、库存校验） | `StateGuard` 接口 + SpEL |
| **入口/出口动作** | 进入/离开状态时自动执行的钩子（如注册超时任务、发布事件） | `EntryAction` / `ExitAction` 接口 |
| **乐观锁防并发** | `version` 字段 CAS 更新，防止并发状态冲突 | `UPDATE ... WHERE version=?` |
| **状态超时矩阵** | 每个非终态配置最大停留时间，超时触发预设处理 | Apollo `state.timeout-matrix` |
| **审计日志** | 每次状态转换记录到 `state_transition_log` 表 | 按月分区、保留 90 天 |

引擎被流程引擎（ADR-037）和原子服务共同调用，位于 **流程引擎（WHAT）→ 状态机引擎（WHEN）→ 原子服务（HOW）** 三层架构的中间层。

### 核心原子服务

7 个标准化的原子操作服务，每个遵循统一契约：**前置校验 → 状态转换 → 业务执行 → 事件发布 → 补偿定义**。

| 服务 | 状态转换 | 触发时机 | 关键守卫 | 补偿逻辑 |
|------|---------|---------|---------|---------|
| **OrderCreateService** | `null → PENDING_PAY` | 买家提交订单/渠道接入 | 商品可售、价格一致、库存可用 | 取消 + 释放库存 + 释放优惠券 |
| **PaymentProcessService** | `PENDING_PAY → PAID` | 三方支付回调 | 金额一致、幂等 | 走标准退款流程（非补偿） |
| **OrderModifyService** | 同态（无状态变更） | 买家改地址/商家改价 | 已发货不可改地址、改价需风控 | 通过修改日志还原 |
| **OrderSplitService** | 子订单独立状态机 | 流程引擎 Step 0 | 拆分量 ≤ 5、金额分摊验证 | 取消所有子订单 + 释放库存 |
| **OrderMergeService** | 源订单→CANCELLED | XXL-Job 定时调度 | 同买家、同店铺、状态 PAID | 反向拆分 |
| **OrderCancelService** | `PENDING_PAY/PAID/TO_SHIP → CANCELLED` | 买家/商家/客服触发 | 已发货不可取消 | 释放库存 + 优惠券 + 退款 |
| **ConfirmReceiptService` | `SHIPPED → DELIVERED` | 买家确认/自动确认 | 物流已签收或超 7 天 | 走售后退货流程 |

所有原子服务集成 **ADR-030 幂等框架**（`Idempotency-Key`），并通过状态机引擎发布 `OrderStatusChangedEvent`（ADR-010/ADR-038）。

### 异常处理机制

| 异常场景 | 检测方式 | 处理动作 | SLA |
|---------|---------|---------|-----|
| **支付超时（30min）** | Tier 2 RocketMQ 延迟消息 | 自动关单 → CLOSED | 30min |
| **库存不足** | 状态机守卫检测 → 转入 HOLD | 等待库存补充后自动释放；48h 超时 P2 告警 | 48h |
| **商家超时未发货** | StuckOrderDetector（5min 扫描） | P2 告警 → 人工干预 | 72h |
| **退款卡住** | RefundReconciliationJob（30min 扫描） | 查询支付网关 → 自动完成或 P1 告警 | 72h / 15d |
| **退货卡住** | RefundReconciliationJob（30min 扫描） | 查询支付网关 → 自动完成或 P1 告警 | 15d |
| **订单任意态卡住** | StuckOrderDetector（5min 扫描） | 按超时矩阵预设策略处理（关单/告警/对账） | 按态配置 |
| **管理员强制介入** | 人工干预 API（冻结/解冻/强制转换） | 客服通过管理后台直接操作，记录审计日志 | 即时 |

**StuckOrderDetector**（XXL-Job，每 5 分钟）扫描所有非终态订单，对照 Apollo 超时矩阵配置决定处理策略（`AUTO_CLOSE` / `AUTO_CONFIRM` / `RECONCILE` / `ALERT_ONLY`）。与 SagaRecoveryJob（ADR-020，扫描 Saga 执行状态）互补。

> 📄 详细设计文档：[ADR-039 订单全生命周期管理](adr/ADR-039-order-lifecycle-management.md)  
> 📊 状态机全图：[13 态状态机](diagrams/state-machine-full.puml)  
> 📊 时序图 - 转换：[状态机引擎流程](diagrams/sequence/state-machine-transition.puml)  
> 📊 时序图 - 拆合单：[订单拆分与合并](diagrams/sequence/order-split-merge.puml)  
> 📊 时序图 - 异常处理：[异常处理流程](diagrams/sequence/order-exception-handling.puml)

### 高性能与高可用架构

订单系统处于核心交易链路，非功能性设计覆盖性能、可用性和数据一致性三大维度（[ADR-040](adr/ADR-040-high-performance-high-availability.md)）：

**性能层**：

| 能力 | 实现 | 基线/指标 |
|------|------|---------|
| **多级缓存** | Caffeine L1(5s TTL) → Redis L2(30s) → ES L3(fallback) | L1 < 0.1ms, L2 < 2ms, L3 ~50ms |
| **异步削峰** | MQ 请求排队 + Canal 批量写入 + Write-behind | 排队 2s 超时 → 429 |
| **DB 优化** | HikariCP 统一连接池 + 只读路由 + Keyset 分页 | OB getById P99 < 3ms |
| **热点防护** | Key Sharding + Request Collapsing + L1 吸收 | L1 10k 条目上限 |
| **SLA 矩阵** | 跨服务性能基线 + 端到端交易延迟 SLI | createOrder P99 < 1s |

**可用性层**：

| 能力 | 实现 | 目标 |
|------|------|------|
| **99.99% 分解** | order-core 99.995% + OB 99.999% + Redis 99.99% + MQ 99.99% | 年故障 < 52.56 min |
| **动态限流** | Sentinel per-service 阈值 + Apollo 4 个 profile 切换 | 超过阈值 → 429 |
| **断路器** | 3 层：Sentinel + 业务断路器 + Apollo 降级 | 全自动 HALF_OPEN 恢复 |
| **Dual-Active** | AZ-A 写 + 两 AZ 读，AZ-B 持续 30% 预热流量 | RTO < 60s |

**一致性层**：

| 能力 | 实现 | 场景 |
|------|------|------|
| **本地消息表** | `event_outbox` 表（同事务） + XXL-Job 兜底 | 支付/退款必须到达 |
| **事务消息** | RocketMQ half-commit/check-back | 订单创建通知下游 |
| **三层集成** | Saga(ADR-020) → 状态机(ADR-039) → 幂等(ADR-030) | 分布式事务端到端 |
| **统一 ID 生成** | Leaf Segment(order_id) + Snowflake(event/saga_id) | 解决 H14 6+ 种 ID 格式 |
| **对账矩阵** | 9 个对账对 + 自动修复 | OB↔ES/Redis/支付/渠道/Saga |

> 📄 详细设计文档：[ADR-040 高性能与高可用](adr/ADR-040-high-performance-high-availability.md)  
> 📄 ID 生成：[ADR-041 统一 ID 生成策略](adr/ADR-041-unique-id-generation.md)  
> 📊 对账矩阵：[数据对账矩阵](reconciliation-matrix.md)

### 下单流程（Saga 编排）

下单由 workflow-service 编排 4 步 Saga：

1. **创建订单** → 状态机 `INIT → PENDING_PAY`
2. **预占库存** → Redis Lua 脚本预占 + DB 记录
3. **请求支付** → 调起三方支付渠道
4. **确认订单** → 最终确认

任一步骤失败时，自动逆序补偿已成功的步骤。

> 📄 详细设计文档：[ADR-043 库存管理服务](adr/ADR-043-inventory-management-service.md)  
> 📊 时序图：[两阶段预占协议](diagrams/sequence/inventory-two-phase.puml)

### 库存管理服务（inventory-service → 已合并到 oms-fulfillment）

库存管理服务（原 inventory-service，现 oms-fulfillment）提供两阶段预占协议、渠道隔离、冻结/解冻等能力，与 oms-trade 通过 Dubbo 调用（HOLD 守卫条件评估）、与 oms-finance 通过回调 confirmDeduct 集成。详见 [ADR-043](adr/ADR-043-inventory-management-service.md)。

**两阶段预占协议**：

| 阶段 | 操作 | 触发时机 | Redis Lua 动作 |
|------|------|---------|---------------|
| Phase 1: 预占 | `reserveStock` | 下单（Saga Step 2） | DECRBY available + INCRBY reserved + 写 hold 记录（15min TTL） |
| Phase 2: 确认 | `confirmDeduct` | 支付成功回调 | DECRBY reserved + INCRBY deducted + hold→CONFIRMED |
| 补偿: 释放 | `releaseHold` | 订单取消/Saga 补偿 | INCRBY available + DECRBY reserved + del hold |
| 补偿: 撤销扣减 | `undoDeduct` | 支付后异常补偿 | INCRBY available + DECRBY deducted + hold→UNDONE |

**核心能力**：

| 能力 | 说明 | 技术方案 |
|------|------|---------|
| **两阶段预占** | 预占→确认→释放→撤销，4 个 Lua 脚本原子执行 | Redis Lua（防 ADR-016 超卖风险） |
| **防超卖** | Lua 单线程原子操作 + Key 分片 | `stock:{sku}:{shard%100}` 分片 + DECRBY 返回值校验 |
| **渠道隔离** | SHARED / DEDICATED / RATIO 三种模式 | `channel_stock_config` 表 + 渠道专用 Redis key |
| **冻结/解冻** | 管理端临时/永久/渠道级冻结 | 状态机 + frozen 事件 + Redis 冻结标记 |
| **批量查询** | 100 SKU 管道查询 | Redis MGET + Caffeine L1(5s) → Redis L2 → DB fallback |
| **库存预警** | 低库存/高库存自动告警 | `stock_alert_config` 表 + P2 事件 |
| **超时释放** | 预占 15min 过期自动归还库存 | RocketMQ 延迟消息(15min) + XXL-Job 每 5min 兜底 |
| **库存对账** | Redis vs DB / 渠道 vs 平台双向对账 | XXL-Job stock-recon-job / channel-stock-recon-job |

**库存状态机**：

```
库存条目:  ACTIVE ↔ FROZEN ↔ ACTIVE（解冻）
          ACTIVE → DISABLED → ARCHIVED（终态）

预占记录:  RESERVED → CONFIRMED → DEDUCTED（正向）
          RESERVED → RELEASED / TIMEOUT（释放）
          CONFIRMED → UNDONE（补偿）
```

**数据模型（5 张表）**：

| 表 | 说明 | 关键字段 |
|------|------|---------|
| `stock_item` | 库存主表（per SKU+仓库） | sku_id, warehouse_id, available_qty, reserved_qty, deducted_qty, frozen_qty, status |
| `inventory_hold` | 预占记录 | hold_id, request_id, sku_id, order_no, quantity, status, expire_at |
| `channel_stock_config` | 渠道库存配置 | sku_id, channel_code, isolation_mode, dedicated_qty, allocation_ratio |
| `inventory_transaction` | 库存流水 | transaction_no, sku_id, operation_type, quantity, before/after_available |
| `stock_alert_config` | 库存预警配置 | sku_id, warehouse_id, low_stock_threshold, alert_level |

**事件**：`inventory.reserved` / `inventory.confirmed` / `inventory.released` / `inventory.frozen` / `inventory.unfrozen` / `inventory.low_stock`

> 📄 详细设计文档：[ADR-043 库存管理服务](adr/ADR-043-inventory-management-service.md)  
> 📊 组件图：[库存服务架构](diagrams/c4/inventory-component.puml)  
> 📊 时序图：[两阶段预占协议](diagrams/sequence/inventory-two-phase.puml)

### 购物车管理（cart-service → 内嵌模块 oms-trade）

> **ADR-044** | 架构图：[cart-component.puml](diagrams/c4/cart-component.puml) | 时序图：[cart-merge-flow.puml](diagrams/sequence/cart-merge-flow.puml)

| 能力 | 实现方案 |
|------|---------|
| **Redis 数据结构** | Hash per item + Sorted Set，支持行级操作和排序 |
| **匿名→登录合并** | 智能合并：同 SKU+活动合并数量，不同 SKU 追加 |
| **TTL/过期策略** | 匿名购物车 30d TTL / 登录用户持久化 / 下单即清理 |
| **并发控制** | Redis Lua 7 脚本原子执行 + version 乐观锁 |
| **过期清理** | XXL-Job 每日扫描，过期前发 `cart.expiring` 事件提醒 |
| **价格联动** | 促销变更后通过 `price-refresh` 批量更新购物车价签 |

**API**：`POST /add` `PUT /modify` `DELETE /remove` `GET /list` `POST /select` `POST /merge` `DELETE /clear` `GET /count`

### 计价引擎与促销与优惠券（price/promotion/coupon → 已合并到 oms-marketing）

> **ADR-045** | 架构图：[price-promo-coupon-component.puml](diagrams/c4/price-promo-coupon-component.puml) | 时序图：[price-calculation-flow.puml](diagrams/sequence/price-calculation-flow.puml)

#### 计价管道（Pricing Pipeline）

`BasicPricer → MemberPricer → PromoPricer → CouponPricer → ShippingPricer → TaxPricer`

管道步骤通过 Apollo 可配置，支持热更新。

| 步骤 | 组件 | 说明 |
|------|------|------|
| Step 1 | BasicPricer | 基础价/渠道价/SKU 原价 |
| Step 2 | MemberPricer | 会员折扣率（调用 member-service） |
| Step 3 | PromoPricer | 满减/折扣/拼团/秒杀匹配与计算 |
| Step 4 | CouponPricer | 优惠券锁定与优惠计算 |
| Step 5 | ShippingPricer | 运费计算（含 VIP 免运费判定） |
| Step 6 | TaxPricer | 税费计算 |

#### 优惠叠加互斥规则

| Type A | Type B | 关系 |
|--------|--------|------|
| 单品直降 | 满减 | 🚫 互斥，取优惠金额大的 |
| 满减 | 平台券 | ✅ 可叠加，先满减再券 |
| 折扣 | 平台券 | ⚠️ 条件叠加（折扣后参与券门槛） |
| 平台券 | 商家券 | 🚫 互斥，二选一 |
| 拼团/秒杀 | 任何促销/券 | 🚫 互斥，特价已是最终价 |

#### 优惠券生命周期

`AVAILABLE → LOCKED（下单预占）→ USED（支付核销）/ EXPIRED（过期）/ REFUNDED（退款回退）`

所有操作基于 ADR-030 幂等框架，`request_id` 保证幂等安全。

### 会员管理（member-service → 已合并到 oms-marketing）

> **ADR-046** | 架构图：[member-component.puml](diagrams/c4/member-component.puml)

| 能力 | 说明 |
|------|------|
| **等级模型** | 6 级固定等级：L0 普通→L1 白银→L2 黄金→L3 铂金→L4 钻石→L5 黑卡 |
| **成长值** | 复合模型：消费金额 × 类目系数 + 频次奖励，立即升级 + 季度降级评估 |
| **积分体系** | Earn rate 1%-5%（按等级），100:1 抵扣，12 月滚动过期，每日对账 |
| **会员价** | 作为 ADR-045 计价管道的标准步骤（Step 2） |
| **VIP 免运费** | L3+ 无条件免运费 / L2 满 49 元免运费 / L1 满 69 元免运费 |
| **权益矩阵** | 折扣率、免运费门槛、生日礼包、专属促销、极速退款等权益按等级配置 |

### 风控集成（Risk Control Integration）

> **ADR-047** | 时序图：[risk-control-flow.puml](diagrams/sequence/risk-control-flow.puml)

| 能力 | 实现方案 |
|------|---------|
| **同步预检查** | 下单前 Dubbo 调用外部风控平台，500ms 超时 + Sentinel 熔断 |
| **黑白名单缓存** | Redis 本地缓存 + 60s XXL-Job 刷新，P99 ≤ 10ms |
| **异步审核队列** | RocketMQ 有序消息（按 orderId 分片），10min 延迟后重评估 |
| **HOLD 联动** | 风控审核拒绝 → 复用 ADR-039 HOLD 状态机（hold_reason=RISK_CONTROL） |
| **退款风控** | 基于 ADR-042 riskScore 三级分档：自动放行 / 人工审核 / 拒绝 |
| **降级策略** | L0 全量 / L1 仅本地缓存 / L2 跳过，Apollo 可配 |

### 共享基础设施

- **订单查询（CQRS）** — Elasticsearch 搜索引擎，Canal binlog 实时同步（oms-trade CQRS 模块）
- **分布式 ID 生成** — Leaf Segment(order_id) + Snowflake(event/saga_id)，内嵌各服务为库（非独立服务）

### 可配置订单流程引擎

不同业务线的订单处理流程差异很大——虚拟商品无需发货和库存、生鲜订单有独立履约时效、B2B 订单需多级审批。订单中台通过 **流程引擎** 将处理流程从硬编码改造为配置驱动。

**核心能力**：

| 能力 | 说明 | 技术方案 |
|------|------|---------|
| **流程模板** | 为不同订单类型定义差异化的处理步骤（实物/虚拟/预售/拼团/跨境） | YAML DSL → Apollo 配置驱动 |
| **订单分类** | 根据商品属性（类目 + 销售模式 + 业务属性）自动匹配对应模板 | SpEL 规则匹配引擎 |
| **扩展点/Hook** | 在订单生命周期关键节点（支付后/发货前/完成时）插入业务逻辑 | SPI 接口 + 同步/异步双模式 |
| **策略插件** | 拆单、合单、运费、发票等策略可配置、可热切换 | Strategy Pattern + Apollo 选择 |
| **Saga 集成** | 流程模板动态生成 Saga 定义，复用 Saga 补偿、恢复机制 | workflow-service 内嵌引擎 |

**支持的订单类型与流程差异**：

| 订单类型 | 流程特点 | 特殊步骤 | 策略差异 |
|----------|---------|---------|---------|
| **实物** | 标准 7 步：校验→计价→促销→优惠券→库存→支付 | 预占库存、需发货 | 按仓库拆单、按重计运费 |
| **虚拟** | 跳过库存，支付即完成 | 空操作跳过库存、自动完成 | 不拆单、免运费、免发票 |
| **预售** | 分段计费：定金→尾款 | 定金计算、尾款等待器（30天） | 不拆单、免运费 |
| **拼团** | 等待成团/不成团退款 | 成团等待器（10天）、拼团取消处理 | 不拆单 |
| **跨境** | 需实名认证 + 海关申报 | 实名认证、海关预录入（含跨境税） | 按品类拆单、强制发票 |

**扩展点示例**：

```
after_payment   → pointsGrantHandler（赠积分） + firstOrderCouponIssuer（首单券）
before_shipment → b2bRiskCheckHandler（B2B 发货前风控，同步阻塞）
after_complete  → crmSyncHandler（同步 CRM 系统）
after_shipment  → notificationHandler（发送物流通知）
```

**架构位置**：流程引擎内嵌在 workflow-service，位于 OrderClassifier（分类）→ ProcessEngine（模板→Saga生成）→ SagaExecutor（执行）的链路上。

> 📄 详细设计文档：[ADR-037 可配置订单流程引擎](adr/ADR-037-configurable-order-process-engine.md)  
> 📊 时序图：[流程引擎执行流程](diagrams/sequence/order-process-engine.puml)

---

## 3. 支付与资金

> 支付结算中心是订单中台的 P0 核心服务（ADR-042），5 层架构覆盖多渠道支付网关、支付核心处理、退款引擎、结算系统与对账会计。

### 3.1 架构层级

```
┌─────────────────────────────────────────────┐
│ Layer 1: 支付网关 (Payment Gateway)          │
│  Alipay SPI / WeChat SPI / 路由 / 签名/验签  │
├─────────────────────────────────────────────┤
│ Layer 2: 支付核心 (Payment Core)             │
│  6 态支付单状态机 / 幂等 / 超时管理 / 查询    │
├─────────────────────────────────────────────┤
│ Layer 3: 退款引擎 (Refund Engine)            │
│  全额/部分退款 / 自动审核规则 / 补偿重试      │
├─────────────────────────────────────────────┤
│ Layer 4: 结算中心 (Settlement Engine)        │
│  T+1/T+7/T+30 周期 / 分账手续费 / 银行出款   │
├─────────────────────────────────────────────┤
│ Layer 5: 对账会计 (Reconciliation & Acct.)   │
│  渠道日账单 / 自动匹配 / 长短款处理 / 会计分录 │
└─────────────────────────────────────────────┘
```

### 3.2 支付网关

| 功能 | 说明 | 技术实现 |
|------|------|---------|
| **多渠道 SPI** | Alipay、WeChat 双适配器，新渠道仅需实现 `PaymentChannelAdapter` 接口 | SPI 框架（类似 ADR-036 ChannelAdapter） |
| **支付路由** | 按金额/渠道来源/商户动态路由，Apollo 配置化 | `payment.routing.rules` YAML |
| **签名验签** | RSA256 回调签名验证 | `PaymentKeyManager` 密钥解密 + RSA256.verify() |
| **异步回调** | 统一回调处理器：验签→幂等→解析→状态转换→事件发布 | event_outbox 事务消息兜底 |
| **费率管理** | 按渠道配置手续费率、封顶、最低值 | Apollo `payment.channel.fees` |

### 3.3 支付核心

| 功能 | 说明 | 关键设计 |
|------|------|---------|
| **支付单状态机** | 6 态：INIT→PROCESSING→SUCCESS/FAILED→REFUNDING→REFUNDED | 独立于订单状态机，通过 event 联动 |
| **支付幂等** | `payment_request_no` 唯一索引 + IdempotentStore (ADR-030) | 重复回调返回 HTTP 200 |
| **支付超时** | 30min 超时关单，XXL-Job 每 30s 扫描 | 三方网关真实状态查询后决策 |
| **PaymentAmountGuard** | 金额守卫，允许 ±¥0.01 的精度差异 | 复用 ADR-039 Guard 模式 |
| **分账支持** | 平台抽成 + 多商户分账，支持比例/固定金额模式 | `payment_split_detail` 分账明细表 |

### 3.4 退款引擎

| 功能 | 说明 | 规则 |
|------|------|------|
| **全额退款** | 整单取消，全款原路返回 | 退款金额 = 支付金额 |
| **部分退款** | 按指定金额退款，订单部分完成 | 退款金额 ≤ 可退金额 |
| **仅退款不退货** | 虚拟商品/协商一致，直接退款 | 无需物流退货 |
| **自动审核** | 规则引擎自动审批小额退款 | ≤¥200 + 未发货 + 非风控 → 自动通过 |
| **人工审核** | 超过自动阈值的退款转入客服工作台 | >¥200 / 已发货 / 风控命中 |
| **退款重试** | 三方退款失败后自动重试（3 次） | 间隔: 1min, 5min, 30min → P1 告警 |

### 3.5 结算系统

| 功能 | 说明 | Apollo 配置 |
|------|------|------------|
| **结算周期** | 按商户级别配置 T+1 / T+7 / T+30 | `merchant.settle.cycle` |
| **净额计算** | 交易总额 - 平台佣金 - 通道手续费 - 退款抵扣 | 公式: `net = sum - commission - fee - refund` |
| **结算审核** | 自动审核（<¥500,000）+ 人工审核（≥¥500,000） | `settlement.audit.threshold` |
| **银行出款** | 银行代付接口，对公/对私账户 | 出款失败 → P1 告警 |
| **跨周期退款冲抵** | 已结算订单退款从下期净额中抵扣 | 自动生成调整单 |

### 3.6 对账与会计

| 功能 | 说明 | 频率 |
|------|------|------|
| **渠道账单下载** | 支付宝/微信 T+1 日账单自动下载 | 每日 02:00 |
| **自动匹配** | 按 payment_request_no / order_no 批量匹配 | 每日 02:30 |
| **长短款处理** | 金额差异/缺失/多余自动识别与修复 | 匹配后实时 |
| **会计分录** | 支付成功/退款/结算全链路借貸平衡 | 实时 |
| **对账报告** | 日对账报告 + 差异明细 | 每日 03:00 |

### 3.7 会计科目

| 业务场景 | 借方 | 贷方 |
|---------|------|------|
| 支付成功 | 应收账款 | 主营业务收入 |
| 支付成功（手续费） | 应收账款 | 应付手续费 |
| 退款 | 主营业务收入 | 应收账款 |
| 结算出款 | 应付账款 | 银行存款 |

### 3.8 数据模型

| 表名 | 说明 | 唯一键 |
|------|------|--------|
| `payment_order` | 支付单主表（6 态） | `payment_no`, `request_no` |
| `payment_transaction` | 支付流水（每次三方交互） | `transaction_no` |
| `payment_channel_config` | 渠道配置（密钥AES加密） | `channel_code` |
| `payment_split_detail` | 分账明细 | — |
| `refund_order` | 退款单 | `refund_no` |
| `settlement_order` | 结算单 | `settlement_no` |
| `settlement_bill` | 结算明细 | — |
| `reconciliation_bill` | 对账结果 | — |
| `account_journal` | 会计分录 | `journal_no` |

> 📄 详细设计：[ADR-042 支付结算中心](adr/ADR-042-payment-settlement-center.md) | 时序图：[支付流程](diagrams/sequence/payment-flow.puml)、[结算流程](diagrams/sequence/settlement-flow.puml)


---

## 4. 售后与物流

### 4.1 售后服务

基于 [ADR-048](adr/ADR-048-aftersale-service.md) 设计：

| 能力 | 说明 |
|------|------|
| **售后类型覆盖** | 支持 REFUND_ONLY（仅退款）、RETURN_REFUND（退货退款）、EXCHANGE（换货）三种类型 |
| **独立售后状态机** | 10 态状态机（PENDING→AUDITING→APPROVED→REFUNDING/RETURNING→INSPECTING→COMPLETED），与订单状态机 REFUNDING/RETURNING/REFUNDED 单向同步 |
| **自动审核规则引擎** | SpEL + Apollo 可配规则（≥60% 自动通过率），支持小额自动放行、高等级会员优先、虚拟商品自动退款、高风险自动拒绝等规则 |
| **退货质检流程** | PASS/FAIL/PARTIAL 三种结果，支持外观/配件/功能/SN/数量五维检查，与 WMS 签收入库联动 |
| **换货流程** | 旧货回收→质检→新订单生成（价格快照）→新货发运→Saga 补偿，全链路可追溯 |
| **退款路径** | 原路退回（支付宝/微信）优先，失败时切至平台余额 |
| **售后对账** | 3 组对账任务：售后单↔退款记录、质检超时、换货新订单↔原售后单 |
| **降级策略** | L0 全量 → L1 全人工审核 → L2 仅退款 → L3 关闭售后续入 |

### 4.2 物流服务

| 功能 | 说明 |
|------|------|
| **物流多承运商** | 顺丰、菜鸟、京东物流对接 |
| **发货管理** | 待发货 → 已发货 → 已签收流转 |

---

## 5. 分布式编排与可靠性

| 功能 | 说明 | 技术选型 |
|------|------|---------|
| **可配置流程引擎** | 订单类型差异化流程编排（实物/虚拟/预售/拼团/跨境），Apollo 配置驱动的模板 + 扩展点 + 策略 | workflow-service（内嵌） |
| **Saga 分布式事务** | 跨服务长事务编排，含正向执行 + 逆序补偿 | workflow-service |
| **延迟任务调度** | 订单超时关闭（30min）、自动确认收货（7天） | XXL-Job |
| **异步任务中心** | 异步任务执行、回调、失败重试 | XXL-Job + RocketMQ |
| **消息驱动** | 事件发布/订阅，事务消息确保一致性 | RocketMQ |
| **事件 Schema 治理** | 事件结构版本管理、兼容性校验 | ADR-010 |
| **业务线扩展点** | 按 `business_type` 路由到不同定制实现 | Extension SPI |

---

## 6. 安全与治理

### 网关安全

| 功能 | 说明 |
|------|------|
| **Internal Gateway (IGW Buyer)** | 买家流量入口，JWT 鉴权、路由、限流熔断 |
| **Internal Gateway (IGW Admin)** | 管理流量入口，JWT 鉴权 + 审计日志 |
| **External Gateway** | 开放平台入口，HMAC 签名验证、AppKey 认证、应用配额管理 |
| **Channel Gateway** | 销售渠道入口，渠道级认证、限流、Webhook 接收 |
| **Filter 链** | `AuthFilter(-2000)` → `RateLimitFilter` → `DegradeFilter` → `VersionRouteFilter` → `BizScopeFilter` |

### 认证与数据安全

| 功能 | 说明 |
|------|------|
| **认证授权** | JWT + OAuth2 + Spring Security，RBAC 权限模型 |
| **API 版本管理** | URL 路径版本 + Header 版本协商，向后兼容策略 |
| **动态数据脱敏** | ShardingSphere 透明加解密，字段级脱敏 |
| **全局幂等框架** | `Idempotency-Key` Header（UUID v4）→ Redis `SET NX` + DB 唯一索引兜底 |
| **密钥管理** | Vault + AWS KMS 动态密钥、证书自动轮换 |

### 可观测性与韧性

| 功能 | 说明 |
|------|------|
| **链路追踪** | SkyWalking APM，全链路 TraceId |
| **监控告警** | Prometheus + Grafana，`omplatform_*` 指标体系 |
| **熔断降级** | Sentinel 慢调用比例/异常比例熔断 + 自动半开恢复 |
| **限流** | Sentinel QPS/并发线程/热点参数限流，Apollo 动态阈值 |
| **降级策略** | 降级等级（L0-L3）+ 降级开关 + 脱敏降级 |
| **全链路灰度** | Gateway VersionRouteFilter → Dubbo GrayTagRouter，6 阶段滚动 |
| **混沌工程** | Chaos Mesh 故障注入（网络延迟/Pod 杀死/CPU 压力）+ 工单值守 |
| **Webhook 系统** | 外部事件推送，HMAC-SHA256 签名，指数退避重试（10 次） |

---

## 7. 数据与生命周期

| 功能 | 说明 |
|------|------|
| **数据分级** | 热（OB/Redis）→ 温（ES）→ 冷（OSS Parquet）→ 销毁 |
| **ES 索引生命周期** | ILM 策略：热 30d → 温 60d → 冷 90d → 删除 365d（B2B 业务 7 年） |
| **冷存储归档** | OB → Parquet 格式 → OSS 对象存储 → 归档清单 |
| **数据清理三步法** | `is_deleted=1` 逻辑标记 → PII 清零匿名化 → 90d 观察 → 物理清除 |
| **法规合规** | 等保三级加密存储、个保法 15d PII 删除、GDPR 被遗忘权 API |
| **备份恢复** | OceanBase 自动备份 + 跨 AZ 冗余，RPO=0 |
| **慢 SQL 治理** | SQL 审计、索引优化、读写分离 |

---

## 8. DevOps 与基础设施

### CI/CD 流水线

```
提交代码 → 单元测试 → 代码质量（SonarQube）→ SAST/SCA 安全扫描
  → 制品构建 → Cosign 签名（Vault 密钥）→ 容器镜像构建
    → 镜像漏洞扫描（Trivy）→ DAST 动态扫描
      → K8s 滚动部署（就绪/存活探针）→ 冒烟测试
```

### 本地开发环境

| 组件 | 方案 |
|------|------|
| 中间件编排 | docker-compose（MySQL / Redis / RocketMQ / ES / Nacos / Apollo） |
| OceanBase 替代 | MySQL 8.0 容器，OB 特性 `@Profile("ob")` |
| 集成测试 | Testcontainers + WireMock（Mock 三方支付） |
| 配置策略 | `application-dev.yml` → Apollo 降级模式 |
| 调试 | IDEA Remote Debug / DevTools 热重载 |

### 部署架构

| 维度 | 方案 |
|------|------|
| 集群拓扑 | 同城 3 AZ（2 数据 AZ + 1 仲裁 AZ），5 节点 OceanBase Paxos |
| 容器编排 | K8s（100+ Pods × 3 AZ） |
| 高可用 | GSLB 全局负载均衡，AZ 故障自动切换 |
| 容灾指标 | RPO=0（Paxos 同步复制），RTO<30s（自动选主） |
| 跨 AZ 流量 | AZ 内优先读取，跨 AZ 写走 Paxos 日志复制 |

### 月度成本估算

| 成本项 | 估算 | 占比 |
|--------|------|------|
| OceanBase 集群 | ¥80,000-120,000 | 35-40% |
| ES 集群 | ¥40,000-60,000 | 15-20% |
| Redis 集群 | ¥30,000-50,000 | 12-15% |
| K8s 计算资源 | ¥30,000-50,000 | 12-15% |
| RocketMQ 集群 | ¥15,000-25,000 | 6-8% |
| 网络跨 AZ 流量 | ¥8,000-15,000 | 3-5% |
| OSS 冷存储 + 备份 | ¥5,000-10,000 | 2-3% |
| 其他（Nacos/Apollo/监控） | ¥5,000-8,000 | 2-3% |
| **总计** | **¥21-34 万/月** | **100%** |

---

## 9. 多业务线与多租户

| 功能 | 说明 |
|------|------|
| **多业务线扩展点** | Extension SPI 按 `business_type` 路由到电商/本地生活/B2B 定制实现 |
| **物理隔离** | 关键业务线独立 OceanBase 实例 / ES 集群 |
| **多租户** | ADR-035 SaaS 多租户模型，租户间数据隔离 |
| **灰度策略** | 灰度环境按业务线/用户/流量百分比路由 |

---

## 10. 架构概要

```
                   ┌─────────────────────────────────────┐
                   │          买家/商家/运营              │
                   └──────────┬──────────────────────────┘
                              │ HTTPS
                     ┌────────v─────────┐
                     │  GSLB 全局负载均衡 │
                     └────────┬─────────┘
               ┌──────────────┼──────────────┐
               │              │              │
        ┌──────v──────┐ ┌────v─────┐        │
        │     IGW     │ │ Ext GW   │        │
        │(内部流量统一) │ │(外部+渠道)│        │
        └──────┬──────┘ └──────┬───┘        │
               │ Dubbo         │ HTTPS       │
               │               │             │
               │    ┌──────────┘             │
               │    │                        │
               │    │        渠道订单接入     │
               │    │    (oms-channel-adapter)│
               ▼    ▼                        ▼
      ┌─────────────────────────────────────────┐
      │              7 个服务集群                │
      │                                         │
      │  ┌─────────────┐  ┌──────────────────┐  │
      │  │  oms-trade  │  │  oms-finance      │  │
      │  │ 订单/工作流  │  │ 支付/售后/结算    │  │
      │  │ 购物车/查询  │  │                  │  │
      │  │ 通知/ID生成  │  │                  │  │
      │  └──────┬──────┘  └───────┬──────────┘  │
      │         │                 │             │
      │  ┌──────┴──────┐  ┌───────┴──────────┐  │
      │  │oms-fulfill  │  │  oms-marketing   │  │
      │  │ 库存/物流   │  │ 计价/促销/券/会员 │  │
      │  └──────┬──────┘  └──────────────────┘  │
      │         │                               │
      │  ┌──────┴──────────────────────┐        │
      │  │  oms-channel-adapter        │        │
      │  │  oms-risk-integration       │        │
      │  └─────────────────────────────┘        │
      └─────────────────────────────────────────┘
                      │
      ┌───────────────┼───────────────────┐
      │               │                   │
 ┌────v────┐   ┌──────v──────┐   ┌───────v──────┐
 │OceanBase│   │Redis Cluster│   │RocketMQ      │
 │5节点    │   │6节点×3AZ    │   │6节点×3AZ     │
 │Paxos    │   │多级缓存     │   │事务消息/事件 │
 └────┬────┘   └──────┬──────┘   └───────┬──────┘
      │               │                  │
      └───────┬───────┴──────────────────┘
              │
       ┌──────v──────┐
       │Elasticsearch│
       │CQRS 查询端   │
       │ILM 生命周期   │
       └─────────────┘
```

### 技术栈

| 层次 | 技术选型 |
|------|---------|
| **开发语言** | Java |
| **服务框架** | Spring Boot + Apache Dubbo |
| **数据库** | OceanBase（MySQL 兼容），ShardingSphere 分片 + 加密 |
| **缓存** | Caffeine（L1 本地缓存）+ Redis Cluster（L2 分布式缓存，ADR-014/040） |
| **消息队列** | RocketMQ（事务消息、顺序消息） |
| **搜索引擎** | Elasticsearch（CQRS 查询端） |
| **网关** | Spring Cloud Gateway（2 个实例：IGW / Ext GW，2026-06-14 合并优化） |
| **注册/配置** | Nacos + Apollo |
| **调度** | XXL-Job |
| **APM/监控** | SkyWalking + Prometheus/Grafana |
| **密钥管理** | HashiCorp Vault |
| **CI/CD** | GitLab CI + SonarQube + Trivy + Cosign |
| **混沌工程** | Chaos Mesh |
| **部署** | Kubernetes（3 AZ） |

---

## 11. 相关文档索引

| 文档 | 链接 | 说明 |
|------|------|------|
| 系统上下文图 | [C4 Level 1](diagrams/c4/context-diagram.puml) | 系统与外部用户/系统的关系 |
| 容器图 | [C4 Level 2](diagrams/c4/container-diagram.puml) | 服务与中间件架构 |
| 组件图（order-core） | [C4 Level 3](diagrams/c4/order-service-component.puml) | 订单核心服务内部架构 |
| 部署架构 | [部署图](diagrams/deployment.puml) | 同城三中心部署拓扑 |
| 状态机 | [订单状态机](diagrams/state-machine.md) | 13 态状态迁移定义 + 转换矩阵 + 状态超时 |
| 时序图 - 状态机转换 | [状态机引擎流程](diagrams/sequence/state-machine-transition.puml) | 状态机引擎调用时序（Guard/乐观锁/事件） |
| 时序图 - 拆合单 | [拆分与合并](diagrams/sequence/order-split-merge.puml) | 拆单/合单原子服务时序 |
| 时序图 - 异常处理 | [异常处理流程](diagrams/sequence/order-exception-handling.puml) | HOLD/超时关单/卡单检测/人工干预 |
| 时序图 - 下单 | [订单创建流程](diagrams/sequence/order-create.puml) | Saga 下单编排 |
| 时序图 - 支付 | [支付回调流程](diagrams/sequence/payment-callback.puml) | 支付回调 + 幂等 |
| 时序图 - 退款 | [售后退款流程](diagrams/sequence/refund-flow.puml) | 退款 Saga 编排 |
| 时序图 - 渠道接入 | [渠道订单接入流程](diagrams/sequence/channel-order-ingestion.puml) | 全渠道订单接入与标准化 |
| 时序图 - 流程引擎 | [流程引擎执行流程](diagrams/sequence/order-process-engine.puml) | 可配置订单流程引擎执行时序 |
| 安全架构 | [安全图](diagrams/security.puml) | 认证、授权、数据加密 |
| 时序图 - 事件 | [事件中心发布与消费流程](diagrams/sequence/order-event-flow.puml) | 事件事务消息发布 + 多消费者路由 |
| ARD 索引 | [ADR 目录](adr/) | 42 份架构决策记录（含合并决策） |
| ADR-040 高性能高可用 | [ADR 文档](adr/ADR-040-high-performance-high-availability.md) | 多级缓存 / 99.99% 可用性 / 本地消息表 / Dual-Active |
| ADR-041 统一 ID 生成 | [ADR 文档](adr/ADR-041-unique-id-generation.md) | Leaf Segment + Snowflake ID 生成策略 |
| **服务合并决策** | (详见 §2a) | 2026-06-14 16 个微服务 → 7 个部署单元 |
| 限界上下文地图 | [上下文地图](bounded-context-map.md) | 14 个 BC + 7 个部署服务的映射 |
| 数据对账矩阵 | [对账矩阵](reconciliation-matrix.md) | 9 个数据对账对：OB↔ES/Redis/支付/渠道/Saga/event_outbox |
| 功能完整性报告 | [完整性检查](functionality-completeness-report.md) | 32 份 ADR × 12 功能域交叉引用，识别覆盖盲区 |

---

## 12. 开放与集成能力

> 订单中台的价值在于输出服务能力。**开放与集成能力** 是连接中台与外部系统（商城/CRM/ERP/第三方 ISV）的桥梁，涵盖 API 标准化、事件驱动集成和集成可观测性三大支柱。

### 12.1 三层集成架构

```
API Layer（统一开放 API）      → 标准化接口输出
Event Layer（订单事件中心）     → 异步事件广播解耦
Observability Layer（集成可观测）→ 全链路监控与诊断
```

| 层次 | 职责 | 消费方 | 技术选型 |
|------|------|--------|---------|
| **API Layer** | 标准化接口输出，统一认证/限流/文档 | 商城/POS/财务/CRM/第三方 ISV | Spring Cloud Gateway × 4 + Knife4j |
| **Event Layer** | 事件广播，最终一致性解耦 | 物流/积分/营销/数据分析/外部 Webhook | RocketMQ 事务消息 + Event SDK |
| **Observability Layer** | 集成全链路可观测 | SRE/业务方/第三方开发者 | Prometheus + Loki + SkyWalking + Grafana |

### 12.2 统一开放 API

**API 分层与接口设计**

| API 分类 | 入口 | 认证方式 | 消费方 | 典型接口 |
|----------|------|---------|--------|---------|
| **Buyer API** | IGW Buyer | JWT（用户 Token） | 商城 APP/POS/小程序 | 下单、订单列表、取消订单 |
| **Admin API** | IGW Admin | JWT + RBAC（角色权限） | 运营后台/客服工作台 | 订单查询、改价、备注、审核 |
| **Backend API** | IGW Admin | JWT + RBAC + IP 白名单 | 财务系统/CRM/ERP | 对账数据、批量导出、统计 |
| **Open API** | Ext Gateway | HMAC-SHA256 + AppKey | 第三方 ISV/合作方 | 订单查询、轨迹、售后 |

**统一响应规范**

所有 API 统一返回 `ApiResult<T>` 格式（`code` + `message` + `data` + `traceId` + `timestamp`），分页查询返回 `PageResult<T>`（`content` + `page` + `size` + `total` + `hasMore`）。

**错误码体系**

| 范围 | 分类 | 示例 |
|------|------|------|
| `1xxx` | 认证与授权 | 1001 Token 过期、1003 权限不足 |
| `2xxx` | 请求参数 | 2001 必填参数缺失、2004 参数格式错误 |
| `3xxx` | 业务逻辑 | 3001 订单状态不允许、3003 库存不足 |
| `4xxx` | 限流与配额 | 4001 QPS 超限、4003 日配额耗尽 |
| `5xxx` | 系统错误 | 5001 内部错误、5003 依赖超时 |

**API 文档与 SDK**
- Knife4j 多模块聚合（Buyer/Admin/Backend/Open 四组）
- OpenAPI 3.0 规范下载 → OpenAPI Generator → Java/Python/PHP SDK 自动生成
- 每个接口标注 `@Operation` + `@ApiResponses` 列出可能错误码

### 12.3 订单事件中心

**事件分类**

| 事件类型 | 说明 | 典型消费者 |
|---------|------|-----------|
| `order.paid` | 支付成功事件 | 物流服务（生成物流单）、积分服务（赠送积分） |
| `order.shipped` | 订单发货事件 | 通知服务（发送物流通知）、数据分析（更新状态） |
| `order.completed` | 订单完成事件 | CRM（同步客户数据）、营销（更新消费等级） |
| `order.cancelled` | 订单取消事件 | 库存服务（回滚库存）、财务（退款处理） |

**事件发布流程**
```
业务服务 → EventPublisher → RocketMQ 事务消息 → 事件存储(event_store)
    → EventRouter → 匹配订阅规则 → 分发给内部消费者（物流/积分/营销）
    → WebhookDispatcher → HMAC-SHA256 签名 → HTTP POST 外部回调
```

**投递保障**
- RocketMQ 事务消息确保事件与本地事务最终一致
- 指数退避重试（1s→10s→30s→1min→5min→15min→30min × 10 次）
- 死信队列兜底 + DLQ 深度 > 100 触发 P1 告警
- `event_delivery_log` 表记录每次投递状态，支持追溯对账

### 12.4 集成可观测性

| 观测维度 | 关键指标 | 告警规则 |
|---------|---------|---------|
| **API** | QPS/延迟 P50 P99/错误率/配额使用率 | 错误率 > 5% P2、配额 > 95% P3 |
| **事件** | 生产/消费速率、堆积深度、消费延迟、DLQ 数量 | 堆积 > 10000 P1、失败率 > 5% P2 |
| **端到端** | API→事件→消费全链路延迟 | P99 > 5s P2 |

三个 Grafana 集成健康看板：
1. **集成总览** — 全局健康评分 + 调用量趋势 + 异常统计
2. **API 详情** — 按 AppKey/端点维度 QPS/延迟/错误率排名
3. **事件详情** — 按事件类型/消费者组维度堆积/失败率/延迟

### 12.5 已覆盖的 ADR

| ADR | 内容 | 与本能力的关系 |
|-----|------|--------------|
| **ADR-025** | API 版本管理 + 外部 Gateway | 开放 API 的网关基础 |
| **ADR-029** | 内部 Gateway 设计 | API 分层入口 |
| **ADR-033** | Webhook 系统 | 事件中心的外部推送通道 |
| **ADR-038** | **开放与集成能力（本文）** | 统一 API + 事件中心 + 集成可观测 |

> 📄 详细设计文档：[ADR-038 开放与集成能力](adr/ADR-038-openness-and-integration.md)  
> 📊 时序图：[事件中心发布与消费流程](diagrams/sequence/order-event-flow.puml)
