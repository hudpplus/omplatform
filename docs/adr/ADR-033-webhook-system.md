# ADR-033: Webhook 系统

## 状态
已接受

## 背景

### 现状分析

**目前情况**：
- ADR-025（外部 Gateway）在应用注册页面收集了"回调 URL"字段，但从未使用
- payment-callback.puml 中出现了 `notify` 参与者（"通知服务"），但未定义其职责和实现
- 内部支付回调有规范设计（签名验证、幂等），但外部业务事件通知为零
- 第三方开发者只能通过轮询 `GET /openapi/v1/orders` 获取订单状态变更，效率低、延迟高

**存在的问题**：

1. **无推送通道**：第三方开发者无法实时接收订单状态变更（已支付、已退款、已取消等），只能轮询 API
2. **回调地址未使用**：ADR-025 的应用注册流程收集了回调 URL，但没有投递引擎使用它
3. **通知服务未定义**：多个时序图中出现的 `notify` 服务仅有概念，无具体设计
4. **无标准事件模型**：推送的事件 payload 格式、签名方式、重试策略均未定义
5. **缺少可观测性**：无法监控外部 Webhook 投递成功率、延迟、失败原因

### 目标

1. 设计 Webhook 订阅管理 API（CRUD）
2. 实现事件驱动的异步投递引擎（RocketMQ + HTTP）
3. 定义投递重试策略（指数退避 + 死信队列）
4. 设计 HMAC-SHA256 签名验证机制
5. 提供投递监控指标和失败告警

## 决策

### 方案对比：投递引擎架构

| 维度 | 方案 A：同步投递 | 方案 B：MQ 异步投递 | 方案 C：DB 轮询投递 |
|------|---------------|------------------|------------------|
| 实时性 | 最高（调用方线程投递） | 高（毫秒级 MQ） | 低（秒级轮询） |
| 业务侵入 | 高（发布方需等待投递完成） | 低（MQ 异步解耦） | 低 |
| 持久性 | 无（丢消息即丢） | 高（MQ 持久化） | 最高（DB 持久化） |
| 重试能力 | 差（同步投递失败难以重试） | 好（MQ 消费端可控重试） | 好 |
| 背压保护 | 无（业务服务直接压入） | 好（MQ 流量削峰） | 好 |
| 复杂度 | 低 | 中 | 中 |

**选择：方案 B（MQ 异步投递）+ DB 兜底**

**选型理由**：
- RocketMQ 已作为项目标准消息中间件，无需引入新组件
- 异步投递解耦业务逻辑（订单状态变更）和投递逻辑（HTTP 调用）
- MQ 消息持久化保障 at-least-once 语义
- DB `webhook_event_record` 作为投递记录兜底，支持 XXL-Job 重试

### 方案对比：重试策略

| 策略 | 最多重试次数 | 总等待时间 | 优缺点 |
|------|------------|-----------|--------|
| 固定间隔 30s | 10 | 5min | 简单但前几次可能过快重试 |
| **指数退避** | **10** | **~20min** | **快速重试瞬时失败，退避缓解持续故障** |
| 分层退避（1/5/30/300s） | 4 | ~6min | 次数太少，无法应对长时间故障 |
| 永不复试 | 0 | - | 消息丢失风险高 |

**选择：指数退避（1s/2s/4s/8s/16s/32s/1m/2m/4m/8m）× 10 次**

## 详细设计

### 1. Webhook 订阅管理

**webhook_subscription 表**：

```sql
CREATE TABLE `webhook_subscription` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `app_key`           VARCHAR(64)     NOT NULL COMMENT '应用标识（对应 ADR-025 AppKey）',
    `merchant_id`       BIGINT          NOT NULL COMMENT '商户 ID',
    `name`              VARCHAR(128)    NOT NULL COMMENT '订阅名称',
    `url`               VARCHAR(1024)   NOT NULL COMMENT '回调 URL',
    `secret`            VARCHAR(128)    NOT NULL COMMENT '签名密钥（HMAC-SHA256）',
    `events`            JSON            NOT NULL COMMENT '订阅事件列表 ["order.created","order.paid"]',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/DELETED',
    `retry_max_count`   INT             NOT NULL DEFAULT 10 COMMENT '最大重试次数',
    `retry_interval_sec` INT            NOT NULL DEFAULT 1 COMMENT '初始重试间隔（秒）',
    `description`       VARCHAR(512)    COMMENT '订阅描述',
    `gmt_create`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_app_key` (`app_key`),
    KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook 订阅表';
