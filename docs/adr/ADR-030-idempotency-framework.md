# ADR-030: 全局幂等框架

## 状态
已接受

## 背景

### 现状分析

当前架构中幂等逻辑分散在多个独立设计中，缺少统一的标准和框架：

| 位置 | 幂等方式 | 范围 | 问题 |
|------|---------|------|------|
| ADR-020 Saga | `idempotent_record` 表，saga_id + step_name 唯一 key | Saga 步骤 | 仅 Saga 场景，与其他幂等互不兼容 |
| ADR-025 外部 Gateway | Nonce Redis `SET NX EX 300` | 防重放（5min） | 防重放而非业务幂等，不缓存响应结果 |
| ADR-021 延迟任务 | `business_key` 唯一索引 | 延迟任务注册 | 仅限延迟任务场景 |
| ADR-010 事件 Schema | `eventId` UUID | 事件消费 | 消费端自行去重，无标准 SDK |
| payment-callback.puml | `payment_transaction_id` 唯一索引 | 支付回调 | 单表索引，crash 后有丢失风险 |
| ADR-016 缓存 | CAS + Lua 脚本 | 库存扣减 | 特定场景，不可通用 |

**存在的问题**：

1. **标准缺失**：没有统一的幂等 Key 请求头、响应语义、后端存储选型标准
2. **碎片化**：6 种不同幂等方式各自维护，新业务接入需重新理解每种方式
3. **缺少 Gateway 层拦截**：幂等 Key 只在业务层处理，Gateway 无法在入口处去重
4. **无 Dubbo 幂等拦截器**：ADR-020 中规划的 Dubbo 幂等拦截器仅有概念，无设计
5. **响应语义不统一**：重复请求有时返回 200，有时 409，有时 500（唯一索引冲突）

### 目标

1. 定义统一的 `Idempotency-Key` 请求头标准和响应语义
2. 设计 Gateway 层幂等拦截机制
3. 设计 `@Idempotent` 注解 + AOP 切面 + Dubbo Provider Filter
4. 定义 Redis + DB 双存储架构
5. 制定存量系统迁移策略

## 决策

### 方案对比：幂等后端存储

| 维度 | 方案 A：纯 Redis | 方案 B：Redis + DB 双写 | 方案 C：纯 DB 唯一索引 |
|------|-----------------|----------------------|---------------------|
| 性能 | 最快（1-2ms） | 中（Redis + 异步 DB） | 慢（10-50ms） |
| 持久性 | 低（Redis 重启丢数据） | 高（DB 兜底） | 最高 |
| TTL 管理 | 原生支持 | Redis TTL + DB 异步清理 | 需 XXL-Job 定时清理 |
| 复杂度 | 低 | 中 | 低 |
| 响应缓存 | 支持（缓存响应体） | 支持 | 不支持（需二次查询） |
| 一致性 | 最终一致 | 最终一致（先写 Redis） | 强一致 |

**选择：方案 B（Redis + DB 双写）**

**选型理由**：
- Redis `SET NX EX TTL` 提供微秒级幂等判断，适合高频写入场景
- DB `idempotent_record` 唯一索引作为持久化兜底，防止 Redis 丢失后重复处理
- Redis 可缓存首次成功响应，重复请求直接返回缓存结果（无需转发到后端）
- TTL 自动过期 Redis key，DB 记录由 XXL-Job 异步清理

### 方案对比：幂等 Key 来源

| 维度 | 方案 A：客户端提供 Idempotency-Key | 方案 B：服务端自动按参数 hash | 方案 C：客户端 Key + 服务端 fallback |
|------|----------------------------------|-----------------------------|------------------------------------|
| 客户端适配 | 需要改造 | 无需改造 | 优先客户端，无则自动 |
| 幂等语义 | 客户端控制 | 服务端控制 | 灵活 |
| 风险 | 客户端不传 Key 则无幂等 | 参数相同即幂等（可能误判） | 最佳平衡 |

**选择：方案 C（客户端 Idempotency-Key + 服务端自动 hash fallback）**

## 详细设计

### 1. Idempotency-Key 规范

**请求头定义**：

