# ADR-021：延迟任务调度平台

## 状态

已接受

---

## 背景

### 现状分析

订单中台存在大量"延迟执行"的需求——业务需要在某个时间点到达时自动执行特定操作。当前各业务自行实现延迟调度，缺乏统一平台：

```
当前延迟任务实现分散：

┌──────────────────────────────────────────────────────┐
│                   延迟任务场景                          │
├──────────────────────┬───────────────────────────────┤
│ 场景                  │ 当前实现方式                   │
├──────────────────────┼───────────────────────────────┤
│ 支付超时关单 (30min)  │ XXL-Job 每分钟扫描 order 表    │
│ 自动确认收货 (7天)    │ XXL-Job 每天扫描 DELIVERED 订单 │
│ 退款超时自动同意 (3天) │ ScheduledExecutorService 定时器 │ 
│ Saga 补偿重试 (1/5/30s)│ Dubbo 调用线程 Sleep + 重试    │
│ 导出任务过期清理 (7天)  │ XXL-Job 每天清理               │
│ 优惠券过期 (固定时间)   │ XXL-Job 每天扫描               │
│ 库存预占释放 (15min)   │ RocketMQ 延迟消息 (Level 14)   │
└──────────────────────┴───────────────────────────────┘
```

### 存在的问题

**问题 1：无统一抽象，每个场景自建调度机制**

- 支付超时用 XXL-Job 扫描 `order` 表（WHERE status='PENDING_PAY' AND created_at < NOW() - 30min）
- 库存预占释放用 RocketMQ 延迟消息（依赖固定 18 级延迟级别）
- Saga 补偿重试用线程 Sleep（JVM 内存状态，不持久）
- 每个场景的调度逻辑不可复用，新增一个延迟任务需要重复造轮子

**问题 2：XXL-Job DB 扫描随着数据量增长成本攀升**

- `order` 表日增量 100 万，全表扫描 `PENDING_PAY` 订单每次扫描约 500 万行
- 每分钟一次扫描 = 每天 720 次全表扫描
- 高峰期 DB CPU 因扫描查询升高 10-15%，与核心交易争抢资源

**问题 3：RocketMQ 延迟消息精度和级别受限**

- 仅支持 18 个固定延迟级别（1s/5s/10s/30s/1m/2m/…/2h），不支持任意延迟时间
- 延迟时长在发送时固定，无法动态调整
- 消息一旦发送无法取消（如买家支付成功 → 关单任务应取消）
- 延迟消息在 Broker 到期前一直占用内存，大量堆积影响 Broker 稳定性

**问题 4：JVM 内 ScheduledExecutorService 不持久**

- Saga 补偿重试的延迟退避存在 JVM 内存中，进程重启后所有待重试任务丢失
- 应用重启后补偿重试从 0 开始，可能超过重试上限导致 Saga 进入死信

**问题 5：缺乏任务生命周期管理**

- 无法查询"当前有哪些延迟任务待执行"
- 无法手动取消/重排任务（如运营手动延长支付有效期）
- 无法统计"每日触发多少延迟任务、成功率多少"

### 当前数据

| 指标 | 数值 | 说明 |
|------|------|------|
| 日触发延迟任务总量 | ~200 万 | 支付超时 + 自动确认 + 重试等 |
| 秒级精度需求场景 | 3 个 | Saga 重试、库存预占、支付超时 |
| 分钟级可接受场景 | 5+ 个 | 关单、确认收货、清理任务等 |
| 最长延迟时间 | 7 天 | 自动确认收货 |
| 现有 XXL-Job 扫描频率 | 1 次/分钟 ~ 1 次/天 | 粒度粗且扫描成本高 |

---

## 决策

引入 **统一延迟任务调度平台**，采用 **分级精度策略**：

1. **分级时间轮（Tiered Time Wheel）**：根据延迟时长选择不同的存储和执行引擎，而非一刀切方案
2. **毫秒级（< 30s）**：HashedWheelTimer + 内存，适用 Saga 补偿退避、重试间隔
3. **秒级（30s ~ 30min）**：RocketMQ 延迟消息封装 + 可取消能力，适用库存预占释放、支付超时
4. **分钟级（> 30min）**：DB 时间桶 + 分钟级轮询，适用自动确认收货、定期清理
5. **统一的 API + 任务生命周期管理**：所有延迟任务通过统一接口注册、取消、查询

```
延迟任务调度平台架构：

                       业务方
                    (order-core / payment / fulfillment / …)
                         │
                         ▼
               ┌─────────────────────┐
               │   DelayedTaskService │ ← 统一入口 API
               │ (register / cancel /  │
               │  reschedule / query) │
               └────────┬────────────┘
                        │ 路由（按 delay 时长）
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
   ┌──────────┐ ┌────────────┐ ┌──────────┐
   │ Tier 1   │ │ Tier 2     │ │ Tier 3   │
   │ 内存时间轮│ │ RocketMQ   │ │ DB 时间桶 │
   │ < 30s    │ │ 30s ~ 30min│ │ > 30min  │
   │ 100ms tick│ │ 可取消封装  │ │ 分钟轮询  │
   └──────────┘ └────────────┘ └──────────┘
                        │
                        ▼
               ┌─────────────────┐
               │ Task Persistence │
               │ (延迟任务表 +    │
               │  任务日志表)     │
               └─────────────────┘
```

---

## 详细设计

### 1. 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                   DelayedTask Platform                     │
│                                                           │
│  ┌──────────────────────────────────────────────────┐     │
│  │              DelayedTaskService API               │     │
│  │  register(task, delay, handler)                   │     │
│  │  cancel(taskId)                                   │     │
│  │  reschedule(taskId, newDelay)                     │     │
│  │  query(status, type, page)                        │     │
│  └──────────────┬───────────────────────────────────┘     │
│                 │                                          │
│         ┌───────┴────────┐                                 │
│         │  TierRouter     │                                 │
│         │  (路由选择器)    │                                 │
│         └───┬───┬───┬────┘                                 │
│             │   │   │                                       │
│     ┌───────┘   │   └──────────┐                           │
│     ▼           ▼              ▼                           │
│  ┌──────┐ ┌──────────┐ ┌──────────────┐                   │
│  │Tier 1│ │ Tier 2   │ │  Tier 3      │                   │
│  │时间轮 │ │MQ 封装   │ │ DB 桶轮询    │                   │
│  └──┬───┘ └────┬─────┘ └──────┬───────┘                   │
│     │          │               │                           │
│     ▼          ▼               ▼                           │
│  ┌──────┐ ┌────────┐ ┌──────────────┐                    │
│  │Task  │ │RocketMQ│ │  OceanBase   │                    │
│  │Store │ │(延迟)  │ │  task_xxx 表  │                    │
│  │(内存)│ └────────┘ └──────┬───────┘                    │
│  └──────┘                   │                             │
│                             ▼                             │
│                    ┌──────────────────┐                   │
│                    │  XXL-Job 轮询     │                   │
│                    │  (每分钟)         │                   │
│                    └──────────────────┘                   │
│                                                           │
│  ┌──────────────────────────────────────────────────┐     │
│  │              监控 & 运维                           │     │
│  │  - TaskScheduledCount / TaskFiredCount            │     │
│  │  - TaskExecutionDuration / TaskCancelledCount     │     │
│  │  - TimeWheelSize / TimeWheelQueueDepth            │     │
│  │  - DBBucketScanLatency / DBBucketTaskCount        │     │
│  └──────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