```

**CRUD API**：

```java
@RestController
@RequestMapping("/admin/v1/webhooks")
public class WebhookSubscriptionController {
    
    @Autowired
    private WebhookSubscriptionService subscriptionService;
    
    // 创建订阅
    @PostMapping
    @RequirePermission(role = "merchant")
    public WebhookSubscriptionResponse create(@Valid @RequestBody CreateSubscriptionRequest request) {
        // 自动生成 HMAC Secret
        String secret = generateSecret();
        WebhookSubscription subscription = subscriptionService.create(request.toEntity(secret));
        return WebhookSubscriptionResponse.from(subscription);
    }
    
    // 查询订阅列表
    @GetMapping
    public PageResult<WebhookSubscriptionResponse> list(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return subscriptionService.listByMerchant(merchantId, page, size);
    }
    
    // 更新订阅
    @PutMapping("/{id}")
    public WebhookSubscriptionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return WebhookSubscriptionResponse.from(
            subscriptionService.update(id, request));
    }
    
    // 删除订阅
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        subscriptionService.delete(id);
    }
    
    // 轮换 Secret
    @PostMapping("/{id}/rotate-secret")
    public WebhookSubscriptionResponse rotateSecret(@PathVariable Long id) {
        String newSecret = generateSecret();
        return WebhookSubscriptionResponse.from(
            subscriptionService.updateSecret(id, newSecret));
    }
}
```

**事件类型枚举**：

```java
public enum WebhookEventType {
    ORDER_CREATED("order.created", "订单创建"),
    ORDER_PAID("order.paid", "订单支付成功"),
    ORDER_REFUNDED("order.refunded", "订单退款成功"),
    ORDER_CANCELLED("order.cancelled", "订单取消"),
    ORDER_STATUS_CHANGED("order.status_changed", "订单状态变更"),
    REFUND_CREATED("refund.created", "退款申请创建"),
    REFUND_COMPLETED("refund.completed", "退款完成");
    
    private final String eventName;
    private final String description;
}
```

### 2. 事件发布 SDK

**整体流程**：

```
业务服务（order-core / payment）
  │  订单状态变更
  ▼
WebhookEventPublisher.publish(eventType, payload)
  │
  ├── 1. 查询匹配的订阅（按 eventType + ACTIVE 状态）
  ├── 2. 写入 webhook_event_record（待投递）
  └── 3. 发送到 RocketMQ Topic: webhook_dispatch
                  │
                  ▼
      WebhookDispatchConsumer（RocketMQ 消费端）
                  │
                  ├── 4. 根据 subscription_id 获取订阅配置
                  ├── 5. 构建签名 payload
                  ├── 6. HTTP POST 到回调 URL
                  ├── 7. 更新 webhook_event_record 状态
                  └── 8. 投递失败 → 投递到重试队列
```

**事件发布 SDK**：

```java
@Component
public class WebhookEventPublisher {
    
    @Autowired
    private WebhookSubscriptionMapper subscriptionMapper;
    
