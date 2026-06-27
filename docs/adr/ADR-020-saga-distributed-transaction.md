# ADR-020：Saga 分布式事务治理

## 状态

已接受

---

## 背景

### 现状分析

当前订单中台的跨服务事务采用**事件驱动（Choreography）模式**。一个业务流程涉及多个服务时，通过 RocketMQ 领域事件串联各环节，每个服务独立执行本地事务并发布事件触发下一步。

```
当前下单流程（Choreography 模式）：

  买家提交订单
        │
        ▼
  ┌──────────────┐
  │ order-core   │───→ 发布 OrderCreatedEvent
  │ 创建订单      │
  │ 状态: PENDING │
  └──────────────┘
        │
        ▼ (异步消费 OrderCreatedEvent)
  ┌──────────────┐
  │ inventory    │───→ 发布 InventoryDeductedEvent
  │ 扣减库存      │
  └──────────────┘
        │
        ▼ (异步消费 InventoryDeductedEvent)
  ┌──────────────┐
  │ payment      │───→ 调用三方支付
  │ 发起支付      │
  └──────────────┘
        │
        ▼ (异步消费 PaymentSuccessEvent)
  ┌──────────────┐
  │ order-core   │
  │ 更新为 PAID  │
  └──────────────┘
```

这套模式在正常流程下工作良好——服务间解耦、每个服务独立扩缩容。但在**异常场景**下暴露出结构性问题：

### 存在的问题

**问题 1：补偿（Compensation）没有形式化保障**  
当支付失败时，需要触发「库存回滚」。当前依赖 `PaymentFailedEvent` 的消费者去调用 inventory 的 `undoDeduct` 接口。但如果：

- 消费者消息堆积或消费失败 → 库存补偿不执行
- 补偿接口调用超时 → 不确定是否执行成功
- 补偿过程中进程崩溃 → 补偿丢失

**没有任何机制确保补偿一定执行**。库存可能被"永久"扣减。

**问题 2：缺少 Saga 全局状态**  
每个服务只知道自己的状态，不知道整个事务的全局进展。当出现异常时：

- 无法回答"这个订单的 Saga 执行到哪一步了"
- 无法区分"正在重试"和"永远卡住了"
- 运维需要人工翻日志拼接全局视图

**问题 3：补偿幂等依赖 Code Review**  
`undoDeduct` 接口是否幂等、`cancelOrder` 是否可重复调用，全靠代码评审保障。没有统一的幂等令牌（Idempotency Key）机制。

**问题 4：超时处理不一致**  
每个服务有自己的超时配置。下游 payment 超时后，上游 order-core 不知道应该等待多久。可能出现"支付超时重试中，但订单已经被取消"的冲突状态。

**问题 5：无恢复机制**  
如果某个服务在 Saga 执行过程中永久故障（如 inventory 宕机超过 30 分钟），没有自动化的恢复流程。运维需要手动执行 SQL 修复——容易出错且不可审计。

### 当前数据

| 场景 | 当前可靠性 | 风险 |
|------|-----------|------|
| 下单 → 扣库存 → 支付 → 确认 | 高（正常流程） | 支付失败时库存补偿可能丢失 |
| 退款 → 退库存 → 退钱 → 完成 | 中 | 退款成功但库存回滚失败 |
| 发货 → 修改库存 → 更新物流 | 中低 | 库存扣减和物流状态可能不一致 |
| 取消订单 → 释放库存 → 释放优惠券 | 低 | 优惠券释放可能遗漏 |

---

## 决策

引入 **Saga 编排器（Orchestrator）**，将核心业务流程（下单、退款、退货）从 Choreography 模式改造为 Orchestration 模式：

1. **SagaExecutor**：每个核心流程对应一个 Saga 定义（步骤列表 + 补偿列表），由编排器统一调度
2. **SagaLog**：每一步的执行结果持久化到 DB，支持崩溃恢复和人工干预
3. **补偿注册**：每个正向步骤必须有对应的补偿步骤，补偿执行有重试 + 死信兜底
4. **全局超时**：Saga 级别超时 + 步骤级别超时，超时自动触发补偿
5. **幂等令牌**：所有参与接口通过 `saga_id + step_id` 实现通用幂等

改造范围限定在**核心资金/库存链路**（下单、支付、退款、退货），不影响纯查询等非事务路径。

```
改造后：Orchestration 模式

         ┌─────────────────────────────────────────────┐
         │              Saga Orchestrator                │
         │                                              │
         │  createOrderSaga:                             │
         │  ┌─ 1. order-core.createOrder()               │
         │  │   compensate: cancelOrder()                │
         │  ├─ 2. inventory.deductStock()                │
         │  │   compensate: undoDeduct()                 │
         │  ├─ 3. payment.charge()                       │
         │  │   compensate: refund()                     │
         │  └─ 4. order-core.confirmOrder()              │
         │      (无补偿——此时事务已完成)                    │
         └─────────────────────────────────────────────┘
                    │    │    │
                    ▼    ▼    ▼
              order-core  inventory  payment
```

