# ADR-025：API 版本管理 + 外部网关

## 状态

已接受

---

## 背景

### 现状分析

订单中台目前通过 **Spring Cloud Gateway** 为内部前端（买家 APP/商家后台/运营后台）提供统一 API 入口。在 `context-diagram.puml` 中定义了「开放平台」（`open_api`）作为第三方开发者接入的系统边界，但目前缺少完整设计。

当前架构在以下方面存在空白：

**问题 1：无 API 版本管理**  
所有 API 没有版本标识（如 `/v1/orders`），内部前端与 API 紧耦合。后端接口变更时无法区分新旧版本调用方，导致：
- 新增字段无法安全下放（前端依赖未知）
- 破坏性变更（字段重命名/删除）只能通过同步前端发布
- 多版本共存能力为零

**问题 2：无外部网关**  
开放平台（第三方开发者）与内部前端共用同一个 Gateway 入口和同一套鉴权机制（JWT 用户认证）。第三方开发者需要的是**应用级凭证**（appKey/appSecret），而非用户级 JWT token。

**问题 3：无 API 文档与开发者门户**  
当前无 Swagger/OpenAPI 定义，第三方开发者接入没有标准文档、SDK 或沙箱环境。每次集成需要通过人工沟通。

**问题 4：无 API 配额与应用管理**  
第三方开发者的 API 调用缺少配额限制、调用统计和应用级监控。单个恶意或异常应用可能影响整体系统稳定性。

### 目标

1. **API 版本化**：以 URL 路径版本为主策略，支持最少 2 个主版本共存（N-1 兼容），确保 API 平滑演进
2. **外部网关**：独立的外部 Gateway 实例，管理第三方应用的认证、限流、配额和计量
3. **开发者生态**：应用注册、API 文档、沙箱环境和 SDK 自动生成
4. **安全合规**：HMAC-SHA256 签名、应用级权限、调用审计

---

## 决策

### API 版本化策略

**主策略：URL 路径版本化（`/v{n}/`）**

| 策略 | 评估 | 决策 |
|------|------|------|
| URL 路径 `/v1/orders` | 最直观，客户端显式声明版本，CDN/网关层可路由 | ✅ **选中** |
| Header `Accept: version=v1` | 对第三方开发者不透明，调试困难 | ❌ |
| Header `X-API-Version: 1` | 需自定义路由解析，Gateway 额外逻辑 | ❌ |
| Query Param `?version=1` | 容易缓污染，不符合 RESTful 风格 | ❌ |

**辅助策略：媒体类型版本（Content Negotiation）**  
对需要精细控制的数据格式变化，配合 `Content-Type: application/vnd.omplatform.order.v2+json`。

### 外部网关架构

**方案：独立 External Gateway 实例 + 内部 Gateway 分级**

```
  ┌───────────────┐    ┌──────────────────┐
  │ 内部前端       │    │ 第三方开发者       │
  │ (APP/Web/Admin)│    │ (ISV/合作方)      │
  └───────┬───────┘    └────────┬─────────┘
          │                     │
          ▼                     ▼
  ┌────────────────┐   ┌──────────────────┐
  │ 内部 Gateway    │   │ 外部 Gateway      │  ← 独立的 Gateway 实例
  │ Spring Cloud    │   │ Spring Cloud      │
  │ JWT 鉴权        │   │ HMAC 验签          │
  │ OAuth2 RBAC     │   │ AppKey 认证        │
  │ 内部限流         │   │ 应用级配额          │
  └───────┬────────┘   │ 调用审计 + 计量     │
          │            └────────┬─────────┘
          │                     │
          ▼                     ▼
  ┌──────────────────────────────────────────┐
  │          内部服务 (order-core 等)          │
  │  通过 X-Internal-Call 识别请求来源         │
  │  外部请求需额外校验 Scope/Permission       │
  └──────────────────────────────────────────┘
```

**理由：**

| 因素 | 评估 |
|------|------|
| **安全隔离** | 外部请求不经过内部 JWT/OAuth2 流程，减少攻击面 |
| **独立扩缩容** | 外部 QPS 模型与内部不同，可独立配置 Pod 数和限流阈值 |
| **统一计量** | 外部 Gateway 集中处理调用审计和计量计费 |
| **版本解耦** | 外部 API 版本与内部 API 版本可独立演进 |

---

## 详细设计

### 1. API 版本化方案

#### 1.1 版本路径规范

```
内部 API：/api/v1/orders           # 内部前端使用
外部 API：/openapi/v1/orders       # 第三方开发者使用（通过外部 Gateway）
管理 API：/admin/v1/orders          # 运营后台使用（保留，暂不开放外部）
```

#### 1.2 版本生命周期

