# ADR-039：订单全生命周期管理

## 状态

已接受

---

## 背景

### 现状分析

订单状态管理是订单中台最核心的业务逻辑，但当前设计存在以下问题：

**问题 1：状态机无形式化定义，非法转换无法被阻止**

当前订单状态仅以 Markdown 图形式定义（`docs/diagrams/state-machine.md`），包含 10 个状态和 13 条转换边。但它的作用是文档（描述性的），而非架构（约束性的）：

```
PENDING_PAY → PAID      ✅   合法
PAID → PENDING_PAY      ❌   非法（但无任何机制阻止）
COMPLETED → REFUNDING   ❌   非法（终态不应可变更）
```

代码层没有任何状态转换引擎来校验"`PAID` 能否直接回到 `PENDING_PAY`"——这个判断完全依赖开发人员在 Controller/Service 中的 if-else 逻辑。**非法转换在代码评审阶段才可能被发现，而非在架构层面被强制禁止。**

架构一致性报告中 **C6 严重缺口** 明确指出：状态机没有任何 ADR 覆盖，state-machine.md 自称"尚无对应的 ADR"。

**问题 2：状态转换逻辑散落在各处，无统一校验**

当前状态变更发生在多个位置：

| 变更场景 | 所在服务 | 校验方式 |
|---------|---------|---------|
| 支付回调 → PAID | payment-core | 无状态校验，仅检查订单存在 |
| 发货 → SHIPPED | logistics-service | 商家 if-else 判断 |
| 取消 → CANCELLED | order-core | 状态枚举 in 列表判断 |
| 退款 → REFUNDING | aftersale-service | 各自维护校验逻辑 |
| 超时关单 → CLOSED | XXL-Job | 无校验，直接 update |

没有统一的状态机引擎拦截非法转换。每个服务各自维护一套"什么状态可以做什么"的判断逻辑——重复、不一致、容易遗漏边界情况。

**问题 3：原子操作无标准契约**

订单创建、支付、修改、拆分、合并、取消、确认收货——这些核心操作没有标准化的接口契约。每个操作的（前置校验 → 状态转换 → 事件发布 → 后置处理 → 补偿）没有统一模板，导致：

| 操作 | 有无接口定义 | 有无状态校验 | 有无补偿定义 |
|------|------------|------------|------------|
| 创建订单 | ❌ 仅在 Saga 中硬编码 | ❌ | ✅ Saga 补偿 |
| 支付处理 | ❌ 支付回调硬编码 | ❌ | ❌ |
| 地址修改 | ❌ Controller 直接 update | ❌ | ❌ |
| 订单拆分 | ✅ ADR-037 策略接口 | ❌ | ❌ |
| 订单合并 | ❌ 未设计 | — | — |
| 订单取消 | ❌ Controller 硬编码 | ❌ | ✅ Saga 补偿 |
| 确认收货 | ❌ XXL-Job 扫描 | ❌ | ❌ |

**问题 4：异常处理分散且不完整**

支付超时关单（30min）和自动确认收货（7d）有设计，但以下场景缺失：

- 库存不足时订单卡在 `PENDING_PAY`，无 HOLD 态标注，用户无法感知
- 商家长时间不发货（`TO_SHIP` 超过正常时效），无自动预警
- 退款处理卡住（`REFUNDING` 长期未完成），无自动检测
- 没有"卡单检测器"——系统被动等待用户/客服发现异常订单
- 没有人工干预 API——客服发现卡单后，唯一手段是改数据库

### 已有基础

| 组件 | ADR | 与本次设计的关系 |
|------|-----|----------------|
| **流程引擎** | ADR-037 | 编排 WHAT（流程步骤），状态机是它的下层依赖 |
| **Saga 事务** | ADR-020 | 确保状态转换的事务性，状态机为 Saga 提供转换校验 |
| **延迟任务** | ADR-021 | 支付超时关单、自动确认收货的基础设施 |
| **事件发布** | ADR-010 | 状态转换后发布 DomainEvent 的 Schema 规范 |
| **全域幂等** | ADR-030 | 原子服务的幂等保障 |
| **开放集成** | ADR-038 | 状态变更作为事件广播到下游系统 |
| **可观测性** | ADR-027 | 状态转换指标、卡单告警的基础设施 |
| **内部网关** | ADR-029 | 买家/管理员 API 的认证和路由 |

### 设计目标

1. **状态机形式化**：定义 13 态及其合法转换矩阵，从架构层面阻止非法转换
2. **原子服务标准化**：7 个核心操作有统一接口契约（校验 → 转换 → 事件 → 补偿）
3. **异常处理自动化**：超时矩阵 + HOLD 态 + 卡单检测器 + 人工干预 API
4. **与现有体系集成**：流程引擎（ADR-037）+ Saga（ADR-020）+ 延迟任务（ADR-021）+ 幂等（ADR-030）+ 事件（ADR-010/038）

### 术语定义

| 术语 | 定义 |
|------|------|
| **状态（State）** | 订单在一段稳定区间内的业务阶段，具有明确的进入条件和退出条件 |
| **转换（Transition）** | 订单从一个状态到另一个状态的合法变更，由外部动作或系统事件触发 |
| **守卫（Guard）** | 转换执行前的业务条件判断，通过则允许转换，拒绝则抛出业务异常 |
| **入口动作（Entry Action）** | 进入某个状态时自动执行的逻辑（如发布事件、触发检查） |
| **出口动作（Exit Action）** | 离开某个状态时自动执行的逻辑（如取消计时器） |
| **原子服务（Atomic Service）** | 完成一次状态转换的标准操作单元，含校验 → 转换 → 事件 → 补偿 |
| **状态超时（State Timeout）** | 订单在某个非终态停留的最大允许时长，超时触发预设处理 |

---

## 决策

### 决策 1：状态机引擎架构 — 自定义轻量引擎

| 策略 | 说明 | 评估 |
|------|------|------|
| **A：自定义轻量引擎** | Java enum + `Map<S, Map<S, Transition>>` 转换表 | ✅ **选中** |
| **B：Spring Statemachine** | 开源框架，状态机 DSL + 监听器 | ❌ 过度设计 |
| **C：状态嵌入 Saga** | 状态转换完全由 Saga 步骤隐式管理 | ❌ 状态与编排耦合 |

**理由**：
- 订单状态空间有限（13 态 × 最多 6 个出边），无需完整状态机框架
- enum 方式转换表自文档化、编译期类型安全
- 接口 `StateMachine<S, E>` 预留了未来替换能力
- 不引入新依赖，开发人员零学习成本
- Spring Statemachine 对于 13 态场景属于"杀鸡用牛刀"，且序列化/持久化需要额外工作量

### 决策 2：状态扩展 — 10 态 → 13 态

| 新增状态 | 来源 | 说明 |
|---------|------|------|
| `RETURNING` | `SHIPPED`/`DELIVERED` → 退货中 | 与 `REFUNDING`（仅退款）并行，独立超时 |
| `HOLD` | `PENDING_PAY`/`PAID` → 挂起 | 库存不足或风控等原因阻塞，回退后恢复 |
| `FROZEN` | 所有非终态 → 冻结 | 客服/管理员人工锁定，禁止自动操作 |

### 决策 3：拆合单状态策略 — 子订单独立状态机

子订单拥有独立的状态机实例。父订单作为"虚拟协调器"反映聚合状态：
- 所有子订单进入同一终态 → 父订单进入该终态
- 子订单状态不一致 → 父订单为 `PARTIAL`（部分完成）
- 订单合并时，子订单关闭，父订单接管状态机

