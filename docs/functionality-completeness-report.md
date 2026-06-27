# 订单中台功能完整性检查报告

> **版本**: 1.0  
> **日期**: 2026-06-13  
> **范围**: docs/adr/ADR-010 ~ ADR-041 + feature-overview.md + 支撑文档  
> **方法**: 逐项交叉引用 feature-overview.md 12 个功能域与 32 份 ADR，识别覆盖盲区

---

## 1. 检查方法

1. 提取 feature-overview.md 12 个功能域中列举的全部功能点
2. 逐项映射到对应的 ADR 文档，记录覆盖状态
3. 对照行业订单中台通用能力清单，识别缺失项
4. 按覆盖度分三级：✅ 高 / ⚠️ 中 / ❌ 低或无

---

## 2. 覆盖总览

| 功能域 | 对应 ADR | 覆盖度 |
|--------|----------|--------|
| 订单生命周期管理 | ADR-039（13态状态机、7原子服务） | ✅ **高** |
| 可配置流程引擎 | ADR-037（YAML DSL、4策略、Hook） | ✅ **高** |
| 全渠道接入 | ADR-036（SPI 适配器、6渠道） | ✅ **高** |
| 开放与集成 | ADR-038（4层 API、事件中心） | ✅ **高** |
| 高性能与高可用 | ADR-040（多级缓存、99.99%、Dual-Active） | ✅ **高** |
| 分布式事务 | ADR-020（Saga 编排/Choreography） | ✅ **高** |
| 统一 ID 生成 | ADR-041（Leaf + Snowflake） | ✅ **高** |
| 可观测性 | ADR-027（SLI/SLO、燃烧率告警） | ✅ **高** |
| 容灾与降级 | disaster-recovery.md, degradation-strategy.md | ✅ **高** |
| 安全 & 网关 | ADR-025/026/029/028（认证/授权/脱敏） | ✅ **高** |
| **支付与资金** | ❌ 无独立 ADR | ⚠️ **中** |
| **售后服务** | ADR-048（售后状态机、退款/退货/换货、自动审核、质检） | ✅ **高** |
| **物流服务** | ❌ 无独立 ADR | ⚠️ **中** |
| **计价与营销** | ADR-045（营销价格服务，含优惠叠加规则） | ✅ **高** |
| **购物车服务** | ADR-044（购物车服务） | ✅ **高** |
| **会员管理** | ADR-046（会员管理） | ✅ **高** |
| **风控集成** | ADR-047（风控集成） | ✅ **高** |

---

## 3. 已完整覆盖的功能 ✅

### 3.1 订单核心能力

| 功能点 | 文档位置 | 关键设计 |
|--------|----------|----------|
| 13 态状态机 | ADR-039 §2 | PENDING_PAY → ... → COMPLETED，REFUNDING/RETURNING → REFUNDED，HOLD/FROZEN |
| 7 个原子服务 | ADR-039 §3 | Create/Payment/Modify/Split/Merge/Cancel/ConfirmReceipt，模板方法模式 |
| 卡单检测器 | ADR-039 §4 | XXL-Job 5min 扫描，Apollo 超时矩阵决策 |
| HOLD 生命周期 | ADR-039 §5 | ¥3 手动履约 / 超时系统取消 DUAL |
| 退款逆向流程 | ADR-039 §6 | 多子单独立退款、库存回滚、券回退 |
| 流程引擎 | ADR-037 §2-3 | YAML DSL + SpEL 订单分类 + Apollo 模板仓库 |
| 5 类订单模板 | ADR-037 §4 | 普通、预售、拼团、秒杀、积分兑换 |
| 4 类策略接口 | ADR-037 §5 | Split/Merge/ShippingCost/Invoice |
| Hook 体系 | ADR-037 §6 | 30+ 扩展点，同步/异步双模式 |

### 3.2 全渠道接入

| 功能点 | 文档位置 | 关键设计 |
|--------|----------|----------|
| 6 渠道适配 | ADR-036 §2 | Tmall/JD/PDD/Douyin/Wechat/POS |
| SPI 框架 | ADR-036 §3 | ChannelAdapter SPI + 渠道加载器 |
| 标准化管线 | ADR-036 §4 | mapping → enrich → validate → route → save → create |
| channel_raw_order | ADR-036 §5 | 原始订单持久化 + 状态回写 |
| 渠道状态同步 | ADR-036 §6 | MQ 消费回写 + XXL-Job 补扫 |