```
版本生命周期
═══════════════════════════════════════════════════

  v1 (GA) ────────────────────────────→ 稳定期 → 维护期 → 废弃
         t0              t1(+6个月)       t2(+12个月)  t3(+18个月)

  v2 (开发) ─→ (预览) ─→ (GA)
         t0         t1         t2

阶段说明：
  - 预览版（Preview）：  附带 -preview 后缀，仅沙箱环境，不承诺稳定性
  - 稳定版（GA/Stable）：正式发布，向前兼容
  - 维护期（Maintenance）：仅修复安全漏洞和严重 Bug，不新增功能
  - 废弃期（Deprecated）：响应头返回 Sunset: date，提醒客户端迁移
                            377 天后正式下线
  - 下线（Sunset）：      返回 410 Gone
```

#### 1.3 版本兼容性规则

```yaml
version-compatibility-rules:
  # ===== 向前兼容（允许的变更） =====
  compatible:
    - "新增可选字段（nullable / default）"
    - "新增枚举值（客户端需忽略未知值）"
    - "响应中新增字段（客户端需忽略未知字段）"
    - "扩展请求参数（仅新增可选参数）"
    - "HTTP 方法扩展（如新增 PATCH 方法）"
    - "响应头新增字段"

  # ===== 破坏性变更（必须发新版本） =====
  breaking:
    - "删除/重命名请求参数"
    - "删除/重命名响应字段"
    - "修改响应类型（String → Number）"
    - "修改枚举值名称"
    - "修改请求/响应结构（如扁平化→嵌套）"
    - "删除 HTTP 方法"
    - "修改端点 URL 路径"
    - "修改错误码含义"
    - "增加必填请求参数"
    - "修改认证/授权方式"

  # ===== 建议避免的变更 =====
  discouraged:
    - "响应中返回 null 代替空数组（[]）"
    - "无意义的状态码变化（200 → 201）"
    - "隐式的字段类型放宽（String → Any）"
```

#### 1.4 Spring 实现

```java
/**
 * Controller 版本化示例
 * 内部和外部 API 在不同包路径下，便于独立管理
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderControllerV1 {

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable String orderId) {
        // v1 实现
    }
}

@RestController
@RequestMapping("/api/v2/orders")
public class OrderControllerV2 {

    @GetMapping("/{orderId}")
    public OrderResponseV2 getOrder(@PathVariable String orderId) {
        // v2 实现（可新增字段、调整结构）
    }
}

/**
 * 版本路由配置（Gateway 层）
 * 将 /v{n}/ 路由到对应版本的 Service
 */
@Configuration
public class VersionRouteConfig {

    @Bean
    public RouteLocator versionRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // v1 路由（历史版本，维护模式）
            .route("orders-v1", r -> r
                .path("/api/v1/orders/**")
                .uri("lb://order-core-v1"))    // v1 版本的服务
            // v2 路由（当前稳定版）
            .route("orders-v2", r -> r
                .path("/api/v2/orders/**")
                .uri("lb://order-core"))        // 当前主版本服务
            .build();
    }
}

/**
 * 版本废弃检测 Filter
 * 当客户端调用已废弃的版本时，在响应头中添加 Sunset 和 Warning
 */
@Component
public class VersionDeprecationFilter implements GlobalFilter {

    private static final Map<String, LocalDate> DEPRECATED_VERSIONS = Map.of(
        "/api/v1", LocalDate.of(2026, 12, 31)    // v1 计划 2026-12-31 下线
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        LocalDate sunset = DEPRECATED_VERSIONS.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);

        if (sunset != null) {
            exchange.getResponse().getHeaders()
                    .add("Sunset", sunset.toString());
            exchange.getResponse().getHeaders()
                    .add("Warning", "299 - \"This API version is deprecated. "
                            + "Please migrate to v2. Sunset: " + sunset + "\"");
        }
        return chain.filter(exchange);
    }
}
```

### 2. 外部 Gateway 设计

#### 2.1 应用注册与管理

