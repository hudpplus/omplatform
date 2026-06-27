# ADR-037：可配置订单流程引擎

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台的所有业务流程采用**硬编码方式**定义。ADR-020 的 Saga 编排器虽然实现了分布式事务的可靠执行，但每个业务流的步骤列表、执行顺序、补偿逻辑都在 Java `@Configuration` 类中硬编码：

```java
// 当前硬编码的 Saga 定义
@Bean
public SagaDefinition createOrderSaga() {
    return SagaDefinition.builder()
        .sagaName("createOrder")
        .steps(Arrays.asList(
            SagaStep.builder().stepName("createOrder").order(1)
                .forwardService("order-core").forwardMethod("createOrder").build(),
            SagaStep.builder().stepName("deductInventory").order(2)
                .forwardService("inventory").forwardMethod("deduct").build(),
            SagaStep.builder().stepName("chargePayment").order(3)
                .forwardService("payment").forwardMethod("charge").build(),
            SagaStep.builder().stepName("confirmOrder").order(4)
                .forwardService("order-core").forwardMethod("confirmPaid").build()
        ))
        .build();
}
```

这种硬编码方式在业务线单一、订单类型同质化的情况下可以工作。但订单中台面临以下差异化场景时，硬编码的流程成为阻碍：

**问题 1：不同订单类型需要不同流程**

| 订单类型 | 需要差异化 | 当前处理 |
|----------|-----------|---------|
| **实物商品** | 需校验库存 → 需发货 → 需物流追踪 → 7 天自动完成 | ✅ 硬编码支持 |
| **虚拟商品** | 无需扣库存 → 无需发货 → 支付即完成 | ❌ 需跳过库存和发货步骤 |
| **预售商品** | 定金支付 → 等待尾款 → 尾款支付 → 发货 | ❌ 需分段支付流程 |
| **拼团商品** | 拼团中 → 成团 → 发货 / 不成团 → 退款 | ❌ 需拼团等待步骤 |
| **数字商品** | 无需扣库存 → 支付即发送 → 无物流 | ❌ 需自动发货步骤 |

当前解决方案是为每种类型创建独立的 Saga 定义，但业务线的运营人员无法自行配置——每次新增订单类型都需要开发团队从头编码。

**问题 2：订单关键节点无法插入业务扩展逻辑**

业务方需要在订单生命周期特定节点执行自定义逻辑：

- 支付成功后赠送积分（电商业务线）
- 发货前调用风控审核（B2B 业务线）
- 订单完成后同步到 CRM 系统（全业务线）
- 退款后释放预占资源（某些业务线需要额外步骤）

当前这些逻辑必须侵入 order-core 的代码，或通过监听 RocketMQ 事件异步处理（ADR-020 Choreography 模式）。缺乏一种**轻量级、可配置**的方式在订单处理的**同步关键路径**上插入扩展逻辑。

**问题 3：拆单/合单/运费/发票策略不可配置**

当前架构没有策略层设计：

- **拆单策略**：多商品订单是否按仓库拆单 / 按商家拆单 / 不拆单？——决策逻辑硬编码
- **合单策略**：同一买家的多笔订单是否合并发货？——无设计
- **运费计算**：按重量 / 按件数 / 按距离 / 满额包邮？——price-service 内部写死
- **发票策略**：哪些订单需要发票 / 什么金额起开？——无设计

业务方无法通过配置改变这些策略，每次策略调整都需要开发上线。

### 目标

1. **流程可配置**：运营/产品可通过配置为不同订单类型定义差异化的处理步骤
2. **扩展点可插拔**：在订单生命周期关键节点提供 SPI 扩展点，业务方插入自定义逻辑
3. **策略可切换**：拆单、合单、运费、发票等策略可配置，支持热生效和灰度
4. **与现有 Saga 集成**：流程模板动态生成 Saga 定义，复用 ADR-020 的执行器、补偿、恢复机制
5. **运营自服务**：运营人员通过 Apollo 配置即可新增/调整流程，无需开发上线

---

## 决策

### 决策 1：流程定义方式 — YAML DSL + Apollo 配置驱动

| 策略 | 评估 |
|------|------|
| **BPMN 引擎（Camunda/Flowable）** | 功能最强，BPMN 2.0 标准，有可视化设计器；但引入独立引擎 > 30MB、部署运维复杂、与现有 Saga 集成成本高、学习曲线陡 |
| **工作流编排服务（Temporal/Serverless Workflow）** | 云原生，但引入额外基础设施、与 Dubbo 集成需适配层 |
| **YAML DSL + 轻量引擎 + Apollo 配置** | ✅ **选中** — 无额外依赖，Apollo 已存在，热生效，Spring Bean 直接复用 |

**理由**：
- Apollo 已是中台统一配置中心（8 个 ADR 使用），零新增依赖
- Spring Bean 直接作为步骤实现，无需额外注册机制
- YAML 对人类可读，运营人员可理解
- 轻量引擎嵌入 workflow-service，无独立部署成本
- 与 ADR-020 Saga 自然集成——模板步骤映射为 SagaStep

### 决策 2：订单分类方式 — 属性规则匹配

| 策略 | 评估 |
|------|------|
| **运营手动选择订单类型** | 依赖前端传值，不规范时路由错误 |
| **按商品类目硬编码映射** | 灵活性差，新增类目需改代码 |
| **属性规则匹配（Apollo 配置规则）** | ✅ **选中** — 完全配置驱动，热生效，支持复杂条件组合 |

**理由**：
- 订单类型由商品属性组合决定（`itemType + saleType + ...`），规则表达式可覆盖所有场景
- 规则存储在 Apollo，新增订单类型只需新增规则 + 模板，无需重启
- SpEL 表达式引擎成熟（Spring 内置），无需引入规则引擎

### 决策 3：扩展点执行模式 — 同步/异步双模式 + SPI

| 策略 | 评估 |
|------|------|
| **纯事件驱动（MQ）** | 完全异步，不阻塞主流程；但无法在关键路径上做同步校验（如发货前风控） |
| **纯同步（Filter 链）** | 可做同步校验，但阻塞主流程，失败会阻断整体 |
| **同步/异步双模式 + SPI** | ✅ **选中** — 同步用于阻断式校验，异步用于非关键逻辑，同一 SPI 接口 |

**理由**：
- 风控审核、必填校验等需要在主流程中同步执行（失败即阻断）
- 积分赠送、通知推送等可异步执行（失败不影响主流程）
- `async` 标记在 Apollo 配置中控制，无需改代码切换同步/异步

### 决策 4：策略选择机制 — Strategy Pattern + Apollo 配置

| 策略 | 评估 |
|------|------|
| **策略硬编码** | 当前方式，每次策略调整需开发上线 |
| **策略 SPI + DB 配置** | 灵活，但 DB 变更需额外刷新机制 |
| **策略接口 + Apollo 配置选择实现** | ✅ **选中** — 策略按接口隔离，实现类由 Apollo 指定，热生效 |

**理由**：
- 每个策略领域（拆单/合单/运费/发票）定义独立接口
- 不同实现类由 Apollo 配置 `{order_type}.strategies.{domain}` 选择
- 策略实现类本身仍是 Spring Bean，可注入任何依赖
- Apollo 的热生效使策略可以灰度切换（按 shop_id 或 buyer_id）

### 决策 5：引擎部署方式 — 内嵌到 workflow-service

| 策略 | 评估 |
|------|------|
| **独立 process-engine 微服务** | 职责清晰，独立扩缩容；但增加调用链跳转延迟，SagaStep 调用需跨服务 |
| **内嵌到 workflow-service** | ✅ **选中** — 与 Saga 编排器同进程，零延迟调用，复用 Saga 基础设施 |