### 2. 统一数据模型

```java
/**
 * 延迟任务 —— 所有 Tier 共用同一抽象
 */
@Data
@Builder
public class DelayedTask {

    private String taskId;              // 全局唯一任务 ID（UUID）
    private String taskType;            // 任务类型枚举：PAYMENT_TIMEOUT / CONFIRM_RECEIPT / SAGA_RETRY / ...
    private String businessKey;         // 业务主键（order_id / refund_id / saga_id）
    private String handlerBean;         // Spring Bean 名（TaskHandler 实现）
    private String handlerMethod;       // 处理方法名

    private TaskStatus status;          // PENDING / CANCELLED / FIRED / EXECUTING / SUCCESS / FAILED
    private long delayMs;               // 延迟毫秒数（相对当前时间）
    private long executeAt;             // 计划执行时间戳（absolute millis）
    private int retryCount;             // 已重试次数
    private int maxRetries;             // 最大重试次数（默认 3）
    private long retryIntervalMs;       // 重试间隔（默认 30s）

    private String payload;             // 任务参数（JSON）
    private String sourceService;       // 来源服务名

    // 时间信息
    private long createdAt;             // 创建时间
    private long updatedAt;             // 最后更新时间
    private long firedAt;               // 实际触发时间
}

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    PENDING,      // 等待执行
    CANCELLED,    // 已取消
    FIRED,        // 已触发（正在执行）
    SUCCESS,      // 执行成功
    FAILED,       // 执行失败（超过重试次数）
    EXPIRED       // 过期未执行（GC 清理）
}

/**
 * 任务处理器接口 —— 业务方实现此接口处理具体逻辑
 */
public interface TaskHandler {
    TaskResult handle(String taskId, String payload);
}

/**
 * 统一 API 入口
 */
@Service
public class DelayedTaskService {

    private final TierRouter tierRouter;
    private final TaskRepository taskRepository;

    /**
     * 注册延迟任务
     */
    public String register(DelayedTask task) {
        task.setTaskId(generateTaskId());
        task.setStatus(TaskStatus.PENDING);
        task.setCreatedAt(System.currentTimeMillis());
        task.setExecuteAt(System.currentTimeMillis() + task.getDelayMs());

        // 1. 持久化到 DB（所有 Tier 都持久化）
        taskRepository.save(task);

        // 2. 路由到对应的 Tier
        tierRouter.route(task);

        return task.getTaskId();
    }

    /**
     * 取消任务
     */
    public boolean cancel(String taskId) {
        DelayedTask task = taskRepository.findById(taskId);
        if (task == null || task.getStatus() != TaskStatus.PENDING) {
            return false;
        }
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.updateStatus(taskId, TaskStatus.CANCELLED);

        // 通知对应 Tier 取消（各 Tier 自行实现取消逻辑）
        tierRouter.cancel(task);
        return true;
    }

    /**
     * 重排任务（修改执行时间）
     */
    public boolean reschedule(String taskId, long newDelayMs) {
        DelayedTask task = taskRepository.findById(taskId);
        if (task == null || task.getStatus() != TaskStatus.PENDING) {
            return false;
        }
        
        // 取消旧的 + 创建新的
        cancel(taskId);
        task.setDelayMs(newDelayMs);
        task.setExecuteAt(System.currentTimeMillis() + newDelayMs);
        task.setStatus(TaskStatus.PENDING);
        task.setTaskId(generateTaskId());
        taskRepository.save(task);
        tierRouter.route(task);
        return true;
    }

    /**
     * 查询任务
     */
    public PageResult<DelayedTask> query(TaskQuery query) {
        return taskRepository.query(query);
    }
}
```

### 3. Tier 1：内存时间轮（< 30s）

#### 3.1 原理

使用 Netty HashedWheelTimer 作为核心引擎，适合毫秒级精度、高吞吐的短延迟任务。

```
HashedWheelTimer 工作原理：

tickDuration = 100ms
wheelSize = 512
总周期 = 100ms × 512 = 51.2s

           tick 0               tick 1               tick 2
      ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
      │ slot 0       │    │ slot 1       │    │ slot 2       │
      │ ├─ task A     │    │ ├─ task C    │    │              │
      │ ├─ task B     │    │ └─ task D    │    │              │
      │ └─ ...        │    │              │    │              │
      └──────────────┘    └──────────────┘    └──────────────┘
           │                     │                    │
      ─────┴─────────────────────┴────────────────────┴─────→ time

  task E (delay 60s) → 放入 (60s/100ms) % 512 = 88 格 × N 轮
  task F (delay 3s)  → 放入 (3s/100ms) = 30 格（同一轮）
```

#### 3.2 代码实现

```java
/**
 * 时间轮 Tier —— 适合 < 30s 的高精度延迟任务
 *
 * 特点：
 * - 100ms tick 精度
 * - 纯内存操作，单次 put/get O(1)
 * - 单机每秒可处理数万任务
 * - 进程重启后内存任务丢失（依赖持久化 Recovery 机制）
 */
@Component
public class TimeWheelTier implements DelayedTaskTier {

    private final HashedWheelTimer timer;
    private final TaskExecutor taskExecutor;
    private final MeterRegistry meterRegistry;

    // 跟踪所有未到期任务（用于取消）
    private final Map<String, Timeout> taskTimeouts = new ConcurrentHashMap<>();

    public TimeWheelTier(TaskExecutor taskExecutor, MeterRegistry meterRegistry) {
        this.taskExecutor = taskExecutor;
        this.meterRegistry = meterRegistry;
        // tick = 100ms, wheel = 512 slots, 总周期 = 51.2s
        this.timer = new HashedWheelTimer(
            new DefaultThreadFactory("timewheel"),
            100, TimeUnit.MILLISECONDS,
            512,
            true,   // 是否泄漏检测
            -1      // 最大等待时间（-1 = 不限制）
        );
    }

    @Override
    public boolean accept(long delayMs) {
        return delayMs > 0 && delayMs <= 30_000;  // 30s 以内
    }

    @Override
    public void schedule(DelayedTask task) {
        Timeout timeout = timer.newTimeout(
            timeoutTask -> {
                taskTimeouts.remove(task.getTaskId());
                taskExecutor.execute(task);
                // 指标：时间轮触发计数
                meterRegistry.counter("task.timewheel.fired",
                    "type", task.getTaskType()).increment();
            },
            task.getDelayMs(),
            TimeUnit.MILLISECONDS
        );
        taskTimeouts.put(task.getTaskId(), timeout);

        meterRegistry.gauge("task.timewheel.pending",
            taskTimeouts, Map::size);
    }

    @Override
    public void cancel(DelayedTask task) {
        Timeout timeout = taskTimeouts.remove(task.getTaskId());
        if (timeout != null && !timeout.isExpired()) {
            timeout.cancel();
            meterRegistry.counter("task.timewheel.cancelled",
                "type", task.getTaskType()).increment();
        }
    }
}
```