```sql
-- 第三方应用注册表
CREATE TABLE `open_api_app` (
    `id` BIGINT AUTO_INCREMENT COMMENT '自增 ID',
    `app_key` VARCHAR(32) NOT NULL COMMENT '应用标识（AppKey）',
    `app_name` VARCHAR(128) NOT NULL COMMENT '应用名称',
    `developer` VARCHAR(128) NOT NULL COMMENT '开发者/企业名称',
    `contact_email` VARCHAR(128) COMMENT '联系邮箱',
    `description` TEXT COMMENT '应用描述',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED'
        COMMENT '状态: ENABLED / DISABLED / AUDITING',
    `allowed_apis` JSON NOT NULL COMMENT '允许调用的 API 列表',
    `daily_quota` BIGINT NOT NULL DEFAULT 100000 COMMENT '每日最大调用次数',
    `qps_quota` INT NOT NULL DEFAULT 100 COMMENT '每秒最大调用次数',
    `ip_whitelist` JSON COMMENT 'IP 白名单（可选）',
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    KEY `idx_developer` (`developer`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放平台应用注册表';

-- API 调用计量表（每小时粒度）
CREATE TABLE `open_api_usage` (
    `id` BIGINT AUTO_INCREMENT COMMENT '自增 ID',
    `app_key` VARCHAR(32) NOT NULL COMMENT '应用标识',
    `api_path` VARCHAR(256) NOT NULL COMMENT 'API 路径（含版本）',
    `http_method` VARCHAR(8) NOT NULL COMMENT 'HTTP 方法',
    `http_status` INT NOT NULL COMMENT 'HTTP 状态码',
    `call_count` INT NOT NULL DEFAULT 0 COMMENT '调用次数',
    `total_latency_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '总延迟（ms）',
    `record_hour` DATETIME NOT NULL COMMENT '记录小时（按小时对齐）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_api_hour` (`app_key`, `api_path`, `record_hour`),
    KEY `idx_record_hour` (`record_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 调用计量表';

-- 应用密钥历史表（支持密钥轮换）
CREATE TABLE `open_api_secret` (
    `id` BIGINT AUTO_INCREMENT COMMENT '自增 ID',
    `app_key` VARCHAR(32) NOT NULL COMMENT '应用标识',
    `app_secret` VARCHAR(128) NOT NULL COMMENT '加密后的 AppSecret',
    `version` INT NOT NULL DEFAULT 1 COMMENT '密钥版本',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '状态: ACTIVE / INACTIVE / EXPIRED',
    `effective_at` DATETIME(3) NOT NULL COMMENT '生效时间',
    `expire_at` DATETIME(3) COMMENT '过期时间（密钥轮换时设置）',
    `gmt_create` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_app_key` (`app_key`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用密钥表';
```

#### 2.2 签名认证流程

```
第三方开发者调用外部 API 的签名流程
═══════════════════════════════════════════════════

请求参数：
  - app_key:    应用标识（明文）
  - timestamp:  当前 Unix 时间戳（秒），误差 ±5min
  - nonce:      请求唯一标识（UUID），防止重放
  - sign_type:  签名算法（HMAC-SHA256 / HMAC-SHA512）
  - signature:  签名结果
  - [业务参数]

签名算法：
  sign_str = HTTP_METHOD + "\n"
           + PATH + "\n"             // /openapi/v2/orders/123
           + app_key + "\n"
           + timestamp + "\n"
           + nonce + "\n"
           + BODY                     // JSON 请求体（GET 请求为空）

  signature = HMAC-SHA256(sign_str, app_secret)

外部 Gateway 验签流程：
  ① 从请求头中提取 app_key
  ② 从 DB/Redis 中查询 app_secret（缓存 5min）
  ③ 检查应用状态（ENABLED / 未过期）
  ④ 检查 IP 白名单（如有配置）
  ⑤ 检查 timestamp 是否在 ±5min 内
  ⑥ 检查 nonce 是否已使用（Redis SET NX EX 300, 5min 去重）
     注：该机制将在 ADR-030 全局幂等框架落地后统一迁移。
  ⑦ 重建 sign_str 并验签
  ⑧ 检查 API 权限（app_key 是否有该 API 的权限）
  ⑨ 检查限流与配额（QPS 限流 / 日配额）
  → 全部通过 → 转发到内部服务
  → 任一失败 → 返回 401 / 403 / 429
```

#### 2.3 外部 Gateway Filter 实现

```java
/**
 * 外部 Gateway — 签名认证 Filter
 */
@Component
@Order(-1000)
public class SignatureAuthenticationFilter implements GlobalFilter {

    private final StringRedisTemplate redis;
    private final OpenApiAppRepository appRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 仅处理外部 API 路径
        if (!path.startsWith("/openapi/")) {
            return chain.filter(exchange);
        }

        // 1. 提取认证参数
        String appKey = request.getHeaders().getFirst("X-App-Key");
        String timestamp = request.getHeaders().getFirst("X-Timestamp");
        String nonce = request.getHeaders().getFirst("X-Nonce");
        String signType = request.getHeaders().getFirst("X-Sign-Type");
        String signature = request.getHeaders().getFirst("X-Signature");

        if (appKey == null || timestamp == null || nonce == null || signature == null) {
            return unauthorized(exchange, "Missing authentication parameters");
        }

        // 2. 时间戳检查（±5min）
        long ts = Long.parseLong(timestamp);
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > 300) {
            return unauthorized(exchange, "Timestamp expired");
        }

        // 3. Nonce 去重（5min 内不重复）
        // TODO: ADR-030 全局幂等框架落地后统一迁移
        String nonceKey = "openapi:nonce:" + nonce;
        Boolean isNew = redis.opsForValue().setIfAbsent(nonceKey, "1", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(isNew)) {
            return unauthorized(exchange, "Nonce already used (replay attack)");
        }

        // 4. 查询应用信息（缓存 5min）
        String cacheKey = "openapi:app:" + appKey;
        String appSecret = redis.opsForValue().get(cacheKey);
        if (appSecret == null) {
            OpenApiApp app = appRepository.findByAppKey(appKey);
            if (app == null || app.getStatus() != AppStatus.ENABLED) {
                return unauthorized(exchange, "Invalid or disabled app_key");
            }
            appSecret = app.getAppSecret();
            redis.opsForValue().set(cacheKey, appSecret, Duration.ofMinutes(5));
        }

        // 5. 重建签名并验证
        String body = resolveBody(exchange);
        String signStr = buildSignString(request.getMethod().name(), path, appKey, timestamp, nonce, body);
        String expectedSig = hmacSha256(signStr, appSecret);

        if (!expectedSig.equals(signature)) {
            return unauthorized(exchange, "Signature mismatch");
        }

        // 6. 检查限流与配额（Redis 计数器 + Lua 脚本原子操作，含 QPS 限流和日配额检查）
        return checkQuota(exchange, chain, appKey, path)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return tooManyRequests(exchange);
                    }
                    // 7. 将 app_key 和来源信息传入上游
                    exchange.getAttributes().put("app_key", appKey);
                    exchange.getAttributes().put("call_source", "openapi");
                    return chain.filter(exchange);
                });
    }

    private String buildSignString(String method, String path, String appKey,
                                    String timestamp, String nonce, String body) {
        return method + "\n" + path + "\n" + appKey + "\n"
                + timestamp + "\n" + nonce + "\n" + (body == null ? "" : body);
    }
}