**理由**：
- 流程引擎本质上是 Saga 定义的"生成器"——运行时将模板转换为 Saga 定义，交给 SagaExecutor 执行
- 内嵌后模板步骤到 SagaStep 的转换为本地方法调用，无网络开销
- 共享 SagaLog、幂等、恢复机制
- workflow-service 本来就承担编排职责，内嵌流程引擎是其自然演进

---

## 详细设计

### 1. 整体架构

```
                          ┌──────────────────────────────────────┐
                          │          Apollo 配置中心              │
                          │  process.template — 流程模板 YAML    │
                          │  process.category — 订单分类规则     │
                          │  process.strategy — 策略选择          │
                          │  process.hook — 扩展点配置            │
                          └────────────────┬─────────────────────┘
                                           │ 配置变更 → 热生效
                                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    workflow-service                              │
│                                                                  │
│  ┌────────────────────┐     ┌──────────────────────────────┐    │
│  │  OrderClassifier   │────→│  ProcessTemplateRepository  │    │
│  │  订单分类引擎       │     │  模板加载 + 缓存             │    │
│  │  (规则匹配)         │     │  (Apollo Watch 自动刷新)     │    │
│  └────────────────────┘     └──────────┬───────────────────┘    │
│                                        │                         │
│                                        ▼                         │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                ProcessEngine                              │   │
│  │                                                           │   │
│  │  ① 根据 orderCategory 获取模板                             │   │
│  │  ② 模板 → SagaDefinition（动态生成）                       │   │
│  │  ③ 注入扩展点 Hook（匹配 event + condition）              │   │
│  │  ④ 选择策略实现（split/merge/shipping/invoice）           │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                        │                                         │
│                        ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  SagaExecutor（ADR-020）                                  │   │
│  │  执行正向 SagaStep → 失败时逆序补偿                        │   │
│  │  SagaLog 持久化 → RecoveryJob 兜底恢复                    │   │
│  └────────────────────┬─────────────────────────────────────┘   │
│                        │                                         │
└────────────────────────┼─────────────────────────────────────────┘
                         │ Dubbo
                         ▼
            ┌─────────────────────────┐
            │  order-core / inventory │
            │  / payment / ...        │
            └─────────────────────────┘
```

**组件职责**：

| 组件 | 职责 | 与现有组件关系 |
|------|------|--------------|
| **OrderClassifier** | 根据订单属性匹配订单分类规则，输出 `orderCategory` | 新增，内嵌 workflow-service |
| **ProcessTemplateRepository** | 从 Apollo 加载模板 YAML，本地缓存，监听配置变更热刷新 | 新增，复用 Apollo Client |
| **ProcessEngine** | 模板 → SagaDefinition 转换，Hook 注入，策略绑定 | 新增，SagaExecutor 的上层编排 |
| **SagaExecutor** | 沿用 ADR-020 的执行器，无变更 | 已有，复用 |
| **HookRegistry** | 管理扩展点 SPI 实现，按 event + order 排序执行 | 新增，Spring Bean 容器 |
| **StrategyRegistry** | 管理策略接口和实现，按配置选择激活的策略 | 新增，Spring Bean 容器 |

### 2. 订单分类体系

#### 2.1 分类维度

订单类型由**三级维度**组合决定：

```
一级：商品类目 (item_type)
  ├── PHYSICAL_GOODS     — 实物商品
  ├── VIRTUAL_GOODS      — 虚拟商品（充值/会员/卡券）
  ├── DIGITAL_GOODS      — 数字商品（电子书/软件/课程）
  └── SERVICE            — 服务类（预约/到家/到店）

二级：销售模式 (sale_type)
  ├── NORMAL             — 普通售卖
  ├── PRESALE            — 预售
  ├── GROUP_BUY          — 拼团
  ├── FLASH_SALE         — 秒杀
  └── AUCTION            — 拍卖

三级：业务属性 (biz_attributes)
  ├── is_overseas        — 跨境
  ├── need_invoice       — 需要发票
  ├── is_gift_card       — 礼品卡
  └── is_subscription    — 订阅制
```

#### 2.2 分类规则（Apollo 配置）

```yaml
# Apollo Namespace: process.category
# 规则顺序 = 优先级（从上到下首次匹配）

order_category_rules:
  - category: "presale"
    name: "预售订单"
    match:
      - "itemType == PHYSICAL_GOODS && saleType == PRESALE"
      - "itemType == VIRTUAL_GOODS && saleType == PRESALE"
    template_id: "presale_flow"

  - category: "group_buy"
    name: "拼团订单"
    match:
      - "saleType == GROUP_BUY"
    template_id: "group_buy_flow"

  - category: "virtual"
    name: "虚拟商品"
    match:
      - "itemType == VIRTUAL_GOODS"
    template_id: "virtual_flow"

  - category: "digital"
    name: "数字商品"
    match:
      - "itemType == DIGITAL_GOODS"
    template_id: "virtual_flow"   # 复用虚拟商品模板

  - category: "overseas_physical"
    name: "跨境实物"
    match:
      - "itemType == PHYSICAL_GOODS && bizAttributes.contains('OVERSEAS')"
    template_id: "overseas_flow"

  - category: "physical"
    name: "标准实物"
    match:
      - "itemType == PHYSICAL_GOODS"
    template_id: "physical_flow"   # 默认兜底
```

#### 2.3 分类引擎

```java
/**
 * 订单分类引擎 —— 根据商品属性匹配订单分类
 *
 * 规则来自 Apollo process.category 命名空间，热生效
 */
@Component
public class OrderClassifier {

    private final ApolloConfig<List<CategoryRule>> rules;

    /**
     * 对订单上下文进行分类
     *
     * @param context 订单上下文（商品 SKU 列表 + 购买属性）
     * @return 订单分类结果
     */
    public OrderCategory classify(OrderCreateContext context) {
        // 1. 编译商品属性快照（itemType, saleType, bizAttributes）
        OrderAttributes attrs = OrderAttributes.from(context);

        // 2. 按规则顺序匹配
        for (CategoryRule rule : rules.get()) {
            if (rule.matches(attrs)) {
                return rule.getCategory();
            }
        }

        // 3. 无匹配 → 默认实物
        return OrderCategory.PHYSICAL;
    }
}
```

### 3. 流程模板 DSL

#### 3.1 模板定义格式

