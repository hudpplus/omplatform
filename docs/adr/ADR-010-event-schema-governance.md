# ADR-010：领域事件 Schema 治理

## 状态

已接受

## 背景

订单中台各服务通过 RocketMQ 领域事件进行异步通信。当前实践中，事件直接发布 Java POJO 序列化后的 JSON 字节，生产者和消费者通过 Code Review 约定事件结构。

### 存在的问题

**问题 1：消费者兼容性靠人肉保障**

```java
// v1 发布的事件（order-core）
public class OrderCreatedEvent {
    private String orderId;
    private Long buyerId;
    private Long payAmount;
}

// v2 生产者删除了 payAmount（以为没用了）
public class OrderCreatedEvent {
    private String orderId;
    private Long buyerId;
    // payAmount 被删除了
}

// 消费者反序列化时 payAmount = null，下游对账系统悄无声息地漏掉了金额
```

**问题 2：无法知晓谁在消费什么字段**

- 生产者不知道消费者依赖了哪些字段
- 加字段看似安全，但消费者可能在用 `JSONObject` 全量转存，新增字段可能引入 PII 泄露
- 删字段/改类型时无法准确评估影响面

**问题 3：消费方崩溃没有提前发现**

- 生产者上线新版本 → 消费者反序列化失败 → 消费阻塞 → 消息堆积 → 报警 → 回滚
- 整个过程在线上发生，没有在 CI 阶段拦截

**问题 4：缺少事件存储和回溯能力**

- 事件只经过 MQ，消费即丢弃，没有归档存储
- 问题排查时需要重现事件流，只能靠猜测
- 数据对账缺少事件层面的审计轨迹

---

## 决策

采用 **Apollo + JSON Schema 轻量方案**（方案 A），不引入 Avro / Confluent Schema Registry。

### 理由

| 维度 | 方案 A：Apollo + JSON Schema | 方案 B：Avro + Confluent SR |
|------|-------------|--------------------------|
| **基础设施** | Apollo 已存在，零新增 | 需要部署 Schema Registry 集群 + 改造序列化层 |
| **团队认知** | JSON + Schema 学习成本低 | Avro Schema + Maven 插件 + 序列化改造，学习曲线陡 |
| **现有代码兼容** | 对现有 POJO + Jackson 零侵入 | 需要生成 Avro 类、替换序列化方式 |
| **可读性** | JSON Schema 人类可读，排查问题时直接看消息体 | Avro 二进制序列化，排查问题需要反编码 |
| **调试便利** | RocketMQ 控制台直接看消息内容 | 需要 Schema Registry + Avro 反序列化器才能看内容 |
| **性能** | JSON 序列化（优于 Avro 的场景少） | Avro 二进制更紧凑，压缩比高 |
| **schema兼容性校验** | 自定义 CI 检查 + Apollo 启动校验 | 内置兼容性校验（兼容模式可配置） |

**核心判断**：订单中台的领域事件 TPS 不高（日均百万订单，分摊到事件维度约 10-50 TPS），性能不是瓶颈。现有 JSON + Jackson 基础设施成熟，改造为 Avro 的收益不足以覆盖迁移成本。Apollo 已有配置灰度、环境隔离、权限管理能力，复用它管理 Schema 最经济。

---

## 详细设计

