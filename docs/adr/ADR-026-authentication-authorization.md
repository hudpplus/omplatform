# ADR-026：认证授权体系

## 状态

已接受

---

## 背景

### 现状分析

订单中台安全架构（`security.puml`）在 Layer 2「应用层安全」中列出了 JWT 鉴权、OAuth2、RBAC 角色权限、@Desensitize 脱敏注解等能力，但这些都是概念名词——**没有一份完整的认证授权体系设计**。当前架构在认证授权方面存在以下空白：

**问题 1：角色模型未定义**  
ADR-023（动态数据脱敏）强依赖角色体系来决定字段可见性，但角色有哪些、层级关系如何、优先级规则是什么——这些均未定义。客服（cs）、运营（ops）、财务（finance）、管理员（admin）各能看到什么数据，没有统一口径。

**问题 2：Token 生命周期缺失**  
JWT Token 如何签发？有效期多长？刷新机制是什么？注销/密码变更后如何吊销 Token？这些问题直接影响系统安全性和用户体验。

**问题 3：Dubbo RPC 角色传递缺失**  
Gateway 层解析 JWT 拿到角色后，角色信息如何随 Dubbo RPC 传递到下游服务？当前没有设计。这直接阻塞 ADR-023（DesensitizeContext 需要角色信息）的落地。

**问题 4：多业务线权限隔离缺失**  
ADR-017 实现了多业务线数据物理隔离，但 **没有权限隔离**。一个电商运营人员理论上可以通过 API 查到 B2B 的业务数据。数据隔离了，权限没有。

**问题 5：用户身份与应用凭证混用**  
ADR-025 外部网关引入 AppKey/AppSecret（应用级凭证），但现有体系只有用户级 JWT 一种认证方式。两者如何共存、如何路由到不同的鉴权链路？未定义。

### 目标

1. **统一角色模型**：定义完整的角色层级、权限矩阵和业务线 Scope
2. **Token 生命周期管理**：JWT 发放/刷新/吊销的完整流程
3. **Dubbo 角色传递**：角色信息随 RPC 调用自动传播（与 ADR-022 灰度标签传播同模式）
4. **多业务线权限隔离**：与 ADR-017 BusinessRouter 配合，实现业务线级权限控制
5. **人与应用的认证分离**：用户级 JWT（内部 Gateway）与应用级 HMAC（外部 Gateway）共存

### 术语定义

| 术语 | 说明 |
|------|------|
| **认证（Authentication）** | 验证用户/应用身份的过程，确认"你是谁" |
| **授权（Authorization）** | 确定已认证的主体可以执行哪些操作，确认"你能做什么" |
| **RBAC** | 基于角色的访问控制（Role-Based Access Control） |
| **Scope** | 业务线作用域，限定角色可操作的业务线范围（ecommerce/locallife/b2b） |
| **Access Token** | 短期有效的 JWT Token，携带用户身份和权限信息 |
| **Refresh Token** | 长期有效的 opaque Token，用于获取新的 Access Token |
| **JTI** | JWT Token ID，用于 Token 吊销时标识具体 Token |

---

## 决策

### 认证方案

**主方案：JWT（Access Token）+ OAuth2 模式**

| 对比维度 | JWT + OAuth2（选中） | Session + Cookie | 自签发 Token |
|----------|---------------------|------------------|-------------|
| **无状态性** | ✅ 服务端无 session 存储 | ❌ 需 Redis Session 集群 | ✅ 无状态 |
| **跨服务传递** | ✅ Header 直接携带 | ❌ 需 Cookie 拦截器 | ✅ Header 携带 |
| **吊销能力** | △ 需 Blacklist 机制 | ✅ Redis 删除即吊销 | △ 需 Blacklist |
| **生态兼容** | ✅ OAuth2 标准 + Spring Security | ✅ 但跨服务需改造 | ❌ 无标准 |
| **信息承载** | ✅ 可携带角色/scope | ❌ 仅 session ID | ✅ 可自定义 |