### 决策 4：状态超时矩阵

| 非终态 | 最大停留时间 | 超时处理 | 延迟任务 Tier |
|--------|------------|---------|---------------|
| `PENDING_PAY` | 30 min | 自动关闭 → `CLOSED` | Tier 2 RocketMQ |
| `PAID` | 24 h | P2 告警（催促发货） | Tier 3 DB 轮询 |
| `TO_SHIP` | 72 h | P2 告警（超时未发货） | Tier 3 DB 轮询 |
| `SHIPPED` | 7 d | 自动确认收货 → `DELIVERED` | Tier 3 DB 轮询 |
| `DELIVERED` | 7 d | 自动完成 → `COMPLETED` | Tier 3 DB 轮询 |
| `HOLD` | 48 h | P2 告警（挂起超时）→ 人工介入 | Tier 3 DB 轮询 |
| `REFUNDING` | 72 h | 触发退款状态对账 → 自动或 P1 告警 | Tier 3 DB 轮询 |
| `RETURNING` | 15 d | 触发退货状态对账 → 自动或 P1 告警 | Tier 3 DB 轮询 |
| `FROZEN` | 无限制 | 管理员手动解冻 | — |

### 决策 5：卡单检测器独立于 SagaRecoveryJob

| 组件 | 扫描对象 | 发现问题 | 触发动作 |
|------|---------|---------|---------|
| **SagaRecoveryJob**（ADR-020） | `saga_instance` 表 | Saga 编排器卡住或补偿失败 | 自动重试补偿 → DLQ + P1 |
| **StuckOrderDetector**（新增） | `order` 表状态列 + 超时矩阵 | 业务态卡住（如 TO_SHIP 超 72h） | P2 告警 → 自动或人工干预 |

两者互补：前者处理 **Saga 执行失败**，后者处理 **业务状态停滞**。

### 决策 6：三层职责分离

```
流程引擎（ADR-037）    →   WHAT     →   何时触发转换
状态机引擎（ADR-039）   →   WHEN     →   转换是否合法
原子服务（本文）        →   HOW      →   如何执行转换
```

---

## 详细设计

### 1. 状态机引擎

#### 1.1 13 态定义

```java
public enum OrderStatus {
    // 正向流转
    PENDING_PAY,    // 待支付（初始状态）
    PAID,           // 已支付
    TO_SHIP,        // 待发货
    SHIPPED,        // 已发货
    DELIVERED,      // 已签收
    COMPLETED,      // 已完成（终态）

    // 逆向流转
    CANCELLED,      // 已取消（终态）
    CLOSED,         // 超时关闭（终态）

    // 退款/退货
    REFUNDING,      // 退款中（仅退款，不退货）
    RETURNING,      // 退货中（需退货后退款）
    REFUNDED,       // 已退款（终态）

    // 异常/干预
    HOLD,           // 挂起（库存不足/风控）
    FROZEN;         // 冻结（管理员人工锁定）
}
```

#### 1.2 N×N 状态转换矩阵

下表中行 = 当前状态，列 = 目标状态。`✅` = 合法转换，空白 = 非法。

| 当前 \ 目标 | PENDING_PAY | PAID | TO_SHIP | SHIPPED | DELIVERED | COMPLETED | CANCELLED | CLOSED | REFUNDING | RETURNING | REFUNDED | HOLD | FROZEN |
|------------|:-----------:|:----:|:-------:|:-------:|:---------:|:---------:|:---------:|:-----:|:---------:|:---------:|:--------:|:----:|:------:|
| **PENDING_PAY** | — | ✅ 支付 | | | | | ✅ 取消 | ✅ 超时 | | | | ✅ 库存不足/风控 | ✅ 冻结 |
| **PAID** | | — | ✅ 确认发货 | | | | ✅ 取消（商务） | | ✅ 退款申请 | | | ✅ 库存不足 | ✅ 冻结 |
| **TO_SHIP** | | | — | ✅ 发货 | | | | | ✅ 退款申请 | ✅ 退货申请 | | | ✅ 冻结 |
| **SHIPPED** | | | | — | ✅ 确认收货 | | | | | ✅ 退货申请 | | | ✅ 冻结 |
| **DELIVERED** | | | | | — | ✅ 自动完成 | | | | ✅ 退货申请 | | | ✅ 冻结 |
| **COMPLETED** | | | | | | — | | | | ✅ 售后申请 | | | ✅ 冻结 |
| **CANCELLED** | | | | | | | — | | | | | | |
| **CLOSED** | | | | | | | | — | | | | | |
| **REFUNDING** | | | | | | | | | — | | ✅ 退款完成 | | ✅ 冻结 |
| **RETURNING** | | | | | | | | | | — | ✅ 退款完成 | | ✅ 冻结 |
| **REFUNDED** | | | | | | | | | | | — | | |
| **HOLD** | ✅ 解除挂起 | ✅ 库存恢复 | | | | | ✅ 取消 | | | | | — | ✅ 冻结 |
| **FROZEN** | ✅ 解冻 | ✅ 解冻 | ✅ 解冻 | ✅ 解冻 | ✅ 解冻 | | ✅ 取消 | | | | | ✅ 解冻 | — |

**禁止转换示例**（引擎强制拦截）：
- `PAID → PENDING_PAY`：支付成功后不能回退到待支付
- `COMPLETED → REFUNDING`：完成后不能直接进入退款（必须走售后流程）
- `CANCELLED → PAID`：取消后不能再支付
- `PENDING_PAY → SHIPPED`：未支付不能发货
- `COMPLETED → any（FROZEN 除外）`：终态不可逆

#### 1.3 Castle: StateMachine Engine 接口设计

```java
/**
 * 订单状态机引擎
 * <p>
 * 职责：根据转换矩阵校验状态转换合法性，执行守卫条件和生命周期钩子。
 * 集成：被流程引擎（ADR-037）和原子服务调用。
 */
public interface StateMachineEngine {

    /**
     * 执行状态转换。
     *
     * @param orderId     订单号
     * @param current     当前状态（用于乐观锁校验）
     * @param target      目标状态
     * @param context     转换上下文（操作人、原因、业务参数等）
     * @return 转换后的新状态
     * @throws IllegalStateTransitionException 非法转换异常
     * @throws StateGuardRejectedException     守卫条件不满足
     * @throws OptimisticLockException         并发冲突
     */
    OrderStatus transition(
            String orderId,
            OrderStatus current,
            OrderStatus target,
            TransitionContext context
    );

    /**
     * 批量状态转换（拆单场景）。
     * 子订单独立转换，任一失败整体回滚。
     */
    List<OrderStatus> transitionBatch(
            List<String> orderIds,
            OrderStatus current,
            OrderStatus target,
            TransitionContext context
    );

    /**
     * 校验转换是否合法（不执行，只判断）。
     */
    boolean canTransition(OrderStatus current, OrderStatus target);

    /**
     * 获取某个状态的合法出边列表。
     */
    Set<OrderStatus> allowedTargets(OrderStatus current);
}
```

```java
/**
 * 转换上下文。
 * 每个字段在转换日志中均可追溯。
 */
@Data
@Builder
public class TransitionContext {
    private String operatorId;          // 操作人 ID（系统操作 = "SYSTEM"）
    private String operatorType;        // 操作人类型：BUYER / SELLER / ADMIN / SYSTEM
    private String source;              // 触发来源：API / JOB / CALLBACK / MQ
    private String reason;              // 转换原因
    private Map<String, Object> extras; // 附加业务参数
}
```