#### 3.3 适用场景

| 场景 | 延迟 | 精度要求 |
|------|------|---------|
| Saga 补偿重试间隔 | 1s / 5s / 30s | ±100ms |
| 幂等记录短期清理 | 10s | ±500ms |
| 缓存逐出延迟通知 | 5s | ±200ms |
| 库存预占释放（短） | 15s | ±500ms |

### 4. Tier 2：RocketMQ 延迟消息封装（30s ~ 30min）

#### 4.1 原理

RocketMQ 原生支持 18 级延迟级别，精度足够覆盖分钟级任务。通过封装层解决：
1. **可取消能力**：通过 `CANCEL_TOPIC` + Redis 标记实现软取消
2. **任意延迟时间**：自动匹配最近的 RocketMQ 延迟级别
3. **业务统一**：屏蔽 RocketMQ API，统一使用 DelayedTaskService

```
原始 RocketMQ 延迟消息：
  延迟级别 → 延迟时间
  1=1s, 2=5s, 3=10s, 4=30s, 5=1m, 6=2m, 7=3m, 8=4m,
  9=5m, 10=6m, 11=7m, 12=8m, 13=9m, 14=10m, 15=20m,
  16=30m, 17=1h, 18=2h

封装策略：
  用户指定 delayMs → TierRouter 自动匹配最近的 RocketMQ Level
  例：delayMs = 2min    → level 6 (2m)，误差 0%
  例：delayMs = 12min   → level 14 (10m)，误差 +2min → 额外 wait 2min
  例：delayMs = 25min   → level 16 (30m)，误差 +5min → 额外 wait 5min
```

#### 4.2 代码实现

```java
/**
 * RocketMQ 延迟消息 Tier —— 适合 30s ~ 30min 的延迟任务
 *
 * 特性：
 * - 原生 18 级延迟，自动匹配最近级别
 * - 可取消：消费端先检查 Redis 标记，已取消则丢弃
 * - 消息本身在 Broker 持久化，重启不丢失
 * - 每个任务单独消息，互相独立
 */
@Component
public class RocketMQDelayedTier implements DelayedTaskTier {

    private final RocketMQProducer producer;
    private final StringRedisTemplate redisTemplate;
    private final TaskExecutor taskExecutor;

    // RocketMQ 预定义延迟级别映射
    private static final int[] DELAY_LEVELS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18
    };

    private static final long[] DELAY_TIMES = {
        1000, 5000, 10000, 30000, 60000, 120000, 180000, 240000,
        300000, 360000, 420000, 480000, 540000, 600000, 1200000, 1800000,
        3600000, 7200000
    };

    private static final String CANCEL_PREFIX = "task:cancel:";

    @Override
    public boolean accept(long delayMs) {
        return delayMs > 30_000 && delayMs <= 1_800_000;  // 30s ~ 30min
    }

    @Override
    public void schedule(DelayedTask task) {
        String topic = resolveTopic(task.getTaskType());
        int delayLevel = matchDelayLevel(task.getDelayMs());

        Message message = new Message(topic, task.getTaskId().getBytes());
        message.setDelayTimeLevel(delayLevel);

        // 如果实际延迟 > 计划延迟，附加一个额外等待时间
        long actualDelayMs = DELAY_TIMES[delayLevel - 1];
        if (actualDelayMs > task.getDelayMs()) {
            message.putUserProperty("extraWaitMs",
                String.valueOf(actualDelayMs - task.getDelayMs()));
        }

        producer.send(message);
    }

    @Override
    public void cancel(DelayedTask task) {
        // RocketMQ 消息一旦发送无法撤回
        // 方案：在 Redis 设置取消标记，消费端检查后丢弃
        redisTemplate.opsForValue().set(
            CANCEL_PREFIX + task.getTaskId(),
            "1",
            Duration.ofDays(1)  // 最多保留 1 天
        );
    }

    /**
     * RocketMQ 消息消费者 —— 消费延迟消息
     */
    @Component
    public class DelayedTaskConsumer {

        @PostConstruct
        public void init() {
            // 订阅所有延迟任务 Topic
        }

        @RocketMQMessageListener(
            topic = "${task.rocketmq.topic.prefix}*",
            consumerGroup = "${task.rocketmq.consumer.group}",
            consumeMode = ConsumeMode.CONCURRENTLY
        )
        public void onMessage(MessageExt message) {
            String taskId = new String(message.getBody());

            // 1. 检查是否已取消
            String cancelled = redisTemplate.opsForValue()
                .get(CANCEL_PREFIX + taskId);
            if (cancelled != null) {
                log.info("任务已取消，跳过执行: {}", taskId);
                return;
            }

            // 2. 处理额外等待（当 RocketMQ 级别 > 请求延迟时）
            String extraWait = message.getUserProperty("extraWaitMs");
            if (extraWait != null) {
                long waitMs = Long.parseLong(extraWait);
                if (waitMs > 1000) {
                    Thread.sleep(waitMs);
                }
            }

            // 3. 查询任务并执行
            DelayedTask task = taskRepository.findById(taskId);
            if (task == null || task.getStatus() != TaskStatus.PENDING) {
                return;
            }
            taskExecutor.execute(task);
        }
    }

    private int matchDelayLevel(long delayMs) {
        for (int i = 0; i < DELAY_TIMES.length; i++) {
            if (DELAY_TIMES[i] >= delayMs) {
                return DELAY_LEVELS[i];
            }
        }
        return 18;  // max = 2h
    }

    private String resolveTopic(String taskType) {
        return "TASK_DELAYED_" + taskType;
    }
}
```

#### 4.3 适用场景

| 场景 | 延迟 | RocketMQ Level |
|------|------|---------------|
| 支付超时关单 | 30min | Level 16 (30m) 精确 |
| 库存预占释放 | 15min | Level 15 (20m) 误差 +5min |
| 退款超时审核（运营不处理时自动同意） | 3min~10min | Level 9~14 |
| 临时权限/功能有效期 | 5min~30min | Level 9~16 |
| Saga 恢复 Job 触发间隔 | 1min | Level 5 (1m) 精确 |

### 5. Tier 3：DB 时间桶轮询（> 30min）

#### 5.1 原理

使用 OceanBase 的时间桶（Time Bucket）策略，将延迟任务按执行时间分桶，XXL-Job 每分钟扫描到期的桶，批量取出执行。

```
DB 时间桶设计：

时间桶 = 按 execute_at 对齐到分钟：

  bucket_20260612_1430: 14:30:00 ~ 14:30:59 需要执行的任务
  bucket_20260612_1431: 14:31:00 ~ 14:31:59
  bucket_20260612_1432: 14:32:00 ~ 14:32:59

写入时：task.executeAt → 对齐到分钟 → 写入对应桶前缀
扫描时：XXL-Job 每分钟扫描当前分钟桶 → 取出所有任务 → 批量执行

好处：避免全表扫描，每次只扫描当前分钟桶（少量数据）
```

#### 5.2 代码实现