---

## 详细设计

### 1. 整体架构

```
Saga 编排器架构：

                      业务请求（下单/退款/退货…）
                             │
                             ▼
                     ┌───────────────┐
                     │ SagaController│
                     │ ① 解析请求     │
                     │ ② 选择 Saga   │
                     │ ③ 创建 SagaId │
                     └───────┬───────┘
                             │
                     ┌───────▼───────┐
                     │ SagaExecutor  │ ← 核心编排器
                     │               │
                     │ 正向执行:      │
                     │  step1 → step2→ step3 → ...
                     │               │
                     │ 异常时:        │
                     │  step3 ↓ → compensate2 → compensate1
                     │               │
                     └───────┬───────┘
                             │
              ┌──────────────┼──────────────────┐
              │              │                  │
              ▼              ▼                  ▼
       ┌──────────┐  ┌──────────┐  ┌────────────────┐
       │ SagaLog  │  │ Saga     │  │ MQ 死信队列     │
       │ (DB 持久) │  │ Recovery │  │ (人工兜底)      │
       └──────────┘  │ Job      │  └────────────────┘
                     └──────────┘
```

### 2. Saga 定义

```java
/**
 * Saga 定义 —— 一个业务流程编排为一个 Saga
 */
public class SagaDefinition {

    private final String sagaName;              // 唯一标识
    private final List<SagaStep> steps;         // 正向步骤（有序）
    private final Duration globalTimeout;       // Saga 整体超时
    private final RetryPolicy retryPolicy;      // 重试策略
}

/**
 * Saga 步骤 —— 一个步骤 = 正向操作 + 补偿操作
 */
@Data
@Builder
public class SagaStep {
    private String stepName;                    // 步骤名称
    private int order;                          // 执行顺序
    
    // 正向操作
    private String forwardService;              // Dubbo 服务名
    private String forwardMethod;               // 方法名
    private Duration stepTimeout;               // 本步骤超时
    
    // 补偿操作
    private String compensateService;           // 补偿服务名
    private String compensateMethod;            // 补偿方法名
    private boolean mandatory;                  // 补偿是否必须执行（默认为 true）
    
    // 补偿重试策略（默认与全局策略一致）
    private RetryPolicy compensateRetry;
}
```

### 3. Saga 执行器

```java
/**
 * Saga 编排器 —— 核心执行引擎
 */
@Component
public class SagaExecutor {

    private final SagaLogRepository sagaLogRepo;
    private final DubboInvoker dubboInvoker;
    private final IdempotentChecker idempotentChecker;

    /**
     * 执行 Saga
     */
    public SagaResult execute(SagaDefinition saga, SagaContext context) {
        String sagaId = context.getSagaId();
        sagaLogRepo.create(sagaId, saga.getSagaName(), SagaStatus.INITIATED);

        for (SagaStep step : saga.getSteps()) {
            // 1. 检查幂等 —— 如果已执行过则跳过
            StepRecord existing = sagaLogRepo.findStep(sagaId, step.getStepName());
            if (existing != null && existing.getStatus() == StepStatus.SUCCEEDED) {
                continue;  // 已成功，跳过
            }
            if (existing != null && existing.getStatus() == StepStatus.FAILED) {
                throw new SagaException("步骤已失败且未补偿: " + step.getStepName());
            }

            // 2. 执行正向步骤（带超时 + 重试）
            sagaLogRepo.upsertStep(sagaId, step.getStepName(), StepStatus.EXECUTING);
            try {
                Object result = dubboInvoker.invoke(
                    step.getForwardService(),
                    step.getForwardMethod(),
                    context.getArgs(step.getStepName()),
                    step.getStepTimeout()
                );
                sagaLogRepo.upsertStep(sagaId, step.getStepName(), StepStatus.SUCCEEDED);
                context.setResult(step.getStepName(), result);
            } catch (Exception e) {
                sagaLogRepo.upsertStep(sagaId, step.getStepName(), StepStatus.FAILED, e);
                // 3. 失败 → 执行补偿
                compensate(saga, context, step.getOrder());
                return SagaResult.failed(sagaId, step.getStepName(), e.getMessage());
            }
        }

        // 所有步骤成功
        sagaLogRepo.updateStatus(sagaId, SagaStatus.COMPLETED);
        return SagaResult.success(sagaId);
    }

    /**
     * 补偿 —— 逆序执行失败步骤之前的所有补偿
     */
    private void compensate(SagaDefinition saga, SagaContext context, int failedAtOrder) {
        sagaLogRepo.updateStatus(context.getSagaId(), SagaStatus.COMPENSATING);

        // 逆序执行（从失败的前一步开始倒序）
        for (int i = failedAtOrder - 1; i >= 0; i--) {
            SagaStep step = saga.getSteps().get(i);
            if (!step.isMandatory()) {
                log.info("跳过非必须补偿: {}", step.getStepName());
                continue;
            }

            StepRecord sr = sagaLogRepo.findStep(context.getSagaId(), step.getStepName());
            if (sr == null || sr.getStatus() != StepStatus.SUCCEEDED) {
                continue;  // 未执行成功的步骤不需要补偿
            }

            try {
                // 带重试执行补偿（默认 3 次，间隔 5s/10s/30s）
                retryWithBackoff(() -> dubboInvoker.invoke(
                    step.getCompensateService(),
                    step.getCompensateMethod(),
                    context.getCompensateArgs(step.getStepName()),
                    step.getStepTimeout()
                ), step.getCompensateRetry());

                sagaLogRepo.upsertCompensateStep(
                    context.getSagaId(), step.getStepName(), StepStatus.COMPENSATED);
            } catch (Exception e) {
                // 补偿执行失败 → 记录 + 投递死信队列
                sagaLogRepo.upsertCompensateStep(
                    context.getSagaId(), step.getStepName(), StepStatus.COMPENSATE_FAILED, e);
                dlqProducer.send("SAGA_COMPENSATE_DLQ", CompensateDeadLetter.builder()
                    .sagaId(context.getSagaId())
                    .stepName(step.getStepName())
                    .retryCount(step.getCompensateRetry().getMaxRetries())
                    .errorMessage(e.getMessage())
                    .build());
            }
        }

        // 检查是否所有补偿都成功了
        boolean allCompensated = saga.getSteps().stream()
            .filter(SagaStep::isMandatory)
            .allMatch(s -> {
                StepRecord rec = sagaLogRepo.findCompensateStep(context.getSagaId(), s.getStepName());
                return rec != null && rec.getStatus() == StepStatus.COMPENSATED;
            });

        sagaLogRepo.updateStatus(context.getSagaId(),
            allCompensated ? SagaStatus.COMPENSATED : SagaStatus.COMPENSATE_FAILED);
    }
}
```