#### 1.4 转换矩阵实现

```java
@Component
public class OrderStateTransitionMatrix {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PENDING_PAY 的出边
        TRANSITIONS.put(PENDING_PAY, Set.of(PAID, CANCELLED, CLOSED, HOLD, FROZEN));
        // PAID 的出边
        TRANSITIONS.put(PAID, Set.of(TO_SHIP, CANCELLED, REFUNDING, HOLD, FROZEN));
        // TO_SHIP 的出边
        TRANSITIONS.put(TO_SHIP, Set.of(SHIPPED, REFUNDING, RETURNING, FROZEN));
        // SHIPPED 的出边
        TRANSITIONS.put(SHIPPED, Set.of(DELIVERED, RETURNING, FROZEN));
        // DELIVERED 的出边
        TRANSITIONS.put(DELIVERED, Set.of(COMPLETED, RETURNING, FROZEN));
        // COMPLETED 的出边
        TRANSITIONS.put(COMPLETED, Set.of(RETURNING, FROZEN));
        // CANCELLED — 终态无出边
        TRANSITIONS.put(CANCELLED, Set.of());
        // CLOSED — 终态无出边
        TRANSITIONS.put(CLOSED, Set.of());
        // REFUNDING 的出边
        TRANSITIONS.put(REFUNDING, Set.of(REFUNDED, FROZEN));
        // RETURNING 的出边
        TRANSITIONS.put(RETURNING, Set.of(REFUNDED, FROZEN));
        // REFUNDED — 终态无出边
        TRANSITIONS.put(REFUNDED, Set.of());
        // HOLD 的出边
        TRANSITIONS.put(HOLD, Set.of(PENDING_PAY, PAID, CANCELLED, FROZEN));
        // FROZEN 的出边
        TRANSITIONS.put(FROZEN, Set.of(PENDING_PAY, PAID, TO_SHIP, SHIPPED, DELIVERED,
                CANCELLED, HOLD, REFUNDING, RETURNING));
    }

    public boolean isValid(OrderStatus current, OrderStatus target) {
        return TRANSITIONS.getOrDefault(current, Collections.emptySet()).contains(target);
    }

    public Set<OrderStatus> getAllowedTargets(OrderStatus current) {
        return TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}
```

#### 1.5 状态机引擎实现

```java
@Component
public class OrderStateMachineEngine implements StateMachineEngine {

    private final OrderStateTransitionMatrix matrix;
    private final OrderRepository orderRepository;
    private final StateTransitionEventPublisher eventPublisher;

    /**
     * 守卫条件注册表：Map<(当前状态, 目标状态), List<Guard>>
     */
    private final Map<TransitionKey, List<StateGuard>> guardRegistry = new HashMap<>();

    /**
     * 入口动作注册表：Map<目标状态, List<EntryAction>>
     */
    private final Map<OrderStatus, List<EntryAction>> entryActions = new HashMap<>();

    /**
     * 出口动作注册表：Map<当前状态, List<ExitAction>>
     */
    private final Map<OrderStatus, List<ExitAction>> exitActions = new HashMap<>();

    @Override
    public OrderStatus transition(String orderId, OrderStatus current, OrderStatus target,
                                  TransitionContext context) {
        // 1. 校验转换合法性
        if (!matrix.isValid(current, target)) {
            throw new IllegalStateTransitionException(orderId, current, target);
        }

        // 2. 执行守卫条件
        evaluateGuards(orderId, current, target, context);

        // 3. 乐观锁获取订单
        Order order = orderRepository.findByIdForUpdate(orderId);
        if (order.getStatus() != current) {
            throw new OptimisticLockException(orderId, current, order.getStatus());
        }

        // 4. 执行出口动作（离开 oldState）
        executeExitActions(current, order, context);

        // 5. 更新状态（CAS 语义：SET status = target WHERE status = current）
        int updated = orderRepository.updateStatusWithVersionCheck(
                orderId, current, target, order.getVersion());
        if (updated == 0) {
            throw new OptimisticLockException(orderId, current, target);
        }

        // 6. 执行入口动作（进入 newState）
        order.setStatus(target);
        executeEntryActions(target, order, context);

        // 7. 发布状态转换事件
        eventPublisher.publish(new OrderStatusChangedEvent(
                orderId, current, target, context));

        return target;
    }

    private void evaluateGuards(String orderId, OrderStatus current,
                                OrderStatus target, TransitionContext context) {
        List<StateGuard> guards = guardRegistry.get(new TransitionKey(current, target));
        if (guards == null) return;
        for (StateGuard guard : guards) {
            if (!guard.evaluate(orderId, context)) {
                throw new StateGuardRejectedException(orderId, current, target,
                        guard.rejectReason());
            }
        }
    }

    // ---------- 注册守卫和动作 ----------

    public void registerGuard(OrderStatus current, OrderStatus target, StateGuard guard) {
        guardRegistry.computeIfAbsent(new TransitionKey(current, target), k -> new ArrayList<>())
                .add(guard);
    }

    public void registerEntryAction(OrderStatus state, EntryAction action) {
        entryActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
    }

    public void registerExitAction(OrderStatus state, ExitAction action) {
        exitActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
    }
}
```

#### 1.6 守卫条件（Guard）设计

守卫条件用于在转换执行前进行业务判断。典型场景：

| 转换 | 守卫条件 | 拒绝原因 |
|------|---------|---------|
| `PENDING_PAY → PAID` | 支付金额与订单金额一致 | "支付金额不匹配" |
| `PAID → CANCELLED` | 非虚拟商品且已发货部分占比为 0 | "已发货订单不可取消" |
| `PAID → TO_SHIP` | 支付完成且退款中标记为 false | "退款处理中不能发货" |
| `PENDING_PAY → HOLD` | 库存可用量 < 订单数量 | "库存不足" |
| `HOLD → PENDING_PAY` | 库存可用量 ≥ 订单数量 | "库存仍未补充" |
| `SHIPPED → DELIVERED` | 物流轨迹显示已签收 OR 操作人 = BUYER | "物流未完成且非买家确认" |

```java
/**
 * 守卫条件接口。
 * 每个守卫专注于一个业务条件的判断，可组合使用。
 */
@FunctionalInterface
public interface StateGuard {
    /**
     * 评估守卫条件。
     * @return true = 通过，false = 拒绝转换
     */
    boolean evaluate(String orderId, TransitionContext context);

    /**
     * 守卫被拒绝时的业务提示信息。
     */
    default String rejectReason() {
        return "非法操作";
    }
}

// 示例：支付金额校验守卫
@Component
public class PaymentAmountGuard implements StateGuard {
    @Override
    public boolean evaluate(String orderId, TransitionContext context) {
        BigDecimal paidAmount = (BigDecimal) context.getExtras().get("paidAmount");
        Order order = orderRepository.findById(orderId);
        return paidAmount.compareTo(order.getTotalAmount()) == 0;
    }

    @Override
    public String rejectReason() {
        return "支付金额与订单金额不匹配";
    }
}
```

#### 1.7 入口/出口动作设计

```java
/**
 * 入口动作：进入某个状态时自动执行。
 */
@FunctionalInterface
public interface EntryAction {
    void onEntry(String orderId, OrderStatus from, TransitionContext context);
}

/**
 * 出口动作：离开某个状态时自动执行。
 */
@FunctionalInterface
public interface ExitAction {
    void onExit(String orderId, OrderStatus to, TransitionContext context);
}
```

典型动作注册：