```java
/**
 * DB 时间桶 Tier —— 适合 > 30min 的大延迟、大批量任务
 *
 * 特性：
 * - 分钟级精度（±1min），对长延迟场景可接受
 * - 每次扫描仅访问当前分钟桶，避免全表扫描
 * - OceanBase 持久化，重启不丢失
 * - 天然支持批量执行
 */
@Component
public class DBBucketTier implements DelayedTaskTier {

    @Override
    public boolean accept(long delayMs) {
        return delayMs > 1_800_000;  // > 30min
    }

    @Override
    public void schedule(DelayedTask task) {
        // 写入时已由 DelayedTaskService.save() 持久化
        // DB 桶通过 execute_at 索引定位
    }

    @Override
    public void cancel(DelayedTask task) {
        // 由 DelayedTaskService 更新 status = CANCELLED
        // DB 桶扫描时跳过已取消的任务
    }
}

/**
 * 时间桶扫描 Job —— XXL-Job 每分钟执行一次
 *
 * 扫描策略：
 * 1. 取当前时间对齐到分钟：bucket_time = NOW() 整分钟
 * 2. 查询 execute_at BETWEEN bucket_time AND bucket_time + 59.999s
 * 3. 取出所有 PENDING 状态的任务
 * 4. 批量提交到 TaskExecutor 执行
 * 5. DB 扫描本身是索引范围扫描（idx_execute_at），每次查询 < 1000 行
 */
@Component
public class BucketScanJob {

    @XxlJob("bucketScanJob")
    public ReturnT<String> scan() {
        long start = System.currentTimeMillis();

        // 1. 计算当前分钟桶范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bucketStart = now.withSecond(0).withNano(0);
        LocalDateTime bucketEnd = bucketStart.plusSeconds(59).withNano(999_999_000);

        // 2. 查询当前分钟待执行任务
        List<DelayedTask> tasks = taskRepository.findPendingBetween(
            bucketStart, bucketEnd, 1000);  // 单次最多取 1000

        if (tasks.isEmpty()) {
            return ReturnT.SUCCESS("无任务");
        }

        log.info("时间桶 {} 扫描到 {} 个待执行任务",
            bucketStart.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")),
            tasks.size());

        // 3. 批量提交执行
        CountDownLatch latch = new CountDownLatch(tasks.size());
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (DelayedTask task : tasks) {
            taskExecutor.submit(() -> {
                try {
                    taskExecutor.execute(task);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("桶任务执行失败: {}", task.getTaskId(), e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(60, TimeUnit.SECONDS);

        // 4. 指标上报
        long cost = System.currentTimeMillis() - start;
        meterRegistry.counter("task.bucket.fired",
            "bucket", bucketStart.format(DateTimeFormatter.ofPattern("HHmm")),
            "success", String.valueOf(success.get()),
            "failed", String.valueOf(failed.get())
        ).increment(tasks.size());
        meterRegistry.timer("task.bucket.scan.duration")
            .record(cost, TimeUnit.MILLISECONDS);

        log.info("桶扫描完成: 总计={}, 成功={}, 失败={}, 耗时={}ms",
            tasks.size(), success.get(), failed.get(), cost);

        return ReturnT.SUCCESS(
            String.format("总计=%d, 成功=%d, 失败=%d", tasks.size(), success.get(), failed.get()));
    }
}
```

#### 5.3 DB DDL

```sql
-- ============================================
-- 延迟任务表 —— 所有 Tier 共用
-- ============================================
CREATE TABLE `delayed_task` (
    `task_id`         VARCHAR(64)     NOT NULL COMMENT '全局唯一任务 ID',
    `task_type`       VARCHAR(32)     NOT NULL COMMENT '任务类型: PAYMENT_TIMEOUT / CONFIRM_RECEIPT / SAGA_RETRY / …',
    `business_key`    VARCHAR(128)    DEFAULT NULL COMMENT '业务主键（重复任务去重依据）',

    `status`          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CANCELLED/SUCCESS/FAILED',
    `delay_ms`        BIGINT          NOT NULL COMMENT '相对延迟毫秒数',
    `execute_at`      DATETIME(3)     NOT NULL COMMENT '计划执行时间（索引字段）',

    `handler_bean`    VARCHAR(64)     NOT NULL COMMENT 'Spring Bean 名（TaskHandler 实现）',
    `payload`         JSON            DEFAULT NULL COMMENT '任务参数',
    `source_service`  VARCHAR(64)     DEFAULT NULL COMMENT '来源服务名',

    `retry_count`     INT             DEFAULT 0 COMMENT '已重试次数',
    `max_retries`     INT             DEFAULT 3 COMMENT '最大重试次数',
    `retry_interval_ms` BIGINT        DEFAULT 30000 COMMENT '重试间隔毫秒',
    `last_error`      TEXT            DEFAULT NULL COMMENT '上次错误信息',

    `fired_at`        DATETIME(3)     DEFAULT NULL COMMENT '实际触发时间',
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`task_id`),
    KEY `idx_execute_at` (`execute_at`),
    KEY `idx_status_execute` (`status`, `execute_at`),
    KEY `idx_business_key` (`business_key`),
    KEY `idx_task_type_status` (`task_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='延迟任务表';

-- ============================================
-- 任务执行日志表 —— 记录每次触发结果
-- ============================================
CREATE TABLE `task_execution_log` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT,
    `task_id`         VARCHAR(64)     NOT NULL COMMENT '任务 ID',
    `task_type`       VARCHAR(32)     NOT NULL,

    `status`          VARCHAR(16)     NOT NULL COMMENT 'SUCCESS / FAILED',
    `execute_at`      DATETIME(3)     NOT NULL COMMENT '执行时间',
    `cost_ms`         INT             DEFAULT NULL COMMENT '执行耗时',
    `result`          TEXT            DEFAULT NULL COMMENT '执行结果摘要',
    `error_message`   TEXT            DEFAULT NULL COMMENT '错误信息',
    `node_ip`         VARCHAR(32)     DEFAULT NULL COMMENT '执行节点 IP',

    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志';

-- ============================================
-- 业务幂等（同一业务键不应重复注册待执行任务）
-- 例如：同一 order_id 不应有 2 个 PAYMENT_TIMEOUT 等待执行
-- ============================================
-- 应用层保证：同一业务键 + 任务类型 + PENDING 状态，
-- 注册新任务前先取消旧的。
-- DB 层不设唯一约束以支持灵活的业务语义。
--
-- TODO: 该应用层去重逻辑将被 ADR-030（全局幂等框架）统一接管，
--       后续迁移到全局幂等框架后不再由本平台维护。
```

#### 5.4 DB 查询优化

```sql
-- 时间桶扫描 SQL（XXL-Job 执行）：
-- 每次查询 1 分钟数据，范围扫描 idx_execute_at 索引，返回 PENDING 状态的任务
SELECT task_id, task_type, handler_bean, payload, retry_count, max_retries, retry_interval_ms
FROM delayed_task FORCE INDEX (idx_status_execute)
WHERE status = 'PENDING'
  AND execute_at >= :bucketStart
  AND execute_at < :bucketEnd
ORDER BY execute_at ASC
LIMIT 1000;