    @Autowired
    private WebhookEventRecordMapper eventRecordMapper;
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    /**
     * 发布 Webhook 事件。
     * 业务服务在关键状态变更后调用，业务零侵入投递逻辑。
     */
    public void publish(WebhookEventType eventType, Object payload) {
        // 1. 查询订阅了该事件类型的 ACTIVE 订阅
        List<WebhookSubscription> subscriptions = 
            subscriptionMapper.selectByEvent(eventType.name());
        
        if (subscriptions.isEmpty()) return;
        
        // 2. 为每个订阅创建投递记录
        for (WebhookSubscription sub : subscriptions) {
            WebhookEventRecord record = WebhookEventRecord.builder()
                .subscriptionId(sub.getId())
                .appKey(sub.getAppKey())
                .eventType(eventType.name())
                .payload(JSON.toJSONString(payload))
                .status(WebhookEventStatus.PENDING)
                .build();
            
            eventRecordMapper.insert(record);
            
            // 3. 发送到 MQ（异步投递）
            rocketMQTemplate.sendOneWay(
                "webhook_dispatch",
                MessageBuilder.withPayload(
                    new WebhookDispatchMessage(record.getId(), sub.getId()))
                    .build());
        }
    }
}
```

**webhook_event_record 表**：

```sql
CREATE TABLE `webhook_event_record` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT,
    `subscription_id`   BIGINT          NOT NULL COMMENT '关联订阅 ID',
    `app_key`           VARCHAR(64)     NOT NULL COMMENT '应用标识',
    `event_type`        VARCHAR(64)     NOT NULL COMMENT '事件类型',
    `payload`           LONGTEXT        NOT NULL COMMENT '事件 payload JSON',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DELIVERING/SUCCEEDED/FAILED/DLQ',
    `http_status`       INT             COMMENT '最后一次 HTTP 响应状态码',
    `response_body`     TEXT            COMMENT '最后一次响应体（截断前 1KB）',
    `retry_count`       INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_at`     DATETIME        COMMENT '下次重试时间',
    `delivered_at`      DATETIME        COMMENT '首次投递成功时间',
    `gmt_create`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_next_retry` (`status`, `next_retry_at`),
    KEY `idx_subscription` (`subscription_id`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook 投递记录表';
```

### 3. 投递引擎

**WebhookDispatchConsumer**：

```java
@Component
@RocketMQMessageListener(
    topic = "webhook_dispatch",
    consumerGroup = "webhook-dispatch-consumer",
    selectorExpression = "*"
)
public class WebhookDispatchConsumer implements RocketMQListener<WebhookDispatchMessage> {
    
    @Autowired
    private WebhookSubscriptionMapper subscriptionMapper;
    
    @Autowired
    private WebhookEventRecordMapper eventRecordMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public void onMessage(WebhookDispatchMessage message) {
        // 1. 获取订阅配置
        WebhookSubscription subscription = subscriptionMapper
            .selectById(message.getSubscriptionId());
        if (subscription == null || !"ACTIVE".equals(subscription.getStatus())) {
            return; // 订阅已删除或禁用，跳过投递
        }
        
        // 2. 获取投递记录
        WebhookEventRecord record = eventRecordMapper
            .selectById(message.getEventRecordId());
        if (record == null || record.getStatus() != WebhookEventStatus.PENDING) {
            return; // 已投递或重复消息
        }
        
        // 3. 标记为投递中
        eventRecordMapper.updateStatus(record.getId(), 
            WebhookEventStatus.PENDING, WebhookEventStatus.DELIVERING);
        
        // 4. 构建签名 payload
        WebhookPayload payload = buildPayload(record, subscription);
        
        try {
            // 5. HTTP POST
            ResponseEntity<String> response = restTemplate.postForEntity(
                subscription.getUrl(),
                createHttpEntity(payload, subscription.getSecret()),
                String.class);
            
            // 6. 2xx → 成功
            if (response.getStatusCode().is2xxSuccessful()) {
                eventRecordMapper.markSucceeded(record.getId(), 
                    response.getStatusCodeValue(), 
                    truncate(response.getBody(), 1024));
                return;
            }
            
            // 7. 非 2xx → 投递失败
            handleFailure(record, subscription, 
                response.getStatusCodeValue(), response.getBody());
            
        } catch (Exception e) {
            // 网络超时/连接失败 → 投递失败
            handleFailure(record, subscription, 0, e.getMessage());
        }
    }
    
    private void handleFailure(WebhookEventRecord record, 
            WebhookSubscription subscription, int httpStatus, String responseBody) {
        
        int nextRetryCount = record.getRetryCount() + 1;
        
        if (nextRetryCount >= subscription.getRetryMaxCount()) {
            // 超过最大重试次数 → 进入死信队列
            eventRecordMapper.markDlq(record.getId(), httpStatus, responseBody);
            MetricsCollector.increment("omplatform_webhook_dlq_total",
                Tags.of("app_key", subscription.getAppKey()));
        } else {
            // 计算下次重试时间（指数退避）
            LocalDateTime nextRetry = LocalDateTime.now()
                .plusSeconds(calculateBackoff(nextRetryCount, 
                    subscription.getRetryIntervalSec()));
            
            eventRecordMapper.markFailed(record.getId(), 
                httpStatus, responseBody, nextRetryCount, nextRetry);
        }
    }
    
    private long calculateBackoff(int retryCount, int baseIntervalSec) {
        // 指数退避：baseInterval * 2^(retryCount-1) ，上限 8 * 60s
        return Math.min(
            (long) (baseIntervalSec * Math.pow(2, retryCount - 1)),
            8 * 60L  // 最多 8 分钟
        );
    }
}
```

**超时和重试配置**：

```yaml
# 投递 HTTP 客户端配置
webhook:
  dispatch:
    connect-timeout: 3000     # 连接超时 3s
    read-timeout: 5000        # 读取超时 5s
    max-retries: 10           # 最大重试次数
    base-interval-sec: 1      # 初始退避间隔 1s
    
spring:
  rocketmq:
    consumer:
      webhook-dispatch-consumer:
        max-reconsume-times: 0  # MQ 层面不重试（由业务层控制重试）
```

### 4. 重试与死信

**投递状态流转**：

```
PENDING → DELIVERING → SUCCEEDED（成功）
                     → FAILED（失败，指数退避重试）
                         → DELIVERING（XXL-Job 或 MQ 重试）
                             → SUCCEEDED
                             → FAILED（超过 10 次 → DLQ）
                                 → DLQ（死信，需人工介入）
```

**XXL-Job 兜底重试**：

```java
@Component
public class WebhookRetryJob {
    
    @XxlJob("webhookRetryJob")
    public ReturnT<String> retryFailedWebhooks(String param) {
        // 每 5 分钟执行一次，重试到期的 FAILED 记录
        List<WebhookEventRecord> failedRecords = eventRecordMapper
            .selectByStatusAndNextRetryBefore(
                WebhookEventStatus.FAILED,
                LocalDateTime.now(),
                100); // 每批 100 条
        
        for (WebhookEventRecord record : failedRecords) {
            // 发送到 MQ 重新投递
            rocketMQTemplate.sendOneWay("webhook_dispatch",
                MessageBuilder.withPayload(
                    new WebhookDispatchMessage(record.getId(), 
                        record.getSubscriptionId()))
                    .build());
        }
        
        XxlJobHelper.log("重试 {} 条失败 Webhook", failedRecords.size());
        return ReturnT.SUCCESS;
    }
    
    @XxlJob("webhookDlqAlertJob")
    public ReturnT<String> alertDlq(String param) {
        // 每小时检查 DLQ 堆积
        int dlqCount = eventRecordMapper.countByStatus(WebhookEventStatus.DLQ);
        if (dlqCount > 100) {
            // P2 告警：死信堆积超过 100
            AlertManager.alert("webhook_dlq_overflow", 
                "Webhook DLQ 堆积: " + dlqCount + " 条");
        }
        return ReturnT.SUCCESS;
    }
}
```

### 5. 有效负载签名

**Payload 格式**：

```json
{
    "id": "evt_550e8400-e29b-41d4-a716-446655440000",
    "type": "order.paid",
    "created_at": "2026-06-12T10:30:00Z",
    "data": {
        "order_id": "ORDER20260612000001",
        "order_no": "20260612001",
        "status": "PAID",
        "paid_amount": 9999,
        "paid_at": "2026-06-12T10:30:00Z"
    }
}
```

**HTTP 请求头**：

```
POST /webhook/callback HTTP/1.1
Content-Type: application/json
User-Agent: OmPlatform-Webhook/1.0
X-Webhook-Id: evt_550e8400-e29b-41d4-a716-446655440000
X-Webhook-Event: order.paid
X-Webhook-Timestamp: 1750264200
X-Webhook-Signature: sha256=abc123def456...
```

**签名算法**：

```java
public class WebhookSignatureUtils {
    
    /**
     * 生成 HMAC-SHA256 签名。
     * 
     * 签名串 = sort(payload JSON) + "." + timestamp
     * 签名结果 = Base64(HMAC-SHA256(签名串, secret))
     */
    public static String generateSignature(String payloadJson, long timestamp, String secret) {
        // 1. 对 JSON 字段排序（确保签名一致性）
        String sortedJson = sortJsonFields(payloadJson);
        // 2. 拼接签名串
        String signContent = sortedJson + "." + timestamp;
        // 3. HMAC-SHA256
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = hmac.doFinal(signContent.getBytes(StandardCharsets.UTF_8));
        // 4. Base64 编码
        return "sha256=" + Base64.getEncoder().encodeToString(signature);
    }
    
    /**
     * 验证签名（接收方使用）。
     */
    public static boolean verifySignature(String payloadJson, long timestamp, 
            String signature, String secret) {
        String expected = generateSignature(payloadJson, timestamp, secret);
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8));
    }
}
```

**接收方验证示例文档（供第三方开发者参考）**：

```java
// 第三方接收 Webhook 的参考实现（Java）
public boolean verifyWebhook(HttpServletRequest request, String secret) {
    String payload = readBody(request);
    long timestamp = Long.parseLong(request.getHeader("X-Webhook-Timestamp"));
    String signature = request.getHeader("X-Webhook-Signature");
    
    // 1. 时间戳校验（±5min，防重放）
    if (Math.abs(System.currentTimeMillis() / 1000 - timestamp) > 300) {
        return false; // 已过期
    }
    
    // 2. HMAC 签名验证
    return WebhookSignatureUtils.verifySignature(payload, timestamp, signature, secret);
}
```

### 6. 幂等投递

**at-least-once 语义**：

```java
/**
 * Webhook 投递保证 at-least-once 语义。
 * 
 * - 投递引擎写入 webhook_event_record 后发送 MQ 消息
 * - MQ 消息消费失败时，XXL-Job 兜底重试
 * - 接收方必须使用 X-Webhook-Id 实现幂等
 * 
 * 接收方幂等建议（文档提供给第三方开发者）：
 *   1. 从 X-Webhook-Id Header 提取投递 ID
 *   2. 在本地建立 webhook_id 去重表或缓存
 *   3. 首次处理 → INSERT webhook_id 表 → 执行业务逻辑
 *   4. 重复投递 → 根据 webhook_id 判断已处理 → 返回 200 OK
 */