### 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Schema 管理平面                               │
│                                                                     │
│  Apollo 配置中心（Schema 存储）                                       │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  Namespace: schema.order-events                             │     │
│  │  Key: OrderCreatedEvent  → { JSON Schema v1, v2, v3 }     │     │
│  │  Key: OrderPaidEvent     → { JSON Schema v1, v2 }          │     │
│  │  Key: OrderRefundedEvent → { JSON Schema v1 }              │     │
│  └────────────────────────────────────────────────────────────┘     │
│           ↑ 写入（CI/CD）              ↓ 读取（服务启动时）            │
│  ┌──────────────────────┐    ┌─────────────────────────────────┐    │
│  │ CI 流水线 - Schema 校验 │    │ 各服务 SchemaValidator         │    │
│  │  PR 触发 → 兼容性检查   │    │ 启动时校验消费者支持版本              │    │
│  └──────────────────────┘    └─────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         事件数据平面                                  │
│                                                                     │
│  生产者（order-core）                                                │
│    ┌──────────────────────────────────────────────┐                 │
│    │  new OrderCreatedEvent(order)                │                 │
│    │  → 自动注入 schemaVersion: 2                  │                 │
│    │  → Jackson 序列化 → RocketMQ                 │                 │
│    └──────────────────────────────────────────────┘                 │
│                           │                                         │
│                     RocketMQ Topic: order_event                     │
│                           │                                         │
│                           ├──────────────────────┐                  │
│                           ▼                      ▼                  │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐ │
│  │ 消费者 A（order-query）       │  │ 消费者 B（通知服务）          │ │
│  │ 声明最小版本: v1              │  │ 声明最小版本: v2              │ │
│  │ 启动校验：v2 兼容 v1 ✓       │  │ 启动校验：v2 兼容 v2 ✓       │ │
│  └──────────────────────────────┘  └──────────────────────────────┘ │
│                                                                     │
│                           ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │ 事件归档服务（Event Archiver）                                   ││
│  │ 消费所有领域事件 → OceanBase 事件归档表 + 附带 Schema 版本号      ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

### 2. Schema 格式定义

使用 JSON Schema Draft-07，每个事件结构独立定义。

```json
// Apollo Namespace: schema.order-events
// Key: OrderCreatedEvent

{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "OrderCreatedEvent",
  "description": "订单创建成功事件",
  "schemaVersion": 2,
  "history": [
    { "version": 1, "since": "2026-06-01", "changes": ["初始版本"] },
    { "version": 2, "since": "2026-07-01", "changes": [
      "新增 businessType 字段",
      "删除 obsoleteFlag 字段",
      "payAmount 改为 optional（兼容后续拆分支付场景）"
    ]}
  ],
  "type": "object",
  "required": ["orderId", "buyerId", "payAmount", "eventTime"],
  "properties": {
    "orderId": {
      "type": "string",
      "description": "订单ID",
      "examples": ["20260610123456789"]
    },
    "orderNo": {
      "type": "string",
      "description": "订单号（展示用）",
      "examples": ["202606101234567"]
    },
    "buyerId": {
      "type": "integer",
      "description": "买家ID",
      "minimum": 1
    },
    "businessType": {
      "type": "string",
      "description": "业务类型",
      "since": 2,
      "enum": ["ecommerce", "local_life", "b2b"],
      "examples": ["ecommerce"]
    },
    "payAmount": {
      "type": "integer",
      "description": "实付金额（分）",
      "minimum": 0
    },
    "orderStatus": {
      "type": "string",
      "description": "订单状态",
      "enum": ["PENDING_PAY", "PAID"]
    },
    "eventTime": {
      "type": "string",
      "format": "date-time",
      "description": "事件发生时间"
    },
    "tags": {
      "type": "array",
      "description": "订单标签",
      "since": 2,
      "items": { "type": "string" }
    },
    "obsoleteFlag": {
      "type": "string",
      "description": "已废弃字段",
      "deprecated": { "version": 2, "message": "不再使用，将在 v3 移除" },
      "until": 3
    }
  }
}
```

**Schema 字段注解说明**：

| JSON Schema 扩展属性 | 含义 | 兼容性影响 |
|---------------------|------|-----------|
| `since` | 从哪个版本开始引入 | 无（新增 optional 字段向前兼容） |
| `deprecated` | 标记为废弃，但仍在 | 无（消费者应停止依赖） |
| `until` | 在哪个版本会物理移除 | BREAKING（consumer 必须在此版本前迁移） |

### 3. 兼容性规则（CI 自动校验）

将兼容性规则编码为自动化检查脚本，在 MR 阶段运行。