**理由**：JWT 的无状态特性适合 Dubbo + Gateway 的微服务架构，角色信息直接在 Token 中携带，下游服务无需回查认证中心即可完成权限校验。

### 授权模型

**主方案：RBAC + 业务线 Scope（层级角色 + 水平 Scope 的双维度模型）**

```
垂直：角色层级  super_admin > admin > finance > ops > cs > merchant
水平：业务线    ecommerce / locallife / b2b / all
```

**理由**：纯 RBAC 无法解决多业务线边界的控制需求。双维度模型允许"cs 角色 + ecommerce scope"这样的精细权限表达，与 ADR-017 的业务线隔离天然互补。

### Token 传播机制

**方案：Dubbo Consumer/Provider Filter + RpcContext（复用 ADR-022 同模式）**

**理由**：ADR-022 已实现 GrayTag 的 Dubbo Filter 传播模式，认证授权直接复用同一套 SPI 机制，保持架构一致性。

---

## 详细设计

### 1. 角色模型

#### 角色定义

```
super_admin  — 超级管理员，全权限（限 2-3 人，操作审计留痕）
    │
  admin      — 系统管理员，可配置系统参数、查看全量脱敏数据
    │
  finance    — 财务角色，可查看资金相关全量数据（含 PII）
    │
  ops        — 运营角色，可查看订单/退款/售后数据（部分脱敏）
    │
  cs         — 客服角色，可查看用户资料部分可见（严格脱敏）
    │
  merchant   — 商家角色，仅可查看自己店铺订单（按商家权限控制）
```

#### 业务线 Scope

```java
public enum BizScope {
    ECOMMERCE,   // 电商
    LOCAL_LIFE,  // 本地生活
    B2B,         // B2B 供应链
    ALL          // 全业务线（仅 super_admin 和 admin）
}
```

#### 权限矩阵（核心资源）

| 资源 \ 角色 | merchant | cs | ops | finance | admin | super_admin |
|------------|---------|----|-----|---------|-------|-------------|
| 订单查询 | 自己店铺 | 全业务线（脱敏） | 全业务线（部分脱敏） | 全业务线 | 全量 | 全量 |
| 订单详情 PII | ❌ 脱敏 | ❌ 脱敏 | ⚠️ 部分可见 | ✅ 可见 | ✅ 可见 | ✅ 可见 |
| 退款审批 | ❌ | ⚠️ 自己业务线 | ✅ 全业务线 | ❌ | ✅ | ✅ |
| 资金对账 | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| 系统配置 | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| 用户角色管理 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

> 注：此矩阵与 ADR-023 的角色-字段脱敏矩阵相互配合，权限矩阵决定"能否操作/查看该资源"，脱敏矩阵决定"看到该资源时字段的展示形式"。

### 2. Token 生命周期

#### Token 结构

```java
// Access Token — JWT 格式，30 分钟有效期
{
  "sub": "u_10086",           // 用户 ID
  "role": "cs",               // 角色
  "biz_scope": "ecommerce",   // 业务线 scope
  "tenant_id": "1001",        // 商家 ID（merchant 角色时有效）
  "exp": 1718000000,          // 过期时间
  "iat": 1717998200,          // 签发时间
  "jti": "jwt_a1b2c3d4",      // Token ID（吊销用）
  "type": "access"            // 类型标识
}

// Refresh Token — Opaque 格式，7 天有效期
// 存储在 Redis：refresh_token:{hash} → {userId, jti, role, scope, expiresAt}
```

#### Token 发放流程

```
                    ┌──────────┐
                    │ 客户端    │
                    └────┬─────┘
                         │ POST /auth/login (username + password)
                         ▼
                    ┌──────────┐
                    │ Gateway   │──→ JWT 校验放行
                    └────┬─────┘
                         │ 转发到 Auth Service
                         ▼
                    ┌──────────┐
                    │ Auth     │──→ 校验用户凭证（调用用户中心）
                    │ Service  │──→ 校验通过 → 生成 Access + Refresh Token
                    └────┬─────┘
                         │ 返回 Token 对
                         ▼
                    ┌──────────┐
                    │ 客户端    │
                    └──────────┘
                    Access Token 存内存，Refresh Token 存 httpOnly Cookie
```