### 3.3 开放与集成

| 功能点 | 文档位置 | 关键设计 |
|--------|----------|----------|
| 4 层 API | ADR-038 §2 | Buyer/Admin/Backend/Open |
| 统一响应 | ADR-038 §3 | ApiResult\<T\> + PageResult\<T\> + 1xxx~5xxx 错误码 |
| 事件中心 | ADR-038 §4 | event_store + event_delivery_log + RocketMQ 事务消息 |
| 集成可观测性 | ADR-038 §5 | 3 Grafana 看板、晚高峰冲正 |
| 后端管理 API | ADR-039 §8 | 订单查询/干预/操作日志 |

### 3.4 非功能能力

| 功能点 | 文档位置 | 关键设计 |
|--------|----------|----------|
| 多级缓存 | ADR-040 §2 | Caffeine L1(5s/10k) → Redis L2(30s) → ES L3(fallback) |
| 99.99% SLA | ADR-040 §3 | order-core 99.995% + OB 99.999% + Redis 99.99% + MQ 99.99% |
| Dual-Active | ADR-040 §4 | AZ-A 写 + 两 AZ 读(70:30)，RTO<60s |
| 3 层断路器 | ADR-040 §5 | Sentinel → 业务断路器 → Apollo 降级 |
| event_outbox | ADR-040 §6 | 同库事务 + XXL-Job 5s 扫 + 指数退避 |
| Saga 事务 | ADR-020 | 编排/Choreography 双模式 + 补偿 + 恢复 Job |
| 对账矩阵 | reconciliation-matrix.md | 9 组对账: OB↔ES/Redis/支付/渠道/Saga/等 |
| ID 生成 | ADR-041 | Leaf Segment(order_id) + Snowflake(event_id/saga_id) |
| SLI/SLO | ADR-027 | order-core 99.95%→99.995%，燃烧率告警阈值 |
| 降级策略 | degradation-strategy.md | L0-L4 四级、per-service Sentinel 阈值矩阵 |
| 容灾切换 | disaster-recovery.md | AZ 切换 SOP、Dual-Active 模式 §2.4 |

### 3.5 支撑能力

| 功能点 | 文档位置 |
|--------|----------|
| Event Schema 治理 | ADR-010 |
| Canal 高可用 | ADR-013 |
| 容量规划 | ADR-015 |
| 物理隔离 | ADR-017 |
| 监控看板增强 | ADR-018 |
| 异步 Job 中心 | ADR-019 |
| 延迟任务调度 | ADR-021 |
| 全链路灰度 | ADR-023 |
| Slow SQL 治理 | ADR-024 |
| API 版本管理 | ADR-025 |
| 数据生命周期 | ADR-031 |
| 本地开发环境 | ADR-032 |
| Webhook 系统 | ADR-033 |
| 混沌工程 | ADR-034 |
| 多租户 | ADR-035 |
| 数据脱敏 | ADR-024 |
| 在线 DDL | ADR-011 |

---

## 4. 缺少独立 ADR 的服务 ⚠️

以下服务在架构图或 feature-overview 中被引用，但 **没有独立的设计文档**：

### 4.1 payment-core（支付服务）

| 现状 | 被 ADR-039 §3.2 提及（支付回调接收方） |
|------|-----------------------------------------|
| 缺失设计 | 三方支付渠道（支付宝/微信）对接、支付网关路由、聚合支付、撤销/冲正、对账文件生成、结算周期管理 |
| 引用点 | ADR-039 §3.2, ADR-040 §6.4, reconciliation-matrix.md `ob_vs_payment` |

### 4.2 inventory-service（库存服务） ✅ 已解决

| 现状 | 通过 ADR-043 完成设计 |
|------|----------------------|
| 设计覆盖 | Redis Lua 两阶段预占协议（4 个脚本：reserve/confirm/release/undo_deduct）、防超卖原子扣减、库存冻结/解冻、渠道库存隔离（SHARED/DEDICATED/RATIO）、批量查询、5 张表 DDL、库存状态机 |
| 引用点 | ADR-043（主设计）、[inventory-two-phase.puml](../diagrams/sequence/inventory-two-phase.puml)、[inventory-component.puml](../diagrams/c4/inventory-component.puml) |

### 4.3 cart-service（购物车服务） ✅ 已解决