| 状态 | 动作类型 | 动作内容 |
|------|---------|---------|
| `PENDING_PAY` | Entry | 注册 30min 支付超时延迟任务（ADR-021） |
| `PENDING_PAY` | Exit | 取消支付超时延迟任务（如已支付） |
| `PAID` | Entry | 发布 `OrderPaidEvent`（ADR-010/038） |
| `TO_SHIP` | Entry | 注册 72h 超时未发货告警任务 |
| `SHIPPED` | Entry | 注册 7d 自动确认收货延迟任务 |
| `COMPLETED` | Entry | 发布 `OrderCompletedEvent` |
| `HOLD` | Entry | 注册 48h 挂起超时告警任务 |
| `FROZEN` | Entry | 通知客服工单系统已冻结 |
| `REFUNDED` | Entry | 释放预占库存、恢复优惠券 |

#### 1.8 乐观锁防并发

```java
// order 表新增 version 字段
@Table(name = "`order`")
public class Order {
    @Version
    private Long version;       // 乐观锁版本号

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;  // 当前状态

    @Column(length = 32)
    private String previousStatus;  // 前一状态（追溯用）

    private LocalDateTime statusChangedAt;  // 上次状态变更时间
    private LocalDateTime statusExpiresAt;  // 当前状态的超时时间（由 Apollo 配置驱动）
}
```

```sql
-- 状态更新 SQL（CAS 语义）
UPDATE `order`
SET status = ?, version = version + 1,
    previous_status = status,
    status_changed_at = NOW(),
    status_expires_at = DATE_ADD(NOW(), INTERVAL ? MINUTE)
WHERE order_no = ? AND status = ? AND version = ?
```

### 2. 核心原子服务

#### 2.1 原子服务接口规范

每个原子服务遵循统一契约：

```
┌─────────────────────────────────────────────┐
│               AtomicOrderService              │
├─────────────────────────────────────────────┤
│ 1. validate()     → 前置校验（状态 + 业务规则）  │
│ 2. stateMachine.transition() → 状态转换       │
│ 3. execute()      → 执行核心业务逻辑           │
│ 4. publishEvent() → 发布状态变更事件           │
│ 5. compensate()   → 补偿逻辑（可选）            │
└─────────────────────────────────────────────┘
```

```java
/**
 * 原子服务基类。
 * 所有订单操作服务继承此类，获得统一的生命周期管理。
 */
public abstract class AbstractAtomicOrderService {

    @Autowired
    private StateMachineEngine stateMachineEngine;

    /**
     * 执行订单操作。
     * 模板方法：校验 → 状态转换 → 执行业务 → 发布事件。
     */
    public final OrderStatus execute(String orderId, TransitionContext context) {
        // 1. 前置校验
        validate(orderId, context);

        // 2. 状态转换（由子类指定 target）
        OrderStatus newStatus = stateMachineEngine.transition(
                orderId, resolveCurrentStatus(orderId),
                resolveTargetStatus(), context);

        // 3. 执行业务逻辑
        doExecute(orderId, context);

        // 4. 发布事件
        publishEvent(orderId, newStatus, context);

        return newStatus;
    }

    /**
     * 补偿操作（可选，Saga 集成时使用）。
     */
    public void compensate(String orderId, TransitionContext context) {
        doCompensate(orderId, context);
        // 补偿时应回退状态
    }

    // ---- 子类实现 ----
    protected abstract void validate(String orderId, TransitionContext context);
    protected abstract OrderStatus resolveTargetStatus();
    protected abstract void doExecute(String orderId, TransitionContext context);
    protected abstract void publishEvent(String orderId, OrderStatus newStatus,
                                          TransitionContext context);

    protected void doCompensate(String orderId, TransitionContext context) {
        // 默认空实现，子类按需覆盖
    }

    private OrderStatus resolveCurrentStatus(String orderId) {
        return orderRepository.findById(orderId).getStatus();
    }
}
```

#### 2.2 OrderCreateService — 订单创建

| 属性 | 内容 |
|------|------|
| **转换** | `null → PENDING_PAY` |
| **触发** | 买家提交订单 / 渠道订单接入（ADR-036） |
| **前置校验** | 商品可售、价格合法性、库存可用性、买家风控等级 |
| **业务执行** | 写入 order 表、预占库存、锁定优惠券、注册支付超时任务 |
| **事件** | `OrderCreatedEvent` |
| **补偿** | 取消订单 + 释放库存 + 释放优惠券 |
| **幂等** | 客户端 `Idempotency-Key` → 幂等校验 → 409 Conflict（重复提交） |

```java
@Service
public class OrderCreateService extends AbstractAtomicOrderService {

    @Override
    protected void validate(String orderId, TransitionContext context) {
        CreateOrderRequest req = (CreateOrderRequest) context.getExtras().get("request");
        // 商品可售性校验
        // 价格一致性校验（防止金额被篡改）
        // 库存预占校验（Redis Lua）
        // 买家风控检查
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.PENDING_PAY;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        CreateOrderRequest req = (CreateOrderRequest) context.getExtras().get("request");
        // 写入 order 表主记录
        // 预占库存（Redis Lua + DB 记录）
        // 锁定优惠券（coupon-service）
        // 注册 30min 支付超时任务（ADR-021）
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus, TransitionContext context) {
        eventPublisher.publish(OrderCreatedEvent.builder()
                .orderId(orderId)
                .buyerId(context.getOperatorId())
                .timestamp(Instant.now())
                .build());
    }

    @Override
    protected void doCompensate(String orderId, TransitionContext context) {
        // 释放库存
        // 释放优惠券
        // 取消超时任务
    }
}
```

#### 2.3 PaymentProcessService — 支付处理

| 属性 | 内容 |
|------|------|
| **转换** | `PENDING_PAY → PAID` |
| **触发** | 三方支付回调（支付宝/微信） |
| **前置校验** | 支付金额与订单一致、支付渠道合法性、幂等 |
| **守卫** | `PaymentAmountGuard` |
| **业务执行** | 写入支付记录、确认预占库存、发送发货提醒 |
| **事件** | `OrderPaidEvent` |
| **补偿** | 发起退款（非补偿机制，走标准售后流程） |

```java
@Service
public class PaymentProcessService extends AbstractAtomicOrderService {

    @Override
    protected void validate(String orderId, TransitionContext context) {
        PaymentCallback callback = (PaymentCallback) context.getExtras().get("callback");
        // 幂等校验（payment_transaction_id 唯一索引）
        // 金额一致性校验
        // 渠道可信校验（签名验证）
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.PAID;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        // 写入 payment_record 表
        // 确认预占库存（从预占到实际扣减）
        // 发送发货提醒（通知商家）
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus, TransitionContext context) {
        eventPublisher.publish(OrderPaidEvent.builder()
                .orderId(orderId)
                .paidAmount((BigDecimal) context.getExtras().get("paidAmount"))
                .payChannel((String) context.getExtras().get("payChannel"))
                .timestamp(Instant.now())
                .build());
    }
}
```

#### 2.4 OrderModifyService — 订单修改

| 属性 | 内容 |
|------|------|
| **转换** | 无状态变更（地址/备注修改）或特定业务需要（如商家改价需审核） |
| **触发** | 买家修改地址/备注、商家修改价格 |
| **前置校验** | 状态必须为 `PENDING_PAY` 或 `PAID`（已发货不可修改地址）；价格修改需风控审核 |
| **守卫** | `PendingPaymentOnlyGuard`（仅待支付可大幅修改） |
| **业务执行** | 更新 order 表对应字段、记录修改审计日志 |
| **事件** | `OrderModifiedEvent` |
| **补偿** | 回退修改字段（通过修改日志还原） |