/**
 * 外部 Gateway — 限流与配额检查 Filter
 * 配合 Lua 脚本做原子计数
 * 限流（Rate Limiting）：QPS 短时间窗口速率控制
 * 配额（Quota）：日调用长时间窗口总量控制
 */
@Component
@Order(-900)
public class QuotaCheckFilter implements GlobalFilter {

    private final StringRedisTemplate redis;

    // Lua 脚本：检查并递增配额，原子操作
    private static final String QUOTA_SCRIPT = """
        local qps_key = KEYS[1]    -- openapi:quota:qps:{app_key}:{秒}
        local daily_key = KEYS[2]  -- openapi:quota:daily:{app_key}:{日期}
        local qps_limit = tonumber(ARGV[1])
        local daily_limit = tonumber(ARGV[2])

        -- QPS 检查
        local qps = redis.call('INCR', qps_key)
        if qps == 1 then
            redis.call('EXPIRE', qps_key, 2)  -- 2s 过期，避免时钟边界问题
        end
        if qps > qps_limit then
            return {0, 'qps_exceeded'}
        end

        -- 日配额检查
        local daily = redis.call('INCR', daily_key)
        if daily == 1 then
            redis.call('EXPIRE', daily_key, 86400)
        end
        if daily > daily_limit then
            return {0, 'daily_quota_exceeded'}
        end

        return {1, ''}
        """;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String appKey = exchange.getAttribute("app_key");
        if (appKey == null) return chain.filter(exchange);

        String qpsKey = "openapi:quota:qps:" + appKey + ":" + (System.currentTimeMillis() / 1000);
        String dailyKey = "openapi:quota:daily:" + appKey + ":" + LocalDate.now();
        int qpsLimit = 100;    // 默认 QPS 限流值（Rate Limiting）
        int dailyLimit = 100000; // 默认日配额（Quota）

        List<String> keys = List.of(qpsKey, dailyKey);
        List<String> args = List.of(String.valueOf(qpsLimit), String.valueOf(dailyLimit));

        List<Object> result = redis.execute(
                new DefaultRedisScript<>(QUOTA_SCRIPT, List.class), keys, args);