### 4. Saga 定义示例

#### 4.1 下单 Saga

```java
/**
 * 下单 Saga —— 定义正向步骤和补偿
 */
@Configuration
public class CreateOrderSagaDefinition {

    @Bean
    public SagaDefinition createOrderSaga() {
        return SagaDefinition.builder()
            .sagaName("createOrder")
            .globalTimeout(Duration.ofMinutes(5))
            .retryPolicy(RetryPolicy.builder()
                .maxRetries(3)
                .backoffIntervals(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30))
                .build())
            .steps(Arrays.asList(
                SagaStep.builder()
                    .stepName("createOrder")
                    .order(1)
                    .forwardService("order-core")
                    .forwardMethod("createOrder")
                    .stepTimeout(Duration.ofSeconds(10))
                    .compensateService("order-core")
                    .compensateMethod("cancelOrder")
                    .mandatory(true)
                    .build(),
                SagaStep.builder()
                    .stepName("deductInventory")
                    .order(2)
                    .forwardService("inventory")
                    .forwardMethod("deduct")
                    .stepTimeout(Duration.ofSeconds(5))
                    .compensateService("inventory")
                    .compensateMethod("undoDeduct")
                    .mandatory(true)
                    .compensateRetry(RetryPolicy.builder()
                        .maxRetries(5)  // 库存补偿最多重试 5 次
                        .backoffIntervals(Duration.ofSeconds(1), Duration.ofSeconds(2),
                            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30))
                        .build())
                    .build(),
                SagaStep.builder()
                    .stepName("chargePayment")
                    .order(3)
                    .forwardService("payment")
                    .forwardMethod("charge")
                    .stepTimeout(Duration.ofSeconds(15))  // 支付超时长一些（含三方调用）
                    .compensateService("payment")
                    .compensateMethod("refund")
                    .mandatory(true)
                    .build(),
                SagaStep.builder()
                    .stepName("confirmOrder")
                    .order(4)
                    .forwardService("order-core")
                    .forwardMethod("confirmPaid")
                    .stepTimeout(Duration.ofSeconds(5))
                    .compensateService("order-core")
                    .compensateMethod("cancelPaid")
                    .mandatory(false)  // 如果用户已看到"支付成功"但最后一步失败，不强补偿
                    .build()
            ))
            .build();
    }
}
```

```
下单 Saga 执行流程：

时间线 ──────────────────────────────────────────────────→

正向执行:
  ① createOrder() ──→ ② deductInventory() ──→ ③ chargePayment() ──→ ④ confirmOrder()
       ✅                 ✅                       ✅                    ✅
                                                                            Saga COMPLETED

异常场景 A：支付超时
  ① createOrder() ──→ ② deductInventory() ──→ ③ chargePayment() ──→ ❌ Timeout
       ✅                 ✅                       ❌
                                                    ↓
  补偿（逆序执行）:
                                                    ② undoDeduct()
                                                        ✅
                                                    ① cancelOrder()
                                                        ✅
                                                    Saga COMPENSATED

异常场景 B：扣库存失败
  ① createOrder() ──→ ② deductInventory() ──→ ❌
       ✅                 ❌
                            ↓
  补偿:
                          ① cancelOrder()
                              ✅
                          Saga COMPENSATED
```

