# ADR-029: 内部 Gateway 设计

## 状态
已接受

## 背景

### 现状分析

当前架构中只有一个 API Gateway 实例（`container-diagram.puml`），承担所有流量的路由、限流和鉴权职责。ADR-025 覆盖了外部 Gateway 的详细设计（HMAC 签名验证、AppKey 凭证、配额管理、开发者门户），但内部 Gateway 的完整架构未设计。现有设计仅定义了 GatewayAuthFilter（ADR-026）作为 JWT 鉴权的单一插件，缺乏完整的插件链架构。

**存在的问题**：

1. **Gateway 职责边界模糊**：内部前端（买家 APP/商家后台/运营后台）和第三方开发者共用同一 Gateway，限流策略和认证方式难以差异化配置
2. **路由配置硬编码**：当前假设路由配置为代码静态定义，缺少动态变更能力和灰度路由支持
3. **缺少内部限流熔断**：Gateway 层没有针对内部服务的限流和熔断机制，个别服务过载时可能级联影响整个 Gateway
4. **插件扩展架构缺失**：GatewayAuthFilter 是独立实现，没有定义插件 SPI 扩展点和优先级规范，后续增加新的横切关注点（审计日志、请求追踪、响应包装）难以标准化
5. **内部 API 文档分散**：各服务各自暴露 Swagger，缺少统一的 API 文档聚合入口

### 目标

1. 定义内部 Gateway 与外部 Gateway 的职责边界和部署拓扑
2. 设计 Apollo 驱动的动态路由配置机制
3. 设计 Gateway 层限流和熔断策略（Sentinel）
4. 定义 GlobalFilter 插件链 SPI 扩展架构
5. 设计内部 API 文档聚合方案

## 决策

### 方案对比

| 维度 | 方案 A：单 Gateway 职责分离 | 方案 B：双 Gateway 独立部署 | 方案 C：服务网格（Istio） |
|------|---------------------------|---------------------------|------------------------|
| 架构 | 一个 Gateway 实例，内部 Filter 判断来源 | 两个独立 Gateway 实例，各自配置 | Sidecar 代理替代 Gateway |
| 隔离性 | 低（共享进程空间） | 高（完全隔离） | 高（独立 Sidecar 进程） |
| 限流策略 | 需在 Filter 中区分来源 | 各自独立配置，互不影响 | Istio 原生 rate limiting |
| 部署复杂度 | 低（1 个实例） | 中（2 个实例，共享 Nacos/Apollo） | 高（需要 Istio 控制面） |
| 运维成本 | 低 | 低~中 | 高 |
| 灵活度 | 中（来自同一代码库） | 高（可各自独立演进） | 中（受 Istio 能力限制） |
| 团队掌握度 | 已熟悉 Spring Cloud Gateway | 已熟悉 Spring Cloud Gateway | 无，需从头学习 |

**选择：方案 B（双 Gateway 独立部署）**

**选型理由**：
- 与 ADR-025 外部 Gateway 设计一致（外部 Gateway 已是独立实例），架构对齐
- 内部 Gateway 的限流阈值和熔断策略不应受外部流量影响
- 两个 Gateway 可独立扩缩容（外部流量波动大，内部相对稳定）
- 运维成本可控（共享 Nacos/Apollo/监控基础设施，仅 Gateway 实例分离）
- 当前团队已有 Spring Cloud Gateway 经验，无需引入新基础设施

### 选型决策表

| 决策点 | 选项 | 选择 |
|-------|------|------|
| 路由配置源 | Apollo / 本地 yaml / Nacos | **Apollo**（动态推送，符合现有架构模式） |
| 内部限流引擎 | Guava RateLimiter / Redis Lua / Sentinel | **Sentinel**（与 Dubbo 生态集成最佳，支持熔断） |
| 熔断机制 | Hystrix / Resilience4j / Sentinel | **Sentinel**（限流熔断统一框架） |
| 插件架构 | 自定义 FilterChain / GatewayFilter SPI | **Spring Cloud Gateway GlobalFilter**（扩展现有模式） |
| API 文档 | Swagger / Knife4j / SpringDoc | **Knife4j**（多模块聚合，UI 友好） |

