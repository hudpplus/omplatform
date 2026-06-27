# 订单中台通用语言（Ubiquitous Language）

> **创建日期**: 2026-06-14  
> **维护原则**: 每个新 ADR 如需引入新术语，应先查此表是否已有定义  
> **关联文档**: [限界上下文地图](bounded-context-map.md)

---

## 1. 全局术语（全域统一含义）

以下术语在所有上下文中含义一致，不允许重载。

| 英文 | 中文 | 定义 | 反例 / 禁止用法 |
|------|------|------|----------------|
| **Order** | 订单 | 买家提交的一次购买请求的领域聚合根 | — |
| **Order No** | 订单号 | 全局唯一标识订单的 19 位数字（Leaf Segment） | 不能混用 payment_no / transaction_id |
| **SKU** | 库存单位 | 商品的最小库存追踪单元 | 不用于表示商品品类 |
| **Buyer** | 买家 | 下单并付款的用户 | — |
| **Seller** | 商家 | 供货方 | — |
| **Operator** | 运营/客服 | 平台运营管理人员 | — |
| **Atomic Service** | 原子服务 | 有明确输入/输出、幂等的业务操作单元（见 ADR-039） | 不表示 Controller 方法 |
| **Saga** | 分布式事务 | 长事务拆分为一系列原子步骤 + 补偿步骤 | 不混用 TCC、2PC |
| **Idempotency** | 幂等 | **业务语义**：同一请求多次执行结果相同 | 见 §3 与 Deduplication 的区分 |
| **Deduplication** | 去重 | **技术手段**：通过请求 ID 防止重复记录 | 见 §3 与 Idempotency 的区分 |

---

## 2. 核心领域术语

### 2.1 订单核心 (Order Core)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Order Aggregate** | 订单聚合 | 订单作为聚合根，包含订单项、支付记录、物流记录 | ADR-039 |
| **Order Item** | 订单项 | 订单中的一行商品（SKU + 数量 + 价格） | ADR-039 |
| **Order Status** | 订单状态 | 订单生命周期的 13 个状态之一 | `state-machine.md` |
| **State Machine** | 状态机 | 定义订单合法状态转换的引擎（矩阵控制） | ADR-039 |
| **Transition** | 转换 | 状态 A → 状态 B 的一次移动 | ADR-039 |
| **Guard** | 守卫 | 允许/禁止转换的条件表达式 | ADR-039 |
| **Action** | 动作 | 进入/离开状态时执行的副作用 | ADR-039 |
| **Process Engine** | 流程引擎 | YAML DSL 定义"做什么"（WHAT） | ADR-037 |
| **Atomic Service** | 原子服务 | 执行"怎么做"（HOW）的幂等操作 | ADR-039 |
| **Extension SPI** | 扩展点 | 按业务类型（电商/本地生活/B2B）路由的策略接口 | ADR-037 |

### 2.2 支付 (Payment)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Payment Order** | 支付单 | 一次支付请求的领域实体 | ADR-042 |
| **Payment Channel** | 支付渠道 | 支付宝/微信等三方支付 | ADR-042 |
| **Refund** | 退款 | 将已支付金额原路返回给买家 | ADR-042 |
| **Settlement** | 结算 | 按周期计算商家应收/平台应收 | ADR-042 |
| **Payment Callback** | 支付回调 | 支付网关异步通知支付结果 | ADR-042 |

### 2.3 库存 (Inventory)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Stock Item** | 库存记录 | 某 SKU 的库存总量数据 | ADR-043 |
| **Hold** | 预占 | 下单时临时锁定库存（不扣减） | ADR-043 |
| **Deduct** | 扣减 | 支付完成后正式扣减库存 | ADR-043 |
| **Release** | 释放 | 取消订单后释放预占库存 | ADR-043 |
| **Channel Stock** | 渠道库存 | 分渠道配置的库存量 | ADR-043 |

### 2.4 售后 (After-Sale)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Aftersale Order** | 售后单 | 一次售后请求的领域聚合根，独立于订单状态机 | ADR-048 |
| **REFUND_ONLY** | 仅退款 | 不退货，仅申请退款 | ADR-048 |
| **RETURN_REFUND** | 退货退款 | 寄回商品后退款 | ADR-048 |
| **EXCHANGE** | 换货 | 以旧换新，生成新订单 | ADR-048 |
| **Inspection** | 质检 | 退货商品检查（PASS/FAIL/PARTIAL） | ADR-048 |
| **Audit Rule Engine** | 审核规则引擎 | SpEL 驱动的自动审核决策系统 | ADR-048 |