        if (result != null && Integer.valueOf(1).equals(result.get(0))) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange);
    }
}
```

#### 2.4 外部 API 路由配置

```yaml
# external-gateway.yml — 外部 Gateway 路由
spring:
  cloud:
    gateway:
      routes:
        # ===== 开放平台 API v1 =====
        - id: openapi-v1-orders
          uri: lb://order-core
          predicates:
            - Path=/openapi/v1/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: '#{@appKeyResolver}'
                redis-rate-limiter.replenishRate: 200
                redis-rate-limiter.burstCapacity: 400
            - AddRequestHeader=X-Call-Source, openapi
            - AddRequestHeader=X-API-Version, v1

        # ===== 开放平台 API v2（当前稳定版） =====
        - id: openapi-v2-orders
          uri: lb://order-core
          predicates:
            - Path=/openapi/v2/orders/**
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: '#{@appKeyResolver}'
                redis-rate-limiter.replenishRate: 500
                redis-rate-limiter.burstCapacity: 1000
            - AddRequestHeader=X-Call-Source, openapi
            - AddRequestHeader=X-API-Version, v2

        # ===== 沙箱环境（用于第三方开发者调试） =====
        - id: openapi-sandbox-orders
          uri: lb://order-core-sandbox
          predicates:
            - Path=/sandbox/openapi/v2/orders/**

        # ===== 开发者管理 API（无需签名） =====
        - id: openapi-devportal
          uri: lb://open-api-admin
          predicates:
            - Path=/developer/**
```

#### 2.5 开发者 API 定义（开放给第三方的接口）

```yaml
# openapi-definition.yaml
openapi: "3.0.3"
info:
  title: "订单中台开放平台 API"
  version: "v2"
  description: "为 ISV 和合作方提供订单查询、创建、物流追踪等能力"
  x-rate-limit: "100 QPS / 100,000 日调用"  # QPS=限流(Rate Limiting), 日调用=配额(Quota)

servers:
  - url: https://openapi.omplatform.com/openapi/v2
    description: "生产环境"
  - url: https://sandbox.omplatform.com/sandbox/openapi/v2
    description: "沙箱环境"

paths:
  # ===== 订单查询 =====
  /orders/{orderNo}:
    get:
      summary: "查询订单详情"
      description: "根据订单号查询订单完整信息（脱敏后）"
      parameters:
        - name: orderNo
          in: path
          required: true
          schema: { type: string }
          example: "ORD202606120001"
      responses:
        "200":
          description: "订单详情"
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/OrderResponse"

  # ===== 订单列表 =====
  /orders:
    get:
      summary: "查询订单列表"
      description: "按条件查询订单列表，支持分页"
      parameters:
        - name: status
          in: query
          schema: { type: string }
        - name: startTime
          in: query
          schema: { type: string, format: date-time }
        - name: endTime
          in: query
          schema: { type: string, format: date-time }
        - name: cursor
          in: query
          schema: { type: string }
          description: "游标分页（上一页最后一条的 orderId）"
        - name: pageSize
          in: query
          schema: { type: integer, default: 20, maximum: 100 }
      responses:
        "200":
          description: "订单列表"

  # ===== 物流追踪 =====
  /orders/{orderNo}/logistics:
    get:
      summary: "查询物流轨迹"
      responses:
        "200":
          description: "物流轨迹信息"

components:
  schemas:
    OrderResponse:
      type: object
      properties:
        orderNo: { type: string }
        orderStatus: { type: string }
        totalAmount: { type: number, format: int64 }
        payAmount: { type: number, format: int64 }
        receiverName:
          type: string
          description: "收货人姓名（已脱敏）"
          x-desensitized: true
        receiverPhone:
          type: string
          description: "收货人电话（已脱敏）"
          x-desensitized: true
        receiverAddress:
          type: string
          description: "收货地址（已脱敏）"
          x-desensitized: true
        logisticsNo: { type: string }
        logisticsCompany: { type: string }
        createTime: { type: string, format: date-time }
        paidTime: { type: string, format: date-time }

  securitySchemes:
    HMACSignature:
      type: apiKey
      in: header
      name: X-Signature
      description: |
        HMAC-SHA256 签名认证。
        需在请求头中传入：X-App-Key, X-Timestamp, X-Nonce, X-Signature
        SignString = HTTP_METHOD + "\\n" + PATH + "\\n" + app_key + "\\n" + timestamp + "\\n" + nonce + "\\n" + BODY
        Signature = HMAC-SHA256(SignString, AppSecret)
```

#### 2.6 外部 API 响应规范

```java
/**
 * 统一响应体（所有外部 API 使用）
 */
@Data
@Builder
public class OpenApiResponse<T> {

    private int code;           // 业务码：0=成功，非0=错误
    private String message;     // 提示信息
    private String requestId;   // 请求追踪 ID（便于开发者排查）
    private T data;             // 业务数据

    // ===== 成功响应 =====
    public static <T> OpenApiResponse<T> success(T data) {
        return OpenApiResponse.<T>builder()
                .code(0)
                .message("success")
                .requestId(TraceContext.getTraceId())
                .data(data)
                .build();
    }

    // ===== 错误码规范 =====
    // 1xxx: 认证错误  2xxx: 参数错误  3xxx: 业务错误  4xxx: 限流错误  5xxx: 系统错误
    public static <T> OpenApiResponse<T> error(int code, String message) {
        return OpenApiResponse.<T>builder()
                .code(code)
                .message(message)
                .requestId(TraceContext.getTraceId())
                .build();
    }
}