## 详细设计

### 1. 内部 Gateway 与外部 Gateway 职责划分

```
┌─────────────────────────────────────────────────────┐
│                   客户端流量                           │
├──────────────┬──────────────────┬───────────────────┤
│  买家 APP    │  商家后台         │  第三方开发者      │
│  小程序/Web  │  运营后台         │  Open API         │
└──────┬───────┴──────┬───────────┴────────┬──────────┘
       │              │                     │
       ▼              ▼                     ▼
┌──────────────┐ ┌───────────┐ ┌──────────────────┐
│ Internal GW  │ │ Internal  │ │ External GW      │
│ (买家端)      │ │ GW(管理端) │ │ (Open API)       │
│ JWT 认证     │ │ JWT 认证  │ │ HMAC 签名验证     │
│ 内部限流     │ │ 管理权限  │ │ AppKey 配额       │
│ 路由到服务   │ │ 审计日志  │ │ 沙箱环境          │
└──────┬───────┘ └─────┬─────┘ └────────┬─────────┘
       │               │                │
       └───────────────┼────────────────┘
                       ▼
              ┌──────────────────┐
              │  内部服务集群     │
              │  (Dubbo/Spring)  │
              └──────────────────┘
```

**流量路径**：
- **买家/商家/运营** → Internal Gateway（JWT 认证） → 内部服务
- **第三方开发者** → External Gateway（HMAC 签名验证） → 内部服务（数据脱敏）
- **内部服务间调用** → Dubbo 直连（不经过 Gateway）

**独立扩缩容策略**：
- Internal Gateway：HPA min=2, max=6（基于请求量）
- External Gateway：HPA min=2, max=10（基于请求量，波动更大）
- 共享 Nacos 注册中心 + Apollo 配置中心 + Prometheus 监控

### 2. Apollo 驱动的动态路由配置

**路由配置存储**：Apollo 命名空间 `gateway.routes`

```json
{
  "routes": [
    {
      "id": "order-service",
      "uri": "dubbo://order-core",
      "predicates": [
        { "name": "Path", "args": { "pattern": "/api/v1/orders/**" } }
      ],
      "filters": [
        { "name": "StripPrefix", "args": { "parts": 2 } },
        { "name": "AuthFilter" },
        { "name": "RateLimitFilter", "args": { "qps": 5000 } }
      ],
      "metadata": {
        "service": "order-core",
        "version": "v1",
        "timeout": 5000
      }
    }
  ],
  "global": {
    "defaultTimeout": 3000,
    "retryOnFailure": true,
    "retryCount": 1
  }
}
```

**Java 配置映射**：

```java
@Component
public class ApolloRouteRefresh implements ApplicationEventPublisherAware {
    
    private static final String ROUTE_NAMESPACE = "gateway.routes";
    private static final String ROUTE_CONFIG_KEY = "routes";
    
    private ApplicationEventPublisher publisher;
    
    @PostConstruct
    public void init() {
        Config config = ConfigService.getConfig(ROUTE_NAMESPACE);
        config.addChangeListener(changeEvent -> {
            // 路由配置变更时刷新 Gateway 路由表
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
        });
    }
    
    @Bean
    public RouteDefinitionLocator dynamicRouteLocator() {
        return new RouteDefinitionLocator() {
            @Override
            public Flux<RouteDefinition> getRouteDefinitions() {
                String routesJson = ConfigService.getConfig(ROUTE_NAMESPACE)
                    .getProperty(ROUTE_CONFIG_KEY, "[]");
                List<RouteDefinition> routes = JSON.parseArray(routesJson, RouteDefinition.class);
                return Flux.fromIterable(routes);
            }
        };
    }
}
```