### 2.5 营销价格 (Price/Promotion/Coupon)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Price Pipeline** | 计价管道 | basic→member→promotion→coupon→shipping→tax 的按序计算 | ADR-045 |
| **Promotion** | 促销 | 满减/折扣/拼团/秒杀等营销活动 | ADR-045 |
| **Coupon Instance** | 优惠券实例 | 用户持有的一张券 | ADR-045 |
| **Coupon Lifecycle** | 优惠券生命周期 | Issue→Lock→Use→Rollback | ADR-045 |
| **Stacking Matrix** | 叠加互斥矩阵 | 促销×促销/促销×券的二维互斥规则 | ADR-045 |
| **Discount Allocation** | 折扣分摊 | 多商品按比例分摊优惠金额 | ADR-045 |

### 2.6 全渠道接入 (Channel Adapter)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Channel** | 渠道 | 销售渠道（天猫/京东/抖音/拼多多/微信/POS） | ADR-036 |
| **Channel Raw Order** | 渠道原始订单 | 渠道推送的 JSON 原文 + 标准化后的中台订单 | ADR-036 |
| **Standardization Pipeline** | 标准化管线 | mapping→enrich→validate→route→save→create 六步 | ADR-036 |
| **Channel SPI** | 渠道适配 SPI | ChannelAdapter 接口的 SPI 实现 | ADR-036 |

### 2.7 风控 (Risk Control)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Pre-check** | 预检查 | 下单时的同步风控检查 | ADR-047 |
| **Async Review** | 异步审核 | 10 分钟后重新评估风险等级 | ADR-047 |
| **White/Black List** | 白/黑名单 | 本地缓存的免检/禁检名单 | ADR-047 |
| **Degradation Level** | 降级等级 | L0 全量 / L1 仅本地 / L2 跳过三级降级 | ADR-047 |

### 2.8 工作流 (Workflow)

| 英文 | 中文 | 定义 | 首次定义位置 |
|------|------|------|------------|
| **Flow Definition** | 流程定义 | YAML DSL 定义的步骤序列和补偿步骤 | ADR-037 |
| **Step** | 步骤 | 流程中的一个业务操作（原子服务调用） | ADR-037 |
| **Saga Orchestrator** | Saga 协调器 | 控制 Saga 正向/补偿执行的引擎 | ADR-020 |
| **Hook** | 扩展点 | 流程中的可插拔回调点（30+） | ADR-037 |

---

## 3. 易混淆术语辨析

### 3.1 Idempotency（幂等） vs Deduplication（去重）

| 维度 | Idempotency | Deduplication |
|------|-------------|---------------|
| **语义** | 业务上允许多次执行，结果相同 | 技术上阻止重复请求 |
| **实现** | 自然幂等（如 `set status=x`）或通过幂等校验 | 唯一索引 + 请求 ID |
| **示例** | `confirmDeduct()` 可调用多次，最终库存一致 | event_outbox 表的 event_id 唯一约束 |
| **范围** | 业务接口层面 | 基础设施层面 |
| **出现位置** | 原子服务接口 | 事件投递、消息消费 |
| **标准名称** | **幂等**（中文） / **Idempotency**（英文代码） | **去重**（中文） / **Deduplication**（英文代码） |

**规则**：文档中统一使用——中文"幂等"和"去重"不混用；英文代码中 `Idempotency-Key` 和 `Deduplication-Key` 不混用。

### 3.2 服务命名规范

| 规范名称 | 格式 | 示例 | 使用场景 |
|---------|------|------|---------|
| **服务构件名** | `{domain}-core` | order-core, payment-core | 容器图、架构文档、部署单元 |
| **合并服务名** | `oms-{domain}` | oms-trade, oms-finance, oms-marketing, oms-fulfillment | 2026-06-14 合并后的 7 个部署单元 |
| **Dubbo 服务名** | `{domain}` | `payment`, `inventory` | ADR-020 Saga 步骤配置 |
| **通用端** | `{domain}-service` | notification-service, logistics-service | 合并前的遗留命名 |

**约定**：
- `oms-trade` 是合并后的交易服务（原 order-core + workflow + cart + order-query + notification）
- `oms-finance` 是资金服务（原 payment-core + aftersale）
- `oms-fulfillment` 是履约服务（原 inventory-service + logistics-service）
- `oms-marketing` 是营销服务（原 price + promotion + coupon + member）
- `oms-channel-adapter` 和 `oms-risk-integration` 保留独立部署（ACL 防腐层）
- 时序图中统一使用新服务名 `oms-trade`、`oms-finance` 等