```java
@Service
public class OrderModifyService extends AbstractAtomicOrderService {

    public OrderStatus modifyAddress(String orderId, ModifyAddressRequest req,
                                     TransitionContext context) {
        context.getExtras().put("modifyType", "ADDRESS");
        context.getExtras().put("request", req);
        // 地址修改不触发状态变更
        validate(orderId, context);
        doExecute(orderId, context);
        publishEvent(orderId, null, context);
        return orderRepository.findById(orderId).getStatus();
    }

    public OrderStatus modifyPrice(String orderId, ModifyPriceRequest req,
                                    TransitionContext context) {
        context.getExtras().put("modifyType", "PRICE");
        context.getExtras().put("request", req);
        // 修改价格需要状态机校验（PENDING_PAY → PENDING_PAY 是合法同态转换）
        return execute(orderId, context);
    }

    @Override
    protected void validate(String orderId, TransitionContext context) {
        String type = (String) context.getExtras().get("modifyType");
        Order order = orderRepository.findById(orderId);
        if ("ADDRESS".equals(type) && order.getStatus() == OrderStatus.SHIPPED) {
            throw new BusinessException("已发货订单不可修改地址");
        }
        if ("PRICE".equals(type)) {
            // 价格修改需要风控审核标记
            // 记录金额变更前后值到审计日志
        }
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        // 大多数修改不改变状态，保留原始状态
        return null; // 同态转换
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        // 更新相应字段
        // 记录审计日志（字段变更前后对比）
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus, TransitionContext context) {
        eventPublisher.publish(OrderModifiedEvent.builder()
                .orderId(orderId)
                .modifyType((String) context.getExtras().get("modifyType"))
                .timestamp(Instant.now())
                .build());
    }
}
```

#### 2.5 OrderSplitService — 订单拆分

| 属性 | 内容 |
|------|------|
| **触发** | 流程引擎（ADR-037）在 Saga Step 0 根据 SplitStrategy 决定 |
| **前置校验** | 拆分量不能超过子订单上限（默认 5 个子单） |
| **状态策略** | 父订单进入 `SPLITTING` 中间态（标记，不可见）；子订单各自独立状态机 |
| **业务执行** | 创建 N 个子订单（引用原订单行）、分配库存、调整价格分摊、生成物流包裹 |
| **事件** | `OrderSplitEvent` |
| **补偿** | 取消所有子订单 + 释放子订单占用的库存 |

```java
@Service
public class OrderSplitService extends AbstractAtomicOrderService {

    public List<String> splitOrder(String parentOrderId, SplitStrategy strategy,
                                    TransitionContext context) {
        // 1. 校验
        validate(parentOrderId, context);

        // 2. 执行策略生成拆分方案
        SplitPlan plan = strategy.resolve(parentOrderId);

        // 3. 创建子订单（每个子订单独立状态机）
        List<String> childOrderIds = createChildOrders(parentOrderId, plan);

        // 4. 父订单标记为已拆分（记录子订单 ID 列表）
        markParentAsSplit(parentOrderId, childOrderIds);

        // 5. 发布事件
        publishEvent(parentOrderId, null, context);

        return childOrderIds;
    }

    private List<String> createChildOrders(String parentOrderId, SplitPlan plan) {
        List<String> childIds = new ArrayList<>();
        for (SplitGroup group : plan.getGroups()) {
            String childOrderId = idGenerator.generate();
            // 创建子订单记录，parentOrderNo = parentOrderId
            // 子订单状态 = PENDING_PAY 或 PAID（继承父订单状态）
            childIds.add(childOrderId);
        }
        return childIds;
    }
}
```

#### 2.6 OrderMergeService — 订单合并

| 属性 | 内容 |
|------|------|
| **触发** | XXL-Job 定时调度（同一买家 + 同一店铺 + 待发货状态） |
| **前置校验** | 所有订单状态均为 `PAID`（或 `TO_SHIP`）、同一买家、同一店铺 |
| **状态策略** | 子订单标记为 `MERGED`（过渡态，不可见）；父订单接替状态机 |
| **业务执行** | 合并订单行到目标订单、关闭源订单、调整运费 |
| **事件** | `OrderMergedEvent` |
| **补偿** | 反向拆分源订单 |

#### 2.7 OrderCancelService — 订单取消

| 属性 | 内容 |
|------|------|
| **转换** | `PENDING_PAY → CANCELLED`（买家取消）<br>`PAID/TO_SHIP → CANCELLED`（商家/客服取消） |
| **触发** | 买家取消 / 商家取消 / 客服强制取消 |
| **前置校验** | `PENDING_PAY` 买家可直接取消；`PAID/TO_SHIP` 取消需审核或商家确认 |
| **守卫** | `CancelGuard`：已发货或已签收不可取消 |
| **业务执行** | 释放库存（如已扣减）、释放优惠券、发起退款（如已支付） |
| **事件** | `OrderCancelledEvent` |
| **补偿** | 不可补偿（取消是终态，如需恢复走新建订单流程） |

```java
@Service
public class OrderCancelService extends AbstractAtomicOrderService {

    @Override
    protected void validate(String orderId, TransitionContext context) {
        Order order = orderRepository.findById(orderId);
        // PENDING_PAY → 买家直接取消
        if (order.getStatus() == OrderStatus.PAID
                && "BUYER".equals(context.getOperatorType())) {
            // 支付后取消需要商家同意
            throw new BusinessException("已支付订单需联系客服取消");
        }
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.CANCELLED;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        // 释放库存
        // 释放优惠券
        // 如已支付，发起退款流程（走标准退款 Saga）
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus, TransitionContext context) {
        eventPublisher.publish(OrderCancelledEvent.builder()
                .orderId(orderId)
                .cancelReason(context.getReason())
                .timestamp(Instant.now())
                .build());
    }
}
```

#### 2.8 ConfirmReceiptService — 确认收货

| 属性 | 内容 |
|------|------|
| **转换** | `SHIPPED → DELIVERED` |
| **触发** | 买家确认收货 / 物流轨迹自动确认 / 超时自动确认（7d） |
| **前置校验** | 订单处于 `SHIPPED` 状态（守卫 `ShippedStatusGuard`） |
| **守卫** | 买家确认：可直接通过；系统自动确认：需物流已签收 OR 超过 7d |
| **业务执行** | 更新收货时间、触发结算流程 |
| **事件** | `OrderDeliveredEvent` |
| **补偿** | 不可补偿（如需纠错走售后退货流程） |

### 3. 异常处理机制

#### 3.1 状态超时矩阵

为每个非终态定义最大停留时间，由 Apollo `state.timeout-matrix` 配置驱动：

```yaml
# Apollo 配置：state.timeout-matrix
state:
  timeout-matrix:
    PENDING_PAY:
      max-duration: 30          # 单位：分钟
      on-timeout: AUTO_CLOSE    # 自动关闭 → CLOSED
      alert-level: P3           # 超时后告警级别
      alert-after: 25           # 超时前 5 分钟 P3 预警
    PAID:
      max-duration: 1440        # 24 小时
      on-timeout: ALERT_ONLY    # 仅告警，不自动操作
      alert-level: P2
      alert-after: 1380         # 超时前 1 小时 P2 预警
    TO_SHIP:
      max-duration: 4320        # 72 小时
      on-timeout: ALERT_ONLY
      alert-level: P2
    SHIPPED:
      max-duration: 10080       # 7 天
      on-timeout: AUTO_CONFIRM  # 自动确认收货 → DELIVERED
      alert-level: P3
    HOLD:
      max-duration: 2880        # 48 小时
      on-timeout: ALERT_ONLY
      alert-level: P2
    REFUNDING:
      max-duration: 4320        # 72 小时
      on-timeout: RECONCILE     # 触发退款状态对账
      alert-level: P1
    RETURNING:
      max-duration: 21600       # 15 天
      on-timeout: RECONCILE
      alert-level: P1
```