**灰度路由**（与 ADR-022 衔接）：

```yaml
# Apollo gateway.routes 命名空间
route-order-gray:
  id: "order-service-gray"
  uri: "dubbo://order-core-gray"        # 灰度版本
  predicates:
    - name: "Header"
      args:
        header: "x-gray-tag"
        regexp: "force-canary"
  filters:
    - name: "StripPrefix"
      args: { parts: 2 }
  metadata:
    version: "gray"
```

### 3. Sentinel 限流方案

**核心规则配置**：

```java
@Configuration
public class SentinelGatewayConfig {
    
    @PostConstruct
    public void initGatewayRules() {
        // 1. API 分组：按服务维度定义
        Set<ApiDefinition> apis = new HashSet<>();
        apis.add(new ApiDefinition("order-core")
            .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                add(new ApiPathPredicateItem().setPattern("/api/v1/orders/**"));
            }}));
        apis.add(new ApiDefinition("payment")
            .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                add(new ApiPathPredicateItem().setPattern("/api/v1/payments/**"));
            }}));
        GatewayApiDefinitionManager.loadApiDefinitions(apis);
        
        // 2. 流控规则：按 API 分组 + QPS
        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(new GatewayFlowRule("order-core")
            .setCount(5000)              // QPS 5000
            .setIntervalSec(1)           // 1 秒窗口
            .setBurst(500)               // 允许 500 突发
            .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM)
                .setFieldName("buyerId")));
        rules.add(new GatewayFlowRule("payment")
            .setCount(2000)
            .setIntervalSec(1)
            .setBurst(200));
        GatewayRuleManager.loadRules(rules);
    }
}
```

**Apollo 动态推送阈值**：

```java
@Component
public class SentinelRuleApolloUpdater {
    
    private static final String NAMESPACE = "gateway.sentinel-rules";
    
    @PostConstruct
    public void init() {
        Config config = ConfigService.getConfig(NAMESPACE);
        
        // 监听流控规则变更
        config.addChangeListener(changeEvent -> {
            String newRules = config.getProperty("flow-rules", "[]");
            List<GatewayFlowRule> rules = JSON.parseArray(newRules, GatewayFlowRule.class);
            GatewayRuleManager.loadRules(new HashSet<>(rules));
        });
        
        // 初始加载
        String rulesJson = config.getProperty("flow-rules", "[]");
        List<GatewayFlowRule> rules = JSON.parseArray(rulesJson, GatewayFlowRule.class);
        if (!rules.isEmpty()) {
            GatewayRuleManager.loadRules(new HashSet<>(rules));
        }
    }
}
```

**限流降级响应**：

```java
@Component
public class SentinelFallbackHandler implements BlockRequestHandler {
    
    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable t) {
        // 记录限流指标
        MetricsCollector.increment("omplatform_gateway_rate_limit_total",
            Tags.of("route", getRouteId(exchange)));
        
        return ServerResponse.status(429)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(new HashMap<String, Object>() {{
                put("code", 429001);
                put("message", "请求过于频繁，请稍后重试");
                put("requestId", exchange.getRequest().getId());
            }}));
    }
}
```

### 4. Sentinel 熔断与隔离

**熔断规则**：

```java
// 熔断规则（按 Dubbo 服务维度）
@Configuration
public class SentinelDegradeConfig {
    
    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        
        // 慢调用比例：1s 内超过 50% 的请求 > 100ms → 熔断 10s
        rules.add(new DegradeRule("order-core")
            .setGrade(RuleConstant.DEGRADE_GRADE_RT)
            .setCount(100)           // 最大 RT 100ms
            .setTimeWindow(10)       // 熔断时长 10s
            .setMinRequestAmount(10) // 触发熔断的最小请求数
            .setSlowRatioThreshold(0.5)); // 慢调用比例阈值
        
        // 异常比例：1s 内异常比例 > 30% → 熔断 30s
        rules.add(new DegradeRule("payment")
            .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
            .setCount(0.3)           // 异常比例 30%
            .setTimeWindow(30)       // 熔断时长 30s
            .setMinRequestAmount(20));
        
        DegradeRuleManager.loadRules(rules);
    }
}
```

