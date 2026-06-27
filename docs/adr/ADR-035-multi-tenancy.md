# ADR-035: 多租户架构

## 状态

提议中

## 背景

### 现状分析

当前一次建设仅面向单一平台方（假设为自营电商），所有商户共享同一个逻辑租户。但随着平台业务扩展，可能面临以下多租户场景：

1. **多平台运营**：同一套系统同时服务多个独立平台（如国内电商 + 跨境 + 本地生活）
2. **ISV 部署**：系统作为 SaaS 交付给不同客户
3. **业务线隔离**：同一平台内不同业务线（电商/本地生活/B2B）需要更强的租户级隔离（当前通过 `business_type` 区分）

**现有隔离措施**：

| 维度 | 现有设计 | 是否能支撑多租户 |
|------|---------|----------------|
| 数据库 | 同表 + `business_type` 字段 | ❌ 纯逻辑隔离，跨租户无数据边界 |
| Redis | 公共集群，key 无租户前缀 | ❌ 租户间可能 key 冲突 |
| MQ | 公共 Topic + tag 区分 | ❌ 无租户级消费隔离 |
| ES | 统一索引 + `business_type` 字段 | ❌ 查询需一直带筛选 |
| K8s | 共享集群，namespace 规划中 | ⚠️ namespace 可隔离 |

**当前约束**：所有 ADR（017 物理隔离、023 脱敏、025 API 认证）都假设单一逻辑租户，未预留多租户扩展点。

### 目标

1. 定义多租户模型和隔离级别
2. 设计租户上下文传递机制（从 Gateway 到服务到中间件）
3. 评估是否需要在当前一期实现多租户

### 非目标

- 当前阶段不实现租户间资源隔离（预留设计）
- 不定义租户计费/配额（与 ADR-025 协作）
- 不涉及跨地域部署

## 决策

### 方案对比：多租户模型

| 维度 | 方案 A：Schema 隔离 | 方案 B：数据库隔离 | 方案 C：字段级共享 |
|------|--------------------|------------------|------------------|
| 隔离级别 | 同 DB 不同 Schema | 独立 DB 实例 | 同表 + `tenant_id` |
| 数据安全性 | 高（Schema 天然隔离） | 最高（物理隔离） | 中（应用层 ISOLATION） |
| 共享程度 | 中（共享 DB 实例） | 低（独立资源） | 高（完全共享） |
| 运维复杂度 | 中（Liquibase 每个 Schema） | 高（N 个 DB 集群） | 低（单 DB） |
| 跨租户查询 | 不支持（Union All） | 不支持 | 支持（带 tenant_id 筛选） |
| 扩缩容 | 按 Schema 分组 | 按 DB 实例 | 随业务量整体扩缩 |
| 租户数上限 | 100-500 | 10-30 | 1000+ |
| 成本 | 中 | 高 | 低 |

**选择：方案 A（Schema 隔离）+ 方案 C（字段级共享）混合**

**选型理由**：

- **核心数据（order、payment）**：Schema 隔离。最敏感的金融数据需要租户间硬隔离，误操作不影响其他租户
- **参考数据（商品、类目）**：字段级共享 + `tenant_id` 分片。便于运营统一管理，减少数据冗余
- **日志/监控数据**：字段级共享。存储量大但安全敏感性低，统一存储卸载成本

### 方案对比：租户上下文传递

| 维度 | 方案 A：HTTP Header 透传 | 方案 B：JWT Claim 编码 | 方案 C：Dubbo RpcContext |
|------|------------------------|----------------------|------------------------|
| 来源 | 请求 Header `X-Tenant-Id` | JWT Token 中解析 | Dubbo 内部调用自动传递 |
| Gateway | 直接提取 | 解析 JWT 获取 | N/A（内部） |
| 服务端 | ThreadLocal 获取 | ThreadLocal 获取 | RpcContext attachment |
| 侵入性 | 需修改所有入口 | 无感（JWT 自带） | 需 SPI Filter |
| 适用范围 | HTTP | HTTP | Dubbo |

**选择：方案 A + B + C 组合**