-- 重试/失败任务升级 SQL（XXL-Job，每分钟扫描）：
-- 查询已触发但执行异常的任务（执行过但失败，需要重试的）
SELECT task_id, task_type, handler_bean, payload,
       retry_count, max_retries, retry_interval_ms
FROM delayed_task
WHERE status = 'FAILED'
  AND retry_count < max_retries
  AND updated_at < DATE_SUB(NOW(), INTERVAL retry_interval_ms/1000 SECOND)
LIMIT 500;

-- 历史清理 SQL（一天一次，凌晨执行）：
-- 保留 30 天，与 ADR-031 数据保留策略 L3=30d 保持一致
DELETE FROM task_execution_log
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

### 6. 任务执行器（TaskExecutor）

```java
/**
 * 任务执行器 —— 统一执行入口
 *
 * 所有 Tier 最终都通过 TaskExecutor 执行任务
 * 职责：反射调用 TaskHandler → 处理结果 → 异常重试 → 记录日志
 */
@Component
public class TaskExecutor {

    private final ApplicationContext applicationContext;
    private final TaskRepository taskRepository;
    private final TaskExecutionLogRepository logRepository;
    private final MeterRegistry meterRegistry;
    private final ThreadPoolTaskExecutor executor;

    public TaskExecutor(ApplicationContext applicationContext,
                        TaskRepository taskRepository,
                        TaskExecutionLogRepository logRepository,
                        MeterRegistry meterRegistry) {
        this.applicationContext = applicationContext;
        this.taskRepository = taskRepository;
        this.logRepository = logRepository;
        this.meterRegistry = meterRegistry;

        // 独立的线程池，与业务线程隔离
        this.executor = new ThreadPoolTaskExecutor();
        this.executor.setCorePoolSize(4);
        this.executor.setMaxPoolSize(16);
        this.executor.setQueueCapacity(512);
        this.executor.setThreadNamePrefix("task-exec-");
        this.executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        this.executor.initialize();
    }

    /**
     * 同步执行（Tier 1 时间轮直接调用）
     */
    public void execute(DelayedTask task) {
        long start = System.currentTimeMillis();
        try {
            // 1. 再次检查状态（可能已被取消）
            if (taskRepository.findById(task.getTaskId()).getStatus() != TaskStatus.PENDING) {
                return;
            }

            // 2. 标记为执行中
            taskRepository.updateStatus(task.getTaskId(), TaskStatus.FIRED);

            // 3. 通过 Spring Bean 执行
            TaskHandler handler = (TaskHandler) applicationContext.getBean(task.getHandlerBean());
            TaskResult result = handler.handle(task.getTaskId(), task.getPayload());

            // 4. 更新状态
            TaskStatus finalStatus = result.isSuccess() ? TaskStatus.SUCCESS : TaskStatus.FAILED;
            taskRepository.updateStatus(task.getTaskId(), finalStatus);
            taskRepository.updateFiredAt(task.getTaskId(), LocalDateTime.now());

            // 5. 记录日志
            logExecution(task, finalStatus, System.currentTimeMillis() - start, null, result.getMessage());

            // 指标
            meterRegistry.counter("task.execution.result",
                "type", task.getTaskType(),
                "status", finalStatus.name()
            ).increment();

        } catch (Exception e) {
            // 异常 → 重试逻辑
            handleRetry(task, e, System.currentTimeMillis() - start);
        }
    }

    /**
     * 异步提交（Tier 3 桶扫描批量提交）
     */
    public void submit(Runnable task) {
        executor.submit(task);
    }

    /**
     * 重试逻辑
     */
    private void handleRetry(DelayedTask task, Exception error, long costMs) {
        log.error("任务执行失败: {} (重试 {}/{})",
            task.getTaskId(), task.getRetryCount() + 1, task.getMaxRetries(), error);

        taskRepository.incrementRetryCount(task.getTaskId(), error.getMessage());

        if (task.getRetryCount() < task.getMaxRetries()) {
            // 还有重试次数 → 重新注册延迟任务（退避时间 = retryIntervalMs）
            DelayedTask retryTask = DelayedTask.builder()
                .taskId(generateTaskId())
                .taskType(task.getTaskType())
                .businessKey(task.getBusinessKey())
                .handlerBean(task.getHandlerBean())
                .delayMs(task.getRetryIntervalMs())
                .retryCount(task.getRetryCount() + 1)
                .maxRetries(task.getMaxRetries())
                .retryIntervalMs(task.getRetryIntervalMs())
                .payload(task.getPayload())
                .build();
            tierRouter.route(retryTask);
        } else {
            // 超过最大重试次数 → 标记 FAILED
            taskRepository.updateStatus(task.getTaskId(), TaskStatus.FAILED);
            logExecution(task, TaskStatus.FAILED, costMs, error.getMessage(), "重试耗尽");

            // P1 告警
            alertService.sendAlert(Alert.builder()
                .severity(P1)
                .title("延迟任务重试耗尽")
                .detail(String.format("任务 %s (类型: %s) 重试 %d 次后仍然失败",
                    task.getTaskId(), task.getTaskType(), task.getMaxRetries()))
                .build());
        }
    }

    private void logExecution(DelayedTask task, TaskStatus status,
                              long costMs, String error, String result) {
        TaskExecutionLog log = TaskExecutionLog.builder()
            .taskId(task.getTaskId())
            .taskType(task.getTaskType())
            .status(status.name())
            .executeAt(LocalDateTime.now())
            .costMs((int) costMs)
            .result(result)
            .errorMessage(error)
            .nodeIp(NetUtil.getLocalhostStr())
            .build();
        logRepository.insert(log);
    }
}
```

### 7. Tier 路由（TierRouter）

```java
/**
 * Tier 路由选择器 —— 根据延迟时长自动路由到合适的 Tier
 */
@Component
public class TierRouter {

    private final List<DelayedTaskTier> tiers;  // 注入所有 Tier

    public TierRouter(List<DelayedTaskTier> tiers) {
        // 按照优先级排序（Tier 1 > Tier 2 > Tier 3）
        this.tiers = tiers.stream()
            .sorted(Comparator.comparingInt(DelayedTaskTier::getOrder))
            .collect(Collectors.toList());
    }

    /**
     * 路由调度
     * 一个任务只会被路由到一个 Tier
     */
    public void route(DelayedTask task) {
        for (DelayedTaskTier tier : tiers) {
            if (tier.accept(task.getDelayMs())) {
                tier.schedule(task);
                return;
            }
        }
        // fallback：没有 Tier 接受时走 DB Tier（兜底）
        tiers.get(tiers.size() - 1).schedule(task);
    }

    /**
     * 路由取消
     * 通知所有 Tier 取消（实际只有 Tier 1 和 Tier 2 需要处理）
     */
    public void cancel(DelayedTask task) {
        for (DelayedTaskTier tier : tiers) {
            tier.cancel(task);
        }
    }
}

/**
 * 延迟任务 Tier 接口
 */
public interface DelayedTaskTier {

    /**
     * 是否接受此延迟时长的任务
     */
    boolean accept(long delayMs);

    /**
     * 调度任务
     */
    void schedule(DelayedTask task);

    /**
     * 取消任务
     */
    void cancel(DelayedTask task);

    /**
     * Tier 优先级（数字越小越优先）
     */
    default int getOrder() {
        if (this instanceof TimeWheelTier) return 1;
        if (this instanceof RocketMQDelayedTier) return 2;
        return 3;
    }
}
```