/**
 * 错误码定义
 */
public final class OpenApiErrorCode {
    // 认证错误（1xxx）
    public static final int MISSING_PARAMETER     = 1001;  // 缺少认证参数
    public static final int INVALID_APP_KEY       = 1002;  // AppKey 无效
    public static final int SIGNATURE_MISMATCH    = 1003;  // 签名不匹配
    public static final int TIMESTAMP_EXPIRED     = 1004;  // 时间戳过期
    public static final int NONCE_REUSED          = 1005;  // Nonce 重复
    public static final int IP_NOT_WHITELISTED    = 1006;  // IP 未在白名单

    // 参数错误（2xxx）
    public static final int INVALID_PARAMETER     = 2001;  // 参数校验失败
    public static final int MISSING_REQUIRED      = 2002;  // 缺少必填参数
    public static final int UNSUPPORTED_VERSION   = 2003;  // 不支持的 API 版本

    // 业务错误（3xxx）
    public static final int ORDER_NOT_FOUND       = 3001;  // 订单不存在
    public static final int ORDER_STATUS_INVALID  = 3002;  // 订单状态不允许
    public static final int PERMISSION_DENIED     = 3003;  // 无权限

    // 限流与配额错误（4xxx）：限流(Rate Limiting)为短时间窗口速率控制，配额(Quota)为长时间窗口总量控制
    public static final int QPS_EXCEEDED          = 4001;  // 超过 QPS 限流（Rate Limiting）
    public static final int DAILY_QUOTA_EXCEEDED  = 4002;  // 超过日配额（Quota）
    public static final int API_NOT_ACCESSIBLE    = 4003;  // API 未授权

    // 系统错误（5xxx）
    public static final int INTERNAL_ERROR        = 5001;  // 系统内部错误
    public static final int SERVICE_UNAVAILABLE   = 5002;  // 服务暂不可用
    public static final int TIMEOUT               = 5003;  // 请求超时
}
```

### 3. 开发者门户

#### 3.1 开发者自助流程

```
开发者入驻流程
═══════════════════════════════════════════════════

  ① 注册账号
     ├── 企业信息（企业名称、营业执照、联系人）
     ├── 开发者账号（邮箱 + 手机）
     └── 签署开发者协议
          │
          ▼
  ② 创建应用
     ├── 填写应用名称、描述、回调地址
     ├── 选择需要调用的 API 权限（按 scope 粒度）
     └── 提交审核 → 自动/人工审核
          │
          ▼
  ③ 获取凭证
     ├── AppKey（应用唯一标识）
     ├── AppSecret（密钥，仅展示一次，需立即保存）
     └── 支持密钥轮换（保留 2 个有效版本）
          │
          ▼
  ④ 沙箱测试
     ├── 沙箱环境：sandbox.omplatform.com
     ├── 沙箱数据：模拟订单数据，不涉及真实资金
     ├── API 文档 + SDK（Java/Python/PHP）
     └── 在线调试工具（Swagger UI / Postman）
          │
          ▼
  ⑤ 上线生产
     ├── 生产环境：openapi.omplatform.com
     ├── 签署 SLA（可用性承诺）
     └── 开始调用 + 监控仪表盘
```

#### 3.2 API 文档自动生成

```java
/**
 * OpenAPI 文档聚合
 *
 * 在外部 Gateway 中增加一个端点，聚合所有开放 API 的 OpenAPI 规范，
 * 供 Swagger UI / Knife4j 渲染
 */
@Configuration
public class OpenApiDocsConfig {

    @Bean
    public GroupedOpenApi openApiV2() {
        return GroupedOpenApi.builder()
                .group("openapi-v2")
                .displayName("订单中台开放平台 API v2")
                .pathsToMatch("/openapi/v2/**")
                .build();
    }

    @Bean
    public GroupedOpenApi openApiV1() {
        return GroupedOpenApi.builder()
                .group("openapi-v1")
                .displayName("订单中台开放平台 API v1 (维护期)")
                .pathsToMatch("/openapi/v1/**")
                .build();
    }
}

/**
 * SDK 自动生成
 * CI 流水线在每次 API 变更后自动生成多语言 SDK
 */
// pipeline stage: sdk-generate
// openapi-generator-cli generate -i openapi.yaml -g java -o sdk/java
// openapi-generator-cli generate -i openapi.yaml -g python -o sdk/python
// openapi-generator-cli generate -i openapi.yaml -g php -o sdk/php
```

### 4. 安全与合规

#### 4.1 外部 API 安全矩阵

```
安全防护层级
═══════════════════════════════════════════════════

Layer 1: 传输层
  ├── HTTPS TLS 1.3（强制）
  └── HSTS（Strict-Transport-Security）