```
兼容性检查矩阵（Schema vN → vN+1）:

┌─────────────────────┬──────────┬──────────┬──────────┐
│ 变更操作             │ 向前兼容  │ 向后兼容  │ CI 阻断  │
├─────────────────────┼──────────┼──────────┼──────────┤
│ 新增 optional 字段   │ ✅       │ ✅       │ ❌ 不阻断 │
│ 新增 required 字段   │ ❌       │ ✅       │ 🚫 阻断  │
│ 删除字段             │ ❌       │ ❌       │ 🚫 阻断  │
│ 修改字段类型          │ ❌       │ ❌       │ 🚫 阻断  │
│ 扩大枚举值集合        │ ✅       │ ✅       │ ❌ 不阻断 │
│ 缩小枚举值集合        │ ❌       │ ❌       │ 🚫 阻断  │
│ 标记字段 deprecated  │ ✅       │ ✅       │ ❌ 不阻断 │
│ 缩小字段长度限制      │ ❌       │ ❌       │ 🚫 阻断  │
│ 扩大字段长度限制      │ ✅       │ ✅       │ ❌ 不阻断 │
│ 修改字段 description │ ✅       │ ✅       │ ❌ 不阻断 │
└─────────────────────┴──────────┴──────────┴──────────┘

向前兼容（Forward Compatible）：新 Schema 可以被旧消费者读取
向后兼容（Backward Compatible）：旧 Schema 可以被新消费者读取

订单中台要求：至少保证向前兼容（新版本生产者发出的消息，
老版本消费者不会崩溃）。向后兼容靠消费者声明最低版本保障。
```

### 4. 生产者 SDK 设计

```java
/**
 * 所有领域事件的基类。
 * schemaVersion 由框架自动注入，开发人员不感知。
 */
public abstract class AbstractDomainEvent {
    /** schemaVersion 字段由框架在序列化时自动注入 */
    private int schemaVersion;
    
    /** 事件唯一ID（UUID），用于去重和归档 */
    private String eventId;
    
    /** 事件发生时间 */
    private Instant eventTime;
    
    /** 来源服务 */
    private String sourceService;
    
    public AbstractDomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.eventTime = Instant.now();
        this.sourceService = ServiceContext.getServiceName();
    }
    
    /**
     * 子类通过注解声明版本号。
     * 框架自动读取 @EventSchema 注解填充 schemaVersion。
     */
    public int getSchemaVersion() {
        EventSchema schema = getClass().getAnnotation(EventSchema.class);
        return schema != null ? schema.version() : 1;
    }
}

/**
 * 事件 Schema 元信息注解。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventSchema {
    /** Schema 版本号，与 Apollo 中存储的 schemaVersion 一致 */
    int version();
    
    /** 事件描述 */
    String description() default "";
    
    /** 所属领域 */
    String domain() default "";
}

// ========== 使用示例 ==========

@EventSchema(version = 2, description = "订单创建成功事件", domain = "order")
public class OrderCreatedEvent extends AbstractDomainEvent {
    private String orderId;
    private String orderNo;
    private Long buyerId;
    private String businessType;   // since v2
    private Long payAmount;
    private String orderStatus;
    private List<String> tags;     // since v2
    private String obsoleteFlag;   // deprecated since v2, will remove at v3
    
    // ... getters / setters
}

/**
 * 事件发布器（注入 Spring 容器使用）。
 * 
 * 自动完成：
 * 1. 注入 schemaVersion
 * 2. 序列化为 JSON
 * 3. 发送到 RocketMQ（携带 schemaVersion 在消息头部）
 * 4. 异步写入事件归档表
 */
@Component
public class DomainEventPublisher {
    
    private final RocketMQTemplate rocketMQTemplate;
    private final ApolloClient apolloClient;
    
    public void publish(AbstractDomainEvent event) {
        // 1. 注入 schemaVersion
        event.setSchemaVersion(event.getSchemaVersion());
        
        // 2. 序列化
        String json = JsonUtils.toJson(event);
        
        // 3. 发送，Schema 版本号放在消息 header 中
        Message<String> message = MessageBuilder
            .withPayload(json)
            .setHeader("schemaVersion", event.getSchemaVersion())
            .setHeader("eventType", event.getClass().getSimpleName())
            .setHeader("eventId", event.getEventId())
            .build();
        
        rocketMQTemplate.send("order_event:" + getEventTag(event), message);
        
        // 4. 异步写入事件归档（不阻塞主流程）
        eventArchiver.archiveAsync(event);
    }
}
```