```yaml
# Apollo Namespace: process.template
# 每个业务场景一个模板

process_templates:
  # ─────────────── 实物订单流程 ───────────────
  - template_id: "physical_flow"
    name: "实物订单流程"
    description: "标准实物商品：校验 → 计价 → 促销 → 库存 → 支付"
    global_timeout_ms: 300000      # 5 分钟全局超时
    retry_policy:
      max_retries: 3
      backoff_seconds: [1, 5, 30]

    steps:
      - step_id: "validate"
        name: "参数校验"
        bean: "orderValidator"
        async: false
        timeout_ms: 5000
        compensate_bean: null            # 只读步骤，无补偿

      - step_id: "calculate_price"
        name: "计价"
        bean: "priceCalculator"
        async: false
        timeout_ms: 5000
        compensate_bean: null

      - step_id: "apply_promotion"
        name: "促销优惠计算"
        bean: "promotionApplier"
        async: false
        timeout_ms: 5000
        compensate_bean: "promotionRollbacker"

      - step_id: "apply_coupon"
        name: "优惠券核销"
        bean: "couponApplier"
        async: false
        timeout_ms: 5000
        compensate_bean: "couponReleaser"

      - step_id: "reserve_inventory"
        name: "预占库存"
        bean: "inventoryReserver"
        async: false
        timeout_ms: 5000
        compensate_bean: "inventoryUndoReserver"

      - step_id: "create_order"
        name: "创建订单记录"
        bean: "orderCreator"
        async: false
        timeout_ms: 5000
        compensate_bean: "orderCanceller"

      - step_id: "request_payment"
        name: "发起支付"
        bean: "paymentRequester"
        async: true
        timeout_ms: 120000
        compensate_bean: "paymentRefunder"

    strategies:
      split: "by_warehouse"
      merge: "by_vendor"
      shipping_cost: "by_weight"
      invoice: "by_amount"

    hooks:
      - event: "after_payment"
        beans: ["pointsGrantHandler", "firstOrderCouponIssuer"]
      - event: "after_complete"
        beans: ["crmSyncHandler"]

  # ─────────────── 虚拟商品流程 ───────────────
  - template_id: "virtual_flow"
    name: "虚拟商品流程"
    description: "虚拟商品：校验 → 计价 → 支付 → 自动完成（无库存/无物流）"
    global_timeout_ms: 120000
    retry_policy:
      max_retries: 2
      backoff_seconds: [1, 10]

    steps:
      - step_id: "validate"
        bean: "orderValidator"

      - step_id: "calculate_price"
        bean: "priceCalculator"

      - step_id: "apply_promotion"
        bean: "promotionApplier"
        compensate_bean: "promotionRollbacker"

      - step_id: "skip_inventory"          # 虚拟商品跳过库存
        bean: "nullStep"                    # 空操作步骤

      - step_id: "create_order"
        bean: "orderCreator"
        compensate_bean: "orderCanceller"

      - step_id: "request_payment"
        bean: "paymentRequester"
        compensate_bean: "paymentRefunder"

      - step_id: "auto_fulfill"            # 虚拟商品支付即完成
        bean: "autoFulfiller"
        compensate_bean: null

    strategies:
      split: "no_split"
      merge: "no_merge"
      shipping_cost: "free"
      invoice: "no_invoice"

    hooks:
      - event: "after_payment"
        beans: ["digitalGoodsDeliverer"]
      - event: "after_complete"
        beans: ["crmSyncHandler"]

  # ─────────────── 预售订单流程 ───────────────
  - template_id: "presale_flow"
    name: "预售订单流程"
    description: "预售：定金支付 → 等待尾款 → 尾款支付 → 发货"
    global_timeout_ms: 2592000000           # 30 天（预售周期长）
    retry_policy:
      max_retries: 3
      backoff_seconds: [5, 30, 120]

    steps:
      - step_id: "validate"
        bean: "orderValidator"

      - step_id: "calculate_deposit"
        name: "计算定金"
        bean: "depositCalculator"
        compensate_bean: null

      - step_id: "create_order"
        bean: "orderCreator"
        compensate_bean: "orderCanceller"

      - step_id: "request_deposit_payment"
        name: "发起定金支付"
        bean: "paymentDepositRequester"
        compensate_bean: "paymentDepositRefunder"

      - step_id: "wait_final_payment"
        name: "等待尾款支付"
        bean: "finalPaymentAwaiter"
        async: true
        timeout_ms: 2592000000              # 30 天
        compensate_bean: "finalPaymentCancelHandler"

      - step_id: "reserve_inventory"        # 尾款后才预占库存
        bean: "inventoryReserver"
        compensate_bean: "inventoryUndoReserver"

      - step_id: "request_final_payment"
        bean: "paymentFinalRequester"
        compensate_bean: "paymentFinalRefunder"

    strategies:
      split: "no_split"                     # 预售不拆单
      merge: "no_merge"
      shipping_cost: "free"
      invoice: "by_amount"

  # ─────────────── 拼团订单流程 ───────────────
  - template_id: "group_buy_flow"
    name: "拼团订单流程"
    description: "拼团：支付 → 等待成团 → 成团发货 / 不成团退款"
    global_timeout_ms: 864000000             # 10 天
    steps:
      - step_id: "validate"
        bean: "orderValidator"
      - step_id: "calculate_price"
        bean: "priceCalculator"
      - step_id: "create_order"
        bean: "orderCreator"
        compensate_bean: "orderCanceller"
      - step_id: "request_payment"
        bean: "paymentRequester"
        compensate_bean: "paymentRefunder"
      - step_id: "wait_group_formation"
        name: "等待成团"
        bean: "groupFormationAwaiter"
        async: true
        timeout_ms: 864000000
        compensate_bean: "groupBuyCancelHandler"
      - step_id: "reserve_inventory"
        bean: "inventoryReserver"
        compensate_bean: "inventoryUndoReserver"

    strategies:
      split: "no_split"
      merge: "no_merge"
      shipping_cost: "by_weight"
      invoice: "by_amount"

  # ─────────────── 跨境订单流程 ───────────────
  - template_id: "overseas_flow"
    name: "跨境订单流程"
    description: "跨境：校验 → 实名认证 → 海关申报 → 支付 → 发货"
    global_timeout_ms: 600000
    steps:
      - step_id: "validate"
        bean: "orderValidator"
      - step_id: "id_verification"
        name: "实名认证"
        bean: "identityVerifier"
        compensate_bean: null
      - step_id: "customs_declare"
        name: "海关申报预录入"
        bean: "customsDeclarer"
        compensate_bean: null
      - step_id: "calculate_price"
        bean: "priceCalculator"              # 含跨境税费
      - step_id: "reserve_inventory"
        bean: "inventoryReserver"
        compensate_bean: "inventoryUndoReserver"
      - step_id: "create_order"
        bean: "orderCreator"
        compensate_bean: "orderCanceller"
      - step_id: "request_payment"
        bean: "paymentRequester"
        compensate_bean: "paymentRefunder"

    strategies:
      split: "by_item"                       # 跨境按商品品类拆单
      merge: "no_merge"
      shipping_cost: "by_weight"
      invoice: "always"                      # 跨境强制发票
```

#### 3.2 模板步骤到 SagaStep 的映射

```yaml
# 模板步骤的属性 → SagaStep 的属性映射

template step:              SagaStep:
  step_id      ──────────→  stepName
  bean         ──────────→  forwardService   # 解析为 Dubbo service + method
  async        ──────────→  (在 SagaExecutor 中处理为异步等待)
  timeout_ms   ──────────→  stepTimeout
  compensate_bean ───────→  compensateService # null = 非必须补偿
  (无)          ──────────→  mandatory        # compensate_bean != null → true
```

**模板步骤的 bean → Dubbo 调用映射规则**：

```yaml
# Apollo Namespace: process.step-mapping
# 模板步骤中 bean 名称 → 实际 Dubbo 服务/方法

step_bean_mapping:
  orderValidator:
    service: "order-core"
    method: "validateOrder"
    version: "1.0.0"

  priceCalculator:
    service: "price-service"
    method: "calculate"
    version: "1.0.0"

  couponApplier:
    service: "coupon-service"
    method: "applyCoupon"
    version: "1.0.0"

  inventoryReserver:
    service: "inventory-service"
    method: "reserve"
    version: "1.0.0"

  inventoryUndoReserver:
    service: "inventory-service"
    method: "undoReserve"
    version: "1.0.0"

  # ... 其他 bean 映射
```

### 4. 流程引擎核心

#### 4.1 核心流程