### 8. 启动恢复机制

```java
/**
 * 启动恢复 —— 应用重启时将所有 DB 中的 PENDING 任务重新调度
 *
 * 场景：
 * - 应用重启 → Tier 1 时间轮中的内存任务全部丢失
 * - 应用重启 → Tier 3 DB 桶中未到期的任务仍存在 DB 中等待扫描
 * - 只有 Tier 1 的短延迟任务需要恢复
 *
 * 策略：
 * - 启动时扫描所有 PENDING 且 execute_at > NOW() 的任务
 * - 重新计算剩余延迟 = execute_at - NOW()
 * - 按剩余延迟重新 route 到对应 Tier
 */
@Component
public class TaskRecoveryJob {

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        log.info("启动恢复：重新调度未执行的延迟任务...");

        List<DelayedTask> pendingTasks = taskRepository
            .findAllByStatusAndExecuteAfter(TaskStatus.PENDING, LocalDateTime.now());

        int recovered = 0;
        for (DelayedTask task : pendingTasks) {
            // 重新计算剩余延迟
            long remainingMs = Duration.between(LocalDateTime.now(), task.getExecuteAt()).toMillis();
            if (remainingMs < 0) {
                // 已过期 → 立即执行
                taskExecutor.execute(task);
            } else {
                // 未到期 → 重新 route
                task.setDelayMs(remainingMs);
                tierRouter.route(task);
            }
            recovered++;
        }

        log.info("启动恢复完成：恢复 {} 个延迟任务", recovered);

        meterRegistry.gauge("task.recovered.count", recovered);
    }
}
```

### 9. 业务接入示例

#### 9.1 支付超时关单

```java
/**
 * 支付超时关单 —— 用户下单后 30min 未支付自动关闭
 */
@Component
public class PaymentTimeoutHandler implements TaskHandler {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public TaskResult handle(String taskId, String payload) {
        // payload = {"orderId": "20260611001"}
        JSONObject params = JSON.parseObject(payload);
        String orderId = params.getString("orderId");

        // 检查订单状态（可能已支付，则跳过）
        Order order = orderRepository.findById(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAY) {
            return TaskResult.success("订单已处理，跳过关单");
        }

        // 执行关单
        orderRepository.updateStatus(orderId, OrderStatus.CLOSED);
        return TaskResult.success("关单成功");
    }
}

// 下单时注册延迟任务
@Service
public class OrderService {

    @Autowired
    private DelayedTaskService delayedTaskService;

    public void createOrder(CreateOrderRequest request) {
        // … 业务逻辑

        // 注册 30min 后关单任务
        delayedTaskService.register(DelayedTask.builder()
            .taskType("PAYMENT_TIMEOUT")
            .businessKey(orderId)
            .handlerBean("paymentTimeoutHandler")
            .delayMs(30 * 60 * 1000L)  // 30min
            .payload(JSON.toJSONString(Map.of("orderId", orderId)))
            .sourceService("order-core")
            .build());

        // 支付成功后取消关单任务
        // delayedTaskService.cancel(previousTaskId);
    }
}
```

#### 9.2 自动确认收货

```java
/**
 * 自动确认收货 —— 发货后 7 天自动确认
 */
@Component
public class ConfirmReceiptHandler implements TaskHandler {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public TaskResult handle(String taskId, String payload) {
        JSONObject params = JSON.parseObject(payload);
        String orderId = params.getString("orderId");

        Order order = orderRepository.findById(orderId);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return TaskResult.success("订单非已签收状态，跳过自动确认");
        }

        orderRepository.updateStatus(orderId, OrderStatus.COMPLETED);
        return TaskResult.success("自动确认收货成功");
    }
}

// 发货时注册
@Service
public class FulfillmentService {

    @Autowired
    private DelayedTaskService delayedTaskService;

    public void ship(String orderId) {
        // … 发货业务逻辑

        // 注册 7 天后自动确认收货
        delayedTaskService.register(DelayedTask.builder()
            .taskType("CONFIRM_RECEIPT")
            .businessKey(orderId)
            .handlerBean("confirmReceiptHandler")
            .delayMs(7 * 24 * 60 * 60 * 1000L)  // 7 天
            .payload(JSON.toJSONString(Map.of("orderId", orderId)))
            .sourceService("fulfillment")
            .build());
    }
}
```

#### 9.3 Saga 补偿重试

```java
/**
 * Saga 补偿重试 —— 补偿失败后延迟重试
 */
@Component
public class SagaCompensateRetryHandler implements TaskHandler {

    @Autowired
    private SagaExecutor sagaExecutor;

    @Override
    public TaskResult handle(String taskId, String payload) {
        JSONObject params = JSON.parseObject(payload);
        String sagaId = params.getString("sagaId");
        String stepName = params.getString("stepName");

        // 重新执行补偿
        try {
            sagaExecutor.compensateStep(sagaId, stepName);
            return TaskResult.success("补偿重试成功");
        } catch (Exception e) {
            return TaskResult.failed("补偿重试失败: " + e.getMessage());
        }
    }
}

// Saga 补偿失败时注册重试（替代原有的 Dubbo 线程 Sleep）
public class SagaExecutor {

    @Autowired
    private DelayedTaskService delayedTaskService;

    // 补偿退避策略
    private static final long[] BACKOFF_INTERVALS = {1000, 5000, 30000};  // 1s, 5s, 30s

    private void retryCompensateWithBackoff(String sagaId, String stepName, int attempt) {
        if (attempt >= BACKOFF_INTERVALS.length) {
            // 超过最大重试次数 → 投递死信
            dlqProducer.send(sagaId, stepName);
            return;
        }

        // 注册延迟重试任务（Tier 1 时间轮，100ms 精度）
        delayedTaskService.register(DelayedTask.builder()
            .taskType("SAGA_COMPENSATE_RETRY")
            .businessKey(sagaId + ":" + stepName)
            .handlerBean("sagaCompensateRetryHandler")
            .delayMs(BACKOFF_INTERVALS[attempt])
            .payload(JSON.toJSONString(Map.of(
                "sagaId", sagaId,
                "stepName", stepName,
                "attempt", attempt + 1
            )))
            .sourceService("saga-orchestrator")
            .build());
    }
}
```

### 10. 监控指标

