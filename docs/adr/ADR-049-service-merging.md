# ADR-049：微服务合并决策

## 状态

已接受 (2026-06-14)

---

## 背景

订单中台项目原始设计包含 **16 个微服务 + 4 个 API Gateway**，每个限界上下文对应一个独立部署服务。这种"一个 BC 一个服务"的设计在文档层面清晰，但在部署层面存在严重问题。

### 问题分析

| 问题 | 影响 |
|------|------|
| **调用亲密度高** | price/promotion/coupon 每次下单同步调用，跨服务 RTT 叠加 |
| **运维成本高** | 20 个部署单元 × CI/CD/监控/日志/告警 = O(N²) 维护成本 |
| **团队沟通面大** | N×N = 256 条沟通路径 |
| **过度拆分** | id-generator、notification-service 等不适合独立部署 |

### 合并原则

1. **按调用亲密度合并**——每次请求中密集互调的服务合为一体
2. **按团队归属合并**——同一团队维护的服务合为一个部署单元
3. **ACL 保留独立**——防腐层（Channel Adapter、Risk Integration）因变更频率和外部依赖原因保留独立
4. **内嵌模块优先**——工作流、购物车、查询、通知、ID 生成等作为模块而非独立服务

---

## 决策

### 合并前后对比

| 合并前（16 个服务 + 4 GW） | 合并后（7 个服务 + 2 GW） | 合并理由 |
|---------------------------|--------------------------|---------|
| order-core, workflow-service, cart-service, order-query-service, notification-service, id-generator | **oms-trade** | 下单链路五大组件协同度极高，workflow 的每一步都调用 order-core，cart 是下单前置 |
| payment-core, aftersale-service | **oms-finance** | 资金域统一由资金团队维护；退款紧密依赖支付 |
| inventory-service, logistics-service | **oms-fulfillment** | 履约域同一团队；发货同时涉及库存扣减和物流单创建 |
| price-service, promotion-service, coupon-service, member-service | **oms-marketing** | 计价管道 4 步骤紧密协作，跨服务 RPC 开销不可接受 |
| channel-adapter | **oms-channel-adapter** | 防腐层独立，新增渠道变更频繁 |
| risk-integration | **oms-risk-integration** | 防腐层独立，外部依赖隔离 |
| id-generator | **内嵌各服务为库** | Leaf/Snowflake 是算法库，非独立服务 |
| IGW Buyer, IGW Admin | **IGW** | 路由规则区分 buyer/admin 流量即可 |
| External Gateway, Channel Gateway | **Ext GW** | 外部流量统一入口 |

### 合并收益

| 指标 | 合并前 | 合并后 | 改善 |
|------|--------|--------|------|
| 部署单元 | 20 | 9 | **-55%** |
| Dubbo RPC（一次下单） | ~15 次 | ~5 次 | **-67%** |
| 团队沟通面 | 256 | 49 | **-81%** |
| CI/CD 流水线 | 20 条 | 9 条 | **-55%** |

### 内嵌模块清单

| 模块 | 归属服务 | 说明 |
|------|---------|------|
| Workflow Engine | oms-trade | Saga 编排引擎，内嵌为模块（非独立部署） |
| Cart Manager | oms-trade | 购物车 Redis 数据结构 + API |
| CQRS Query | oms-trade | ES 查询端，Canal 同步 |
| Notification | oms-trade | 纯事件消费者，内嵌为模块 |
| ID Generator | 各服务内嵌库 | Leaf Segment + Snowflake 算法库 |

---

## 影响

### 正向影响
- 显著降低部署复杂度和运维成本
- 消除跨服务 RPC 开销，降低端到端延迟
- 减少 Saga 步骤数，降低分布式事务风险
- 团队结构自然对齐部署边界

### 负面影响
- 部分服务（oms-trade）单体体积增大，需要做好模块化
- 原有 ADR 中引用的独立服务名需要迁移
- 对账矩阵（reconciliation-matrix.md）中的服务归属需要更新

### 缓解措施
- oms-trade 内部按模块分包（`com.omplatform.trade.order`、`com.omplatform.trade.cart`、`com.omplatform.trade.workflow` 等）
- 模块间通过内部 API 接口隔离，防止退化回大泥球架构
- 每个模块保留独立的数据库表空间和 MyBatis 映射文件

---

## 相关 ADR

| ADR | 关系 |
|-----|------|
| ADR-039 | 订单状态机 + 原子服务 → oms-trade |
| ADR-037 | 流程引擎 → oms-trade 内嵌模块 |
| ADR-044 | 购物车服务 → oms-trade 内嵌模块 |
| ADR-042 | 支付结算 → oms-finance |
| ADR-048 | 售后服务 → oms-finance |
| ADR-045 | 营销价格 → oms-marketing |
| ADR-046 | 会员管理 → oms-marketing |
| ADR-043 | 库存管理 → oms-fulfillment |
| ADR-036 | 全渠道接入 → oms-channel-adapter |
| ADR-047 | 风控集成 → oms-risk-integration |