```java
/**
 * 流程引擎 —— 模板驱动的订单处理编排
 *
 * 职责链：分类 → 模板加载 → 策略绑定 → Saga 生成 → 执行
 */
@Component
public class ProcessEngine {

    private final OrderClassifier classifier;
    private final ProcessTemplateRepository templateRepo;
    private final StrategyRegistry strategyRegistry;
    private final HookRegistry hookRegistry;
    private final SagaExecutor sagaExecutor;

    /**
     * 处理订单创建请求
     *
     * @param request 订单创建请求（含 SKU 列表、买家信息、购买属性）
     * @return 处理结果（订单号 + 执行状态）
     */
    public OrderProcessResult process(OrderCreateRequest request) {
        // 1. 订单分类
        OrderCategory category = classifier.classify(request);

        // 2. 加载流程模板
        ProcessTemplate template = templateRepo.getTemplate(category.getTemplateId());

        // 3. 绑定策略实现
        ProcessStrategies strategies = strategyRegistry.resolve(
            category, template.getStrategies());

        // 4. 注入扩展点
        List<HookPoint> hooks = hookRegistry.getHooks(template.getHookEvents());

        // 5. 动态生成 Saga 定义
        SagaDefinition sagaDefinition = buildSagaDefinition(template, strategies, hooks);

        // 6. 执行 Saga（复用 ADR-020 的执行器）
        SagaContext context = SagaContext.builder()
            .businessKey(request.getOutTradeNo())
            .args(request)
            .build();

        SagaResult result = sagaExecutor.execute(sagaDefinition, context);

        return OrderProcessResult.from(sagaDefinition, result, category);
    }

    /**
     * 流程模板 + 策略 + 扩展点 → SagaDefinition
     */
    private SagaDefinition buildSagaDefinition(
            ProcessTemplate template,
            ProcessStrategies strategies,
            List<HookPoint> hooks) {

        List<SagaStep> sagaSteps = new ArrayList<>();

        // 步骤 0：策略执行步骤（在业务步骤之前执行策略决策）
        sagaSteps.add(buildStrategyStep("resolve_split_strategy",
            strategies.getSplitStrategy(), StepPriorities.STRATEGY));

        // 步骤 1-N：模板步骤
        for (TemplateStep step : template.getSteps()) {
            sagaSteps.add(buildStepFromTemplate(step));
        }

        // 步骤 N+1：扩展点执行步骤
        for (HookPoint hook : hooks) {
            sagaSteps.add(buildHookStep(hook));
        }

        return SagaDefinition.builder()
            .sagaName(template.getTemplateId())
            .globalTimeout(template.getGlobalTimeout())
            .retryPolicy(template.getRetryPolicy())
            .steps(sagaSteps)
            .build();
    }
}
```

#### 4.2 步骤执行器 SPI

每个模板步骤的 `bean` 名称对应一个 `StepExecutor` 实现：

```java
/**
 * 步骤执行器 —— 模板步骤的业务逻辑实现
 *
 * 所有业务步骤实现此接口，通过 Spring Bean 名称被模板引用
 */
public interface StepExecutor {

    /**
     * 执行正向步骤
     *
     * @param context 步骤上下文（含订单参数、前置步骤结果）
     * @return 执行结果
     */
    StepResult execute(StepContext context);

    /**
     * 执行补偿（可选）
     *
     * @param context 补偿上下文
     */
    default void compensate(StepContext context) {
        // 默认无补偿
    }
}

/**
 * 空步骤 —— 用于跳过某些操作（如虚拟商品跳过库存）
 */
@Component("nullStep")
public class NullStepExecutor implements StepExecutor {

    @Override
    public StepResult execute(StepContext context) {
        return StepResult.success("skipped");
    }
}

/**
 * 订单校验器
 */
@Component("orderValidator")
public class OrderValidatorExecutor implements StepExecutor {

    @Override
    public StepResult execute(StepContext context) {
        OrderCreateRequest request = context.getArg(OrderCreateRequest.class);
        // 参数校验、库存初步检查、风控预检
        return StepResult.success("验证通过");
    }
}

/**
 * 预占库存
 */
@Component("inventoryReserver")
public class InventoryReserverExecutor implements StepExecutor {

    @Autowired
    private InventoryService inventoryService;

    @Override
    public StepResult execute(StepContext context) {
        DeductRequest req = context.getArg(DeductRequest.class);
        DeductResponse resp = inventoryService.deduct(
            context.getSagaId(), "reserve_inventory", req);
        context.setResult("inventoryResult", resp);
        return StepResult.success(resp);
    }

    @Override
    public void compensate(StepContext context) {
        DeductRequest req = context.getArg(DeductRequest.class);
        inventoryService.undoDeduct(
            context.getSagaId(), "reserve_inventory:compensate", req);
    }
}
```

### 5. 扩展点系统

#### 5.1 扩展点定义

```java
/**
 * 扩展点 Hook —— 在订单生命周期特定事件触发
 *
 * 实现类通过 Spring Bean 名称在 Apollo 配置中注册
 */
public interface OrderHook {

    /**
     * 执行扩展逻辑
     *
     * @param event 触发事件
     * @param context 事件上下文（订单数据、前置结果）
     */
    void handle(OrderLifecycleEvent event, HookContext context);

    /**
     * 执行顺序（越小越优先）
     */
    default int order() {
        return 0;
    }

    /**
     * 执行条件（SpEL 表达式）
     * 返回 null 表示无条件执行
     */
    default String condition() {
        return null;
    }
}
```

#### 5.2 内置扩展点

```
订单生命周期事件（event）：
  after_order_created    — 订单创建完成后（PENDING_PAY）
  after_payment          — 支付成功后（PAID）
  before_shipment        — 发货前校验
  after_shipment         — 发货后
  after_delivery         — 签收后
  before_cancel          — 取消前校验
  after_cancel           — 取消后
  after_complete         — 完成后
  after_refund           — 退款后
```

#### 5.3 扩展点配置

```yaml
# Apollo Namespace: process.hook
# 按业务线配置扩展点

hook_configs:
  # ═══════ 电商业务线 ═══════
  - biz_scope: "ecommerce"
    hooks:
      - event: "after_payment"
        bean: "pointsGrantHandler"
        order: 1
        condition: "context.totalAmount >= 100"   # 满 100 赠积分
        async: true

      - event: "after_payment"
        bean: "firstOrderCouponIssuer"
        order: 2
        condition: "context.isFirstOrder == true"
        async: true

      - event: "after_shipment"
        bean: "notificationHandler"
        async: true

      - event: "after_complete"
        bean: "crmSyncHandler"
        async: true

  # ═══════ B2B 业务线 ═══════
  - biz_scope: "b2b"
    hooks:
      - event: "before_shipment"
        bean: "b2bRiskCheckHandler"             # B2B 发货前强制风控
        order: 1
        async: false                             # 同步阻塞
        timeout_ms: 5000
        on_failure: "BLOCK"                      # 失败 = 阻断发货

      - event: "after_order_created"
        bean: "b2bApprovalNotifier"              # B2B 大单通知审批
        order: 1
        condition: "context.totalAmount >= 10000"
        async: true

  # ═══════ 本地生活业务线 ═══════
  - biz_scope: "locallife"
    hooks:
      - event: "after_complete"
        bean: "locallifeReviewReminder"          # 完成后提醒评价
        async: true
```

#### 5.4 扩展点调度

```java
/**
 * 扩展点调度器 —— 在 Saga 步骤执行间隙调用
 *
 * 通过 SagaExecutor 的 HookStep 集成到执行流程中
 */
@Component
public class HookDispatcher {

    private final Map<String, List<OrderHook>> hookMap;

    /**
     * 分发扩展点事件
     *
     * @param event 事件类型
     * @param context 事件上下文
     */
    public void dispatch(OrderLifecycleEvent event, HookContext context) {
        List<OrderHook> hooks = hookMap.getOrDefault(event.name(), Collections.emptyList());

        for (OrderHook hook : hooks) {
            // 1. 检查执行条件
            if (hook.condition() != null) {
                boolean matches = SpelEvaluator.evaluate(
                    hook.condition(), context, Boolean.class);
                if (!matches) continue;
            }

            // 2. 执行
            try {
                hook.handle(event, context);
            } catch (Exception e) {
                if (hook.isBlocking()) {
                    throw new HookExecutionException(event, hook, e);
                }
                log.error("非阻塞 Hook 执行失败: event={}, hook={}, error={}",
                    event, hook.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
```