```

**X-Webhook-Id 生成**：

```java
// 投递 ID 格式：webhook_event_record 的 ID + 重试次数
// 同一事件的不同重试有不同 X-Webhook-Id
public String generateWebhookId(Long eventRecordId, int retryCount) {
    return "wh_" + eventRecordId + "_r" + retryCount;
}
```

### 7. 告警与监控

**Prometheus 指标**：

```java
@Configuration
public class WebhookMetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> webhookMetrics() {
        return registry -> {
            // 投递总数
            Counter.builder("omplatform_webhook_dispatch_total")
                .tags("event_type", "unknown", "app_key", "unknown")
                .register(registry);
            
            // 投递成功数
            Counter.builder("omplatform_webhook_success_total")
                .tags("event_type", "unknown", "app_key", "unknown")
                .register(registry);
            
            // 投递失败数
            Counter.builder("omplatform_webhook_failure_total")
                .tags("event_type", "unknown", "app_key", "unknown", 
                      "reason", "timeout")
                .register(registry);
            
            // 投递延迟
            Timer.builder("omplatform_webhook_dispatch_duration_ms")
                .publishPercentiles(0.5, 0.9, 0.99)
                .tags("event_type", "unknown", "app_key", "unknown")
                .register(registry);
            
            // 重试次数分布
            Gauge.builder("omplatform_webhook_retry_count", () ->
                getRetryCountDistribution())
                .tags("app_key", "unknown")
                .register(registry);
            
            // DLQ 深度
            Gauge.builder("omplatform_webhook_dlq_depth", () ->
                eventRecordMapper.countByStatus(WebhookEventStatus.DLQ))
                .register(registry);
        };
    }
}
```

**指标矩阵**：

| 指标名称 | 类型 | 标签 | 说明 |
|---------|------|------|------|
| `omplatform_webhook_dispatch_total` | Counter | event_type, app_key | 投递总数 |
| `omplatform_webhook_success_total` | Counter | event_type, app_key | 投递成功数 |
| `omplatform_webhook_failure_total` | Counter | event_type, app_key, reason | 投递失败数 |
| `omplatform_webhook_dispatch_duration_ms` | Timer | event_type, app_key | 投递延迟 |
| `omplatform_webhook_retry_count` | Gauge | app_key | 平均重试次数 |
| `omplatform_webhook_dlq_depth` | Gauge | - | 死信队列深度 |

**Grafana 面板**：

```
Webhook 投递大盘：
  ┌─────────────────────────────────────────────────────┐
  │  Webhook 投递总览                                    │
  ├────────────┬──────────┬──────────┬───────────────────┤
  │  总投递量   │ 成功率    │ 平均延迟   │ DLQ 深度          │
  │  1,234,567  │ 99.87%   │ 245ms     │ 23                │
  ├────────────┴──────────┴──────────┴───────────────────┤
  │  按 AppKey 的投递成功率（柱状图）                      │
  │  ┃████████████████████████████████████━━━ 99.9% app_a │
  │  ┃████████████████████████████━━━━━━━━━ 95.2% app_b │
  │  ┃████████████████████████━━━━━━━━━━━━━ 88.5% app_c │
  ├─────────────────────────────────────────────────────┤
  │  失败原因分布（饼图）                                  │
  │  ● 超时 45%  ● 5xx 30%  ● 连接失败 15%  ● 其他 10%  │
  ├─────────────────────────────────────────────────────┤
  │  DLQ 趋势（时间序列）                                  │
  │  ╱╲        ╱╲         ╱╲                             │
  │ ╱  ╲  ╱╲  ╱  ╲  ╱╲  ╱  ╲                            │
  │╱    ╲╱  ╲╱    ╲╱  ╲╱    ╲                           │
  └─────────────────────────────────────────────────────┘