#### Token 刷新流程

```
客户端 401 → 携带 Refresh Token 请求 /auth/refresh
→ Auth Service 校验 Refresh Token（Redis 中存在且未过期）
→ 吊销旧 Refresh Token
→ 签发新 Access Token + 新 Refresh Token（Token Rotation）
```

#### Token 吊销

```java
// 登出/密码变更/角色变更时吊销
// Redis Blacklist：blacklist:{jti}
// TTL = Access Token 剩余有效期（最大 30min）

public class TokenRevocationService {
    
    public void revokeByUser(String userId, RevokeReason reason) {
        // 1. 从 Redis 查询该用户所有有效的 jti
        Set<String> jtis = redisTemplate.members("user_tokens:" + userId);
        // 2. 全部加入黑名单
        for (String jti : jtis) {
            redisTemplate.opsForValue().set(
                "blacklist:" + jti, 
                reason.name(), 
                30, TimeUnit.MINUTES
            );
        }
        // 3. 删除 Refresh Token
        redisTemplate.delete("refresh_tokens:" + userId);
    }
}
```

### 3. Gateway 鉴权过滤器

```java
@Component
@Order(-2000)  // 与 ADR-029 Gateway Filter 链架构协同，认证/授权类 Filter 统一使用 -2000~-1001 区间，在路由过滤器之前执行
public class GatewayAuthFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 白名单路径跳过鉴权
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 提取并校验 JWT
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange, "Missing token");
        }

        try {
            JwtClaims claims = jwtVerifier.verify(token);
            
            // 检查 Token 是否被吊销
            if (tokenRevocationService.isRevoked(claims.getJti())) {
                return unauthorized(exchange, "Token revoked");
            }

            // 写入 Headers，传递给下游服务
            ServerWebRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Role", claims.get("role"))
                .header("X-Biz-Scope", claims.get("biz_scope"))
                .header("X-Tenant-Id", claims.get("tenant_id"))
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
            
        } catch (JwtExpiredException e) {
            return unauthorized(exchange, "Token expired");
        } catch (JwtVerificationException e) {
            return unauthorized(exchange, "Invalid token");
        }
    }
}
```

#### 白名单路径

```java
private boolean isPublicPath(String path) {
    return path.matches("^(/auth/|/health|/openapi/|/swagger-ui/|/v3/api-docs).*$");
}
```

### 4. Dubbo 角色传播

复用 ADR-022 定义的同一种 SPI Filter 模式，传递粒度扩展到角色信息：

```java
// ========== Consumer 端：发送请求时写入角色 ==========
@Activate(group = "consumer", order = -8000)
public class AuthConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        AuthContext context = AuthContextHolder.get();
        if (context != null) {
            RpcContext.getClientAttachment()
                .setAttachment("X-User-Id", context.getUserId())
                .setAttachment("X-User-Role", context.getRole().name())
                .setAttachment("X-Biz-Scope", context.getBizScope().name());
        }
        return invoker.invoke(invocation);
    }
}

// ========== Provider 端：接收请求时建立上下文 ==========
@Activate(group = "provider", order = -8000)
public class AuthProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String role = RpcContext.getServerAttachment().getAttachment("X-User-Role");
        String scope = RpcContext.getServerAttachment().getAttachment("X-Biz-Scope");
        
        if (role != null) {
            AuthContext context = AuthContext.builder()
                .role(Role.valueOf(role))
                .bizScope(BizScope.valueOf(scope))
                .build();
            AuthContextHolder.set(context);
        }
        
        try {
            return invoker.invoke(invocation);
        } finally {
            AuthContextHolder.clear();  // 防止 ThreadLocal 泄漏
        }
    }
}
```