### 6. 策略插件框架

#### 6.1 策略接口定义

```java
/**
 * ═══════════════ 拆单策略 ═══════════════
 *
 * 多商品订单是否需要拆分为多个子订单
 */
public interface SplitStrategy {

    /**
     * 判断是否需要拆单，以及如何拆分
     *
     * @param order 原始订单
     * @param items 订单商品列表
     * @return 拆单结果（不拆 = 返回 1 个分组）
     */
    List<SplitGroup> split(Order order, List<OrderItem> items);
}

/**
 * 不拆单 —— 所有商品在一个订单
 */
@Component("no_split")
public class NoSplitStrategy implements SplitStrategy {
    @Override
    public List<SplitGroup> split(Order order, List<OrderItem> items) {
        return Collections.singletonList(
            SplitGroup.single(items));
    }
}

/**
 * 按仓库拆单 —— 不同仓库的商品拆分为不同子订单
 */
@Component("by_warehouse")
public class ByWarehouseSplitStrategy implements SplitStrategy {
    @Override
    public List<SplitGroup> split(Order order, List<OrderItem> items) {
        return items.stream()
            .collect(Collectors.groupingBy(OrderItem::getWarehouseId))
            .entrySet().stream()
            .map(e -> SplitGroup.of(e.getKey(), "warehouse", e.getValue()))
            .collect(Collectors.toList());
    }
}

/**
 * 按商家拆单 —— 不同店铺/商家的商品各自独立
 */
@Component("by_vendor")
public class ByVendorSplitStrategy implements SplitStrategy {
    @Override
    public List<SplitGroup> split(Order order, List<OrderItem> items) {
        return items.stream()
            .collect(Collectors.groupingBy(OrderItem::getShopId))
            .entrySet().stream()
            .map(e -> SplitGroup.of(e.getKey(), "vendor", e.getValue()))
            .collect(Collectors.toList());
    }
}

/**
 * 按商品品类拆单 —— 不同大类的商品分开（跨境场景）
 */
@Component("by_item")
public class ByItemCategorySplitStrategy implements SplitStrategy {
    @Override
    public List<SplitGroup> split(Order order, List<OrderItem> items) {
        return items.stream()
            .collect(Collectors.groupingBy(OrderItem::getCategoryId))
            .entrySet().stream()
            .map(e -> SplitGroup.of(e.getKey(), "category", e.getValue()))
            .collect(Collectors.toList());
    }
}


/**
 * ═══════════════ 合单策略 ═══════════════
 *
 * 同一买家的多笔待发货订单是否合并发货
 */
public interface MergeStrategy {

    /**
     * 判断两笔订单是否可以合并发货
     */
    MergeDecision canMerge(Order orderA, Order orderB);
}

@Component("no_merge")
public class NoMergeStrategy implements MergeStrategy {
    @Override
    public MergeDecision canMerge(Order orderA, Order orderB) {
        return MergeDecision.no();
    }
}

@Component("by_vendor")
public class ByVendorMergeStrategy implements MergeStrategy {
    @Override
    public MergeDecision canMerge(Order orderA, Order orderB) {
        if (orderA.getShopId().equals(orderB.getShopId())
                && orderA.getBuyerId().equals(orderB.getBuyerId())) {
            return MergeDecision.yes("同一商家");
        }
        return MergeDecision.no();
    }
}


/**
 * ═══════════════ 运费计算策略 ═══════════════
 *
 * 根据订单重量、件数、金额等计算运费
 */
public interface ShippingCostStrategy {
    Money calculate(Order order, ShippingAddress address);
}

@Component("free")
public class FreeShippingStrategy implements ShippingCostStrategy {
    @Override
    public Money calculate(Order order, ShippingAddress address) {
        return Money.zero();   // 虚拟商品免运费
    }
}

@Component("by_weight")
public class ByWeightShippingStrategy implements ShippingCostStrategy {
    @Override
    public Money calculate(Order order, ShippingAddress address) {
        int totalWeight = order.getItems().stream()
            .mapToInt(OrderItem::getWeight).sum();
        // 首重 10 元/kg，续重 5 元/kg
        return Money.of(1000 + Math.max(0, totalWeight - 1000) * 5 / 1000);
    }
}


/**
 * ═══════════════ 发票策略 ═══════════════
 *
 * 判断订单是否需要开具发票
 */
public interface InvoiceStrategy {
    InvoiceDecision decide(Order order);
}

@Component("no_invoice")
public class NoInvoiceStrategy implements InvoiceStrategy {
    @Override
    public InvoiceDecision decide(Order order) {
        return InvoiceDecision.notRequired();
    }
}

@Component("by_amount")
public class ByAmountInvoiceStrategy implements InvoiceStrategy {
    @Override
    public InvoiceDecision decide(Order order) {
        // 订单金额 >= 200 元默认开具发票
        if (order.getTotalAmount().compareTo(Money.of(20000)) >= 0) {
            return InvoiceDecision.required("金额达到开票阈值");
        }
        return InvoiceDecision.optional();
    }
}

@Component("always")
public class AlwaysInvoiceStrategy implements InvoiceStrategy {
    @Override
    public InvoiceDecision decide(Order order) {
        return InvoiceDecision.required("跨境订单强制开票");
    }
}
```

#### 6.2 策略注册与选择

```java
/**
 * 策略注册表 —— 管理所有策略实现
 *
 * 按策略接口分组，Apollo 配置选择具体实现
 */
@Component
public class StrategyRegistry {

    /**
     * 根据分类和策略配置解析具体策略实现
     */
    public ProcessStrategies resolve(OrderCategory category, Map<String, String> strategyConfig) {
        return ProcessStrategies.builder()
            .splitStrategy(resolveStrategy(SplitStrategy.class, strategyConfig.get("split")))
            .mergeStrategy(resolveStrategy(MergeStrategy.class, strategyConfig.get("merge")))
            .shippingCostStrategy(resolveStrategy(ShippingCostStrategy.class, strategyConfig.get("shipping_cost")))
            .invoiceStrategy(resolveStrategy(InvoiceStrategy.class, strategyConfig.get("invoice")))
            .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T resolveStrategy(Class<T> strategyType, String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
}
```

#### 6.3 策略执行位置

策略在流程引擎中的执行时机：

```
模板步骤执行前的策略解析：

  ① OrderClassifier 分类 → 输出 orderCategory
  ② ProcessEngine 加载模板 template
  ③ StrategyRegistry 绑定策略实现
  ④ SplitStrategy.split() 执行拆单决策          ← 策略执行
      │
      ▼
  ⑤ 为每个 SplitGroup 生成独立的子流程
      │
      ▼
  ⑥ 每个子流程执行各自的模板步骤
      │  - priceCalculator（含 ShippingCostStrategy）
      │  - 创建子订单
      │  - 发起支付
      ▼
  ⑦ 支付成功后，InvoiceStrategy.decide() 决定开票  ← 策略执行
```

### 7. Apollo 配置汇总

| Namespace | 用途 | 热生效 |
|-----------|------|--------|
| `process.category` | 订单分类规则 | ✅ |
| `process.template` | 流程模板 YAML 定义 | ✅ |
| `process.step-mapping` | 步骤 bean → Dubbo 服务/方法映射 | ✅ |
| `process.strategy` | 各业务线/订单类型的策略选择 | ✅ |
| `process.hook` | 扩展点 SPI 注册与配置 | ✅ |