Layer 2: 认证层
  ├── HMAC-SHA256 签名（请求级别）
  ├── AppKey + AppSecret（应用级别）
  └── Nonce 防重放（5min 窗口）

Layer 3: 授权层
  ├── API 权限 Scope（应用注册时选择）
  ├── IP 白名单（可选）
  └── 敏感数据脱敏（遵守 ADR-023 脱敏规则）

Layer 4: 限流与配额层
  ├── QPS 限流（Rate Limiting，每秒粒度）
  ├── 日配额（Quota，每日 00:00 重置）
  └── 并发连接数限制

Layer 5: 审计层
  ├── 所有请求记录审计日志
  ├── 调用计量（小时粒度）
  └── 异常行为检测（多次签名失败 → 临时封禁）
```

#### 4.2 外部请求与脱敏的交互

外部 API 返回的订单数据必须经过脱敏（与 ADR-023 一致）：

```java
/**
 * 外部请求的脱敏规则
 * 所有第三方开发者看到的敏感字段默认脱敏
 * 需要原始数据的特殊场景需单独申请权限
 */
@RestController
@RequestMapping("/openapi/v2/orders")
public class OpenApiOrderController {

    @GetMapping("/{orderNo}")
    public OpenApiResponse<OrderDTO> getOrder(@PathVariable String orderNo) {
        // 1. 查询订单（内部服务）
        OrderDTO order = orderService.getByOrderNo(orderNo);

        // 2. 强制脱敏（外部请求必须脱敏）
        //    由 DesensitizeContext + Jackson Serializer 自动处理
        //    外部请求在 Gateway 中设置 X-Call-Source: openapi
        //    DesensitizeProviderFilter 识别到 openapi → 强制应用最严格规则
        //    所有 PII 字段按 "所有角色" 脱敏

        return OpenApiResponse.success(order);
    }
}
```

#### 4.3 异常检测与封禁

```java
/**
 * 异常行为检测 —— 自动封禁恶意应用
 *
 * 触发条件（任一）：
 *   - 签名失败连续 > 10 次（5min 内）
 *   - QPS 超限连续 > 30 次（5min 内）
 *   - 调用未授权 API > 5 次
 *
 * 处理方式：
 *   首次违规 → 仅告警
 *   二次违规（24h 内）→ 临时封禁 30min
 *   三次违规（7d 内）→ 人工审核，永久封禁
 */
@Component
public class AnomalyDetection {

    private final StringRedisTemplate redis;

    public void recordSignatureFailure(String appKey, String reason) {
        String key = "openapi:anomaly:sign_fail:" + appKey;
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            redis.expire(key, Duration.ofMinutes(5));
        }
        if (count >= 10) {
            // 自动封禁 30min
            String banKey = "openapi:banned:" + appKey;
            redis.opsForValue().set(banKey, "sign_failure", Duration.ofMinutes(30));
            // 告警通知
            alertService.send(new Alert(AlertLevel.P2,
                "OpenAPI 应用自动封禁: " + appKey, reason));
        }
    }
}
```

### 5. Prometheus 指标

```yaml
metrics:
  - name: openapi_request_total
    type: counter
    labels: [app_key, api_path, http_method, http_status]
    help: "外部 API 总调用次数"

  - name: openapi_request_duration_ms
    type: histogram
    labels: [app_key, api_path]
    help: "外部 API 调用延迟分布"
    buckets: [5, 10, 20, 50, 100, 200, 500, 1000, 2000]

  - name: openapi_quota_remaining
    type: gauge
    labels: [app_key, quota_type]  # quota_type: daily(日配额/Quota) / qps(QPS限流/Rate Limiting)
    help: "应用剩余配额"

  - name: openapi_sign_failure_total
    type: counter
    labels: [app_key, reason]
    help: "签名失败次数"

  - name: openapi_banned_apps
    type: gauge
    help: "当前被封禁的应用数"

  - name: openapi_active_apps
    type: gauge
    help: "当前活跃的应用数"

  - name: openapi_version_distribution
    type: gauge
    labels: [api_version]
    help: "API 版本调用分布"