#### 3.2 StuckOrderDetector — 卡单检测器

XXL-Job 周期性扫描卡住订单，按超时矩阵配置触发告警或自动操作：

```java
@Component
public class StuckOrderDetector {

    /**
     * 默认每 5 分钟执行一次。
     * 扫描所有非终态订单，检查是否超过状态最大停留时间。
     */
    @XxlJob("stuckOrderDetectJob")
    public void detect() {
        // 1. 从 Apollo 加载超时矩阵
        Map<String, StateTimeoutConfig> matrix = loadTimeoutMatrix();

        // 2. 对每个非终态类型分别扫描
        for (Map.Entry<String, StateTimeoutConfig> entry : matrix.entrySet()) {
            OrderStatus status = OrderStatus.valueOf(entry.getKey());
            StateTimeoutConfig config = entry.getValue();

            List<Order> stuckOrders = orderRepository.findStuckOrders(
                    status, config.getMaxDuration());

            for (Order order : stuckOrders) {
                handleStuckOrder(order, config);
            }
        }
    }

    private void handleStuckOrder(Order order, StateTimeoutConfig config) {
        // 记录指标
        Metrics.counter("omplatform_order_stuck_total",
                "status", order.getStatus().name()).increment();

        switch (config.getOnTimeout()) {
            case AUTO_CLOSE:
                // 自动关闭订单
                stateMachineEngine.transition(order.getOrderNo(),
                        order.getStatus(), OrderStatus.CLOSED,
                        TransitionContext.systemContext("支付超时自动关闭"));
                break;
            case AUTO_CONFIRM:
                // 自动确认收货
                stateMachineEngine.transition(order.getOrderNo(),
                        order.getStatus(), OrderStatus.DELIVERED,
                        TransitionContext.systemContext("超时自动确认收货"));
                break;
            case RECONCILE:
                // 触发对账（适用于 REFUNDING/RETURNING）
                triggerReconciliation(order);
                break;
            case ALERT_ONLY:
                // 仅告警
                alertService.sendAlert(order.getOrderNo(), config.getAlertLevel(),
                        String.format("订单 %s 在 %s 状态停留超过 %d 分钟",
                                order.getOrderNo(), order.getStatus(),
                                config.getMaxDuration()));
                break;
        }
    }
}
```

#### 3.3 HOLD 状态生命周期

```
PAID 状态 → 库存不足 / 风控触发
    │
    ▼
HOLD ← 状态机转换（PENDING_PAY/PAID → HOLD）
    │
    ├──→ 库存补充完成 → HOLD → PENDING_PAY（重新支付）
    ├──→ 库存补充完成 → HOLD → PAID（已支付转来，恢复待发货）
    ├──→ 商家放弃 → HOLD → CANCELLED（取消订单）
    ├──→ 超时 48h → P2 告警，触发人工介入
    └──→ 人工冻结 → HOLD → FROZEN
```

```java
// 库存恢复后自动释放挂起作业（可被 XXL-Job 调用或被 MQ 库存变更事件触发）
@Component
public class HoldReleaseJob {

    @Autowired private StateMachineEngine stateMachineEngine;

    /**
     * 检查挂起订单的库存是否已补充，自动释放。
     */
    @XxlJob("holdReleaseJob")
    public void releaseHoldOrders() {
        List<Order> heldOrders = orderRepository.findByStatus(HOLD);

        for (Order order : heldOrders) {
            boolean inventoryAvailable = inventoryService.checkAvailability(
                    order.getOrderNo());

            if (inventoryAvailable) {
                // 根据挂起前的状态决定恢复到 PENDING_PAY 还是 PAID
                OrderStatus restoreTarget = order.getPreviousStatus() == PAID
                        ? PAID : PENDING_PAY;

                stateMachineEngine.transition(
                        order.getOrderNo(), HOLD, restoreTarget,
                        TransitionContext.systemContext("库存已补充，自动解除挂起"));
            }
        }
    }
}
```

#### 3.4 退款状态同步（一致性修正）

当前 refund-flow.puml 缺少 ADR-020 定义的 coupon release 步骤。在 ADR-039 中明确修正后的退款流程：

```
──────────────────────────────────────────────────────
退款处理 Saga（钉正后流程）：

Step 1: validateRefund(order-core)     → 只读校验
Step 2: restoreInventory(inventory)    → 回滚库存
Step 3: releaseCoupon(coupon-service)  → 释放优惠券  ← 此前缺失
Step 4: processRefund(payment)         → 调用三方退款（不可补偿）
Step 5: pushToFinance(finance)         → 推送结算数据
──────────────────────────────────────────────────────

状态机路径：
  PAID/TO_SHIP     → REFUNDING → REFUNDED    （仅退款）
  SHIPPED/DELIVERED → RETURNING → REFUNDED   （退货退款）
```

退款状态对账 Job：

```java
@Component
public class RefundReconciliationJob {

    /**
     * 每 30 分钟扫描卡在 REFUNDING/RETURNING 超过 72h/15d 的订单。
     * 调用支付网关查询退款状态，自动完成或告警。
     */
    @XxlJob("refundReconcileJob")
    public void reconcile() {
        List<Order> stuckRefunds = orderRepository.findStuckRefunds();

        for (Order order : stuckRefunds) {
            PaymentStatus paymentStatus = paymentClient.queryRefundStatus(
                    order.getPaymentTransactionId());

            if (paymentStatus == SUCCESS) {
                // 支付网关退款成功 → 自动推进到 REFUNDED
                stateMachineEngine.transition(
                        order.getOrderNo(), order.getStatus(), REFUNDED,
                        TransitionContext.systemContext("退款对账确认已完成"));
            } else if (paymentStatus == FAILED) {
                // 退款失败 → P1 告警，人工介入
                alertService.sendP1("退款失败",
                        "订单 " + order.getOrderNo() + " 退款调用支付网关失败");
            }
            // PENDING → 仍处理中，跳过
        }
    }
}
```

#### 3.5 人工干预 API

为客服/管理员提供操作卡单的标准化接口：

```java
@RestController
@RequestMapping("/api/admin/v1/orders")
public class OrderAdminController {

    /**
     * 人工冻结订单（拦截所有自动操作）。
     * POST /api/admin/v1/orders/{orderNo}/freeze
     */
    @PostMapping("/{orderNo}/freeze")
    public ApiResult<Void> freeze(@PathVariable String orderNo,
                                   @RequestBody FreezeRequest request) {
        // 校验操作人权限（ADMIN role only）
        // 调用状态机：currentStatus → FROZEN
        return ApiResult.success();
    }

    /**
     * 人工解冻订单。
     * POST /api/admin/v1/orders/{orderNo}/unfreeze
     */
    @PostMapping("/{orderNo}/unfreeze")
    public ApiResult<Void> unfreeze(@PathVariable String orderNo,
                                     @RequestBody UnfreezeRequest request) {
        // 还原到冻结前的状态
        return ApiResult.success();
    }

    /**
     * 强制状态转换（仅用于极端场景，需审批流程）。
     * POST /api/admin/v1/orders/{orderNo}/force-transition
     */
    @PostMapping("/{orderNo}/force-transition")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResult<Void> forceTransition(@PathVariable String orderNo,
                                            @RequestBody ForceTransitionRequest request) {
        // 跳过守卫条件，直接执行转换
        // 记录操作审计日志（必须保留原始状态）
        return ApiResult.success();
    }

    /**
     * 查询卡单列表。
     * GET /api/admin/v1/orders/stuck?status=TO_SHIP&maxDuration=4320
     */
    @GetMapping("/stuck")
    public ApiResult<PageResult<StuckOrderVO>> listStuckOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "4320") int maxDurationMinutes,
            PageParam page) {
        return ApiResult.success(stuckOrderDetector.queryStuckOrders(
                status, maxDurationMinutes, page));
    }
}
```