### 5. 消费者 SDK 设计

```java
/**
 * 消费者基类。继承该类即可获得 Schema 校验 + 版本兼容性保障。
 */
public abstract class SchemaAwareConsumer<T extends AbstractDomainEvent> {
    
    private final Class<T> eventClass;
    private final int minSupportedVersion;
    private final SchemaValidator schemaValidator;
    
    protected SchemaAwareConsumer(Class<T> eventClass, int minSupportedVersion) {
        this.eventClass = eventClass;
        this.minSupportedVersion = minSupportedVersion;
        this.schemaValidator = SchemaValidatorFactory.getValidator(eventClass);
    }
    
    /**
     * 框架层统一的反序列化入口。
     * 在调用业务 handle() 之前完成 Schema 校验。
     */
    @RocketMQMessageListener(topic = "order_event", consumerGroup = "${consumer.group}")
    public void onMessage(MessageExt message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        int msgSchemaVersion = Integer.parseInt(
            message.getProperty("schemaVersion"));
        
        // 1. Schema 版本兼容性检查
        if (msgSchemaVersion < minSupportedVersion) {
            // 消费者声明的最低版本 > 消息中的版本
            // 说明消费者依赖了新字段，但消息中没有
            // 理论上不会发生（兼容性原则保证），兜底处理
            log.error("Message schema version {} < consumer min version {}, msgId={}",
                msgSchemaVersion, minSupportedVersion, message.getMsgId());
            throw new SchemaViolationException("Message version too old");
        }
        
        // 2. JSON Schema 校验（生产环境可通过 Apollo 开关关闭以提升性能）
        if (schemaValidator.isEnabled()) {
            List<String> errors = schemaValidator.validate(json, msgSchemaVersion);
            if (!errors.isEmpty()) {
                log.warn("Schema validation failed for event: {}", errors);
                // 校验失败不阻塞消费，记录报警
                // 因为严格模式下应该在 CI 阶段拦截
            }
        }
        
        // 3. 反序列化为 POJO
        T event = JsonUtils.fromJson(json, eventClass);
        
        // 4. 调用业务处理
        handle(event);
    }
    
    /**
     * 子类实现业务逻辑。
     * 此时 event 已经过 Schema 校验，可安全使用字段。
     */
    protected abstract void handle(T event);
}

// ========== 使用示例 ==========

@Component
public class OrderCreatedEventConsumer 
        extends SchemaAwareConsumer<OrderCreatedEvent> {
    
    // 声明该消费者最低支持 v1 版本
    public OrderCreatedEventConsumer() {
        super(OrderCreatedEvent.class, 1);
    }
    
    @Override
    protected void handle(OrderCreatedEvent event) {
        // 这里可以安全使用所有字段
        // businessType 虽然 v2 才引入，但本消费者声明了 minVersion=1
        // 框架保证了消息中一定包含此字段需要的兼容性
        // 但具体业务代码仍需注意：@Since(v2) 的字段在 v1 消息中不存在
        String orderId = event.getOrderId();
        Long payAmount = event.getPayAmount();
        // ...
    }
}
```

### 6. CI 流水线集成

在 `.gitlab-ci.yml` 中新增阶段：