```
Idempotency-Key: <UUID v4>
```

**Key 组成规则**：

```
幂等 Key = {method}:{path}:{idempotency_key}
          POST:/api/v1/orders:550e8400-e29b-41d4-a716-446655440000
```

- `method`：HTTP 方法（POST/PUT/PATCH）
- `path`：请求路径（不含 query string）
- `idempotency_key`：客户端传入的 UUID v4（客户端未提供时，按请求体 hash 自动生成）

**Dubbo 调用 Key 格式**：

```
幂等 Key = dubbo:{interface}:{method}:{idempotency_key}
          dubbo:com.omplatform.order.api.OrderService:createOrder:550e8400-...
```

- `interface`：Dubbo 服务接口全限定名
- `method`：方法名
- `idempotency_key`：由调用方生成并放入 RpcContext attachment 的 UUID

**Saga 步骤 Key 格式**：

```
幂等 Key = saga:{saga_id}:{step_name}
           saga:SAGA20260612-001:deductInventory
```

- `saga_id`：Saga 事务全局 ID（Saga 启动时生成，贯穿整个生命周期）
- `step_name`：步骤名（如 `deductInventory`、`compensateInventory`、`createPayment`）

Saga 编排器在每个步骤发起 Dubbo 调用时，自动将 Key 注入 RpcContext attachment，由 Provider 端 `IdempotentDubboFilter` 统一拦截（见第 5 节）。

**三种 Key 格式对比**：

| 维度 | HTTP | Dubbo | Saga |
|------|------|-------|------|
| 前缀 | HTTP Method | `dubbo` | `saga` |
| 生成方 | 客户端（或 Gateway fallback） | 上游服务 | Saga 编排器 |
| 拦截位置 | IdempotencyFilter (Gateway) | IdempotentDubboFilter (Provider) | IdempotentDubboFilter (Provider) |
| 存储 | Redis + DB 双写 | Redis + DB 双写 | Redis + DB 双写 |
| 适用场景 | 外部 API、管理端 API | 内部 Dubbo 服务调用 | Saga 步骤重试与补偿 |

**适用范围**：

```java
// 幂等适用于以下写操作
POST   /api/v1/orders              // 创建订单
POST   /api/v1/payments            // 发起支付
POST   /api/v1/refunds             // 发起退款
PUT    /api/v1/orders/{id}/cancel  // 取消订单
PATCH  /api/v1/orders/{id}/address // 修改地址

// 读操作不适用幂等（天然幂等）
GET    /api/v1/orders/{id}
GET    /api/v1/orders
```

### 2. Gateway 层幂等拦截

**IdempotencyFilter 执行流程**：

```
请求 → GatewayAuthFilter (JWT 校验)
     → IdempotencyFilter (幂等检查)
       
       判断：非幂等方法（GET/HEAD）→ 跳过，直接放行
       判断：请求头无 Idempotency-Key → 自动生成（SHA256(body)），放行（无缓存）
       
       有 Idempotency-Key：
       ┌──────────────────────────────────────────────┐
       │ Redis: GET idempotent:{key}                  │
       │   ├── 命中 → 检查是否与本次请求体相同        │
       │   │   ├── 相同 → 返回 200 + 缓存响应          │
       │   │   └── 不同 → 返回 409 Conflict            │
       │   └── 未命中 → Redis SET NX + 继续路由        │
       └──────────────────────────────────────────────┘
       
       后端响应后：
       ┌──────────────────────────────────────────────┐
       │ 成功 (2xx) → Redis 缓存响应 + 异步写入 DB     │
       │ 失败 (4xx/5xx) → Redis 删除 key（允许重试）   │
       └──────────────────────────────────────────────┘
```

**Filter 实现**：