#### 3.6 降级策略

| 降级场景 | 策略 | 效果 |
|---------|------|------|
| 状态机引擎不可用 | 回退到无校验模式 | 允许直接 update status（风险模式，Apollo 开关） |
| 状态机响应超时 | 熔断 → 写降级日志 → 走 DB 直写 | 订单操作不受影响，但状态校验暂时关闭 |
| 乐观锁频繁冲突 | 调整重试策略（指数退避 3 次） | 高并发下避免频繁重试 |

```yaml
# Apollo 配置：state.engine
state:
  engine:
    enabled: true                              # 状态机总开关
    strict-mode: true                          # 严格模式（开启守卫）
    retry-on-conflict: true                    # 乐观锁冲突重试
    max-retries: 3                             # 最大重试次数
    circuit-breaker:
      threshold: 10                            # 10 次超时后熔断
      half-open-after: 30_000                  # 30s 后半开
    fallback: simple-status-update             # 降级方案
```

### 4. 数据模型

#### order 表新增字段

```sql
-- order 表新增生命周期管理字段
ALTER TABLE `order`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN `previous_status` VARCHAR(20) COMMENT '前一状态（状态追溯用）',
    ADD COLUMN `status_changed_at` DATETIME COMMENT '上次状态变更时间',
    ADD COLUMN `status_expires_at` DATETIME COMMENT '当前状态的超时时间',
    ADD COLUMN `hold_reason` VARCHAR(255) COMMENT '挂起原因（HOLD 态）',
    ADD COLUMN `frozen_reason` VARCHAR(255) COMMENT '冻结原因（FROZEN 态）',
    ADD INDEX `idx_status_changed_at` (`status`, `status_changed_at`);
```

#### state_transition_log 表（状态变更审计）