**配置变更流程**：

```
运营/产品修改 Apollo 配置
        │
        ▼
Apollo Config Change Listener 收到变更通知
        │
        ▼
本地缓存刷新（AtomicReference 原子替换）
        │
        ▼
新请求使用新配置（存量请求不受影响）
        │
        ▼
监控 `process.config.version` 指标，发现配置版本变化 → Grafana 事件标注
```

### 8. 与 Saga（ADR-020）的集成

流程引擎**不是替代** Saga，而是在 Saga 之上的**编排层**：

```
┌──────────────────────────────────────────────────────┐
│                   ProcessEngine                      │
│  模板加载 → 策略绑定 → Hook注入 → SagaDefinition       │
└──────────────────────────┬───────────────────────────┘
                           │ 生成
                           ▼
┌──────────────────────────────────────────────────────┐
│                   SagaExecutor                       │
│  步骤执行 → 补偿 → 重试 → 恢复                        │
│  (ADR-020 的 Saga 编排器，无变更)                      │
└──────────────────────────────────────────────────────┘
```

**集成点**：

| 方面 | 流程引擎 | Saga（ADR-020） |
|------|---------|----------------|
| **职责** | 定义"做什么"（流程模板 → 步骤列表） | 负责"怎么可靠执行"（正向/补偿/重试） |
| **输入** | 订单创建请求 + Apollo 配置 | SagaDefinition + SagaContext |
| **输出** | SagaDefinition | SagaResult（成功/补偿/失败） |
| **配置源** | Apollo 配置中心 | 无（硬编码 Saga 定义） |
| **变更方式** | 运营改 Apollo 配置 | 开发改 Java 代码 |

**关键约定**：

1. 流程引擎生成的 `SagaDefinition` 遵循 ADR-020 的全部规范
2. 所有 `StepExecutor` 的 `compensate()` 映射为 Saga 的补偿步骤
3. Saga 的 `saga_id` 由流程引擎生成，格式为 `{template_id}:{out_trade_no}:{timestamp}`
4. SagaLog 完全复用 ADR-020 的表结构和恢复 Job
5. 扩展点 Hook 的同步执行通过 `StepExecutor` 封装，异步执行通过 RocketMQ

---

## 数据模型

### 1. 流程执行记录表