```java
// === Tier 1：时间轮指标 ===
// 时间轮中等待执行的任务数量
meterRegistry.gauge("task.timewheel.pending",
    tag("type", taskType), taskTimeouts::size);

// 时间轮触发任务计数
Counter.builder("task.timewheel.fired")
    .tag("type", taskType)
    .register(meterRegistry);

// 时间轮取消任务计数
Counter.builder("task.timewheel.cancelled")
    .tag("type", taskType)
    .register(meterRegistry);

// === Tier 2：RocketMQ 延迟消息指标 ===
// 通过 MQ 延迟的触发计数
Counter.builder("task.mq.fired")
    .tag("topic", topic)
    .tag("delayLevel", String.valueOf(delayLevel))
    .register(meterRegistry);

// MQ 消息取消计数（消费端发现已取消）
Counter.builder("task.mq.cancelled_on_consume")
    .tag("topic", topic)
    .register(meterRegistry);

// === Tier 3：DB 桶扫描指标 ===
// 桶扫描执行任务数
Counter.builder("task.bucket.fired")
    .tag("bucket", bucketTime)
    .register(meterRegistry);

// 桶扫描耗时
Timer.builder("task.bucket.scan.duration")
    .register(meterRegistry);

// === 全局任务指标 ===
// 延迟任务注册速率
Counter.builder("task.registered.total")
    .tag("type", taskType)
    .register(meterRegistry);

// 任务执行结果
Counter.builder("task.execution.result")
    .tag("type", taskType)
    .tag("status", status)  // SUCCESS / FAILED / CANCELLED
    .register(meterRegistry);

// 任务执行耗时（秒级和分钟级分别统计）
Timer.builder("task.execution.duration")
    .tag("type", taskType)
    .register(meterRegistry);

// 各 Tier 延迟分布
DistributionSummary.builder("task.delay.distribution")
    .tag("tier", tierName)  // timewheel / mq / bucket
    .publishPercentiles(0.5, 0.9, 0.99)
    .register(meterRegistry);
```

### 11. Alert 规则

```yaml
# alerts/delayed_task_alerts.yml
groups:
  - name: delayed_task_alerts
    interval: 60s
    rules:
      # 大量任务执行失败
      - alert: TaskExecutionHighFailureRate
        expr: |
          rate(task_execution_result{status="FAILED"}[5m])
          / rate(task_execution_result[5m]) * 100 > 10
        for: 3m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "延迟任务执行失败率超过 10%"
          description: "5min 内 {{ $value | humanizePercentage }} 的任务执行失败"

      # 桶扫描耗时过长
      - alert: BucketScanLatencyHigh
        expr: task_bucket_scan_duration_seconds_max > 30
        for: 2m
        labels:
          severity: P2
          team: sre
        annotations:
          summary: "DB 桶扫描耗时超过 30s"
          description: "当前扫描耗时 {{ $value }}s，可能任务堆积过多"

      # 时间轮堆积过多任务
      - alert: TimeWheelTaskBacklog
        expr: task_timewheel_pending > 10000
        for: 1m
        labels:
          severity: P3
          team: sre
        annotations:
          summary: "时间轮堆积超过 10000 个待执行任务"
          description: "当前 {{ $value }} 个，检查是否有任务泄漏"

      # 注册速率突降（可能服务异常）
      - alert: TaskRegistrationDrop
        expr: |
          rate(task_registered_total[5m])
          / rate(task_registered_total[5m] offset 1h) < 0.1
        for: 5m
        labels:
          severity: P3
          team: sre
        annotations:
          summary: "延迟任务注册量突降，可能服务异常"
          description: "当前注册速率仅为 1h 前的 {{ $value | humanizePercentage }}"
```

### 12. 任务类型清单

| 任务类型 | 延迟时长 | Tier | 备注 |
|---------|---------|------|------|
| `PAYMENT_TIMEOUT` | 30min | Tier 2 (RocketMQ) | 下单后未支付自动关单 |
| `CONFIRM_RECEIPT` | 7 天 | Tier 3 (DB 桶) | 签收后自动确认收货 |
| `SAGA_COMPENSATE_RETRY` | 1s / 5s / 30s | Tier 1 (时间轮) | Saga 补偿指数退避 |
| `INVENTORY_RESERVATION` | 15min | Tier 2 (RocketMQ) | 库存预占超时释放 |
| `REFUND_AUTO_APPROVE` | 3 天 | Tier 3 (DB 桶) | 商家未处理退款自动同意 |
| `EXPORT_JOB_CLEANUP` | 7 天 | Tier 3 (DB 桶) | 导出文件过期清理 |
| `COUPON_EXPIRE` | 固定时间 | Tier 3 (DB 桶) | 优惠券过期失效 |
| `SAGA_IDEMPOTENT_CLEANUP` | 30 天 | Tier 3 (DB 桶) | 幂等记录定期清理 |

### 13. 与现有方案的对比

| 维度 | 当前分散方案 | 统一调度平台 |
|------|------------|------------|
| **调度方式** | XXL-Job / MQ 延迟 / Sleep 混用 | 统一 API 接入 |
| **精度** | 分钟级 ~ 秒级不等 | 100ms ~ 分钟级自适应 |
| **可取消** | 不支持（RocketMQ 消息不可取消） | 支持（所有 Tier 均可取消） |
| **可查询** | 无（日志散落各处） | 统一查询 + 执行日志 |
| **重启恢复** | 不恢复（Sleep 中的任务丢失） | 启动时自动恢复未过期任务 |
| **可观测性** | 无统一指标 | 6+ Prometheus 指标 + 日志 |
| **接入成本** | 每个场景自建 | 注册一行代码 |
| **DB 压力** | 全表扫描 order 表 | 索引范围扫描桶数据 |

---

## 实施计划

| 阶段 | 任务 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | 基础设施 | 平台骨架 | 2.5 |
| 1.1 | 数据模型 + DDL（`delayed_task` + `task_execution_log`） | PR: schema | 0.5 |
| 1.2 | `DelayedTaskService` 统一 API + `TierRouter` | PR: service | 1 |
| 1.3 | `TaskExecutor` + 重试逻辑 + 指标埋点 | PR: executor | 1 |
| **Phase 2** | Tier 实现 | 引擎就绪 | 3 |
| 2.1 | Tier 1 `TimeWheelTier`（Netty HashedWheelTimer 封装） | PR: tier1 | 1 |
| 2.2 | Tier 2 `RocketMQDelayedTier`（延迟消息 + 可取消封装） | PR: tier2 | 1 |
| 2.3 | Tier 3 `DBBucketTier` + `BucketScanJob`（XXL-Job 桶扫描） | PR: tier3 | 1 |
| **Phase 3** | 恢复 + 运维 | 生产就绪 | 1.5 |
| 3.1 | `TaskRecoveryJob` 启动恢复 | PR: recovery | 0.5 |
| 3.2 | Alert 规则 + Grafana 看板 | PR: monitoring | 0.5 |
| 3.3 | 运维文档 + 异常处理 SOP | Wiki | 0.5 |
| **Phase 4** | 业务接入（一期） | 3 个接入场景 | 2 |
| 4.1 | 支付超时关单接入（`PAYMENT_TIMEOUT`） | PR: payment-timeout | 0.5 |
| 4.2 | 自动确认收货接入（`CONFIRM_RECEIPT`） | PR: confirm-receipt | 0.5 |
| 4.3 | Saga 补偿重试接入（`SAGA_COMPENSATE_RETRY`） | PR: saga-retry | 0.5 |
| 4.4 | 灰度放量 + 旧代码下线 | 上线 | 0.5 |
| **Phase 5** | 业务接入（二期） | 3 个接入场景 | 2 |
| 5.1 | 库存预占释放接入（`INVENTORY_RESERVATION`） | PR: inventory | 0.5 |
| 5.2 | 退款超时自动同意接入（`REFUND_AUTO_APPROVE`） | PR: refund | 0.5 |
| 5.3 | 导出清理 + 优惠券过期接入 | PR: cleanup | 0.5 |
| 5.4 | 验证 + 收尾 | 验证 | 0.5 |