**熔断恢复探测**：

```
熔断状态流转：
  CLOSED（正常）
    → 触发规则 → OPEN（熔断，请求快速失败）
    → timeWindow 到期 → HALF_OPEN（半开，放行探测请求）
      → 探测成功 → CLOSED（恢复）
      → 探测失败 → OPEN（重新熔断）
```

**熔断降级行为**：

```java
// Gateway 层熔断降级：返回缓存数据或友好提示
@Component
public class DegradeFallbackFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String routeId = getRouteId(exchange);
        
        if (DegradeRuleManager.hasDegradeRule(routeId) && isDegraded(routeId)) {
            // 返回降级响应，不走后端服务
            return ServerResponse.status(503)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new HashMap<String, Object>() {{
                    put("code", 503001);
                    put("message", "服务暂不可用，请稍后重试");
                }}).flatMap(response -> response.writeTo(exchange, new ResponseContext()));
        }
        
        return chain.filter(exchange);
    }
}
```

### 5. Gateway GlobalFilter 链 SPI 架构

**Filter 链执行顺序**：

```
请求 → ① GatewayAuthFilter (Order=-2000) : JWT 校验 + 角色解析
     → ② BizScopeFilter (Order=-1500)    : 注入 biz_scope 上下文
     → ③ IdempotencyFilter (Order=-1000) : 幂等 Key 检查 (ADR-030)
     → ④ RateLimitFilter (Order=-500)    : Sentinel 限流
     → ⑤ DegradeFilter (Order=0)         : 熔断状态检查
     → ⑥ VersionRouteFilter (Order=500)  : 灰度版本路由 (ADR-022)
     → ⑦ AuditLogFilter (Order=1000)     : 请求审计日志
     → ⑧ 路由到后端服务
     → ⑨ ResponseWrapperFilter (Order=2000): 统一响应包装
```

**SPI 扩展点定义**：

```java
/**
 * Gateway 插件 SPI 接口。
 * 所有横切关注点实现此接口，通过 Spring 自动注入到 FilterChain。
 * 
 * order 值约定：
 *   -2000 ~ -1001：认证/授权类
 *   -1000 ~ -1：   限流/熔断/幂等
 *     0  ~  999：   路由/版本
 *    1000 ~  2000： 日志/审计/响应
 */
public interface GatewayPlugin extends Ordered {
    
    /**
     * 插件过滤逻辑。
     * @return CONTINUE 继续执行后续插件；BLOCK 终止请求并返回响应
     */
    GatewayPluginResult filter(ServerWebExchange exchange, GatewayPluginChain chain);
    
    /**
     * 插件名称，用于监控和日志标识。
     */
    String getName();
}

// 插件注册（Spring Boot AutoConfiguration）
@Configuration
public class GatewayPluginAutoConfiguration {
    
    @Autowired(required = false)
    private List<GatewayPlugin> plugins = Collections.emptyList();
    
    @Bean
    public GatewayPluginChain gatewayPluginChain() {
        // 按 Order 排序后构建责任链
        plugins.sort(Comparator.comparingInt(GatewayPlugin::getOrder));
        return new DefaultGatewayPluginChain(plugins);
    }
}
```

**Filter 实现示例**：

```java
@Component
@Order(-1500)
public class BizScopeFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从 JWT（已在 AuthFilter 中解析）获取 biz_scope
        String bizScope = exchange.getAttribute("biz_scope");
        if (bizScope != null) {
            // 写入请求头，传递给后端服务
            exchange.getRequest().mutate()
                .header("X-Biz-Scope", bizScope);
            // 写入 MDC 用于日志关联
            MDC.put("bizScope", bizScope);
        }
        return chain.filter(exchange);
    }
}
```