#### AuthContext（ThreadLocal）

```java
public class AuthContext {
    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();
    
    private final String userId;
    private final Role role;
    private final BizScope bizScope;
    private final String tenantId;  // 商家 ID，仅 merchant 角色有值
    
    public static AuthContext get() { return HOLDER.get(); }
    public static void set(AuthContext ctx) { HOLDER.set(ctx); }
    public static void clear() { HOLDER.remove(); }
    
    // 权限校验快捷方法
    public boolean hasPermission(String resource, String action) {
        return PermissionRegistry.check(role, bizScope, resource, action);
    }
    
    // 用于 ADR-023：脱敏时判断当前角色
    public boolean shouldMask(DesensitizeType type, String... allowedRoles) {
        return !Arrays.asList(allowedRoles).contains(role.name());
    }
}
```

> **与 ADR-023 的衔接**：`AuthContext` 就是 ADR-023 中 `DesensitizeContext` 的数据来源。Gateway 鉴权时解析 JWT 得到角色 → AuthConsumerFilter 写入 RpcContext → Provider 端接收后建立 AuthContext → Jackson 序列化时通过 AuthContext 判断角色并决定是否脱敏。

### 5. 权限校验（AOP）

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String role() default "";          // 要求的最低角色
    String bizScope() default "";      // 要求的业务线 scope
    String resource() default "";      // 资源类型
    String action() default "read";    // 操作类型
}
```

```java
@Aspect
@Component
public class PermissionCheckAspect {
    
    @Around("@annotation(permission)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequirePermission permission) throws Throwable {
        AuthContext ctx = AuthContext.get();
        if (ctx == null) {
            throw new AccessDeniedException("No auth context");
        }
        
        // 角色级别校验：当前角色 >= 要求角色
        if (!permission.role().isEmpty()) {
            if (ctx.getRole().ordinal() < Role.valueOf(permission.role()).ordinal()) {
                throw new AccessDeniedException("Insufficient role: require " + permission.role());
            }
        }
        
        // 业务线 scope 校验
        if (!permission.bizScope().isEmpty()) {
            BizScope required = BizScope.valueOf(permission.bizScope());
            if (ctx.getBizScope() != BizScope.ALL && ctx.getBizScope() != required) {
                throw new AccessDeniedException("Biz scope mismatch");
            }
        }
        
        return pjp.proceed();
    }
}
```

#### 使用示例

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/{id}")
    @RequirePermission(role = "cs", bizScope = "ecommerce")
    public OrderDTO getOrder(@PathVariable String id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/{id}/refund-approve")
    @RequirePermission(role = "ops", bizScope = "ALL", action = "approve")
    public void approveRefund(@PathVariable String id, @RequestBody RefundApproveRequest req) {
        refundService.approve(id, req);
    }
}
```

### 6. 多业务线权限隔离

与 ADR-017 的 `BusinessRouter` 配合实现：

```java
// ADR-017 的 BusinessRouter 增强：根据用户权限过滤数据
@Aspect
@Component
public class BizScopeDataFilter {

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping)")
    public Object filterByBizScope(ProceedingJoinPoint pjp) throws Throwable {
        AuthContext ctx = AuthContext.get();
        
        // super_admin/admin 可以查看所有业务线
        if (ctx.getBizScope() == BizScope.ALL) {
            return pjp.proceed();
        }
        
        // 非全业务线角色 → 在查询条件中追加 biz_scope 过滤
        BizScopeContextHolder.set(ctx.getBizScope());
        try {
            return pjp.proceed();
        } finally {
            BizScopeContextHolder.clear();
        }
    }
}
// 最终在 DAO 层：WHERE biz_scope = #{bizScope}
```

### 7. 人与应用认证共存