```

---

## 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| **P1 API 版本化** | 定义版本规范 + 内部 API 迁移到 /api/v1 + Gateway 路由配置 + VersionDeprecationFilter | 2d | 内部 API 全量版本化 |
| **P2 外部 Gateway** | 独立 External Gateway 实例部署 + HMAC 签名认证 + Quota 检查 + 路由配置 | 3d | 外部 Gateway 上线 |
| **P3 开发者门户** | 应用注册表 + 开发者管理后台 + AppKey/AppSecret 生成 + 沙箱环境 | 2d | 开发者自助入驻 |
| **P4 API 文档 + SDK** | OpenAPI 定义 + Swagger UI + SDK 自动生成 CI + 开发者文档 | 1.5d | API 文档门户 |
| **P5 监控 + 审计** | Prometheus 指标 + 调用计量表 + 异常检测 + Grafana 开发者看板 | 1.5d | 开发者监控仪表盘 |
| **P6 安全加固** | IP 白名单 + 自动封禁 + 审计日志 + 渗透测试 | 1d | 安全审计报告 |

**总计：11 人天**

---

## 上线检查清单

### API 版本化
- [ ] 所有内部 API 路径已加上版本前缀（`/api/v1/...`）
- [ ] Gateway 版本路由配置正确（v1 → order-core-v1, v2 → order-core）
- [ ] 废弃版本的 Sunset/Warning 响应头验证通过
- [ ] 无版本路径的旧端点返回 301 到新版

### 外部 Gateway
- [ ] 外部 Gateway 独立部署，与内部 Gateway 隔离
- [ ] HMAC-SHA256 签名认证全链路验证通过
- [ ] Nonce 防重放机制验证通过（同一 nonce 第二次请求被拒绝）
- [ ] 时间戳误差 ±5min 验证通过
- [ ] IP 白名单过滤验证通过
- [ ] QPS 限流和日配额限制验证通过
- [ ] 超出配额返回正确的 ErrorCode（4001/4002）

### 开发者门户
- [ ] 应用注册 → 审核 → 上线流程走通
- [ ] AppSecret 仅创建时展示一次（后续不可查看）
- [ ] 密钥轮换流程验证通过（新旧密钥过渡期）
- [ ] 沙箱环境与生产环境数据隔离
- [ ] 开发者仅可查看自己应用的数据

### API 文档
- [ ] OpenAPI 规范覆盖所有外部接口
- [ ] Swagger UI 在线调试工具可用
- [ ] 多语言 SDK（Java/Python/PHP）编译通过
- [ ] 错误码说明文档完整

### 安全
- [ ] 签名失败连续 10 次 → 自动封禁验证通过
- [ ] 外部 API 响应中 PII 字段已脱敏（与 ADR-023 一致）
- [ ] 审计日志记录所有外部请求
- [ ] 渗透测试通过（重放/篡改/越权等场景）

---

## 与其他 ADR 的关系

| ADR | 关系 |
|-----|------|
| **ADR-023**（动态数据脱敏） | 外部 API 响应必须脱敏，遵循 ADR-023 的脱敏规则 |
| **ADR-022**（全链路灰度） | 灰度版本的外部 API 通过版本路由隔离，不影响稳定版 |
| **ADR-015**（容量规划） | 外部 Gateway 的限流与配额配置影响容量规划模型 |
| **ADR-018**（监控大盘） | 开放平台调用统计作为监控大盘的补充面板 |
| **ADR-019**（异步任务） | 外部 API 的批量导出任务使用 ADR-019 的异步任务中心 |
| **security.puml** | 外部 Gateway 签名认证作为安全架构 L2 的补充 |
| **context-diagram.puml** | 本 ADR 是 open_api 系统边界的完整设计实现 |
| **ADR-030**（全局幂等框架） | Nonce 去重机制将在 ADR-030 落地后统一迁移，详见第 2.2 节签名认证流程 |

---

## 附录：C4 容器图补充（External Gateway）

```puml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml

title 外部网关容器图 — 开放平台

Person(developer, "第三方开发者", "ISV / 合作方")

System_Boundary(openapi, "开放平台") {
    Container(ext_gw, "External Gateway", "Spring Cloud Gateway", "签名验证、限流、配额、审计")
    Container(dev_portal, "开发者门户", "Vue + Spring Boot", "应用管理、API 文档、调用统计")
    Container(sandbox, "沙箱环境", "隔离的 order-core", "模拟数据、联调测试")
    ContainerDb(app_db, "应用注册表", "OceanBase", "AppKey/Secret、配额、权限")
    ContainerDb(usage_db, "调用计量", "OceanBase", "每小时粒度调用统计")
}

System_Boundary(omp, "订单中台") {
    Container(internal_gw, "内部 Gateway", "Spring Cloud Gateway", "JWT 鉴权、内部路由")
    Container(order_core, "order-core", "Spring Boot + Dubbo", "订单核心服务")
}

Rel(developer, ext_gw, "HTTPS 签名", "openapi.omplatform.com")
Rel(developer, dev_portal, "HTTPS", "developer.omplatform.com")
Rel(ext_gw, internal_gw, "内部转发", "可信网络")
Rel(ext_gw, app_db, "查询应用信息", "JDBC")
Rel(ext_gw, usage_db, "写入调用计量", "JDBC/异步")
Rel(dev_portal, app_db, "管理应用", "JDBC")
Rel(sandbox, order_core, "沙箱隔离调用", "隔离网络")

@enduml
```