### 6. Knife4j 内部 API 文档聚合

**多模块聚合配置**：

```yaml
# Gateway 应用配置
spring:
  cloud:
    gateway:
      routes:
        - id: "knife4j-order"
          uri: "lb://order-core"
          predicates:
            - Path=/v3/api-docs/order-core/**
          filters:
            - RewritePath=/v3/api-docs/order-core/(?<path>.*), /v3/api-docs/$\{path}
```

```java
@Configuration
public class Knife4jAggregationConfig {
    
    @Bean
    public List<GroupedOpenApi> apis() {
        // 内部服务 API 分组
        return Arrays.asList(
            GroupedOpenApi.builder()
                .group("order-core")
                .displayName("订单核心服务")
                .pathsToMatch("/api/v1/orders/**")
                .build(),
            GroupedOpenApi.builder()
                .group("payment")
                .displayName("支付服务")
                .pathsToMatch("/api/v1/payments/**")
                .build(),
            GroupedOpenApi.builder()
                .group("inventory")
                .displayName("库存服务")
                .pathsToMatch("/api/v1/inventory/**")
                .build(),
            // ... 其他服务
        );
    }
    
    // 内部 API vs 管理 API 分组
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .displayName("管理后台 API")
            .pathsToMatch("/admin/v1/**")
            .addOpenApiCustomizer(api -> api.info(new Info()
                .title("订单中台管理系统 API")
                .version("v1")
                .description("仅限内部运营和管理员使用")))
            .build();
    }
}
```

**版本标签**：

```java
@Operation(summary = "创建订单", tags = { "v1", "v2" })
@PostMapping("/orders")
public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
    return orderService.createOrder(request);
}

@Operation(summary = "查询订单列表", tags = { "v1" })
@GetMapping("/orders")
public PageResult<OrderSummary> listOrders(OrderQuery query) {
    return orderQueryService.listOrders(query);
}
```

### 7. Gateway 可观测性指标

**Prometheus 指标定义**：

```java
// Gateway 指标注册
@Configuration
public class GatewayMetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> gatewayMetrics() {
        return registry -> {
            // 请求量
            Counter.builder("omplatform_gateway_request_total")
                .tags("route", "unknown", "status", "unknown", "biz_scope", "unknown")
                .register(registry);
            
            // 请求延迟
            Timer.builder("omplatform_gateway_request_duration_ms")
                .publishPercentiles(0.5, 0.9, 0.99)
                .publishPercentileHistogram()
                .register(registry);
            
            // 限流次数
            Counter.builder("omplatform_gateway_rate_limit_total")
                .tags("route", "unknown")
                .register(registry);
            
            // 熔断次数
            Counter.builder("omplatform_gateway_circuit_break_total")
                .tags("route", "unknown", "reason", "unknown")
                .register(registry);
            
            // 路由版本分布
            Gauge.builder("omplatform_gateway_route_version_distribution", () -> 
                routeVersionCounts())
                .tags("route", "unknown", "version", "stable")
                .register(registry);
        };
    }
}
```

**指标矩阵**：

| 指标名称 | 类型 | 标签 | 说明 |
|---------|------|------|------|
| `omplatform_gateway_request_total` | Counter | route, status, biz_scope | 请求总数 |
| `omplatform_gateway_request_duration_ms` | Timer | route, status | 请求延迟 |
| `omplatform_gateway_rate_limit_total` | Counter | route | 限流拦截次数 |
| `omplatform_gateway_circuit_break_total` | Counter | route, reason | 熔断拦截次数 |
| `omplatform_gateway_active_requests` | Gauge | route | 当前活跃请求数 |
| `omplatform_gateway_route_count` | Gauge | - | 路由规则总数 |

## 实施计划