```

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | Webhook 订阅管理：表 DDL + CRUD API + Secret 生成 | 1d | 订阅管理 API |
| P2 | 事件发布 SDK：WebhookEventPublisher + webhook_event_record 表 | 0.5d | 发布 SDK |
| P3 | 投递引擎：WebhookDispatchConsumer + HTTP 投递 + 签名 | 1.5d | 投递引擎 |
| P4 | 重试策略：指数退避 + DLQ + XXL-Job 兜底重试 | 0.5d | 重试 Job |
| P5 | Webhook 监控：指标注册 + Grafana 面板 + DLQ 告警 | 0.5d | 监控 |
| P6 | 开发者文档：签名验证示例（Java/Python/PHP）+ 幂等建议 | 0.5d | 开发者文档 |

**合计**：4.5 人天

## 上线检查清单

- [ ] 基础设施：`webhook_subscription` + `webhook_event_record` 表 DDL 执行
- [ ] 基础设施：RocketMQ Topic `webhook_dispatch` 创建
- [ ] 基础设施：Apollo 命名空间 `webhook.config` 配置
- [ ] 代码：Webhook 订阅 CRUD API（POST/GET/PUT/DELETE）
- [ ] 代码：WebhookEventPublisher（业务服务调用入口）
- [ ] 代码：WebhookDispatchConsumer（RocketMQ 消费端）
- [ ] 代码：HMAC-SHA256 签名 + 验证工具类
- [ ] 代码：指数退避重试逻辑（最大 10 次，上限 8min）
- [ ] 代码：XXL-Job webhookRetryJob（每 5 分钟）
- [ ] 代码：XXL-Job webhookDlqAlertJob（每小时）
- [ ] 监控：`omplatform_webhook_` 指标注册
- [ ] 监控：DLQ > 100 → P2 告警已配置
- [ ] 监控：投递成功率 < 95% → P1 告警已配置
- [ ] 文档：开发者签名验证示例（Java/Python/PHP）
- [ ] 文档：X-Webhook-Id 幂等建议
- [ ] 兼容性：外部 Gateway（ADR-025）应用注册中的回调 URL 字段关联 webhook_subscription

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-025** (外部 Gateway) | 应用注册的回调 URL 字段关联到 webhook_subscription；AppKey 作为订阅标识；开发者门户新增 Webhook 配置页面 |
| **payment-callback.puml** | 时序图中的 `notify` 参与者具体化为 Webhook Dispatch Consumer；区分内部支付回调（Dubbo）和外部 Webhook（HTTP） |
| **ADR-030** (幂等框架) | Webhook 使用 `X-Webhook-Id` 配合幂等框架 Idempotency-Key 实现接收方幂等 |
| **ADR-027** (可观测性) | Webhook 投递指标纳入 `omplatform_` 前缀规范；Grafana 大盘增加 Webhook 面板 |
| **ADR-028** (密钥管理) | Webhook Secret 的存储和分发纳入 ADR-028 密钥管理体系 |
| **ADR-010** (事件 Schema) | Webhook payload 使用 ADR-010 已定义的事件模型（OrderCreatedEvent、OrderPaidEvent 等） |
| **ADR-031** (数据归档) | Webhook 投递记录（webhook_event_record）纳入数据生命周期管理（推荐保留 30 天） |
| **ADR-029** (内部 Gateway) | Webhook 的回调 HTTP 请求经内部 Gateway 出站 |

## 备选方案评估

### 方案 A：同步投递

业务服务直接 HTTP POST 到订阅 URL，等待响应。

- **优点**：实现最简单，无 MQ 依赖
- **缺点**：外部投递慢 → 阻塞订单状态更新；外部故障 → 导致业务服务线程堆积；丢消息风险
- **适用场景**：低吞吐、对实时性要求极高的场景

### 方案 C：DB 轮询投递

定时扫描 `webhook_event_record` 表，将待投递记录取出后 HTTP 投递。

- **优点**：数据持久化最可靠；无需 MQ
- **缺点**：轮询延迟（秒级）；DB 压力大（高频扫描）；需自行处理并发和锁
- **适用场景**：MQ 不可用时的降级方案