| 现状 | 通过 ADR-044 完成设计 |
|------|---------------------|
| 设计覆盖 | Redis Hash per item + Sorted Set 数据结构、匿名/登录智能合并、TTL 分层策略（匿名 30d/登录持久化）、Redis Lua 7 脚本原子并发控制、XXL-Job 过期清理、事件联动 |
| 引用点 | ADR-044（主设计）、[cart-component.puml](../diagrams/c4/cart-component.puml)、[cart-merge-flow.puml](../diagrams/sequence/cart-merge-flow.puml) |

### 4.4 price-service / promotion-service / coupon-service（计价营销服务） ✅ 已解决

| 现状 | 通过 ADR-045 完成设计 |
|------|---------------------|
| 设计覆盖 | 可配置计价管道（basic→member→promotion→coupon→shipping→tax）、轻量职责链+SpEL 规则引擎、优惠叠加互斥矩阵（优先级分层+二维矩阵）、折扣分摊算法（按金额比例/按优先级）、统一促销定义模型（FULL_REDUCTION/DISCOUNT/GROUP_BUY/FLASH）、优惠券状态机（AVAILABLE→LOCKED→USED→REFUNDED）、Saga 集成（修正 H7/H10/M11） |
| 引用点 | ADR-045（主设计）、[price-promo-coupon-component.puml](../diagrams/c4/price-promo-coupon-component.puml)、[price-calculation-flow.puml](../diagrams/sequence/price-calculation-flow.puml) |

### 4.5 logistics-service（物流服务）

| 现状 | 无设计，feature-overview §4 仅三行 |
|------|-------------------------------------|
| 缺失设计 | 多承运商（顺丰/菜鸟/京东物流）对接、电子面单生成、物流轨迹订阅（快递鸟/菜鸟裹裹）、运费模板计算 |
| 引用点 | feature-overview §4, ADR-039 §3.4（SHIPPED 发货动作） |

### 4.6 aftersale-service（售后服务） ✅ 已解决

| 现状 | 通过 ADR-048 完成设计 |
|------|----------------------|
| 设计覆盖 | 售后独立状态机（10 态）、REFUND_ONLY/RETURN_REFUND/EXCHANGE 三类售后、SpEL 自动审核规则引擎、退货质检流程（PASS/FAIL/PARTIAL 三种结果）、换货全流程（旧货回收→质检→新订单生成→新货发运）、价格快照策略、Saga 补偿、6 张表 DDL、Buyer/Admin/仓库 API 三套、10 个事件、降级策略 L0-L3 |
| 引用点 | ADR-048（主设计）、[aftersale-component.puml](../diagrams/c4/aftersale-component.puml)、[aftersale-selection-flow.puml](../diagrams/sequence/aftersale-selection-flow.puml) |

### 4.7 notification-service（通知服务）

| 现状 | 被 ADR-037 §6.2（异步 Hook）多次引用 |
|------|---------------------------------------|
| 缺失设计 | 消息模板管理、渠道路由（短信/APP Push/邮件/站内信）、发送频率限制、回执处理 |
| 引用点 | ADR-037 §6.2, ADR-038 §4 |

---

## 5. 功能缺口（业界标准订单中台功能）❌

对照行业主流订单中台能力，以下功能未在当前文档体系中覆盖：

### 5.1 P2 优先级（建议本次补齐）

| 缺口 | 说明 | 依赖服务 |
|------|------|----------|
| **分期付款** | 支付宝花呗/微信分期接入、分期手续费计算分摊、退款原路分期返还 | payment-service |
| **O2O / 门店自提** | 自提点选择地图、核销码生成/验证、备货状态跟踪管理 | aftersale-service |
| **部分发货 / 多包裹** | 一单多包裹逐批发货（多个物流单号）、部分签收→部分完成状态 | logistics-service, state-machine |
| **电子发票** | 对接百望/航天金税等税控接口、开票申请→自动开票→红冲 | payment-service |
| **B2B 合同订单** | 合同→订单转化、多级审批流、账期支付、预存款扣减 | flow-engine, payment |
| **批量操作** | 批量发货（Excel 导入）、批量改价（按条件筛选）、批量审核 | admin-api |

#### ✅ 已解决（本次补齐）

| 缺口 | 解决方案 |
|------|---------|
| **风控集成** | ADR-047：同步预检查+异步审核队列+HOLD联动+黑白名单缓存+退款风控 |
| **优惠叠加规则** | ADR-045：互斥矩阵+优先级分层+折扣分摊算法 |
| **会员等级/价格体系** | ADR-046：6 级会员+成长值+积分+会员价+VIP 免运费 |