```sql
-- process_execution: 流程执行实例表
-- 记录每次订单处理的流程执行轨迹
CREATE TABLE `process_execution` (
    `execution_id`      VARCHAR(64)    NOT NULL COMMENT '执行 ID（与 saga_id 一致）',
    `saga_id`           VARCHAR(64)    NOT NULL COMMENT '关联 Saga 实例',
    `template_id`       VARCHAR(64)    NOT NULL COMMENT '使用的模板 ID',
    `order_category`    VARCHAR(32)    NOT NULL COMMENT '订单分类结果',
    `business_key`      VARCHAR(128)   DEFAULT NULL COMMENT '业务主键（order_no）',
    `status`            VARCHAR(24)    NOT NULL DEFAULT 'PROCESSING' COMMENT '执行状态',
    `split_groups`      JSON           DEFAULT NULL COMMENT '拆单分组信息',
    `strategy_snapshot` JSON           DEFAULT NULL COMMENT '策略执行快照',
    `hook_executions`   JSON           DEFAULT NULL COMMENT '扩展点执行记录',
    `started_at`        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completed_at`      DATETIME(3)    DEFAULT NULL,
    `version`           INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (`execution_id`),
    KEY `idx_saga_id` (`saga_id`),
    KEY `idx_business_key` (`business_key`),
    KEY `idx_template_id` (`template_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程执行实例表';
```

### 2. 扩展点执行记录表

```sql
-- hook_execution_log: 扩展点执行日志
-- 记录每个 hook 的执行结果，用于排查和审计
CREATE TABLE `hook_execution_log` (
    `id`                BIGINT         NOT NULL AUTO_INCREMENT,
    `execution_id`      VARCHAR(64)    NOT NULL COMMENT '流程执行 ID',
    `event`             VARCHAR(32)    NOT NULL COMMENT '事件类型',
    `hook_bean`         VARCHAR(64)    NOT NULL COMMENT 'Hook Bean 名称',
    `async`             TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否异步',
    `status`            VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/SKIPPED',
    `condition_result`  TINYINT(1)     DEFAULT NULL COMMENT '条件判定结果',
    `error_message`     TEXT           DEFAULT NULL COMMENT '错误信息',
    `duration_ms`       INT            DEFAULT NULL COMMENT '执行耗时',
    `executed_at`       DATETIME(3)    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_execution_event` (`execution_id`, `event`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扩展点执行日志表';
```

### 3. 策略执行记录表

```sql
-- strategy_execution_log: 策略执行日志
-- 记录拆单/合单/运费/发票等策略的执行结果
CREATE TABLE `strategy_execution_log` (
    `id`                BIGINT         NOT NULL AUTO_INCREMENT,
    `execution_id`      VARCHAR(64)    NOT NULL COMMENT '流程执行 ID',
    `strategy_type`     VARCHAR(32)    NOT NULL COMMENT '策略类型（split/merge/shipping/invoice）',
    `strategy_bean`     VARCHAR(64)    NOT NULL COMMENT '选中的策略实现 Bean',
    `input_snapshot`    JSON           DEFAULT NULL COMMENT '输入参数快照',
    `output_snapshot`   JSON           DEFAULT NULL COMMENT '输出结果快照',
    `duration_ms`       INT            DEFAULT NULL,
    `executed_at`       DATETIME(3)    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_strategy_type` (`strategy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略执行日志表';
```

---

## API 设计

### 1. 流程模板管理 API（运营后台）

```java
// === 流程模板管理 ===

// 查询所有模板
GET /admin/v1/process-templates
Response: List<ProcessTemplateVO>

// 查询单个模板详情（含完整 YAML）
GET /admin/v1/process-templates/{templateId}

// 更新模板（通过 Apollo 配置，无需 API）
// 运营人员直接在 Apollo 编辑 YAML，热生效


// === 订单分类规则管理 ===

// 查询分类规则
GET /admin/v1/process-categories
Response: List<CategoryRuleVO>

// 测试订单分类（输入商品属性 → 输出分类结果）
POST /admin/v1/process-categories/test
Request: {
    "itemType": "PHYSICAL_GOODS",
    "saleType": "GROUP_BUY",
    "bizAttributes": ["OVERSEAS"]
}
Response: {
    "category": "group_buy",
    "templateId": "group_buy_flow",
    "matchedRule": "saleType == GROUP_BUY"
}


// === 策略配置管理 ===

// 查询策略配置
GET /admin/v1/process-strategies?templateId=physical_flow
Response: {
    "templateId": "physical_flow",
    "strategies": {
        "split": "by_warehouse",
        "merge": "by_vendor",
        "shippingCost": "by_weight",
        "invoice": "by_amount"
    }
}

// 查询可用的策略实现
GET /admin/v1/process-strategies/available
Response: {
    "split": ["no_split", "by_warehouse", "by_vendor", "by_item"],
    "merge": ["no_merge", "by_vendor"],
    "shippingCost": ["free", "by_weight", "by_volume"],
    "invoice": ["no_invoice", "by_amount", "always"]
}


// === 扩展点管理 ===

// 查询扩展点配置
GET /admin/v1/process-hooks?bizScope=ecommerce
Response: List<HookConfigVO>

// 模拟执行扩展点（测试用）
POST /admin/v1/process-hooks/test
Request: {
    "event": "after_payment",
    "bean": "pointsGrantHandler",
    "mockContext": {}
}
Response: {
    "success": true,
    "durationMs": 45,
    "result": "积分赠送成功: +100"
}
```

### 2. 流程执行查询 API

```java
// 查询流程执行轨迹（运营排查用）
GET /admin/v1/process-executions/{sagaId}
Response: {
    "executionId": "xxx",
    "templateId": "physical_flow",
    "orderCategory": "physical",
    "status": "COMPLETED",
    "steps": [
        {"stepId": "validate", "status": "SUCCESS", "durationMs": 12},
        {"stepId": "calculate_price", "status": "SUCCESS", "durationMs": 34},
        ...
    ],
    "hooks": [
        {"event": "after_payment", "bean": "pointsGrantHandler",
         "status": "SUCCESS", "async": true}
    ],
    "strategies": {
        "split": {
            "bean": "by_warehouse",
            "result": "拆分为 2 组: [warehouse_1(3 items), warehouse_2(1 item)]"
        }
    },
    "totalDurationMs": 1256
}
```

---

## 实施计划

| 阶段 | 任务 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | 流程引擎框架核心 | 基础设施 | 4 |
| 1.1 | Apollo 配置 Namespace（category/template/step-mapping） | PR: config | 0.5 |
| 1.2 | OrderClassifier + CategoryRule 匹配引擎 | PR: classifier | 0.5 |
| 1.3 | ProcessTemplateRepository（Apollo 加载 + 缓存 + 热刷新） | PR: template repo | 0.5 |
| 1.4 | ProcessEngine 核心（模板 → SagaDefinition 生成） | PR: engine | 1 |
| 1.5 | 步骤 bean → Dubbo 调用映射（StepMappingRegistry） | PR: step mapping | 0.5 |
| 1.6 | 数据模型 + Liquibase（process_execution/hook_log/strategy_log） | PR: schema | 0.5 |
| 1.7 | 单元测试 + 集成测试（分类/模板加载/Saga 生成） | 测试 | 0.5 |
| **Phase 2** | 策略插件框架 | 插件体系 | 2 |
| 2.1 | 4 个策略接口定义 + 所有内置实现 | PR: strategies | 1 |
| 2.2 | StrategyRegistry + Apollo 配置选择 | PR: registry | 0.5 |
| 2.3 | 策略执行日志（strategy_execution_log） | PR: strategy log | 0.5 |
| **Phase 3** | 扩展点系统 | Hook 体系 | 2 |
| 3.1 | OrderHook SPI 接口 + HookRegistry | PR: hook spi | 0.5 |
| 3.2 | HookDispatcher（同步/异步调度 + 条件评估） | PR: dispatcher | 0.5 |
| 3.3 | 内置 Hook 实现（pointsGrant/notification/crmSync/riskCheck） | PR: built-in hooks | 0.5 |
| 3.4 | hook_execution_log 记录 + 监控 | PR: hook monitoring | 0.5 |
| **Phase 4** | 首批流程模板 | 业务接入 | 2 |
| 4.1 | physical_flow 模板 + 所有步骤 Executor | PR: physical flow | 0.5 |
| 4.2 | virtual_flow 模板 + autoFulfiller | PR: virtual flow | 0.5 |
| 4.3 | presale_flow 模板 + deposit/final payment 支持 | PR: presale flow | 0.5 |
| 4.4 | group_buy_flow 模板 + group formation awaiter | PR: group buy | 0.5 |
| **Phase 5** | 运营管理 API | 自服务 | 1.5 |
| 5.1 | 模板/分类/策略/扩展点查询 API | PR: admin api | 0.5 |
| 5.2 | 分类测试 API（输入属性 → 输出分类） | PR: test api | 0.3 |
| 5.3 | 流程执行轨迹查询 API | PR: execution api | 0.3 |
| 5.4 | 运营文档（配置说明 + 最佳实践） | 文档 | 0.4 |
| **Phase 6** | 灰度上线 | 验证 | 1.5 |
| 6.1 | 灰度开关（Apollo switch: process.enabled=false 默认关） | 配置 | 0.2 |
| 6.2 | 灰度策略（先 5% buyer_id → 20% → 100%） | 验证 | 0.5 |
| 6.3 | 流程执行结果与硬编码对比对账 Job | 验证 | 0.5 |
| 6.4 | 全量切流 + 旧硬编码 Saga 清理 | 清理 | 0.3 |

**合计：13 人天**

---

## 监控与告警

### 1. Prometheus 指标

```java
// === 流程执行指标 ===

// 流程执行总数（按 template_id + status 分类）
Counter.builder("process.execution.total")
    .tag("template_id", templateId)
    .tag("status", status)       // processing / completed / compensated / failed
    .register(meterRegistry);

// 流程执行耗时（按 template_id 分类）
Timer.builder("process.execution.duration")
    .tag("template_id", templateId)
    .register(meterRegistry);

// 订单分类分布（按 category 分类）
Counter.builder("process.category.distribution")
    .tag("category", category)
    .register(meterRegistry);

// 步骤执行耗时（按 step_id 分类）
Timer.builder("process.step.duration")
    .tag("step_id", stepId)
    .register(meterRegistry);

// 步骤执行失败数
Counter.builder("process.step.failure")
    .tag("step_id", stepId)
    .tag("error_type", errorType)
    .register(meterRegistry);


// === 扩展点指标 ===

// Hook 执行总数（按 event + bean 分类）
Counter.builder("process.hook.execution")
    .tag("event", event)
    .tag("hook_bean", beanName)
    .tag("status", status)       // success / failed / skipped
    .register(meterRegistry);

// Hook 执行耗时（按 bean 分类）
Timer.builder("process.hook.duration")
    .tag("hook_bean", beanName)
    .register(meterRegistry);

// Hook 条件跳过率
Counter.builder("process.hook.skipped")
    .tag("event", event)
    .tag("hook_bean", beanName)
    .tag("reason", "condition_not_met")
    .register(meterRegistry);


// === 策略指标 ===

// 策略执行分布（按 strategy_type + bean 分类）
Counter.builder("process.strategy.distribution")
    .tag("type", strategyType)
    .tag("bean", beanName)
    .register(meterRegistry);

// 拆单结果分布（按拆分组数）
Gauge.builder("process.split.group_count", splitGroupCounter)
    .register(meterRegistry);


// === 配置指标 ===

// 配置版本号（Apollo 配置变更时自增）
Gauge.builder("process.config.version")
    .tag("namespace", namespace)
    .register(meterRegistry);

// 模板加载数量
Gauge.builder("process.template.loaded")
    .register(meterRegistry);
```

### 2. 告警规则

```yaml
# alerts/process_engine_alerts.yml
groups:
  - name: process_engine_alerts
    interval: 30s
    rules:
      # 流程执行成功率低于 99.5%
      - alert: ProcessExecutionSuccessRateLow
        expr: |
          sum(rate(process_execution_total{status!="completed"}[15m]))
          / sum(rate(process_execution_total[15m])) * 100 > 0.5
        for: 5m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "流程执行成功率低于 99.5%"

      # 某一步骤大量失败
      - alert: ProcessStepFailureSpike
        expr: rate(process_step_failure[15m]) > 10
        for: 5m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "步骤 {{ $labels.step_id }} 失败率异常"

      # Hook 执行大量失败
      - alert: ProcessHookFailureSpike
        expr: rate(process_hook_execution{status="failed"}[15m]) > 20
        for: 5m
        labels:
          severity: P3
          team: sre
        annotations:
          summary: "Hook {{ $labels.hook_bean }} 失败次数超过阈值"

      # 阻塞式 Hook 失败
      - alert: BlockingHookFailed
        expr: rate(process_hook_execution{hook_bean=~".*RiskCheck.*|.*Blocking.*", status="failed"}[5m]) > 0
        for: 1m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "阻塞式 Hook {{ $labels.hook_bean }} 失败，影响订单流程"
```

### 3. Grafana 看板

**看板：流程引擎运营看板**

```
行 1：核心指标
  - 流程执行总TPS（按 template_id 堆叠）
  - 流程执行成功率（最近 1h / 6h / 24h）
  - 流程执行 P99 耗时

行 2：分类与模板
  - 订单分类分布（饼图）
  - 模板执行占比（堆叠面积图）
  - 步骤执行耗时热力图

行 3：扩展点
  - Hook 执行成功率（按 event 分组）
  - Hook 执行耗时 TOP 10
  - Hook 跳过率（按条件/非条件）

行 4：策略
  - 拆单策略分布（饼图）
  - 运费策略分布
  - 发票策略分布

行 5：配置
  - 配置版本变更事件（Annotations）
  - 模板加载数 / 分类规则数
  - 最近配置变更记录
```

---

## 上线检查清单

### 基础设施
- [ ] `process_execution` 表已创建
- [ ] `hook_execution_log` 表已创建
- [ ] `strategy_execution_log` 表已创建
- [ ] Apollo 4 个 Namespace 已创建（category/template/step-mapping/strategy/hook）
- [ ] 灰度开关 `process.enabled` 已配置（默认 false）

### 代码
- [ ] 流程引擎框架核心已发布
- [ ] OrderClassifier + 分类规则引擎已发布
- [ ] ProcessTemplateRepository（Apollo 热刷新）已发布
- [ ] 4 个策略接口 + 内置实现已发布
- [ ] OrderHook SPI + HookRegistry + HookDispatcher 已发布
- [ ] process_execution/hook_log/strategy_log 写入已完成
- [ ] 灰度开关集成（Dubbo Consumer Filter 检查 `process.enabled`）

### 模板
- [ ] physical_flow 模板 + 所有 StepExecutor 已完成
- [ ] virtual_flow 模板 + autoFulfiller 已完成
- [ ] presale_flow 模板 + 分段支付支持已完成
- [ ] group_buy_flow 模板 + 成团等待支持已完成
- [ ] overseas_flow 模板 + 身份验证/海关申报已完成
- [ ] 分类规则全部定义（physical/virtual/presale/group_buy/overseas）

### 策略
- [ ] SplitStrategy: no_split / by_warehouse / by_vendor / by_item 均已实现
- [ ] MergeStrategy: no_merge / by_vendor 均已实现
- [ ] ShippingCostStrategy: free / by_weight / by_volume 均已实现
- [ ] InvoiceStrategy: no_invoice / by_amount / always 均已实现
- [ ] 各模板的 strategy 配置已填写

### 扩展点
- [ ] pointsGrantHandler（支付后赠积分）已实现
- [ ] notificationHandler（发货通知）已实现
- [ ] crmSyncHandler（完成同步 CRM）已实现
- [ ] riskCheckHandler（发货前风控，B2B）已实现
- [ ] 各业务线的 hook 配置已填写

### 测试
- [ ] 单元测试：OrderClassifier（分类规则匹配）
- [ ] 单元测试：ProcessEngine（模板 → SagaDefinition 生成）
- [ ] 单元测试：StrategyRegistry（策略选择）
- [ ] 单元测试：HookDispatcher（同步/异步调度 + 条件评估）
- [ ] 集成测试：physical_flow 完整执行
- [ ] 集成测试：virtual_flow（跳过库存步骤）
- [ ] 集成测试：presale_flow（定金 → 尾款流程）
- [ ] 集成测试：group_buy_flow（成团 → 发货 / 不成团 → 退款）
- [ ] 集成测试：拆单策略（按仓库/按商家/不拆）
- [ ] 故障注入：Hook 执行失败 → 阻塞式阻断流程 / 非阻塞式继续

### 监控
- [ ] Prometheus 指标已注册
- [ ] Grafana 流程引擎运营看板已上线
- [ ] ProcessExecutionSuccessRateLow 告警已启用（P2）
- [ ] ProcessStepFailureSpike 告警已启用（P2）
- [ ] 灰度切换事件已添加到 On-Call 值班面板

### 兼容性
- [ ] Apollo 开关 `process.enabled=false` 时，走原有硬编码 Saga
- [ ] `process.enabled=true` 时，走流程引擎生成的 Saga
- [ ] 灰度期间两个模式并行，对账 Job 验证结果一致
- [ ] 全量切流后删除旧的硬编码 Saga 定义

---

## 与现有 ADR 的关联

| ADR | 关联内容 |
|-----|---------|
| **ADR-020 Saga 分布式事务** | 流程引擎生成的 SagaDefinition 由 SagaExecutor 执行，复用 SagaLog/补偿/恢复机制 |
| **ADR-030 全局幂等框架** | 流程引擎执行步骤时复用 Idempotency-Key 标准，步骤 bean 的 Dubbo 调用自动幂等 |
| **ADR-017 业务线扩展点** | 扩展点按 `biz_scope` 区分（ecommerce/b2b/locallife），与 ADR-017 的业务线 Scope 一致 |
| **ADR-010 事件 Schema 治理** | 扩展点 Hook 的执行结果事件（`HookExecutedEvent`）需注册 Schema |
| **ADR-015 容量规划** | 流程引擎本身无显著开销（CPU 密集型模板 → Saga 转换），每个订单增加 < 5ms 延迟 |
| **ADR-022 全链路灰度** | 流程模板切换可通过灰度标签控制（`gray_tag=canary` 的请求使用新版模板） |
| **ADR-035 多租户** | 扩展点和策略配置按 `tenant_id` 隔离，不同租户可绑定不同的 hook 集合 |
| **ADR-036 全渠道订单接入** | 渠道适配器标准化后的订单，通过 OrderClassifier 分类后进入流程引擎处理 |

---

## 备选方案评估

### 方案 A（选定）：YAML DSL + Apollo + 轻量引擎

**优点**：
- 无需额外基础设施（Apollo 已存在）
- 与现有 Saga 自然集成
- YAML 人类可读，运营可配置
- Spring Bean 直接作为步骤实现

**缺点**：
- 无 BPMN 可视化设计器（需开发简单管理界面）
- 模板复杂度受限于 DSL 表达能力

### 方案 B：Camunda BPMN 引擎

**优点**：
- BPMN 2.0 标准，有可视化设计器
- 成熟的流程引擎，社区活跃
- 内置历史、审计、身份管理

**缺点**：
- 引入独立引擎（30MB+ 依赖）
- 需要部署 Camunda Runtime 或嵌入式引擎
- Dubbo 服务调用需自定义 JavaDelegate 适配层
- 与现有 Saga 补偿机制难以集成
- 运维复杂度大幅增加（BPMN 部署/版本管理）

### 方案 C：纯 Java 配置 + 代码生成

**优点**：
- 不改现有架构
- 编译期类型安全

**缺点**：
- 每次新增流程需开发上线
- 无法满足运营自服务的需求
- 策略调整仍需 PR + 发布

### 方案 D：规则引擎（Drools）定义流程

**优点**：
- 规则引擎表达能力极强
- 复杂条件分支支持好

**缺点**：
- 规则引擎 ≠ 流程引擎，缺少步骤顺序编排能力
- DRL 文件学习成本高，运营难以理解和维护
- 性能有额外开销

---

> **结论**：方案 A（YAML DSL + Apollo + 轻量引擎）在灵活性和简洁性之间取得最佳平衡。它没有引入额外的基础设施依赖，与现有的 Saga 框架和 Apollo 配置体系自然融合，同时给了运营人员足够的配置自由度。如果未来需要更复杂的流程编排能力，可以在方案 A 的基础上叠加 BPMN 可视化层。