```java
@Component
@Order(-1000)  // 在 AuthFilter 之后，RateLimitFilter 之前
public class IdempotencyFilter implements GlobalFilter {
    
    private static final String IDEM_KV_PREFIX = "idempotent:";
    private static final int DEFAULT_TTL_SECONDS = 86400; // 24h
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private IdempotentRecordMapper idempotentMapper;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        
        // 读操作跳过
        if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
            return chain.filter(exchange);
        }
        
        String idempotencyKey = exchange.getRequest().getHeaders()
            .getFirst("Idempotency-Key");
        
        // 客户端未提供 Key → 自动生成，继续路由（不做幂等缓存）
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            String path = exchange.getRequest().getURI().getPath();
            return chain.filter(exchange).then(Mono.fromRunnable(() ->
                cacheResponseIfSuccess(exchange, path, autoGenerateKey(exchange))));
        }
        
        String fullKey = buildKey(exchange, idempotencyKey);
        
        // Redis SET NX
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(IDEM_KV_PREFIX + fullKey, "PROCESSING", 
                Duration.ofSeconds(DEFAULT_TTL_SECONDS));
        
        if (Boolean.TRUE.equals(acquired)) {
            // 首次请求 → 继续路由
            return chain.filter(exchange).then(Mono.fromRunnable(() ->
                handleResponse(exchange, fullKey)));
        } else {
            // 重复 Key → 检查是否是同一请求体
            String cachedBody = redisTemplate.opsForValue()
                .get(IDEM_KV_PREFIX + fullKey + ":body");
            String currentBody = getBodyHash(exchange);
            
            if (cachedBody != null && cachedBody.equals(currentBody)) {
                // 相同请求 → 返回缓存结果
                String cachedResponse = redisTemplate.opsForValue()
                    .get(IDEM_KV_PREFIX + fullKey + ":response");
                if (cachedResponse != null) {
                    return writeCachedResponse(exchange, cachedResponse);
                }
                // 缓存不存在（仍在处理中）→ 返回 409
            }
            // 不同请求体使用相同 Key → 409
            return writeConflictResponse(exchange, fullKey);
        }
    }
}
```

### 3. HTTP 响应语义

| 场景 | HTTP 状态码 | 响应体 | 说明 |
|------|------------|--------|------|
| 首次请求，执行成功 | **201 Created** | 正常业务响应 | 请求被处理并持久化 |
| 重复请求（已完成） | **200 OK** | 首次返回的缓存响应 | 幂等友好，不报错 |
| 重复请求（处理中） | **409 Conflict** | `{code: 409001, message: "请求正在处理中"}` | 客户端应等待 |
| 不同请求体相同 Key | **409 Conflict** | `{code: 409002, message: "Idempotency-Key 冲突"}` | 客户端应更换 Key |
| Key 过期（超过 TTL） | **422 Unprocessable** | `{code: 422001, message: "Idempotency-Key 已过期"}` | 客户端应换新 Key |

**响应体格式**：

```json
// 重复请求（已完成）
HTTP 200 OK
{
    "idempotent": true,
    "originalRequestId": "550e8400-...",
    "data": { ... }  // 首次的完整响应
}

// Key 冲突
HTTP 409 Conflict
{
    "code": 409002,
    "message": "Idempotency-Key 已用于不同的请求体，请更换 Key 重试",
    "key": "POST:/api/v1/orders:550e8400-..."
}
```

### 4. @Idempotent 注解 + AOP

**注解定义**：

```java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    
    /**
     * 幂等超时时间（秒），默认 24h。
     * 超过 TTL 的重复 Key 将被视为新请求。
     */
    int ttl() default 86400;
    
    /**
     * Scope 前缀，用于区分不同业务域。
     */
    String scope() default "";
    
    /**
     * Key 前缀，配合 SpEL 表达式提取参数。
     */
    String keyPrefix() default "";
    
    /**
     * SpEL 表达式，从方法参数中提取幂等 Key。
     * 示例： "#request.orderId"
     */
    String key() default "";
}
```

**AOP 实现**：