- HTTP 入口：Gateway 从 `X-Tenant-Id` Header 提取，优先级高于 JWT
- JWT 回退：如果 Header 缺失，从 JWT claim `tenant_id` 解析
- Dubbo 内部：通过 `TenantContextFilter`（Provider/Consumer SPI Filter）自动传递 TenantId

### 决策总结

| 决策项 | 选择 | 原因 |
|-------|------|------|
| 隔离模型 | Schema 隔离（核心）+ 字段共享（非核心） | 安全 + 成本平衡 |
| 上下文传递 | Header → JWT → RpcContext | 全链路无侵入 |
| 当前一期是否实现 | **否（延迟到二期）** | 当前业务无强制需求，但架构需预留 |
| Schema 管理 | Liquibase + tenant 模板 + CI 自动化 | 复用 ADR-011 的 DDL 规范 |

## 详细设计

### 1. 租户模型

```java
/**
 * 租户核心模型
 */
public class Tenant {
    private Long id;              // 内部 ID
    private String tenantId;      // 业务标识（如 "ecommerce" / "locallife" / "b2b"）
    private String tenantName;    // 租户名
    private String schemaPrefix;  // 数据库 schema 前缀（如 "t_ecommerce"）
    private TenantStatus status;  // ACTIVE / SUSPENDED / DELETED
    private String plan;          // 套餐（standard / enterprise）
    
    // 隔离配置
    private TenantIsolation isolation;
}

public class TenantIsolation {
    private boolean schemaIsolation;     // 是否使用独立 Schema
    private boolean esSeparateIndex;     // 是否使用独立 ES 索引
    private boolean mqSeparateTopic;     // 是否使用独立 MQ Topic
    private boolean cacheKeyPrefix;      // 是否启用 Redis key 前缀隔离
    private int dbShardCount;            // 分库数量
}
```

**租户注册流程**：

```
租户注册申请
  → 审批（自动或人工）
  → 判断隔离等级（Schema / 字段）
    → Schema 隔离：
      → 创建新 Schema（模板 DDL）
      → 执行 Liquibase
      → 初始化基础数据
    → 字段隔离：
      → tenant_config 表注册
      →（仅初始化基础配置）
  → 租户激活
  → 返回 TenantId
```

### 2. Schema 隔离实现

**数据库架构**：

```
同一 OB 实例（或集群）：
  ├── t_ecommerce/              # Schema：电商租户
  │   ├── order                 # 完全独立
  │   ├── payment
  │   └── inventory
  ├── t_locallife/              # Schema：本地生活租户
  │   ├── order
  │   ├── payment
  │   └── inventory
  ├── shared/                   # 共享 Schema
  │   ├── tenant_config         # 租户配置
  │   ├── product_catalog       # 共享商品目录（字段级 tenant_id）
  │   └── region                # 共享地理数据
  └── ...
```

**Schema 模板 DDL**：

```sql
-- tenant_template DDL（新租户接入时自动执行）
-- 注：实际由 Liquibase changeset 管理

-- order 系列表
CREATE TABLE `${schema_prefix}`.`order` (
    `order_id`           VARCHAR(32)     NOT NULL COMMENT '订单号',
    `buyer_id`           BIGINT          NOT NULL COMMENT '买家 ID',  
    `seller_id`          BIGINT          NOT NULL COMMENT '卖家 ID',
    `status`             VARCHAR(32)     NOT NULL COMMENT '订单状态',
    `total_amount`       DECIMAL(12,2)   NOT NULL COMMENT '订单金额',
    `gmt_create`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`order_id`),
    KEY `idx_buyer` (`buyer_id`, `status`, `gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- payment 系列表