```yaml
stages:
  - ...（之前阶段）
  - schema-check    # 新增：Schema 兼容性检查
  - ...（之后阶段）

schema-check:
  stage: schema-check
  script:
    # 步骤 1：提取本 MR 中变更的领域事件类
    - changed_events=$(git diff --name-only origin/develop...HEAD
        -- '**/domain/event/**' | grep 'Event.java$' || true)
    
    # 步骤 2：解析 @EventSchema 注解生成新的 JSON Schema
    - java -jar schema-tools.jar extract
        --input-dir=src/main/java
        --output-dir=target/schemas
    
    # 步骤 3：从 Apollo 拉取线上 Schema（CI 环境有 Apollo 只读权限）
    - java -jar schema-tools.jar fetch
        --namespace=schema.order-events
        --output-dir=target/remote-schemas
    
    # 步骤 4：执行兼容性检查
    - java -jar schema-tools.jar validate
        --local-dir=target/schemas
        --remote-dir=target/remote-schemas
        --rules=FORWARD_COMPATIBLE
    
    # 步骤 5：检查所有消费者是否满足其声明的最低版本
    - java -jar schema-tools.jar check-consumers
        --schemas-dir=target/schemas
        --source-dir=src/main/java
    
  only:
    - merge_requests
  except:
    variables:
      - $CI_MERGE_REQUEST_TITLE =~ /^docs/
```

CI 检查的输出示例：

```
========================================
 Schema Compatiblity Check Report
========================================

 Event: OrderCreatedEvent  v2 → v3
 ┌──────────────────────────────────────────────┬────────┐
 │ 检查项                                        │ 结果   │
 ├──────────────────────────────────────────────┼────────┤
 │ 新增 optional 字段: sourceChannel            │ ✅     │
 │ 标记废弃字段: obsoleteFlag                   │ ✅     │
 │ 向前兼容（Forward Compatible）                │ ✅     │
 │ 向后兼容（Backward Compatible）               │ ✅     │
 └──────────────────────────────────────────────┴────────┘

 Consumer Compatibility:
 ┌────────────────────────────────────┬─────────┬──────────┬────────┐
 │ Consumer                          │ 当前声明  │ 消息最新  │ 结果   │
 ├────────────────────────────────────┼─────────┼──────────┼────────┤
 │ OrderCreatedEventConsumer(查询端)  │ v1      │ v3       │ ✅     │
 │ OrderCreatedEventConsumer(通知)    │ v2      │ v3       │ ✅     │
 │ OrderPaidEventConsumer(库存)       │ v1      │ v2       │ ✅     │
 └────────────────────────────────────┴─────────┴──────────┴────────┘

 Result: ✅ PASSED
```

### 7. 事件归档设计

**用途**：
- 问题排查：直接查归档表代替拦截 MQ 消息
- 数据对账：归档事件 + 下游消费结果对比
- 审计：满足等保三级审计日志永久保留要求

```sql
-- OceanBase 事件归档表（按月分区）

CREATE TABLE `event_archive` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `event_id`     VARCHAR(64)  NOT NULL COMMENT '事件唯一ID',
    `event_type`   VARCHAR(128) NOT NULL COMMENT '事件类型（全类名）',
    `schema_version` INT        NOT NULL DEFAULT 1 COMMENT '事件 Schema 版本',
    `source_service` VARCHAR(64) NOT NULL COMMENT '来源服务',
    `trace_id`     VARCHAR(64)  DEFAULT NULL COMMENT '链路 TraceID',
    `order_id`     VARCHAR(64)  DEFAULT NULL COMMENT '关联订单ID（冗余便于查询）',
    `payload`      JSON         NOT NULL COMMENT '事件原始 JSON 载荷',
    `gmt_create`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`, `gmt_create`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_event_type` (`event_type`, `gmt_create`),
    KEY `idx_order_id` (`order_id`, `gmt_create`),
    KEY `idx_source_service` (`source_service`, `gmt_create`)
) PARTITION BY RANGE (UNIX_TIMESTAMP(`gmt_create`))
  SUBPARTITION BY HASH(`event_type`) SUBPARTITIONS 8 (
    PARTITION p2026q1 VALUES LESS THAN (1740873600),
    PARTITION p2026q2 VALUES LESS THAN (1743552000),
    PARTITION p2026q3 VALUES LESS THAN (1746230400),
    PARTITION p2026q4 VALUES LESS THAN (1748908800),
    PARTITION p_future VALUES LESS THAN MAXVALUE
  )
COMMENT '领域事件归档表 — 用于排查/对账/审计，保留 180 天后归档到对象存储';

-- 归档数据查询示例
-- 排查某个订单的事件流：
SELECT event_type, schema_version, payload, gmt_create
FROM event_archive
WHERE order_id = '20260610123456789'
  AND gmt_create >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY gmt_create ASC;
```