```java
@Aspect
@Component
public class IdempotentAspect {
    
    @Autowired
    private IdempotentStore idempotentStore;
    
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 从 Idempotency-Key Context 获取 Key
        String idempotencyKey = IdempotentContext.getKey();
        if (idempotencyKey == null) {
            // 无 Key（非 HTTP 入口，如 Dubbo 调用）→ 自动生成
            idempotencyKey = autoGenerateKey(joinPoint, idempotent);
        }
        
        String fullKey = buildKey(idempotent, idempotencyKey);
        
        // 2. 检查幂等
        Optional<Object> cached = idempotentStore.get(fullKey);
        if (cached.isPresent()) {
            return cached.get();  // 直接返回缓存结果
        }
        
        // 3. 加锁执行（防止并发）
        boolean locked = idempotentStore.tryLock(fullKey);
        if (!locked) {
            throw new IdempotentConflictException("请求正在处理中");
        }
        
        try {
            // 4. 执行方法
            Object result = joinPoint.proceed();
            // 5. 缓存结果
            idempotentStore.set(fullKey, result, idempotent.ttl());
            return result;
        } catch (Exception e) {
            // 执行失败 → 释放锁，允许重试
            idempotentStore.remove(fullKey);
            throw e;
        } finally {
            IdempotentContext.clear();
        }
    }
}
```

**使用示例**：

```java
@RestController
public class OrderController {
    
    @PostMapping("/api/v1/orders")
    @Idempotent(scope = "order", keyPrefix = "create", 
                key = "#request.buyerId + ':' + #request.outBizId")
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }
}
```

### 5. Dubbo Provider Filter 幂等

```java
@Activate(group = {CommonConstants.PROVIDER}, order = -8000)
public class IdempotentDubboFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 1. 从 RpcContext 获取幂等 Key
        String idempotencyKey = RpcContext.getServerAttachment()
            .getAttachment("idempotency-key");
        
        if (idempotencyKey == null) {
            return invoker.invoke(invocation);
        }
        
        // 2. 设置 ThreadLocal
        IdempotentContext.setKey(idempotencyKey);
        IdempotentContext.setScope(RpcContext.getServerAttachment()
            .getAttachment("idempotency-scope"));
        
        try {
            // 3. 检查 @Idempotent 注解
            Method method = invoker.getInterface()
                .getMethod(invocation.getMethodName(), invocation.getParameterTypes());
            Idempotent idempotent = method.getAnnotation(Idempotent.class);
            
            if (idempotent != null) {
                return handleIdempotent(invoker, invocation, idempotent);
            }
            
            return invoker.invoke(invocation);
        } finally {
            IdempotentContext.clear();
        }
    }
}
```

**SPI 配置**：

```properties
# META-INF/dubbo/org.apache.dubbo.rpc.Filter
idempotentProvider=com.omplatform.idempotent.filter.IdempotentDubboProviderFilter
```

```yaml
# application.yml（Provider 端加载）
dubbo:
  provider:
    filter: idempotentProvider,default
```

### 6. DB 唯一索引兜底

**idempotent_record 表**：

```sql
CREATE TABLE `idempotent_record` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `idempotent_key`    VARCHAR(512)    NOT NULL COMMENT '幂等 Key（method:path:idempotency_key）',
    `idempotency_key`   VARCHAR(64)     NOT NULL COMMENT '客户端传入的 UUID',
    `api_method`        VARCHAR(16)     NOT NULL COMMENT 'HTTP 方法',
    `api_path`          VARCHAR(256)    NOT NULL COMMENT '请求路径',
    `request_body_hash` VARCHAR(64)              COMMENT '请求体 SHA256',
    `response_body`     LONGTEXT                 COMMENT '响应体 JSON（首次成功结果）',
    `http_status`       INT             NOT NULL DEFAULT 200 COMMENT 'HTTP 状态码',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCEEDED',
    `expire_at`         DATETIME        NOT NULL COMMENT '过期时间',
    `gmt_create`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录表';
```

**XXL-Job 清理**：

```java
@Component
public class IdempotentRecordCleanupJob {
    
    @XxlJob("idempotentRecordCleanup")
    public ReturnT<String> cleanup(String param) {
        int batchSize = 500;
        int totalDeleted = 0;
        
        // 分批删除过期记录（expire_at < NOW() - 7d 的已完成记录）
        while (true) {
            int deleted = idempotentMapper.deleteExpired(
                LocalDateTime.now().minusDays(7), batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) break;
        }
        
        // 兜底清理：超过 30 天的所有记录（防止遗忘的 PROCESSING 状态）
        idempotentMapper.deleteByExpireAtBefore(
            LocalDateTime.now().minusDays(30), 1000);
        
        XxlJobHelper.log("清理过期幂等记录: {} 条", totalDeleted);
        return ReturnT.SUCCESS;
    }
}
```