**合计：11 人天**

---

## 上线检查清单

### 基础设施
- [ ] `delayed_task` 表已创建（索引 `idx_execute_at`, `idx_status_execute`）
- [ ] `task_execution_log` 表已创建
- [ ] RocketMQ Topic 已创建（每个 `task_type` 一个 Topic，或统一 Topic）
- [ ] XXL-Job `bucketScanJob` 已配置（每分钟）
- [ ] XXL-Job `taskCleanupJob` 已配置（每天凌晨，清理 30 天前的执行日志，与 ADR-031 L3=30d 保持一致）

### 代码
- [ ] `DelayedTaskService` 统一 API 已发布
- [ ] `TimeWheelTier` 已发布（tick 100ms, 512 slots）
- [ ] `RocketMQDelayedTier` 已发布（含 `DelayedTaskConsumer`）
- [ ] `BucketScanJob` 已发布
- [ ] `TaskRecoveryJob` 已发布（`ApplicationReadyEvent` 监听）
- [ ] `PaymentTimeoutHandler` 已接入
- [ ] `ConfirmReceiptHandler` 已接入
- [ ] `SagaCompensateRetryHandler` 已接入

### 兼容性
- [ ] 支付超时关单：新订单走新平台，存量订单由旧 XXL-Job 覆盖（双跑期 3 天）
- [ ] Saga 补偿重试：新旧链路灰度，验证无重复执行
- [ ] 注册新任务前取消旧任务（`cancel` 幂等）

### 测试
- [ ] 单元测试：TierRouter 路由选择（各延迟区间命中正确的 Tier）
- [ ] 单元测试：TimeWheelTier 调度 + 取消
- [ ] 单元测试：RocketMQDelayedTier 取消标记消费端检查
- [ ] 单元测试：DBBucketTier 时间桶范围扫描正确性
- [ ] 单元测试：TaskExecutor 重试耗尽 → 标记 FAILED + P1 告警
- [ ] 集成测试：支付超时关单全链路
- [ ] 集成测试：Saga 补偿退避重试 → 成功
- [ ] 故障注入：应用重启 → 验证 TaskRecoveryJob 恢复所有 PENDING 任务
- [ ] 故障注入：Tier 1 时间轮满负荷 → 验证任务不丢失
- [ ] 压力测试：10 万 PENDING 任务在 DB → 每分钟桶扫描耗时 < 5s

### 监控
- [ ] Grafana 延迟任务看板已上线（注册速率 / 执行结果 / 各 Tier 延迟分布 / DB 桶扫描耗时 / 时间轮深度）
- [ ] TaskExecutionHighFailureRate 告警已启用（P2）
- [ ] BucketScanLatencyHigh 告警已启用（P2）
- [ ] TimeWheelTaskBacklog 告警已启用（P3）

---

## 备选方案评估

### 方案 A（选定）：分级时间轮 — Tier 1 内存时间轮 + Tier 2 RocketMQ + Tier 3 DB 桶

**优点**：
- 每个 Tier 做自己最擅长的事：短延迟用内存（高精度、高吞吐）、中延迟用 MQ（持久化、可取消）、长延迟用 DB（低成本、大批量）
- 不引入新中间件（RocketMQ 和 DB 已有，Netty 已是项目依赖）
- DB 桶设计避免全表扫描，从 O(n) 降到 O(1) 每桶

**缺点**：
- 三个 Tier 的实现和维护成本
- 跨 Tier 的一致性：任务可能在一个 Tier 失败但其他 Tier 不知道
- 路由选择是全系统的关键决策点

### 方案 B：全面使用 RocketMQ 延迟消息

**优点**：
- 架构简单，只用 MQ 一种机制
- 消息持久化，崩溃不丢失

**缺点**：
- 只有 18 级固定延迟级别，< 30s 只有 3 个级别可选（1s/5s/10s）
- 消息不可取消，需要额外 Redis 标记实现"软取消"
- RoccketMQ 延迟消息在到期前一直占用内存，大量堆积影响 Broker 稳定性
- 无法查询"当前有哪些未到期的延迟任务"

**结论**：RocketMQ 延迟消息定位为中间 Tier，不适合覆盖全场景

### 方案 C：全面使用 DB 轮询 + 内存时间轮缓存

**优点**：
- DB 持久化，不丢失
- 时间轮提供高精度

**缺点**：
- 需要自己实现所有延迟逻辑（时间轮到期后如何触发执行？）
- 时间轮只覆盖秒级，分钟级仍需 DB 轮询
- 没有 MQ 延迟消息的中级衔接

**结论**：与方案 A 相比缺少 MQ 层的缓冲，DB 轮询压力会更大

### 方案 D：引入 Redis 有序集合（ZSet）作为延迟队列

```redis
ZADD delayed:tasks <executeAtTimestamp> <taskId>
ZCARD delayed:tasks
ZRANGEBYSCORE delayed:tasks 0 <now> LIMIT 0 100
```

**优点**：
- Redis 持久化，高性能
- ZSet 范围查询天然适合延迟队列
- 支持取消（ZREM）

**缺点**：
- Redis 的 ZSet 在大规模下（百万级）的性能会劣化（ZRANGEBYSCORE 在 ZSet > 10 万时 O(log N) + M）
- Redis 内存有限，不能存储大量延迟任务（每个任务约 200 bytes，100 万 = 200MB）
- Redis 集群模式下 ZSet 跨槽位操作受限
- 缺少重试、死信等机制，需要自己实现

**结论**：Redis ZSet 适合中等规模的延迟队列（< 10 万），不适合订单中台百万级任务

---

## 与现有文档的关联

| 文档 | 关联内容 |
|------|---------|
| 状态机图 §PENDING_PAY 超时关闭 | 延迟任务 `PAYMENT_TIMEOUT` 实现 `PENDING_PAY → CLOSED` 状态迁移 |
| 状态机图 §DELIVERED 自动完成 | 延迟任务 `CONFIRM_RECEIPT` 实现 `DELIVERED → COMPLETED` 自动确认 |
| ADR-015 容量规划模型 | 补充延迟任务调度平台的容量估算（Tier 1 时间轮单机 5 万 TPS，Tier 3 桶扫描每分钟 < 100ms） |
| ADR-019 异步任务中心 | 异步导出任务的过期清理可接入延迟任务平台统一调度 |
| ADR-020 Saga 分布式事务 | Saga 补偿重试从线程 Sleep 改为 Tier 1 时间轮调度，提供持久化保证 |
| ADR-013 Canal 多分区消费 | Canal 延迟监控告警中的时间窗口检查可复用延迟任务平台的定时触发能力 |
| ADR-030 全局幂等框架 | 本 ADR 中 business_key 应用层去重（注册新任务前取消旧的）将在全局幂等框架就绪后统一接管，本平台不再维护去重逻辑 |