#### 4.2 退款 Saga

```java
@Configuration
public class RefundOrderSagaDefinition {

    @Bean
    public SagaDefinition refundOrderSaga() {
        return SagaDefinition.builder()
            .sagaName("refundOrder")
            .globalTimeout(Duration.ofMinutes(30))
            .retryPolicy(RetryPolicy.builder()
                .maxRetries(3)
                .backoffIntervals(Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofSeconds(60))
                .build())
            .steps(Arrays.asList(
                SagaStep.builder()
                    .stepName("validateRefund")
                    .order(1)
                    .forwardService("order-core")
                    .forwardMethod("validateRefundRequest")
                    .stepTimeout(Duration.ofSeconds(10))
                    .compensateService(null)  // 只读校验，无补偿
                    .compensateMethod(null)
                    .mandatory(false)
                    .build(),
                SagaStep.builder()
                    .stepName("restoreInventory")
                    .order(2)
                    .forwardService("inventory")
                    .forwardMethod("restoreStock")
                    .stepTimeout(Duration.ofSeconds(5))
                    .compensateService("inventory")
                    .compensateMethod("deduct")  // 补偿 = 再次扣减（如果退款取消）
                    .mandatory(true)
                    .build(),
                SagaStep.builder()
                    .stepName("releaseCoupon")
                    .order(3)
                    .forwardService("coupon-center")
                    .forwardMethod("releaseCoupon")
                    .stepTimeout(Duration.ofSeconds(5))
                    .compensateService("coupon-center")
                    .compensateMethod("reissueCoupon")
                    .mandatory(true)
                    .build(),
                SagaStep.builder()
                    .stepName("processRefund")
                    .order(4)
                    .forwardService("payment")
                    .forwardMethod("refund")
                    .stepTimeout(Duration.ofSeconds(30))  // 三方退款可能较慢
                    .compensateService(null)  // 钱已退，无法补偿（需人工介入）
                    .compensateMethod(null)
                    .mandatory(false)  // 不可补偿：标记为最终步骤
                    .build()
            ))
            .build();
    }
}
```

### 5. Saga 日志持久化

```sql
-- saga_instance: Saga 实例表
CREATE TABLE `saga_instance` (
    `saga_id`         VARCHAR(64)    NOT NULL COMMENT '全局唯一 Saga ID',
    `saga_name`       VARCHAR(64)    NOT NULL COMMENT 'Saga 定义名',
    `status`          VARCHAR(24)    NOT NULL DEFAULT 'INITIATED' COMMENT '状态',
    `business_key`    VARCHAR(128)   DEFAULT NULL COMMENT '业务主键（如 order_id）',
    `context`         JSON           NOT NULL COMMENT 'Saga 上下文（参数）',
    `initiator`       VARCHAR(64)    NOT NULL COMMENT '发起人/服务',
    `started_at`      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completed_at`    DATETIME(3)    DEFAULT NULL COMMENT '完成/补偿完成时间',
    `version`         INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (`saga_id`),
    KEY `idx_business_key` (`business_key`),
    KEY `idx_status` (`status`),
    KEY `idx_started_at` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga 实例表';