### 7. 存量系统迁移策略

**迁移计划**：

```
Phase 1（框架建设）：
  → 实现 IdempotencyFilter（Gateway）、@Idempotent（AOP）、IdempotentDubboFilter
  → 实现 IdempotentStore（Redis + DB）
  → 新增 `idempotent_record` 表
  → Apollo 开关：idempotent.enabled=false（默认关闭）

Phase 2（存量接入）：
  → payment-callback：替换唯一索引幂等 → 调用 IdempotentStore
  → Saga（ADR-020）：替换 saga_id + step_name 唯一索引 → Key 格式 `saga:{saga_id}:{step_name}`，Saga 编排器注入 RpcContext
  → 延迟任务（ADR-021）：替换 business_key 去重 → @Idempotent 注解

Phase 3（切换与下线）：
  → Apollo 全员开启 idempotent.enabled=true
  → 观察 1 周无问题后，下线原有独立表/索引
  → 存量幂等数据迁移到统一表
```

**兼容性策略**：

```java
// Apollo 开关控制幂等行为
public class IdempotentConfig {
    
    @Value("${idempotent.enabled:false}")
    private boolean enabled;
    
    @Value("${idempotent.store:redis}")
    private String storeType; // redis / db / dual
    
    @Value("${idempotent.default-ttl:86400}")
    private int defaultTtl;
}
```

**存量去重表映射**：

| 存量位置 | 当前方式 | 迁移后方式 |
|---------|---------|-----------|
| Saga idempotent_record | saga_id + step_name 唯一索引 | 共用全局幂等框架。Key 格式 `saga:{saga_id}:{step_name}`，Saga 编排器自动注入 RpcContext，Dubbo Filter 统一拦截 |
| Gateway nonce | Redis SET NX EX 300 | 保持 nonce（防重放，5min），与幂等框架并存 |
| 延迟任务 business_key | 唯一索引 | 使用 @Idempotent 注解替代 |
| 事件 eventId | 消费端自行实现 | 可选接入幂等框架，不强制 |
| 支付回调 transaction_id | 唯一索引 | 替换为 IdempotentStore 调用 |

### 8. 幂等监控指标

```java
@Configuration
public class IdempotentMetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> idempotentMetrics() {
        return registry -> {
            // 幂等 Key 命中率
            Counter.builder("omplatform_idempotent_hit_total")
                .tags("scope", "unknown")
                .register(registry);
            
            Counter.builder("omplatform_idempotent_miss_total")
                .tags("scope", "unknown")
                .register(registry);
            
            // 存储延迟
            Timer.builder("omplatform_idempotent_store_duration_ms")
                .tags("store", "redis", "operation", "get")
                .register(registry);
            
            // 重复请求数
            Counter.builder("omplatform_idempotent_duplicate_total")
                .tags("scope", "unknown", "status", "completed")
                .register(registry);
            
            // Key 冲突数
            Counter.builder("omplatform_idempotent_conflict_total")
                .tags("scope", "unknown")
                .register(registry);
        };
    }
}
```

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `omplatform_idempotent_hit_total` | Counter | 幂等 Key 命中（重复请求） |
| `omplatform_idempotent_miss_total` | Counter | 幂等 Key 未命中（首次请求） |
| `omplatform_idempotent_store_duration_ms` | Timer | 存储操作延迟 |
| `omplatform_idempotent_duplicate_total` | Counter | 重复请求数（按状态） |
| `omplatform_idempotent_conflict_total` | Counter | Key 冲突数 |
| `omplatform_idempotent_cleanup_total` | Counter | 过期清理记录数 |

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | IdempotentStore 核心：Redis + DB 双实现 + Apollo 开关 | 1d | Store 接口 + 实现 + 配置 |
| P2 | IdempotencyFilter：Gateway 层幂等拦截 + 响应缓存 + 409 处理 | 1d | GlobalFilter 实现 |
| P3 | @Idempotent 注解 + AOP 切面 + SpEL Key 提取 | 0.5d | 注解 + Aspect |
| P4 | IdempotentDubboFilter：Dubbo Provider Filter（SPI -8000） | 0.5d | Dubbo Filter + SPI 配置 |
| P5 | 存量迁移：payment-callback 接入 + Saga 表共用 + 延迟任务接入 | 1.5d | 存量改造 |
| P6 | 监控指标 + 文档 + 上线检查 | 0.5d | 指标 + 清理 Job |