```sql
CREATE TABLE `state_transition_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `from_status` VARCHAR(20) NOT NULL COMMENT '原状态',
    `to_status` VARCHAR(20) NOT NULL COMMENT '目标状态',
    `operator_id` VARCHAR(64) COMMENT '操作人',
    `operator_type` VARCHAR(20) COMMENT '操作人类型：BUYER/SELLER/ADMIN/SYSTEM',
    `source` VARCHAR(32) COMMENT '触发来源：API/JOB/CALLBACK/MQ',
    `reason` VARCHAR(255) COMMENT '转换原因',
    `guard_result` VARCHAR(1024) COMMENT '守卫条件评估结果（JSON）',
    `duration_ms` INT COMMENT '转换耗时（毫秒）',
    `success` TINYINT(1) NOT NULL COMMENT '是否成功',
    `error_code` VARCHAR(32) COMMENT '失败错误码',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (`id`),
    INDEX `idx_order_no` (`order_no`),
    INDEX `idx_from_to` (`from_status`, `to_status`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='状态转换审计日志';

-- 按月 RANGE 分区，保留 90 天
ALTER TABLE `state_transition_log` PARTITION BY RANGE (TO_DAYS(`created_at`)) (
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### 5. 与流程引擎（ADR-037）的集成

```
流程引擎（ADR-037）                   状态机引擎（本文）
─────────────────                    ────────────────
YAML 模板定义步骤                      定义状态和转换矩阵
步骤执行器调用原子服务                   拦截非法转换
         │                                     │
         └─────────── 调用 ────────────────────┘
               stateMachine.transition(current, target)
```

```yaml
# 流程模板中引用状态机（ADR-037 YAML 模板增强）
process:
  template: "physical_order"
  steps:
    - name: "create_order"
      service: "orderCreateService"
      transition: "INIT → PENDING_PAY"     # 显式声明状态转换
    - name: "request_payment"
      service: "paymentRequester"
      transition: null                      # 无状态变换
    - name: "confirm_payment"
      service: "paymentProcessService"
      transition: "PENDING_PAY → PAID"     # 显式声明
```

### 6. 与 Saga（ADR-020）的集成

```java
// 在 Saga 步骤中使用状态机引擎做转换校验
@Component
public class ConfirmPaymentSagaStep implements SagaStep {

    @Autowired
    private StateMachineEngine stateMachineEngine;

    @Override
    public SagaStepResult execute(SagaContext ctx) {
        // 执行状态转换（通过状态机引擎校验合法性）
        stateMachineEngine.transition(
                ctx.getOrderId(),
                OrderStatus.PENDING_PAY,
                OrderStatus.PAID,
                TransitionContext.systemContext("支付确认"));
        // ... 后续业务逻辑
        return SagaStepResult.success();
    }

    @Override
    public SagaStepResult compensate(SagaContext ctx) {
        // Saga 补偿时：PAID → PENDING_PAY 在正常逻辑中是非法的
        // 但在 Saga 补偿上下文中明确跳过守卫
        stateMachineEngine.forceTransition(
                ctx.getOrderId(),
                OrderStatus.PAID,
                OrderStatus.PENDING_PAY,
                TransitionContext.systemContext("Saga 补偿"));
        return SagaStepResult.success();
    }
}
```

### 7. Apollo 配置

#### Namespace: `state.timeout-matrix`

```yaml
# 状态超时矩阵配置（见 3.1 节完整 YAML）
state:
  timeout-matrix:
    PENDING_PAY:
      max-duration: 30
      on-timeout: AUTO_CLOSE
      alert-level: P3
    # ... 其余状态
```

#### Namespace: `state.engine`

```yaml
# 状态机引擎配置（见 3.6 节完整 YAML）
state:
  engine:
    enabled: true
    strict-mode: true
    # ...
```

#### Namespace: `state.guards`

```yaml
# 守卫条件开关（按转换维度控制）
state:
  guards:
    PENDING_PAY_to_PAID:
      payment-amount-check: true
    PAID_to_CANCELLED:
      require-admin-approval: false
```

### 8. 可观测性

#### Prometheus 指标

```java
// 订单生命周期指标
public class OrderLifecycleMetrics {

    // 状态转换计数器（带 from/to 标签）
    static final Counter stateTransitionTotal = Counter.build()
            .name("omplatform_order_state_transition_total")
            .labelNames("from_status", "to_status", "source", "success")
            .help("订单状态转换次数")
            .register();

    // 状态停留时间分布
    static final Histogram stateDwellDuration = Histogram.build()
            .name("omplatform_order_state_dwell_duration_minutes")
            .labelNames("status")
            .buckets(1, 5, 15, 30, 60, 180, 360, 720, 1440, 4320, 10080)
            .help("订单在各状态的停留时间分布")
            .register();

    // 卡单计数器
    static final Counter stuckOrderTotal = Counter.build()
            .name("omplatform_order_stuck_total")
            .labelNames("status")
            .help("超时未转换的卡单数量")
            .register();

    // 乐观锁冲突计数器
    static final Counter optimisticLockConflictTotal = Counter.build()
            .name("omplatform_order_optimistic_lock_conflict_total")
            .labelNames("from_status", "to_status")
            .help("状态转换乐观锁冲突次数")
            .register();

    // 冻结订单数（当前值）
    static final Gauge frozenOrderCount = Gauge.build()
            .name("omplatform_order_frozen_current")
            .help("当前冻结状态的订单数")
            .register();

    // HOLD 订单数（当前值）
    static final Gauge holdOrderCount = Gauge.build()
            .name("omplatform_order_hold_current")
            .help("当前挂起状态的订单数")
            .register();
}
```

#### Grafana 看板

**看板：订单生命周期健康**

```
┌─────────────────────────────────────────────────────────────┐
│  订单生命周期健康总览                                        │
├──────────────────────┬──────────────────────────────────────┤
│  状态转换量/分钟      │  卡单数量按状态分布                    │
│  (折线图，按 from→to  │  (柱状图，PENDING_PAY/TO_SHIP/...)   │
│   着色)              │                                      │
├──────────────────────┼──────────────────────────────────────┤
│  状态停留时间分布      │  当前各状态订单数                    │
│  (热力图)            │  (饼图，终态vs非终态比例)              │
├──────────────────────┴──────────────────────────────────────┤
│  异常事件列表                                                │
│  时间 | 订单号 | from→to | 错误码 | 耗时                      │
├─────────────────────────────────────────────────────────────┤
│  乐观锁冲突趋势 (折线图)  |  HOLD/冻结趋势 (折线图)          │
└─────────────────────────────────────────────────────────────┘
```

#### 告警规则

```yaml
groups:
  - name: order-lifecycle
    rules:
      - alert: OrderStuckInState
        expr: omplatform_order_stuck_total{status=~"PAID|TO_SHIP"} > 0
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "订单在 {{ $labels.status }} 状态卡住超过超时时间"

      - alert: OrderHoldTooLong
        expr: omplatform_order_stuck_total{status="HOLD"} > 0
        for: 5m
        labels: { severity: P2 }
        annotations:
          summary: "挂起订单超过 48h 未处理"

      - alert: OrderRefundStuck
        expr: omplatform_order_stuck_total{status=~"REFUNDING|RETURNING"} > 0
        for: 5m
        labels: { severity: P1 }
        annotations:
          summary: "退款/退货订单卡住超过 SLA 时间"

      - alert: StateMachineOptimisticLockHigh
        expr: rate(omplatform_order_optimistic_lock_conflict_total[5m]) > 10
        labels: { severity: P3 }
        annotations:
          summary: "状态机乐观锁冲突率过高（>10/min）"
```

---

## 实施计划

| 阶段 | 内容 | 工时 | 交付物 |
|------|------|------|--------|
| **Phase 1：状态机引擎** | 13 态定义 + 转换矩阵 + 引擎接口 + Guard/Action 框架 + 乐观锁 + 状态转换日志表 | 4d | ADR-039 Part A |
| **Phase 2：原子服务** | 7 个原子服务接口 + 实现 + 守卫 + 事件发布 + 补偿（其中拆分/合单复用 ADR-037 策略） | 3d | ADR-039 Part B |
| **Phase 3：异常处理** | 超时矩阵配置 + HOLD 生命周期 + 卡单检测器 + 退款对账 + 人工干预 API + 降级策略 | 2.5d | ADR-039 Part C |
| **Phase 4：可观测性** | Prometheus 指标 + Grafana 看板 + 告警规则 + Apollo 命名空间 | 1d | 度量配置 |
| **Phase 5：文档与集成** | 4 个 PlantUML 时序图 + 更新 4 个现有文档 + ADR 交叉引用 | 2d | 全部修改 |
| **总计** | | **12.5d** | |

---

## 上线检查清单

### 基础设施
- [ ] order 表新增 `version`/`previous_status`/`status_changed_at`/`status_expires_at` 字段
- [ ] 创建 `state_transition_log` 表及按月分区
- [ ] Apollo 创建 `state.timeout-matrix`、`state.engine`、`state.guards` 命名空间

### 状态机引擎
- [ ] `OrderStatus` 枚举定义 13 态
- [ ] `OrderStateTransitionMatrix` 实现 N×N 转换表
- [ ] `StateMachineEngine` 接口及 `OrderStateMachineEngine` 实现
- [ ] 守卫条件注册（支付金额校验、库存校验、状态守卫等）
- [ ] 入口/出口动作注册（超时任务注册/取消、事件发布）
- [ ] 乐观锁 CAS 更新测试覆盖所有转换路径

### 原子服务
- [ ] 6 个 `AbstractAtomicOrderService` 子类（创建/支付/修改/拆分/取消/确认）
- [ ] 每个服务的前置校验、事件发布、补偿逻辑
- [ ] 拆单子订单独立状态机验证
- [ ] `OrderMergeService` XXL-Job 定时调度

### 异常处理
- [ ] `StuckOrderDetector` XXL-Job 每隔 5 分钟扫描卡单
- [ ] `HoldReleaseJob` 库存补充后自动释放
- [ ] `RefundReconciliationJob` 退款状态对账
- [ ] 人工干预 API（冻结/解冻/强制转换/卡单查询）
- [ ] 降级开关（Apollo `state.engine.enabled` = false 时跳过校验）

### 可观测性
- [ ] 4 个 Prometheus 指标注册（转换量/停留时间/卡单数/乐观锁冲突）
- [ ] Grafana 看板配置
- [ ] 告警规则：卡单 P2、挂起超时 P2、退款卡住 P1、乐观锁冲突 P3

### ADR 交叉引用
- [ ] ADR-037 流程模板声明状态转换
- [ ] ADR-020 Saga step 通过状态机执行转换
- [ ] ADR-021 延迟任务作为状态超时机制
- [ ] ADR-030 原子服务幂等
- [ ] ADR-010/038 状态转换事件发布
- [ ] ADR-027 可观测性集成

---

## 与现有 ADR 的关联

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-037** | 上层调用 | 流程模板显式声明状态转换，状态机引擎校验合法性 |
| **ADR-020** | 集成调用 | Saga 步骤通过状态机引擎执行转换，补偿时可跳过守卫 |
| **ADR-021** | 延迟任务 | 支付超时关单、自动确认收货使用延迟任务框架 |
| **ADR-030** | 幂等 | 原子服务继承 `Idempotency-Key` 校验 |
| **ADR-010** | 事件 Schema | 状态转换事件遵循 Schema 治理规范 |
| **ADR-038** | 事件发布 | 状态变更事件通过 EventPublisher 广播 |
| **ADR-027** | 可观测性 | 状态机指标纳入 Prometheus/Grafana 体系 |
| **ADR-036** | 渠道接入 | 渠道订单创建也通过 OrderCreateService 原子服务 |

## 备选方案评估

| 备选方案 | 优点 | 缺点 | 结论 |
|---------|------|------|------|
| **Spring Statemachine** | 成熟框架、DSL 配置、内建监听器 | 13 态过度设计、序列化复杂、团队学习成本 | ❌ 不选 |
| **状态嵌入 Saga** | 无需新组件 | 状态与编排耦合、无法独立校验、终态不可逆难表达 | ❌ 不选 |
| **状态机即服务** | 独立状态机微服务 | 调用链增加延迟、分布式一致性问题 | ❌ 不选 |
| **自定义轻量引擎（选中）** | 轻量、类型安全、零依赖、易集成 | 无可视化界面、手动维护转换表 | ✅ **选中** |