CREATE TABLE `${schema_prefix}`.`payment` (
    `payment_id`     VARCHAR(32)     NOT NULL COMMENT '支付单号',
    `order_id`       VARCHAR(32)     NOT NULL COMMENT '订单号',
    `amount`         DECIMAL(12,2)   NOT NULL COMMENT '支付金额',
    `status`         VARCHAR(32)     NOT NULL COMMENT '支付状态',
    `gmt_create`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`payment_id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';
```

**Liquibase 多 Schema 支持**：

```xml
<!-- liquibase/changelog-master.xml -->
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">
    
    <!-- 共享 Schema -->
    <include file="changelog/shared/001-tenant-config.xml"/>
    <include file="changelog/shared/002-product-catalog.xml"/>
    
    <!-- 租户 Schema 模板（通过 Liquibase 参数化）-->
    <include file="changelog/tenant/001-order-tables.xml"/>
    <include file="changelog/tenant/002-payment-tables.xml"/>
    <include file="changelog/tenant/003-inventory-tables.xml"/>
</databaseChangeLog>
```

```xml
<!-- changelog/tenant/001-order-tables.xml（参数化 Schema） -->
<databaseChangeLog>
    <changeSet id="tenant-001" author="omplatform">
        <sql>
            CREATE SCHEMA IF NOT EXISTS `${schemaPrefix}`;
        </sql>
        <createTable schemaName="${schemaPrefix}" tableName="order">
            <column name="order_id" type="VARCHAR(32)">
                <constraints primaryKey="true"/>
            </column>
            <column name="buyer_id" type="BIGINT">
                <constraints nullable="false"/>
            </column>
            <!-- ... -->
        </createTable>
    </changeSet>
</databaseChangeLog>
```

### 3. 租户上下文传递

**Gateway TenantFilter**：

```java
@Component
@Order(-200)  // 最早执行，在 AuthFilter 之前
public class TenantFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 从请求头获取
        String tenantId = exchange.getRequest().getHeaders()
            .getFirst("X-Tenant-Id");

        if (tenantId == null || tenantId.isEmpty()) {
            // 2. 从 JWT claim 获取（如果已解析）
            String token = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token.substring(7))
                        .getPayload();
                    tenantId = claims.get("tenant_id", String.class);
                } catch (Exception ignored) {}
            }
        }

        if (tenantId != null && !tenantId.isEmpty()) {
            // 3. 校验租户有效性
            Tenant tenant = tenantService.getTenant(tenantId);
            if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
                return writeForbidden(exchange, "Invalid or inactive tenant");
            }

            // 4. 设置到 ThreadLocal（后续 Filter 和服务通过 TenantContext 读取）
            TenantContext.set(tenant);
            
            // 5. 写入请求头透传到下游服务
            exchange.getRequest().mutate()
                .header("X-Tenant-Id", tenantId);
        }

        return chain.filter(exchange).then(Mono.fromRunnable(TenantContext::clear));
    }
}
```

**Dubbo 租户传递 Filter**：

```java
@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER}, order = -9000)
public class TenantContextFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (CommonConstants.CONSUMER.equals(invoker.getUrl().getParameter("side"))) {
            // Consumer 端：将 TenantContext 写入 RpcContext
            Tenant tenant = TenantContext.get();
            if (tenant != null) {
                RpcContext.getClientAttachment()
                    .setAttachment("X-Tenant-Id", tenant.getTenantId());
                RpcContext.getClientAttachment()
                    .setAttachment("X-Tenant-Schema", tenant.getSchemaPrefix());
            }
        } else {
            // Provider 端：从 RpcContext 恢复 TenantContext
            String tenantId = RpcContext.getServerAttachment()
                .getAttachment("X-Tenant-Id");
            if (tenantId != null && TenantContext.get() == null) {
                Tenant tenant = tenantService.getTenant(tenantId);
                if (tenant != null) {
                    TenantContext.set(tenant);
                }
            }
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            if (CommonConstants.PROVIDER.equals(invoker.getUrl().getParameter("side"))) {
                TenantContext.clear();  // Provider 端清理
            }
        }
    }
}
```

**数据源路由**：

```java
/**
 * 动态数据源：根据 TenantContext 中的 Schema 路由到不同 Schema
 */
@Component
public class TenantAwareDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        Tenant tenant = TenantContext.get();
        if (tenant != null && tenant.getIsolation().isSchemaIsolation()) {
            return tenant.getSchemaPrefix();
        }
        // 共享 Schema 回退
        return "shared";
    }
}

// 使用：多数据源配置
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        TenantAwareDataSource dataSource = new TenantAwareDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        // 共享数据源（默认）
        targetDataSources.put("shared", createSharedDataSource());
        // 租户数据源（延迟加载，按需创建）
        targetDataSources.put("t_ecommerce", createSchemaDataSource("t_ecommerce"));
        targetDataSources.put("t_locallife", createSchemaDataSource("t_locallife"));
        
        dataSource.setTargetDataSources(targetDataSources);
        dataSource.setDefaultTargetDataSource(createSharedDataSource());
        return dataSource;
    }
}
```

### 4. ES 多租户

**Schema 隔离租户**：使用独立索引 `orders-t_ecommerce-202606`、`orders-t_locallife-202606`

**字段共享租户**：同索引 + `tenant_id` 字段 + 查询时强制带 Filter

```java
// ES 查询时自动注入租户筛选
@Component
public class TenantElasticsearchInterceptor {

    @Around("execution(* com.omplatform..*Repository.search*(..))")
    public Object injectTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        Tenant tenant = TenantContext.get();
        if (tenant == null) {
            return pjp.proceed();
        }

        // 修改查询条件注入 tenant_id
        Object[] args = pjp.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof NativeSearchQuery query) {
                query.getFilter().add(QueryBuilders.termQuery("tenant_id", tenant.getTenantId()));
            }
        }
        return pjp.proceed();
    }
}
```

### 5. Redis 多租户

```java
// Redis Key 前缀隔离
public class TenantAwareKeyGenerator {

    public static String key(String prefix, String suffix) {
        Tenant tenant = TenantContext.get();
        if (tenant != null && tenant.getIsolation().isCacheKeyPrefix()) {
            return "t:" + tenant.getTenantId() + ":" + prefix + ":" + suffix;
        }
        return prefix + ":" + suffix;
    }
}

// 使用
String orderKey = TenantAwareKeyGenerator.key("order:detail", orderId);
```

### 6. MQ 多租户

```java
// MQ 消息自动注入租户
public class TenantMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) {
        Tenant tenant = TenantContext.get();
        if (tenant != null) {
            message.getProperties().put("X-Tenant-Id", tenant.getTenantId());
        }
        return message;
    }
}

// 消费者端提取租户上下文
@Component
@RocketMQMessageListener(topic = "order-event", consumerGroup = "order-group")
public class OrderEventConsumer implements RocketMQListener<OrderEvent> {

    @Override
    public void onMessage(OrderEvent event) {
        String tenantId = RocketMQContext.getProperties().get("X-Tenant-Id");
        // 恢复租户上下文
        TenantContext.set(tenantService.getTenant(tenantId));
        try {
            // 处理消息
        } finally {
            TenantContext.clear();
        }
    }
}
```

### 7. 中间件资源映射

| 中间件 | Schema 隔离 | 字段级隔离 | 说明 |
|--------|-----------|-----------|------|
| OceanBase | 不同 Schema | 共享 Schema + `tenant_id` | 建表模板自动化 |
| ES | 独立索引 `orders-{tenant}` | 共享索引 + `tenant_id` | 强制查询筛选项 |
| Redis | 独立前缀 `t:{tenant}:` | 独立前缀 `t:{tenant}:` | 轻度方案统一前缀 |
| MQ | 独立 Topic | 共享 Topic + `X-Tenant-Id` | 消费端过滤 |
| Apollo | 独立 Namespace `{tenant}.xxx` | 共享 + 配置 key 带 `{tenant}` | 按需隔离配置 |
| Nacos | 共享（metadata 记录租户） | 共享 | 服务注册不变 |

### 8. 管理 API

```java
@RestController
@RequestMapping("/admin/v1/tenants")
public class TenantAdminController {

    @PostMapping
    public Tenant createTenant(@Valid @RequestBody CreateTenantRequest request) {
        // 1. 校验 tenantId 唯一性
        // 2. 创建 Schema（如果需要）
        // 3. 执行 Liquibase 租户模板
        // 4. 初始化 ES 索引模板
        // 5. 初始化默认配置
        // 6. 返回 Tenant 对象
    }

    @GetMapping("/{tenantId}")
    public Tenant getTenant(@PathVariable String tenantId) {
        return tenantService.getTenant(tenantId);
    }

    @PostMapping("/{tenantId}/suspend")
    public void suspendTenant(@PathVariable String tenantId) {
        // 暂停租户：禁掉所有请求，保留数据
    }

    @DeleteMapping("/{tenantId}")
    public void deleteTenant(@PathVariable String tenantId) {
        // 删除前：90d 观察期
        // 删除：清理 Schema / 索引 / 缓存
    }
}
```

## 实施计划

| 阶段 | 任务 | 工时 | 依赖 |
|------|------|------|------|
| **Phase 0（预留设计）** | | 2d | — |
| | TenantContext（ThreadLocal + Filter 链） | 1d | — |
| | X-Tenant-Id Header 规范定义 | 0.5d | — |
| | 现有中间件租户预留分析（ES/Redis/MQ） | 0.5d | — |
| **Phase 1（框架）** | | 5d | Phase 0 |
| | Tenant 模型 + 注册 API（CRUD） | 1d | Phase 0 |
| | Gateway TenantFilter | 0.5d | — |
| | Dubbo TenantContextFilter（Consumer/Provider） | 1d | — |
| | TenantAwareDataSource（动态路由） | 1.5d | — |
| | ES 租户拦截器 + Redis 前缀 | 0.5d | — |
| | 配置 + 验证 | 0.5d | — |
| **Phase 2（自动化）** | | 4d | Phase 1 |
| | Liquibase 租户 Schema 模板 | 1.5d | ADR-011 |
| | CI 自动化租户接入流程 | 1d | — |
| | 租户级管理 API + 管理后台 | 1d | — |
| | 文档 + 指南 | 0.5d | — |

**合计**：11 人天（Phase 0-2）

**第一阶段（Phase 0）估值**：2 人天。建议在主架构阶段完成 Phase 0，仅做预留设计，不实现多租户逻辑。

## 上线检查清单

- [ ] TenantContext ThreadLocal + finally 清理（防止内存泄漏）
- [ ] Gateway TenantFilter 在 AuthFilter 之前执行（Order=-200）
- [ ] Dubbo TenantContextFilter 在业务 Filter 之前（Order=-9000）
- [ ] TenantAwareDataSource 回退逻辑（无 TenantContext 时走共享 Schema）
- [ ] ES 查询自动注入 tenant_id（防止跨租户数据泄露）
- [ ] Redis key 前缀隔离（无前缀命中检查）
- [ ] MQ 消费端 X-Tenant-Id 透传
- [ ] Tenant 管理 API 鉴权（仅 admin 角色可创建/修改租户）
- [ ] TenantContext.clear() 在各类 Filter 的 finally 块中执行

## 与现有文档的关联

| 文档 | 关系 |
|------|------|
| **ADR-017**（业务线物理隔离） | 多租户是 ADR-017 的高阶扩展——从业务线隔离升级到完整租户隔离 |
| **ADR-011**（DDL 治理） | Liquibase 管理多 Schema DDL，参数化模板 |
| **ADR-023**（数据脱敏） | 脱敏规则需支持按租户定制 |
| **ADR-025**（API 网关） | 外部 Gateway 需支持 X-Tenant-Id 传递 |
| **ADR-026**（认证授权） | JWT 加入 tenant_id claim；RBAC 扩展为 租户 × 角色 |
| **ADR-016**（多 AZ 缓存） | 缓存 key 前缀隔离与多 AZ 策略配合 |
| **ADR-022**（灰度发布） | 灰度流量需考虑租户维度的灰度 |

## 备选方案评估

### 方案 B：数据库隔离

独立 DB 实例为每个租户分配独立的 OB 集群。

- **优点**：最高级别物理隔离；单个租户故障不影响其他租户；租户间资源隔离
- **缺点**：成本线性增长（每租户 5 节点 × 3 AZ）；运维复杂（备份/恢复 × N）；扩缩容不灵活
- **适用场景**：金融合规要求极高、数据不可共享

### 方案 D：纯字段级共享

全表 `tenant_id` 列 + 查询强制筛选。

- **优点**：成本最低、运维最简单、扩展灵活
- **缺点**：无物理隔离边界、SQL 漏写 `tenant_id` 导致数据泄露（Row-Level Security 可缓解但 OB 不完全支持）、软删除/恢复困难
- **适用场景**：租户数 1000+、数据安全要求低的场景