**合计**：5 人天

## 上线检查清单

- [ ] 基础设施：`idempotent_record` 表 DDL 执行
- [ ] 基础设施：Redis 幂等 Key 自动过期策略配置（默认 24h）
- [ ] 代码：IdempotentStore 接口 + Redis/DB 实现
- [ ] 代码：IdempotencyFilter（Gateway GlobalFilter, Order=-1000）
- [ ] 代码：自定义异常处理（409 Conflict / 422 Unprocessable）
- [ ] 代码：@Idempotent 注解 + AOP Aspect + SpEL 支持
- [ ] 代码：IdempotentDubboFilter（SPI -8000）
- [ ] 代码：IdempotentContext ThreadLocal（finally 清理）
- [ ] 代码：Dubbo/Saga 幂等 Key 格式验证（dubbo: / saga: 前缀规范）
- [ ] 代码：XXL-Job idempotentRecordCleanup（每日一次）
- [ ] 监控：`omplatform_idempotent_` 指标注册
- [ ] 监控：409 冲突率告警（异常升高说明客户端使用不当）
- [ ] 配置：Apollo `idempotent.enabled=false` 默认关闭
- [ ] 存量：payment-callback 唯一索引迁移到 IdempotentStore
- [ ] 存量：Saga idempotent_record 表共用（数据迁移）

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-020** (Saga) | Saga 步骤幂等走全局框架，Key 格式 `saga:{saga_id}:{step_name}`，Saga 编排器自动注入 RpcContext，替换 Saga 原 `idempotent_record` 表 |
| **ADR-025** (外部 Gateway) | Nonce dedup（5min 防重放）与幂等框架（24h 业务幂等）共存，Nonce 保持独立 |
| **ADR-021** (延迟任务) | 替换 `business_key` 去重逻辑，使用 `@Idempotent` 注解 |
| **ADR-010** (事件 Schema) | 事件消费端可选接入幂等框架 |
| **payment-callback.puml** | 替换 `payment_transaction_id` 唯一索引，改用 IdempotentStore |
| **ADR-029** (内部 Gateway) | IdempotencyFilter 作为内部 Gateway 的 Filter 链第三步 |
| **ADR-033** (Webhook) | Webhook 推送使用 `X-Webhook-Id` 配合幂等框架实现接收方幂等 |
| **ADR-026** (认证授权) | AuthContext 与 IdempotentContext 各自独立 ThreadLocal，无冲突 |
| **ADR-027** (可观测性) | 幂等指标纳入统一 `omplatform_` 前缀规范 |

## 备选方案评估

### 方案 A：纯 Redis

完全依赖 Redis SET NX + TTL，不做 DB 持久化。

- **优点**：实现简单，性能最佳
- **缺点**：Redis 重启丢失幂等记录 → 重复请求可能穿透到后端
- **适用场景**：允许偶发重复的非关键业务

### 方案 C：纯 DB 唯一索引

完全依赖 DB 唯一索引做幂等判断。

- **优点**：数据持久化，强一致性
- **缺点**：10-50ms 写入延迟；无响应缓存，重复请求仍需查 DB 获取结果；高并发下 DB 压力大
- **适用场景**：写频率低的场景

### 幂等 Key 生成：纯服务端自动

不依赖客户端传入 Key，服务端按 `method + path + body_hash` 自动判断。

- **优点**：客户端零改造
- **缺点**：相同参数不同语义的请求无法区分（如 "再试一次" 场景）；body 可能包含时间戳等每次不同的字段
- **适用场景**：作为客户端未提供 Key 时的 fallback