```yaml
内部 Gateway（/api/*）：
  认证方式：JWT（用户级）
  鉴权流程：GatewayAuthFilter → 校验 JWT → 建立 AuthContext → 路由到服务

外部 Gateway（/openapi/*）：
  认证方式：HMAC-SHA256（应用级，定义于 ADR-025）
  鉴权流程：SignatureAuthFilter → 校验签名 → 限制到应用级权限 → 路由到服务
  应用级角色固定为 "external_app"，biz_scope 按 App 注册时申请

共存原则：
  - 两个 Gateway 实例独立部署（ADR-025 已确定）
  - 内部服务通过 "X-Internal-Call: true" Header 识别来源
  - 外部 API 响应强制脱敏（ADR-023 最严格级别）
```

### 8. 核心表设计

```sql
-- 用户角色表
CREATE TABLE auth_user_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(64)    NOT NULL COMMENT '用户 ID（来自用户中心）',
    role        VARCHAR(32)    NOT NULL COMMENT '角色：super_admin/admin/finance/ops/cs/merchant',
    biz_scope   VARCHAR(128)   NOT NULL COMMENT '业务线 scope：ecommerce,locallife,b2b 逗号分隔',
    tenant_id   VARCHAR(64)             COMMENT '商家 ID（merchant 角色时必填）',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_user_role (user_id, role),
    KEY idx_user (user_id),
    KEY idx_role (role)
) COMMENT '用户角色分配表';

-- Token 黑名单表（用于 Token 吊销追踪）
CREATE TABLE auth_token_blacklist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti         VARCHAR(64)    NOT NULL COMMENT 'JWT Token ID',
    reason      VARCHAR(32)    NOT NULL COMMENT '吊销原因：logout/pwd_change/role_change',
    user_id     VARCHAR(64)    NOT NULL,
    expires_at  DATETIME       NOT NULL COMMENT '原 token 过期时间',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_jti (jti),
    KEY idx_expires (expires_at)
) COMMENT 'Token 吊销黑名单';
```

### 9. 与现有体系的关系图

```
                    ┌─────────────────────┐
                    │  用户 / 应用          │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   Gateway 层         │
                    │   ┌───────────────┐ │
                    │   │ JWT 校验/提取  │ │ ← ADR-026 核心
                    │   │ 写入请求头      │ │
                    │   └───────┬───────┘ │
                    └───────────┼─────────┘
                               │ X-User-Role / X-Biz-Scope
                    ┌──────────▼──────────┐
                    │  Dubbo Consumer     │
                    │  AuthConsumerFilter │──→ RpcContext 写入角色信息
                    └──────────┬──────────┘
                               │ Dubbo RPC
                    ┌──────────▼──────────┐
                    │  Dubbo Provider     │
                    │  AuthProviderFilter │──→ 建立 AuthContext (ThreadLocal)
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  业务层              │
                    │  ┌──────────────┐   │
                    │  │@RequirePerm. │   │ ← ADR-026 权限校验
                    │  └──────┬───────┘   │
                    │         │           │
                    │  ┌──────▼───────┐   │
                    │  │AuthContext   │───┼──→ ADR-023 脱敏决策
                    │  │.shouldMask() │   │   DesensitizeContext.getRole()
                    │  └──────────────┘   │
                    │         │           │
                    │  ┌──────▼───────┐   │
                    │  │BizScopeFilter│───┼──→ ADR-017 业务线数据隔离
                    │  └──────────────┘   │
                    └────────────────────┘
```

### 10. Apollo 动态配置

```yaml
auth:
  jwt:
    signing-key: ${JWT_SIGNING_KEY}     # 从 Vault 读取，非明文配置
    access-token-ttl: 30m               # Access Token 有效期
    refresh-token-ttl: 7d               # Refresh Token 有效期
  blacklist:
    cleanup-cron: "0 3 * * *"          # 每天凌晨 3 点清理过期黑名单
  rate-limit:
    login: "5/1m"                       # 登录接口 1 分钟 5 次
```

---

## 备选方案评估

### Token 格式选择