### 5.2 P3 优先级（后续迭代）

| 缺口 | 说明 |
|------|------|
| **以旧换新** | 旧机估价/质检、回收订单→新机订单联动、旧机物流上门取件 |
| **定时配送 / 时段选择** | 配送时段选择（预约日历）、前置仓履约调度 |
| **多币种 / 多语言** | 跨境多币种计价、汇率转换引擎、多语言商品/订单信息 |
| **订阅制 / 周期性订单** | 自动续费、周期购（每日/每周/每月）、履约计划编排 |
| **礼品卡 / 储值卡** | 礼品卡购买→绑定→使用→余额管理 |
| **换货流程** | 售后换货直接生成新订单、换货物流上门取件+派送 |
| **协商一致退款** | 仅退款不退货场景、小额自动放行策略 |

---

## 6. 优先补齐建议

基于平台现状和业务场景，建议按以下优先级推进：

### Phase 1：补齐 9 个服务的 ADR 设计文档

| 服务 | 优先级 | 理由 | 状态 |
|------|--------|------|------|
| payment-service | **P0** | 被对账、Saga 多处强依赖，且与资金直接相关 | ⏳ 待补 |
| inventory-service | **P0** | 被下单扣库存强依赖，数据一致性敏感 | ✅ ADR-043 |
| cart-service | **P1** | 购物车是高并发入口 | ✅ **ADR-044** |
| aftersale-service | **P1** | 售后状态机涉及退款、库存回滚、券回退联动 | ✅ **ADR-048** |
| logistics-service | **P1** | 与发货 SHIPPED/DELIVERED 状态联动 | ⏳ 待补 |
| price/promotion/coupon | **P2** | 含优惠叠加规则设计 | ✅ **ADR-045** |
| member-service | **P2** | 会员等级/积分/成长值/会员价/VIP 免运费 | ✅ **ADR-046** |
| risk-integration | **P2** | 风控集成：预检+异步审核+HOLD+黑白名单 | ✅ **ADR-047** |
| notification-service | **P2** | Hook 体系已解耦，可延后 | ⏳ 待补 |

### Phase 2：扩展 feature-overview.md §3（支付）和 §4（售后物流）

当前这两个章节只有 12 行表格，远不够支撑上下游团队理解。建议每段扩展到 30-50 行。

### Phase 3：高级功能增量

- P2 优先：分期付款、O2O 自提、部分发货、电子发票
- P3 后续：以旧换新、定时配送、订阅制等

#### ✅ 本次已补齐

| P2 缺口 | ADR | 核心设计 |
|---------|-----|---------|
| 风控集成 | ADR-047 | 外部风控平台集成：预检查+审核队列+HOLD联动+黑白名单+退款风控+三级降级 |
| 优惠叠加规则 | ADR-045 §10 | 优先级分层+互斥矩阵+折扣分摊算法，支付/退款的券回退集成 |
| 会员等级/价格体系 | ADR-046 | 6 级会员+复合成长值+积分体系+会员价管道+VIP 免运费 |

---

## 7. 设计资源分析

### 7.1 已有，可直接沿用

- **状态机框架** → ADR-039，新增状态（如部分发货）需扩展
- **流程引擎** → ADR-037，新增模板（如订阅制）较容易
- **Saga 事务** → ADR-020，新业务补偿只需定义子事务
- **幂等框架** → ADR-030，所有批量/异步操作可直接整合
- **事件中心** → ADR-038，新业务事件直接发布

### 7.2 需新建

- 支付渠道对接规范
- 库存预占/确认/释放协议
- 计价引擎接口契约
- 物流承运商接入 SPI

---

## 8. 结论

| 维度 | 结论 |
|------|------|
| **核心订单域** | ✅ 覆盖度高，状态机/流程引擎/渠道接入/开放 API 均有深度设计 |
| **非功能能力** | ✅ 覆盖度高，99.99%/多级缓存/Dual-Active/对账矩阵体系完整 |
| **支撑服务独立 ADR** | ⚠️ 9 个服务缺少独立设计文档，但均在 ADR-039/037 中有接口级引用 |
| **功能缺口** | ❌ 约 13 项高级电商功能未覆盖，P2 缺口 8 项、P3 缺口 6 项 |
| **总体** | **中台核心骨架完整，支撑服务层和增值功能层需补齐** |