### 3.3 状态术语统一

| 所属上下文 | 状态字段名 | 值域前缀 | 互操作规则 |
|-----------|-----------|---------|-----------|
| 订单核心 | `order.status` | PENDING_PAY / PAID / TO_SHIP / ... | 对外发布的事件使用全名 `"PAID"` |
| 支付 | `payment.status` | INIT / PROCESSING / SUCCESS / FAILED | 支付成功 → 映射为订单 `PAID` |
| 售后 | `aftersale.status` | PENDING / AUDITING / APPROVED / ... | 售后完成 → 映射为订单 `REFUNDED` |
| 库存 | `stock.status` | AVAILABLE / RESERVED / DEDUCTED | 库存状态不对外暴露 |
| 优惠券 | `coupon.status` | AVAILABLE / LOCKED / USED / REFUNDED | 券退回 → 触发价格重算 |

---

## 4. 已废弃/不推荐使用的术语

| 术语 | 状态 | 替代 | 最后一次出现位置 |
|------|------|------|----------------|
| `order-service` | ❌ 废弃 | `order-core` | 部分时序图（已迁移） |
| `payment-service` | ❌ 废弃 | `payment-core` → 进一步合并为 `oms-finance` | container-diagram.puml（已迁移） |
| `payment-core` | ❌ 废弃 | `oms-finance` | 已合并到 oms-finance |
| `OrderService` | ❌ 废弃 | `oms-trade` | ADR-010 代码示例 |
| `workflow-service` | ❌ 废弃 | `oms-trade`（内嵌模块） | 已合并到 oms-trade |
| `cart-service` | ❌ 废弃 | `oms-trade`（内嵌模块） | 已合并到 oms-trade |
| `order-query-service` | ❌ 废弃 | `oms-trade`（CQRS 模块） | 已合并到 oms-trade |
| `notification-service` | ❌ 废弃 | `oms-trade`（内嵌模块） | 已合并到 oms-trade |
| `id-generator` | ❌ 废弃 | 内嵌各服务为库 | 不再独立部署 |
| `inventory-service` | ❌ 废弃 | `oms-fulfillment` | 已合并 |
| `logistics-service` | ❌ 废弃 | `oms-fulfillment` | 已合并 |
| `price-service` | ❌ 废弃 | `oms-marketing` | 已合并 |
| `promotion-service` | ❌ 废弃 | `oms-marketing` | 已合并 |
| `coupon-service` | ❌ 废弃 | `oms-marketing` | 已合并 |
| `member-service` | ❌ 废弃 | `oms-marketing` | 已合并 |
| `aftersale-service` | ❌ 废弃 | `oms-finance` | 已合并 |
| `channel-adapter` | ❌ 废弃 | `oms-channel-adapter` | 名称规范化 |
| `risk-integration` | ❌ 废弃 | `oms-risk-integration` | 名称规范化 |
| `Gray Release` | ❌ 废弃 | `Canary Release` | ADR-022（已统一） |
| `Idempotency Key` / `幂等性` / `Idempotent` | ❌ 混用 | `Idempotency-Key`（HTTP头） / `幂等`（中文文档） | — |
| `去重`（当"幂等"用） | ❌ 混用 | 区分使用：`去重`=技术防重，`幂等`=业务幂等 | — |

---

## 5. 术语管理流程

```
新增术语需求
    │
    ├── 是否已存在于本表？
    │      ├── 是 → 复用现有术语，不新建
    │      └── 否 → 继续
    │
    ├── 是否与其他上下文的术语冲突？
    │      ├── 是 → 加限定前缀，如 `payment.status` vs `order.status`
    │      └── 否 → 继续
    │
    └── 写入本表 + ADR 中引用本表章节号
```

---

## 参考文献

| 来源 | 术语贡献 |
|------|---------|
| [ADR-039](./adr/ADR-039-order-lifecycle-management.md) §术语定义 | 状态机 7 术语 |
| [ADR-042](./adr/ADR-042-payment-settlement-center.md) §术语定义 | 支付 5 术语 |
| [ADR-048](./adr/ADR-048-aftersale-service.md) §术语定义 | 售后 8 术语 |
| [ADR-045](./adr/ADR-045-price-promotion-coupon.md) | 营销价格 6 术语 |
| [ADR-010](./adr/ADR-010-event-schema-governance.md) | 领域事件相关术语 |