| 维度 | JWT（选中） | OAuth2 Token（opaque） | Session |
|------|------------|----------------------|---------|
| 无状态服务端 | ✅ 完全无状态 | ✅ 无状态（需 introspection） | ❌ 需 Redis |
| 携带信息 | ✅ 丰富 | ❌ 仅 token 值 | ✅ 丰富 |
| 吊销成本 | △ Blacklist | △ Introspection | ✅ 即时 |
| 微服务友好 | ✅ Header 直接使用 | △ 需 Introspection 端点 | ❌ Cookie 跨服务 |
| 信息泄露风险 | ⚠️ 签名防篡改但 payload base64 | ✅ 仅随机字符串 | ⚠️ Session ID |

**结论**：JWT 微服务友好度最高，配合 Blacklist 解决吊销问题。

### RBAC vs ABAC

| 维度 | RBAC（选中） | ABAC |
|------|-------------|------|
| 模型复杂度 | 低，角色 + 权限清晰 | 高，属性引擎复杂 |
| 性能 | ✅ 低开销，缓存友好 | ⚠️ 策略解析增加延迟 |
| 满足当前需求 | ✅ 角色矩阵清晰 | △ 过度设计 |
| 团队维护成本 | ✅ 直观易懂 | ⚠️ 策略维护专业性强 |

**结论**：RBAC 满足当前角色矩阵需求，未来可扩展 ABAC 补充。

---

## 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| P1 角色模型定义 | 角色枚举 + 权限矩阵（表）+ 配置化 | 1.5d | auth_user_role 表 + Role/BizScope 枚举 + 权限注册表 |
| P2 Token 体系 | JWT 签发/校验/刷新/吊销 + Auth Service | 2d | Auth Service + TokenRevocationService + GatewayAuthFilter |
| P3 Dubbo 角色传播 | AuthConsumerFilter + AuthProviderFilter + SPI 配置 | 1.5d | AuthContext(ThreadLocal) + Filter 集成验证 |
| P4 权限校验 AOP | @RequirePermission 注解 + Aspect 实现 | 1d | AOP 拦截器 + 权限异常处理 |
| P5 业务线权限集成 | BizScope 与 ADR-017 BusinessRouter 集成 | 1.5d | 多业务线数据过滤 + 集成测试 |
| P6 存量接口兼容 | 各服务 Controller + Dubbo API 添加权限注解 | 1.5d | 存量接口全覆盖 |

**合计**：9 人天

---

## 上线检查清单

- [ ] `auth_user_role` 表初始化 + 基础角色数据
- [ ] Gateway JWT Verifier 集成（校验 + 黑名单检查）
- [ ] AuthConsumerFilter/ProviderFilter SPI 配置加载验证
- [ ] AuthContext ThreadLocal 无泄漏（finally 清理）
- [ ] 白名单路径配置（/health /auth /openapi /swagger）
- [ ] JWT 密钥从 Vault 获取（非代码硬编码）
- [ ] Refresh Token Rotation 安全校验
- [ ] 多业务线权限边界验证（e-commerce 用户不可访问 B2B 数据）
- [ ] Token 吊销场景验证（登出/密码变更/角色变更）
- [ ] 灰度期间权限兼容性（旧版本无 Token 的降级处理）
- [ ] 压测：Gateway + 权限校验 < 1ms P99
- [ ] @RequirePermission 注解覆盖所有 Controller 接口

---

## 与现有文档的关联

- **ADR-023**：`AuthContext.shouldMask()` 是 DesensitizeContext 的角色数据来源，两个 Context 共用同一 ThreadLocal
- **ADR-025**：用户级 JWT（内部 Gateway）与应用级 HMAC（外部 Gateway）两种认证方式按路径分流
- **ADR-017**：`biz_scope` 字段控制用户可操作的业务线范围，与 BusinessRouter 配合实现数据隔离
- **ADR-022**：Dubbo Filter 传播模式复用（AuthFilter 与 GrayTagFilter 使用同一 SPI 扩展机制）
- **security.puml**：Layer 2 应用层安全的详细设计落地