| 阶段 | 任务 | 工时 | 产出 |
|------|------|------|------|
| P1 | Gateway 实例拆分：从单实例拆分为 Internal + External | 1.5d | 双 Gateway 部署配置 + K8s manifest |
| P2 | Apollo 动态路由模块：RouteDefinitionLocator + 变更监听 | 1d | 动态路由 Java 配置 |
| P3 | Sentinel 限流 + 熔断集成：规则配置 + Apollo 动态推送 | 1.5d | Sentinel 规则 + 降级 Handler |
| P4 | GlobalFilter 链 SPI 架构：GatewayPlugin 接口 + 责任链 | 1d | SPI 接口 + AutoConfiguration |
| P5 | Knife4j 多模块聚合：Gateway 侧聚合 + 服务侧 OpenAPI 配置 | 0.5d | 聚合配置 + 分组配置 |
| P6 | Gateway 指标 + container-diagram 更新 | 0.5d | 指标注册 + 容器图更新 |

**合计**：6 人天

## 上线检查清单

- [ ] 基础设施：Internal Gateway 独立部署，与 External Gateway 共享 Nacos/Apollo
- [ ] 基础设施：Internal Gateway HPA 配置（min=2, max=6）
- [ ] 代码：Apollo 动态路由配置 + 变更监听（namespace `gateway.routes`）
- [ ] 代码：Sentinel 限流规则（QPS/并发线程/热点）+ Apollo 动态推送
- [ ] 代码：Sentinel 熔断规则（慢调用/异常比例）+ 半开恢复
- [ ] 代码：GlobalFilter SPI 接口 + 责任链构建
- [ ] 代码：Knife4j 聚合配置 + 内部/管理 API 分组
- [ ] 监控：`omplatform_gateway_` 指标注册 + 大盘
- [ ] 监控：Gateway 429/503 响应告警配置
- [ ] 兼容性：现有 GatewayAuthFilter（ADR-026）迁移到新 Filter 链
- [ ] 兼容性：现有 ADR-022 灰度路由整合到 VersionRouteFilter
- [ ] 文档：`container-diagram.puml` 更新为双 Gateway

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-025** (外部 Gateway) | 内部 Gateway 与外部 Gateway 独立部署，复用基础设施；内部专注于 JWT 认证和内部限流 |
| **ADR-026** (认证授权) | GatewayAuthFilter 重构为内部 Gateway 核心插件之一，复用 JWT 校验逻辑 |
| **ADR-022** (全链路灰度) | VersionRouteFilter 整合灰度路由规则（gray tag → 灰度版本实例） |
| **ADR-030** (幂等框架) | IdempotencyFilter 作为 Filter 链的第三步，在认证之后路由之前拦截 |
| **ADR-027** (可观测性) | Gateway 指标统一使用 `omplatform_gateway_` 前缀，纳入整体观测体系 |
| **ADR-015** (容量规划) | Gateway 限流阈值影响容量模型估算 |
| **container-diagram.puml** | 需将 API Gateway 拆分为 Internal Gateway + External Gateway 两个容器 |
| **deployment.puml** | Gateway 跨 AZ 部署配置需区分内外部实例 |

## 备选方案评估

### 方案 A：单 Gateway 职责分离

单实例运行，通过请求来源 IP/Header 区分内部和外部请求，共用进程空间。

- **优点**：部署简单，运维成本最低
- **缺点**：外部流量波动可能影响内部可用性；限流/熔断策略难以精细隔离；扩缩容时内部/外部需求冲突
- **适用场景**：流量规模小、内部/外部路径明确可区分

### 方案 C：服务网格（Istio）

完全用 Istio Sidecar 替代 Gateway，通过 VirtualService/DestinationRule 配置路由和限流。

- **优点**：Sidecar 级别隔离；原生灰度路由；控制面统一管理
- **缺点**：团队无 Istio 经验；引入 Istio 增加 K8s 集群要求；学习曲线陡峭
- **适用场景**：已有 Istio 基础设施的团队