-- saga_step_log: 步骤执行日志表
CREATE TABLE `saga_step_log` (
    `id`                BIGINT         NOT NULL AUTO_INCREMENT,
    `saga_id`           VARCHAR(64)    NOT NULL,
    `step_name`         VARCHAR(64)    NOT NULL COMMENT '步骤名',
    `step_order`        INT            NOT NULL COMMENT '执行顺序',
    `status`            VARCHAR(24)    NOT NULL DEFAULT 'PENDING' COMMENT '执行状态',
    `compensate_status` VARCHAR(24)    DEFAULT NULL COMMENT '补偿状态',
    `request`           JSON           DEFAULT NULL COMMENT '请求参数',
    `response`          JSON           DEFAULT NULL COMMENT '响应结果',
    `error_message`     TEXT           DEFAULT NULL COMMENT '错误信息',
    `retry_count`       INT            DEFAULT 0 COMMENT '重试次数',
    `started_at`        DATETIME(3)    DEFAULT NULL,
    `completed_at`      DATETIME(3)    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_saga_step` (`saga_id`, `step_name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Saga 步骤执行日志';
```

Saga 状态枚举：

```
SagaInstance 状态机：

        ┌────────────┐
        │  INITIATED  │ ← 创建 Saga 实例
        └─────┬──────┘
              │
      ┌───────┴───────┐
      │               │
      ▼               ▼
┌──────────┐   ┌──────────┐
│COMPLETED │   │COMPENSATI│ ← 某一步失败，开始执行补偿
│          │   │NG        │
└──────────┘   └────┬─────┘
                    │
           ┌────────┼────────┐
           │        │        │
           ▼        ▼        ▼
     ┌────────┐ ┌────────┐ ┌──────────┐
     │COMPENS │ │COMPENS │ │PARTIAL_CO│
     │ATED    │ │ATE_FAIL│ │MPENSATED │
     └────────┘ │ED      │ │(需人工)   │
                └────────┘ └──────────┘
```

### 6. 幂等机制

```java
/**
 * 通用幂等检查器 —— 所有参与 Saga 的服务共用
 * 
 * 原理：saga_id + step_name 构成全局唯一键
 * - 第一次执行：INSERT 成功 → 执行业务逻辑
 * - 重复执行：INSERT 报错（唯一键冲突）→ 返回上次结果
 */
@Component
public class IdempotentChecker {

    private final IdempotentRecordRepository repo;

    /**
     * 尝试获取执行权限
     * @return true = 可以执行（首次）, false = 已执行过, 返回上次结果
     */
    public boolean tryAcquire(String sagaId, String stepName) {
        try {
            repo.insert(IdempotentRecord.builder()
                .idempotentKey(sagaId + ":" + stepName)
                .sagaId(sagaId)
                .stepName(stepName)
                .status(IdempotentStatus.EXECUTING)
                .expireAt(LocalDateTime.now().plusDays(30))
                .build());
            return true;  // 首次执行
        } catch (DuplicateKeyException e) {
            return false; // 已执行过
        }
    }

    /**
     * 记录执行结果
     */
    public void complete(String sagaId, String stepName, Object result) {
        repo.updateStatus(sagaId + ":" + stepName, IdempotentStatus.SUCCEEDED, result);
    }

    /**
     * 获取上次结果
     */
    public Object getPreviousResult(String sagaId, String stepName) {
        return repo.findResult(sagaId + ":" + stepName);
    }
}

/**
 * 幂等记录表
 * 注意：使用唯一索引保证同一 saga+step 只能执行一次
 */
-- idempotent_record 幂等记录表
-- TODO: ADR-030 全局幂等框架落地后，此表及 IdempotentChecker 机制将统一迁移至 ADR-030 框架。
-- 迁移过渡期两套并行，优先使用 ADR-030 的 Idempotency-Key + Redis SET NX 路径。
-- Idempotency-Key 格式为 UUID v4（由客户端/编排器生成），saga_id 映射为 Idempotency-Key 组。
CREATE TABLE `idempotent_record` (
    `idempotent_key`  VARCHAR(128)   NOT NULL COMMENT 'saga_id:step_name',
    `saga_id`         VARCHAR(64)    NOT NULL,
    `step_name`       VARCHAR(64)    NOT NULL,
    `status`          VARCHAR(24)    NOT NULL DEFAULT 'EXECUTING',
    `result`          JSON           DEFAULT NULL COMMENT '执行结果快照',
    `created_at`      DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expire_at`       DATETIME       NOT NULL COMMENT '30 天后可清理',
    PRIMARY KEY (`idempotent_key`),
    KEY `idx_saga_id` (`saga_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录表';
```

```java
/**
 * 服务端使用示例 —— inventory.deduct() 接口的幂等实现
 *
 * 所有参与 Saga 的服务按此模式改造：
 * 1. 接口参数中要求 sagaId + stepName
 * 2. 方法开始时检查幂等
 * 3. 首次执行正常处理
 * 4. 重复调用直接返回上次结果
 */
@Service
public class InventoryService {
    
    @Autowired
    private IdempotentChecker idempotentChecker;

    @Autowired
    private SagaStepValidator stepValidator;

    /**
     * 扣减库存 —— Saga 兼容版本
     * 
     * @param sagaId   Saga 实例 ID
     * @param stepName 步骤名称
     * @param request  扣减请求
     */
    public DeductResponse deduct(String sagaId, String stepName, DeductRequest request) {
        // 1. 校验入参合法性
        stepValidator.validateStep(sagaId, stepName, "deductInventory");
        
        // 2. 幂等检查
        if (!idempotentChecker.tryAcquire(sagaId, stepName)) {
            // 已执行过 → 返回上次结果（保障幂等）
            return (DeductResponse) idempotentChecker.getPreviousResult(sagaId, stepName);
        }

        // 3. 执行业务逻辑
        try {
            DeductResponse result = doDeduct(request);
            idempotentChecker.complete(sagaId, stepName, result);
            return result;
        } catch (Exception e) {
            // 失败 → 删除幂等记录（允许重试重新执行）
            idempotentChecker.remove(sagaId, stepName);
            throw e;
        }
    }

    /**
     * 回滚扣减 —— 补偿操作，同样需要幂等
     */
    public UndoDeductResponse undoDeduct(String sagaId, String stepName, UndoDeductRequest request) {
        if (!idempotentChecker.tryAcquire(sagaId + ":compensate", stepName)) {
            return (UndoDeductResponse) idempotentChecker.getPreviousResult(sagaId + ":compensate", stepName);
        }

        UndoDeductResponse result = doUndoDeduct(request);
        idempotentChecker.complete(sagaId + ":compensate", stepName, result);
        return result;
    }
}
```

### 7. 恢复机制

```java
/**
 * Saga 恢复 Job —— XXL-Job 每 30s 执行一次
 *
 * 扫描所有状态异常的 Saga（RUNNING 超时、COMPENSATE_FAILED）
 * 自动执行恢复逻辑
 */
@Component
public class SagaRecoveryJob {

    @XxlJob("sagaRecoveryJob")
    public ReturnT<String> recover() {
        // 1. 扫描「正在执行但超时」的 Saga（理论上不应有，如果出现说明进程崩溃过）
        List<SagaInstance> stuckSagas = sagaLogRepo.findByStatusAndTimeout(
            SagaStatus.INITIATED, LocalDateTime.now().minusMinutes(10));

        for (SagaInstance saga : stuckSagas) {
            log.warn("发现卡住的 Saga: {}，尝试恢复", saga.getSagaId());
            try {
                // 检查各步骤实际状态
                List<StepRecord> steps = sagaLogRepo.findSteps(saga.getSagaId());
                boolean allForwardDone = steps.stream()
                    .allMatch(s -> s.getStatus() == StepStatus.SUCCEEDED);
                
                if (allForwardDone) {
                    // 正向全部完成，只是状态未更新 → 直接完成
                    sagaLogRepo.updateStatus(saga.getSagaId(), SagaStatus.COMPLETED);
                } else {
                    // 有步骤未完成 → 执行剩余正向步骤或触发补偿
                    SagaDefinition def = sagaRegistry.get(saga.getSagaName());
                    SagaContext ctx = SagaContext.fromJson(saga.getContext());
                    executor.resume(def, ctx);
                }
            } catch (Exception e) {
                log.error("恢复 Saga {} 失败", saga.getSagaId(), e);
                sagaLogRepo.updateStatus(saga.getSagaId(), SagaStatus.COMPENSATE_FAILED);
            }
        }

        // 2. 扫描「补偿失败」的 Saga → 重试补偿
        List<SagaInstance> failed = sagaLogRepo.findByStatus(
            SagaStatus.COMPENSATE_FAILED, 100);

        for (SagaInstance saga : failed) {
            // 检查补偿失败步骤的重试次数
            List<StepRecord> failedSteps = sagaLogRepo.findCompensateFailedSteps(saga.getSagaId());
            
            for (StepRecord step : failedSteps) {
                if (step.getCompensateRetryCount() < MAX_COMPENSATE_RETRIES) {
                    // 还有重试次数 → 重新执行补偿
                    SagaDefinition def = sagaRegistry.get(saga.getSagaName());
                    SagaStep sagaStep = def.findStep(step.getStepName());
                    try {
                        dubboInvoker.invoke(
                            sagaStep.getCompensateService(),
                            sagaStep.getCompensateMethod(),
                            step.getCompensateRequest(),
                            sagaStep.getStepTimeout());
                        sagaLogRepo.updateCompensateStatus(saga.getSagaId(),
                            step.getStepName(), StepStatus.COMPENSATED);
                    } catch (Exception e) {
                        sagaLogRepo.incrementCompensateRetry(saga.getSagaId(), step.getStepName());
                        log.error("补偿重试失败: {} / {}", saga.getSagaId(), step.getStepName(), e);
                    }
                }
            }

            // 检查是否全部补偿成功
            boolean allDone = sagaLogRepo.findCompensateFailedSteps(saga.getSagaId()).isEmpty();
            if (allDone) {
                sagaLogRepo.updateStatus(saga.getSagaId(), SagaStatus.COMPENSATED);
            }
        }

        // 3. 补偿重试超过上限的 Saga → 发告警（需要人工介入）
        List<SagaInstance> deadSagas = sagaLogRepo.findCompensateExhausted(MAX_COMPENSATE_RETRIES);
        for (SagaInstance saga : deadSagas) {
            alertService.sendAlert(Alert.builder()
                .severity(P1)
                .title("Saga 补偿失败，需要人工介入")
                .detail(String.format("SagaId: %s, 业务: %s, 失败步骤: %s",
                    saga.getSagaId(), saga.getBusinessKey(),
                    sagaLogRepo.findCompensateFailedSteps(saga.getSagaId())))
                .build());
        }

        return ReturnT.SUCCESS("恢复完成，处理 %d 个卡住 Saga，%d 个补偿失败",
            stuckSagas.size(), failed.size());
    }
}
```

### 8. 降级策略

| 场景 | 降级动作 | 影响 |
|------|---------|------|
| **Saga 编排器自身不可用** | 回退到 Choreography 模式（事件驱动），所有消息照常投递 | 无——Choreography 是当前模式 |
| **SagaLog DB 不可用** | 回退到 Choreography 模式 + 本地日志记录 | 无持久 Saga 状态，恢复期后重建 |
| **补偿执行失败** | 重试 3-5 次 → 投递死信队列 → SRE 人工处理 | 资金/库存可能短暂不一致 |
| **某服务长时间宕机** | Saga 卡在等待步骤 → 超时后自动触发补偿 | 业务流程中断 |
| **幂等记录表写失败** | 降级为乐观锁（版本号）或跳过幂等检查 | 重复执行风险上升 |
| **并发冲突** | 乐观锁失败 → 自动重试 3 次 | 用户体验略微延迟 |

### 9. 监控指标

```java
// === Saga 执行指标 ===
// Saga 提交总数（按 saga_name 和最终状态分类）
Counter.builder("saga.submit.total")
    .tag("saga_name", sagaName)
    .register(meterRegistry)
    .increment();

// Saga 完成率（成功 vs 补偿 vs 失败）
Counter.builder("saga.result")
    .tag("saga_name", sagaName)
    .tag("status", status)   // completed / compensated / failed
    .register(meterRegistry);

// Saga 执行耗时
Timer.builder("saga.execution.duration")
    .tag("saga_name", sagaName)
    .tag("status", status)
    .register(meterRegistry);

// Saga 执行中的当前正执行的 Saga 数量
Gauge.builder("saga.inflight", sagaLogRepo, r -> r.countByStatus(SagaStatus.INITIATED))
    .register(meterRegistry);

// 补偿执行次数
Counter.builder("saga.compensate.total")
    .tag("step_name", stepName)
    .tag("status", "triggered")  // triggered / succeeded / failed
    .register(meterRegistry);

// 幂等检查命中率
Counter.builder("saga.idempotent.hit")
    .tag("result", "first")   // first / replay
    .register(meterRegistry);
```

```yaml
# alerts/saga_alerts.yml
groups:
  - name: saga_alerts
    interval: 30s
    rules:
      # Saga 补偿失败需要人工介入
      - alert: SagaCompensateFailed
        expr: rate(saga_compensate_total{status="failed"}[15m]) > 0
        for: 5m
        labels:
          severity: P1
          team: sre
        annotations:
          summary: "Saga 补偿失败，需要人工介入"
          description: "15 分钟内有 {{ $value }} 次补偿失败"

      # Saga 执行成功率低于 99.9%
      - alert: SagaSuccessRateLow
        expr: |
          sum(rate(saga_result{status!="completed"}[30m]))
          / sum(rate(saga_result[30m])) * 100 > 0.1
        for: 10m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "Saga 执行成功率低于 99.9%"

      # 大量卡住的 Saga（超过 10 个卡在 INITIATED > 5min）
      - alert: SagaStuck
        expr: saga_inflight > 10
        for: 5m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "大量 Saga 卡住未完成"
```

### 10. 实施计划

| 阶段 | 任务 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | Saga 框架核心 | 基础设施 | 3 |
| 1.1 | Saga 数据模型（saga_instance + saga_step_log + idempotent_record 表） | PR: schema | 0.5 |
| 1.2 | Saga 编排器核心引擎（SagaExecutor + 步骤执行 + 补偿执行） | PR: executor | 1.5 |
| 1.3 | 通用幂等检查器 + Dubbo 拦截器 | PR: idempotent | 0.5 |
| 1.4 | Saga 定义注册机制（Spring Config 方式定义 Saga） | PR: registry | 0.5 |
| **Phase 2** | 恢复与兜底 | 可靠性 | 1.5 |
| 2.1 | SagaRecoveryJob（XXL-Job 扫描卡住 Saga + 恢复） | PR: recovery | 0.5 |
| 2.2 | 补偿死信队列 + 人工兜底控制台 | PR: DLQ + console | 0.5 |
| 2.3 | 补偿执行业务注解 @SagaCompensate 简化接入 | PR: annotation | 0.5 |
| **Phase 3** | 下单 Saga 改造 | 第一个接入业务 | 2 |
| 3.1 | 定义 CreateOrderSaga + 调通正向流程 | PR: create saga | 1 |
| 3.2 | 补偿逻辑联调 + 异常场景覆盖 | PR: compensate | 0.5 |
| 3.3 | 灰度上线（5% → 20% → 100%） | 验证 | 0.5 |
| **Phase 4** | 退款 Saga 改造 | 第二个接入业务 | 1.5 |
| 4.1 | 定义 RefundOrderSaga + 调通正向流程 | PR: refund saga | 0.5 |
| 4.2 | 退款异常场景 + 补偿联调 | PR: refund compensate | 0.5 |
| 4.3 | 灰度上线 | 验证 | 0.5 |
| **Phase 5** | 监控 + 文档 + 收尾 | 收尾 | 1 |
| 5.1 | Prometheus 指标 + Grafana Saga 看板 | PR: monitoring | 0.3 |
| 5.2 | Saga 运维手册（异常处理 SOP + 人工介入流程） | Wiki | 0.5 |
| 5.3 | 已有 Choreography 链路的梳理和文档 | 文档 | 0.2 |

**合计：9 人天**

### 11. 上线检查清单

#### 基础设施
- [ ] `saga_instance` 表已创建
- [ ] `saga_step_log` 表已创建
- [ ] `idempotent_record` 表已创建（唯一索引确认）
- [ ] RocketMQ Topic `SAGA_COMPENSATE_DLQ` 已创建
- [ ] XXL-Job `sagaRecoveryJob` 已配置（每 30s）
- [ ] Dubbo consumer 超时配置已调整（配合步骤超时）

#### 代码
- [ ] Saga 编排器核心已发布
- [ ] 通用幂等检查器已发布
- [ ] 幂等 Dubbo 拦截器已配置（自动注入 saga_id + step_name）
- [ ] CreateOrderSaga 定义已发布
- [ ] RefundOrderSaga 定义已发布
- [ ] order-core 的 createOrder / cancelOrder / confirmPaid 已做幂等改造
- [ ] inventory 的 deduct / undoDeduct 已做幂等改造
- [ ] payment 的 charge / refund 已做幂等改造
- [ ] Saga 死信人工控制台已上线

#### 兼容性
- [ ] 开启 Saga 模式时，下单不走原有 Choreography 链路（需 Apollo 开关控制）
- [ ] 关闭 Saga 模式时，无缝回退到 Choreography 模式
- [ ] 两个模式灰度共存验证（同一条订单只走一个模式）

#### 测试
- [ ] 单元测试：SagaExecutor 正向 + 补偿 + 重试逻辑
- [ ] 单元测试：幂等检查器（首次 / 重复 / 并发冲突）
- [ ] 集成测试：下单成功全链路
- [ ] 集成测试：扣库存失败 → 触发补偿 → 验证订单取消 + 库存未扣
- [ ] 集成测试：支付超时 → 触发补偿 → 验证订单取消 + 库存回滚
- [ ] 故障注入：SagaLog DB 写入失败 → 验证降级到 Choreography
- [ ] 故障注入：补偿步骤抛出异常 → 验证重试 → 死信投递
- [ ] 故障注入：SagaExecutor 进程崩溃 → 验证 RecoveryJob 恢复

#### 监控
- [ ] Grafana Saga 看板已上线（成功率 / 执行耗时 / 补偿次数 / 卡住数量）
- [ ] SagaCompensateFailed 告警已启用（P1）
- [ ] SagaStuck 告警已启用（P2）
- [ ] Saga 看板已添加到值班 On-Call 面板

### 12. 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 架构文档 §3.4 Saga 编排 | 补充 Saga 编排器的详细设计，替换原有的 Choreography-only 描述 |
| 状态机图 | Saga 的状态迁移与订单状态机一致，Saga 步骤映射到状态机的正向和逆向流转 |
| ADR-010 事件 Schema 治理 | Saga 编排器产生的领域事件（`SagaCompletedEvent` / `SagaCompensatedEvent`）需要 Schema 注册 |
| ADR-015 容量规划模型 | Saga 编排器本身的容量估算（每 Saga 约 5-10 次 Dubbo 调用，需预留 Pod） |
| ADR-019 异步任务中心 | 死信兜底可复用异步任务的 Job 框架 |
| ADR-030 全局幂等框架 | 当前 Saga 的 `idempotent_record` 表和幂等检查机制（IdempotentChecker）将在 ADR-030 框架落地后统一迁移，避免两套幂等体系并存 |

---

## 备选方案评估

### 方案 A（选定）：Saga Orchestration + 独立编排器

**优点**：全局可见性、补偿有保障、崩溃可恢复、适合复杂流程

**缺点**：编排器是中心组件，需保证自身高可用；业务代码需改造接入幂等

### 方案 B：TCC（Try-Confirm-Cancel）

**优点**：更强的隔离性（每个资源预留锁定）

**缺点**：库存锁定会导致并发能力下降（大促场景不适用）；TCC 的 Cancel 在业务失败时确认性不如 Saga 的补偿直观；接入改造成本更高

**结论**：订单场景更适合 Saga——每个步骤是**已完成操作**而不是**预留操作**，补偿是业务意义上的回退（取消订单、回滚库存），不是技术意义上的撤销（Cancel）。

### 方案 C：Seata AT 模式

**优点**：侵入性最低（自动生成反向 SQL）；社区成熟

**缺点**：依赖 Seata Server 组件；反向 SQL 功能受限于数据库（OceanBase 兼容性有风险）；不支持 Dubbo 调用的第三方服务（如支付网关）

**结论**：订单中台部分调用依赖外部（三方支付），Seata AT 无法覆盖非 DB 操作。Saga Orchestration 对混合场景（DB + 外部调用 + RPC）更通用。

### 方案 D：保留纯 Choreography + 增强补偿可靠性

**优点**：无需改造现有流程；无单点依赖

**缺点**：全局可见性永远无法解决；"补偿丢失"问题在纯事件模式下只能缓解（监控告警）不能根治

**结论**：混合模式——**核心交易链路走 Orchestration，非核心链路走 Choreography**。两者通过 Saga ID 关联，共享幂等和审计基础设施。