**归档写入策略**：

```
生产者同步写入 vs 异步写入？

决策：异步写入（RocketMQ 单独消费）

理由：
  - 事件发布主流程不能因为归档写入而阻塞
  - 使用 RocketMQ Topic: event_archive，单独的事件归档服务消费
  - 归档服务写入 OceanBase（批量 batch insert，每批 500 条）
  - 归档失败不影响主流程（打日志 + 延迟重试 + 死信人工处理）

Event Archiver 独立服务：
  @Component
  public class EventArchiver {
      
      // 消费所有领域事件，写入归档表
      @RocketMQMessageListener(topic = "event_archive",
                               consumerGroup = "event-archiver")
      public void onEvent(Message message) {
          // 批量写入，每 500 条 flush 一次
          // 或者每 5s flush 一次
          archiveBatch(message);
      }
  }
```

---

## 实施步骤

### Step 1：基础设施搭建（2 天）

1. 定义所有领域事件的 JSON Schema（约 10-15 个事件）
2. Schema 上传到 Apollo（Namespace: `schema.order-events`）
3. 开发 Schema Validator 工具（启动时从 Apollo 拉取 Schema 校验）
4. 开发 CI Schema 检查脚本

### Step 2：SDK 改造（3 天）

1. 定义 `AbstractDomainEvent` 基类 + `@EventSchema` 注解
2. 改造 `DomainEventPublisher`：自动注入 schemaVersion、异步归档
3. 改造每个消费者继承 `SchemaAwareConsumer`：声明 minSupportedVersion
4. 现有事件 POJO 添加 `@EventSchema` 注解

### Step 3：事件归档（2 天）

1. 创建 `event_archive` 表（DDL 纳入 Liquibase 管理）
2. 开发 Event Archiver 服务（消费归档 Topic + 批量写入）
3. 生产者改造：在 `publish()` 中同时发送到 `event_archive` Topic

### Step 4：CI 集成（1 天）

1. CI Runner 配置 Apollo 只读权限
2. 新增 `schema-check` CI stage
3. 评估兼容性规则，初始阶段设置为 warning 级别（不阻断，仅通知）
4. 运行 2 周稳定后改为 blocking 级别

### Step 5：灰度切换（1 天）

1. 新版本生产者、消费者灰度上线
2. 验证 schemaVersion 自动注入正确
3. 验证消费者兼容性检测正常工作
4. 确认归档数据正常写入

---

## 兼容性说明与降级策略

| 场景 | 处理方式 |
|------|---------|
| Apollo 宕机，Schema 拉取失败 | 使用本地缓存的上次有效 Schema（Apollo Client 自带本地缓存快照） |
| Schema 校验发现不兼容变更 | CI 阻断；紧急情况跳过 Schema 检查可加 `[skip-schema]` 在 commit 中 |
| 消费者启动时 minVersion 校验失败 | 打印 ERROR 日志 + 启动报警 + 拒绝启动（Fail Fast） |
| 归档写入 OceanBase 失败 | 归档服务重试 3 次 → 死信队列（不影响主事件流） |
| 历史事件没有 schemaVersion | 默认视为 v1 |

---

## 事件治理收益总结

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| 新增事件字段 | Code Review 人工检查消费者 | CI 自动校验兼容性 + 消费者版本声明 |
| 废弃事件字段 | 不敢删，留下大量 @Deprecated 死代码 | 明确标记 until 版本，到期自动移除 |
| 新开发排查问题 | 翻代码 + 猜事件结构 | 直接查归档表 + Schema 文档 |
| 事件对账 | 人工写 SQL | 归档表 + 标准化字段 |
| 合规审计 | 没有事件层面的审计痕迹 | 事件永久归档，可追溯 |
| 新同学上手 | 找代码中的消费者一个个读 | 读 Schema 即可了解事件结构和消费方 |
