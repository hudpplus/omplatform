# ADR-023：动态数据脱敏

## 状态

已接受

---

## 背景

### 现状分析

订单中台存储了大量个人敏感信息（PII），分布在订单实体及各业务线扩展表中：

| 敏感级别 | 字段 | 存储方式 | 涉及角色 |
|----------|------|----------|----------|
| **L4 极敏感** | 身份证号、银行卡号 | AES-256 加密存储（security.puml） | 仅 admin |
| **L3 高敏感** | 手机号、收货地址、买家姓名、税号、发票抬头 | 明文存储 | finance / admin |
| **L2 中敏感** | buyerId、IP 地址、支付金额 | 明文存储 | ops / finance / admin |
| **L1 低敏感** | 订单备注、商家备注 | 自由文本，可能包含任意 PII | 全员可见 |

当前系统在数据脱敏方面存在以下问题：

**问题 1：API 响应无运行时脱敏**  
`security.puml` 中已定义 `@Desensitize` 脱敏注解，但缺少详细设计——目前只有导出模块（ADR-019 `FieldPermissionFilter`）实现了基于角色的字段级可见性控制，而 REST API 响应中的 PII 字段以明文返回给所有调用方。客服（cs）角色的 API 接口返回的数据与管理员完全相同，存在越权查看 PII 的风险。

**问题 2：脱敏策略分散、无统一标准**  
手机号是脱敏为 `138****1234` 还是 `138***1234`？姓名是 `张*` 还是 `张**`？当前无统一规范，不同开发人员可能按不同方式处理。

**问题 3：跨服务调用时 PII 不受控**  
Dubbo 服务间调用时，order-core 返回的买家手机号/地址完整传递到下游服务，即使下游服务（如 logistics）不需要完整 PII。缺少"最小必要数据"的强制机制。

**问题 4：审计日志可能泄露 PII**  
`@AuditLog` 记录的请求参数中如果包含手机号/身份证号，将明文落入审计日志表，扩大 PII 暴露面。

### 目标

1. **API 响应自动脱敏**：基于调用方角色，在 JSON 序列化层自动对 PII 字段做脱敏
2. **统一的脱敏标准**：每种 PII 类型有确定的脱敏格式，降低维护成本
3. **多通道覆盖**：REST API、Dubbo 响应、导出、日志打印均实现脱敏
4. **最小性能损耗**：脱敏在序列化层完成，不影响业务逻辑执行性能

### 术语定义

| 术语 | 说明 |
|------|------|
| **脱敏（Masking）** | 将敏感数据的部分或全部替换为不可识别的字符，但保留格式和部分信息 |
| **PII** | 个人敏感信息（Personally Identifiable Information） |
| **脱敏策略** | 针对特定 PII 类型的脱敏规则，如手机号保留前 3 后 4 |
| **角色** | admin / finance / ops / cs，各角色对同一字段的可见性不同 |

---

## 决策

**主方案：注解驱动的 Jackson 序列化脱敏**  
在 Jackson 序列化层注入自定义 `JsonSerializer`，通过 `@Desensitize` 注解标记需要脱敏的字段，运行时根据 `DesensitizeContext`（ThreadLocal）中的角色信息决定是否脱敏及使用何种策略。

**理由：**

| 因素 | 评估 |
|------|------|
| **无业务侵入** | 序列化层处理，Service/Controller 无需感知脱敏逻辑 |
| **统一管控** | 所有 API 响应统一经过同一条脱敏路径，避免遗漏 |
| **角色感知** | 可结合 Spring Security Context 拿到当前用户角色，实现角色级差异化可见性 |
| **与现有架构兼容** | Jackson 已是 Spring Boot 默认序列化框架，ShardingSphere Mask 做 DB 查询脱敏，两者互补 |
| **测试容易** | 单元测试可 mock DesensitizeContext 验证不同角色的脱敏结果 |

---

## 详细设计

### 1. 脱敏策略分类（DesensitizeType）

```java
public enum DesensitizeType {

    /** 手机号：138****1234 */
    PHONE {
        public String mask(String value) {
            if (value == null || value.length() < 7) return value;
            return value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        }
    },

    /** 中文姓名：张*（2字） / 张**（3+字） */
    NAME {
        public String mask(String value) {
            if (value == null || value.length() < 2) return value;
            if (value.length() == 2) return value.charAt(0) + "*";
            return value.charAt(0) + "*".repeat(value.length() - 2) + value.charAt(value.length() - 1);
        }
    },

    /** 身份证号：110101****1234**** */
    ID_CARD {
        public String mask(String value) {
            if (value == null || value.length() < 10) return value;
            return value.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
        }
    },

    /** 银行卡号：6222****1234 */
    BANK_CARD {
        public String mask(String value) {
            if (value == null || value.length() < 8) return value;
            return value.replaceAll("(\\d{4})\\d+(\\d{4})", "$1****$2");
        }
    },

    /** 地址：北京市***街道***号 */
    ADDRESS {
        public String mask(String value) {
            if (value == null || value.length() < 6) return value;
            // 保留前 6 个字符（省市），其余替换为 *
            return value.substring(0, 6) + "*".repeat(value.length() - 6);
        }
    },

    /** 电子邮箱：u***@example.com */
    EMAIL {
        public String mask(String value) {
            if (value == null || !value.contains("@")) return value;
            String[] parts = value.split("@");
            String name = parts[0];
            if (name.length() <= 1) return value;
            return name.charAt(0) + "***@" + parts[1];
        }
    },

    /** IP 地址：10.0.*.* */
    IP {
        public String mask(String value) {
            if (value == null) return value;
            String[] octets = value.split("\\.");
            if (octets.length != 4) return value;
            return octets[0] + "." + octets[1] + ".*.*";
        }
    },

    /** 税号/统一社会信用代码：913100*****8B */
    TAX_ID {
        public String mask(String value) {
            if (value == null || value.length() < 8) return value;
            return value.substring(0, 6) + "*****" + value.substring(value.length() - 2);
        }
    },

    /** 自定义正则 / 固定字符 */
    CUSTOM {
        // 通过 @Desensitize 的 customMask 参数传入正则和替换模板
        public String mask(String value, String regex, String replacement) {
            if (value == null) return value;
            return value.replaceAll(regex, replacement);
        }
    };

    public String mask(String value) { return value; }
}
```

### 2. @Desensitize 注解

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@JacksonAnnotationsInside
@JsonSerializer(using = DesensitizeSerializer.class)
public @interface Desensitize {

    /** 脱敏类型 */
    DesensitizeType type() default DesensitizeType.CUSTOM;

    /**
     * 允许查看原始值的角色（白名单模式）
     * 当 currentRole 在此列表中时，返回原始值
     * 优先级高于 deniedRoles
     */
    String[] allowedRoles() default {};

    /**
     * 强制脱敏的角色（黑名单模式）
     * 当 currentRole 在此列表中时，强制脱敏
     * 当 allowedRoles 非空时，此字段不生效
     */
    String[] deniedRoles() default {};

    /**
     * CUSTOM 类型时的正则替换表达式
     * 格式：regex::replacement
     * 示例："(.{3}).*(.{4})::$1****$2"
     */
    String customMask() default "";

    /**
     * 掩码字符
     */
    char maskChar() default '*';

    /**
     * 字段为空时的默认值（如 "***"），
     * 为空则直接返回 null
     */
    String nullPlaceholder() default "";
}
```

#### 使用示例

```java
public class OrderDTO {

    private String orderId;

    @Desensitize(type = NAME, deniedRoles = {"cs", "ops"})
    private String buyerName;

    @Desensitize(type = PHONE, allowedRoles = {"admin", "finance"})
    private String buyerPhone;

    @Desensitize(type = ADDRESS, allowedRoles = {"admin", "finance", "cs"})
    private String receiverAddress;

    @Desensitize(type = ID_CARD, allowedRoles = {"admin"})
    private String idCardNumber;

    @Desensitize(type = BANK_CARD, allowedRoles = {"admin"})
    private String bankCardNo;

    @Desensitize(type = EMAIL, deniedRoles = {"cs"})
    private String buyerEmail;

    @Desensitize(type = IP, deniedRoles = {"cs", "ops"})
    private String clientIp;

    @Desensitize(type = TAX_ID, allowedRoles = {"finance", "admin"})
    private String taxId;

    // 非敏感字段不需要注解
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createTime;
}
```

### 3. DesensitizeContext（ThreadLocal）

```java
/**
 * 脱敏上下文 —— 存储当前请求的角色和脱敏开关
 *
 * 生命周期：Gateway 解析 JWT → 提取角色 → 设置到 DesensitizeContext
 *          → 经过 Jackson 序列化时读取 → 请求结束时清除
 *
 * 传递方式：Dubbo RpcContext attachment（跨服务传递角色）
 */
public class DesensitizeContext {

    private static final ThreadLocal<DesensitizeContext> CONTEXT_HOLDER =
            ThreadLocal.withInitial(DesensitizeContext::new);

    /** 当前请求角色（来自 JWT） */
    private String currentRole;

    /** 是否强制脱敏（用于内部服务的"最小必要"场景） */
    private boolean forceMasking;

    /** 脱敏开关（Apollo 远程配置，全局熔断） */
    private static volatile boolean maskingEnabled = true;

    public static DesensitizeContext get() {
        return CONTEXT_HOLDER.get();
    }

    public static void setRole(String role) {
        get().currentRole = role;
    }

    public static String getRole() {
        return get().currentRole;
    }

    public static boolean isMaskingEnabled() {
        return maskingEnabled;
    }

    public static void setMaskingEnabled(boolean enabled) {
        maskingEnabled = enabled;
    }

    public static boolean shouldMask(Desensitize annotation) {
        if (!maskingEnabled) return false;
        if (forceMasking) return true;
        String role = get().currentRole;

        // 1. 白名单模式：allowedRoles 包含当前角色 → 不脱敏
        String[] allowed = annotation.allowedRoles();
        if (allowed.length > 0) {
            return !Arrays.asList(allowed).contains(role);
        }

        // 2. 黑名单模式：deniedRoles 包含当前角色 → 强制脱敏
        String[] denied = annotation.deniedRoles();
        if (denied.length > 0) {
            return Arrays.asList(denied).contains(role);
        }

        // 3. 默认：不脱敏（需要显式声明才脱敏）
        return false;
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
```

### 4. DesensitizeSerializer（Jackson 核心）

```java
/**
 * Jackson 序列化器 —— @Desensitize 注解驱动的脱敏引擎
 *
 * 注册方式：Jackson2ObjectMapperBuilder 或 @JsonComponent
 */
public class DesensitizeSerializer extends JsonSerializer<String>
        implements ContextualSerializer {

    private Desensitize annotation;

    public DesensitizeSerializer() {}

    public DesensitizeSerializer(Desensitize annotation) {
        this.annotation = annotation;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider prov)
            throws IOException {

        if (value == null) {
            gen.writeString(annotation.nullPlaceholder());
            return;
        }

        if (DesensitizeContext.shouldMask(annotation)) {
            DesensitizeType type = annotation.type();
            String masked;
            if (type == DesensitizeType.CUSTOM && !annotation.customMask().isEmpty()) {
                String[] parts = annotation.customMask().split("::", 2);
                String regex = parts[0];
                String replacement = parts.length > 1 ? parts[1] : "****";
                masked = value.replaceAll(regex, replacement);
            } else {
                masked = type.mask(value);
            }
            gen.writeString(masked);
        } else {
            gen.writeString(value);  // 原始值
        }
    }

    /**
     * 从字段注解获取配置，为每个注解创建专用 Serializer 实例
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            Desensitize ann = property.getAnnotation(Desensitize.class);
            if (ann == null) {
                ann = property.getContextAnnotation(Desensitize.class);
            }
            if (ann != null) {
                return new DesensitizeSerializer(ann);
            }
        }
        return this;  // 无注解时返回默认（透传）
    }
}
```

### 5. 角色-脱敏矩阵（完整定义）

```java
/**
 * 角色脱敏配置 —— 统一管控所有字段的脱敏规则
 *
 * 此配置既可以 Java 代码形式存在，
 * 也可以通过 Apollo 动态下发，实现运行时热更新脱敏策略
 */
public class DesensitizeConfig {

    /**
     * 角色 → 字段 → 脱敏类型映射
     * 优先级高于 @Desensitize 注解上的 allowedRoles/deniedRoles
     */
    public static final Map<String, Map<String, DesensitizeType>> ROLE_FIELD_MASKING = Map.of(
        "cs", Map.of(
            "buyerName",    DesensitizeType.NAME,
            "buyerPhone",   DesensitizeType.PHONE,
            "receiverPhone",DesensitizeType.PHONE,
            "receiverName", DesensitizeType.NAME,
            "idCardNumber", DesensitizeType.ID_CARD,
            "bankCardNo",   DesensitizeType.BANK_CARD,
            "buyerEmail",   DesensitizeType.EMAIL,
            "clientIp",     DesensitizeType.IP
        ),
        "ops", Map.of(
            "buyerPhone",   DesensitizeType.PHONE,
            "receiverPhone",DesensitizeType.PHONE,
            "idCardNumber", DesensitizeType.ID_CARD,
            "bankCardNo",   DesensitizeType.BANK_CARD
        ),
        "finance", Map.of(
            "idCardNumber", DesensitizeType.ID_CARD,
            "bankCardNo",   DesensitizeType.BANK_CARD
        ),
        "admin", Map.of()  // 管理员全可见
    );

    /**
     * 角色 → 可见字段列表（与 ROLE_FIELD_MASKING 互补）
     * 不在可见列表中的字段在响应中被剔除（而非脱敏）
     * 用于"某些角色根本不应该看到某个字段"的场景
     */
    public static final Map<String, Set<String>> ROLE_VISIBLE_FIELDS = Map.of(
        "cs", Set.of(
            "orderId", "buyerName", "status", "createTime",
            "deliveryStatus", "receiverAddress", "logisticsNo",
            "totalAmount"
        ),
        "ops", Set.of(
            "orderId", "buyerId", "buyerName", "buyerPhone",
            "totalAmount", "status", "createTime", "businessType",
            "region", "receiverAddress", "logisticsNo"
        ),
        "finance", Set.of("ALL"),
        "admin", Set.of("ALL")
    );
}
```

### 6. Gateway 层角色注入

在 Spring Cloud Gateway 的 GlobalFilter 中，解析 JWT 后将角色写入 `DesensitizeContext`，同时通过 Dubbo `RpcContext` 传递给下游服务：

```java
@Component
@Order(-500)  // 在 GatewayGrayFilter 之后执行
public class DesensitizeContextFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            // 1. 从 JWT 或请求属性中提取角色
            String role = extractRole(exchange);
            if (role != null) {
                DesensitizeContext.setRole(role);
                // 2. 通过 Dubbo RpcContext 传递角色给下游服务
                RpcContext.getClientAttachment().setAttachment("x-role", role);
            }
            return chain.filter(exchange);
        } finally {
            // 3. 响应结束后清理 ThreadLocal
            DesensitizeContext.clear();
        }
    }

    private String extractRole(ServerWebExchange exchange) {
        // 从 JWT token 中提取 role 声明
        // 简化实现：从请求头 X-User-Role 获取（开发环境）
        String token = exchange.getRequest().getHeaders()
                .getFirst("Authorization");
        if (token == null) return "guest";
        // JWT 解析 → claims.get("role")
        return "admin";  // 示例
    }
}
```

### 7. Dubbo 服务间脱敏传递

Provider 端 Filter 从 RpcContext 中提取角色，设置到 DesensitizeContext，确保 Dubbo 响应序列化时也执行脱敏：

```java
@Activate(group = CommonConstants.PROVIDER, order = -7000)
public class DesensitizeProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            // 从 RpcContext 中提取调用方角色
            String role = RpcContext.getServerAttachment()
                    .getAttachment("x-role");
            if (role != null) {
                DesensitizeContext.setRole(role);
            }

            // 内部服务调用标记：如果是内部服务调用（非外部请求），
            // 且 non-mask 模式，则跳过脱敏（内部信任网络）
            boolean internalCall = "true".equals(
                    invocation.getAttachment("x-internal-call"));
            if (internalCall) {
                DesensitizeContext.setMaskingEnabled(false);
            }

            return invoker.invoke(invocation);
        } finally {
            DesensitizeContext.clear();
        }
    }
}
```

Consumer 端 Filter 在调用下游服务时传递角色：

```java
@Activate(group = CommonConstants.CONSUMER, order = -7000)
public class DesensitizeConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 传递当前角色给下游服务
        String role = DesensitizeContext.getRole();
        if (role != null) {
            RpcContext.getClientAttachment().setAttachment("x-role", role);
        }
        return invoker.invoke(invocation);
    }
}
```

### 8. 多通道脱敏覆盖

```
脱敏覆盖矩阵
═══════════════════════════════════════════════════

通道                    │ 脱敏方式                │ 启用时机
────────────────────────┼─────────────────────────┼────────────────────
REST API JSON 响应      │ Jackson DesensitizeSerializer │ Controller 返回时自动
Dubbo POJO 响应         │ DesensitizeProviderFilter     │ Provider 序列化时
数据导出 (CSV/Excel)    │ FieldPermissionFilter         │ 导出模块（已有 ADR-019）
日志打印 (Logback)      │ MaskingConverter             │ 日志输出时
DB 查询返回             │ ShardingSphere Mask           │ 查询时（已有）
WebSocket 推送          │ 同 Jackson Serializer        │ 推送时
消息队列 (MQ)           │ 生产者自行脱敏                │ 发送前
```

### 9. 日志脱敏组件

防止 `@AuditLog` 或业务日志中打印 PII 明文：

```java
/**
 * Logback 的 MessageConverter —— 日志输出时脱敏
 *
 * logback-spring.xml 配置：
 * <conversionRule conversionWord="maskedMsg"
 *   converterClass="com.omplatform.common.mask.LogMaskingConverter" />
 *
 * 使用方式：%maskedMsg 替代 %msg
 */
public class LogMaskingConverter extends MessageConverter {

    private static final Pattern[] PII_PATTERNS = {
        // 手机号：13812341234 → 138****1234
        Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})"),
        // 身份证：110101199001011234 → 110101********1234
        Pattern.compile("(\\d{6})\\d{8}(\\d{4})"),
        // 银行卡：6222021234561234 → 6222****1234
        Pattern.compile("(\\d{4})\\d{8,12}(\\d{4})"),
        // 邮箱：user@example.com → u***@example.com
        Pattern.compile("(\\w)[\\w.-]*@(\\S+)"),
    };

    private static final String[] REPLACEMENTS = {
        "$1****$2",
        "$1********$2",
        "$1****$2",
        "$1***@$2",
    };

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null) return null;
        for (int i = 0; i < PII_PATTERNS.length; i++) {
            msg = PII_PATTERNS[i].matcher(msg).replaceAll(REPLACEMENTS[i]);
        }
        return msg;
    }
}

/**
 * @AuditLog 注解的参数脱敏
 * 在记录审计日志前，对参数中的 PII 字段做脱敏
 */
@Aspect
@Component
public class AuditLogMaskingAspect {

    @Around("@annotation(auditLog)")
    public Object maskArgs(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object[] args = pjp.getArgs();
        Object[] maskedArgs = Arrays.stream(args)
                .map(this::maskPII)
                .toArray();
        // 将脱敏后的参数传递给 AuditLogAspect
        return pjp.proceed(maskedArgs);
    }

    private Object maskPII(Object arg) {
        if (arg instanceof String) {
            String s = (String) arg;
            // 匹配并脱敏 PII
            return maskString(s);
        }
        return arg;
    }
}
```

### 10. Apollo 动态脱敏配置

```json
{
  "masking": {
    "enabled": true,
    "forceMasking": false,
    "defaultMaskChar": "*",
    "nullPlaceholder": "***",
    "roleOverrides": {
      "cs": {
        "buyerName": "NAME",
        "buyerPhone": "PHONE",
        "idCardNumber": "ID_CARD"
      },
      "ops": {
        "buyerPhone": "PHONE"
      }
    },
    "logMasking": {
      "enabled": true,
      "patterns": [
        {"regex": "(1[3-9]\\d)\\d{4}(\\d{4})", "replacement": "$1****$2"},
        {"regex": "(\\d{6})\\d{8}(\\d{4})", "replacement": "$1********$2"}
      ]
    }
  }
}
```

```java
/**
 * Apollo 配置监听 —— 动态刷新脱敏规则
 */
@ApolloConfigChangeListener("application")
public class DesensitizeConfigListener {

    @Autowired
    private Config config;

    @PostConstruct
    public void init() {
        refreshMaskingConfig(config);
    }

    @ApolloConfigChangeListener("application")
    public void onChange(ConfigChangeEvent changeEvent) {
        if (changeEvent.changedKeys().stream()
                .anyMatch(k -> k.startsWith("masking."))) {
            refreshMaskingConfig(changeEvent.getConfig());
        }
    }

    private void refreshMaskingConfig(Config config) {
        boolean enabled = config.getBooleanProperty("masking.enabled", true);
        DesensitizeContext.setMaskingEnabled(enabled);
        // 解析 roleOverrides → 更新 ROLE_FIELD_MASKING 映射
        String roleOverrides = config.getProperty("masking.roleOverrides", "{}");
        updateRoleMasking(JSON.parseObject(roleOverrides));
    }
}
```

### 11. 性能设计

```
脱敏性能优化策略
═══════════════════════════════════════

1. 零拷贝脱敏（避免中间 String 对象）
   ──────────────────────────────────
   手机号：char[] 直接替换，不创建正则 Matcher
   姓名：StringBuilder 原地替换

2. 脱敏结果缓存（相同值相同脱敏结果）
   ──────────────────────────────────
   Caffeine 本地缓存，容量 10,000，TTL 60s
   key = originalValue + "::" + role + "::" + type
   value = maskedValue
   命中率预估：分页查询中大量重复值（相同手机号）时有效

3. 按需脱敏（仅序列化时执行）
   ──────────────────────────────────
   脱敏在 Jackson 序列化层完成，业务层始终操作原始值
   不对业务逻辑产生副作用

4. 内部调用跳过脱敏（信任网络）
   ──────────────────────────────────
   Dubbo 内部服务间调用时，通过 x-internal-call 标记跳过脱敏
   减少 90% 以上的脱敏计算（全链路调用中仅对外 API 需要脱敏）

预估性能损耗：P99 < 0.5ms（单字段脱敏 < 0.02ms）
```

### 12. 脱敏质量保障

```java
/**
 * 脱敏测试 —— 验证每种策略的正确性和安全性
 */
@ParameterizedTest
@CsvSource({
    "PHONE,   13812341234,        138****1234",
    "PHONE,   1381234,            1381234",          // 短号不脱敏
    "NAME,    张三,               张*",
    "NAME,    李小明,             李*明",
    "NAME,    司马光强,           司**强",
    "ID_CARD, 110101199001011234, 110101********1234",
    "BANK_CARD,6222021234561234,  6222****1234",
    "ADDRESS, 北京市朝阳区建国路88号, 北京市朝阳区建国路88号*", // 保留前6字
    "EMAIL,   user@example.com,   u***@example.com",
    "IP,      10.88.99.100,       10.88.*.*",
    "TAX_ID,  91310000123456789B, 913100*****89B",
})
void testMasking(DesensitizeType type, String input, String expected) {
    assertThat(type.mask(input)).isEqualTo(expected);
}

/**
 * 边界条件测试
 */
@ParameterizedTest
@ValueSource(strings = {"", "a", "ab"})
void testShortValuesNotMasked(String shortValue) {
    // 太短的字符串不脱敏（信息量不足以识别）
    assertThat(DesensitizeType.PHONE.mask(shortValue)).isEqualTo(shortValue);
    assertThat(DesensitizeType.NAME.mask(shortValue)).isEqualTo(shortValue);
}
```

### 13. 集成配置

```java
/**
 * Spring Boot Jackson 自动配置 —— 注册脱敏序列化器
 */
@Configuration
public class DesensitizeAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer desensitizeCustomizer() {
        return builder -> {
            // 注册脱敏序列化器模块
            SimpleModule module = new SimpleModule("DesensitizeModule");
            module.addSerializer(String.class, new DesensitizeSerializer());
            builder.modules(module);
        };
    }

    @Bean
    public DesensitizeContextFilter desensitizeContextFilter() {
        return new DesensitizeContextFilter();
    }
}
```

```xml
<!-- dubbo-spi-desensitize.properties -->
<!-- META-INF/dubbo/org.apache.dubbo.rpc.Filter -->
desensitizeProvider=com.omplatform.common.mask.filter.DesensitizeProviderFilter
desensitizeConsumer=com.omplatform.common.mask.filter.DesensitizeConsumerFilter
```

---

## 方案评估

| 维度 | A: 注解驱动 + Jackson（选中） | B: AOP + @Around | C: ShardingSphere Mask 全覆盖 |
|------|-------------------------------|------------------|------------------------------|
| **实现复杂度** | 中（Serializer + Context + 注解） | 低（AOP 切面即可） | 高（需配置所有查询的 Mask 规则） |
| **无业务侵入** | ✓ 序列化层处理 | △ 需定义切点和参数映射 | ✓ SQL 层处理 |
| **角色感知** | ✓ 原生支持 ThreadLocal 角色传递 | ✓ 可获取角色 | ✗ 无角色上下文 |
| **多通道覆盖** | ✓ API / Dubbo / WebSocket | ✗ 仅 API 层 | ✗ 仅 DB 查询 |
| **动态热更新** | ✓ Apollo 配置 | ✓ Apollo 配置 | ✗ 需重启 |
| **字段级粒控** | ✓ 每个字段独立策略 | △ 按方法粗粒度 | ✓ 按列配置 |
| **测试难度** | 低（mock Context 即可） | 中（需启动 AOP 容器） | 高（需连接真实数据库） |
| **与现有兼容** | ✓ 已有 security.puml 定义 @Desensitize | ✓ Spring AOP | ✓ 已有 ShardingSphere |

**结论**：方案 B（纯 AOP）仅覆盖 API 层，方案 C（ShardingSphere）仅覆盖 DB 查询层，两者都无法覆盖 Dubbo 响应和日志脱敏。方案 A 作为主方案，配合方案 C 做 DB 层补充，形成「API + Dubbo → 注解驱动 Jackson，DB → ShardingSphere Mask」的双层脱敏策略。

---

## 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| **P1 核心框架** | DesensitizeType 枚举 + @Desensitize 注解 + DesensitizeSerializer + DesensitizeContext | 2d | 核心 jar 包，单元测试通过 |
| **P2 Gateway 集成** | DesensitizeContextFilter（JWT 角色提取 → Context）+ 端到端角色传递验证 | 1d | Gateway 角色注入 + Filter |
| **P3 Dubbo 集成** | DesensitizeProvider/ConsumerFilter + SPI 配置 + 内部调用跳过 | 1d | Dubbo 全链路角色传递 |
| **P4 脱敏标注** | 在 OrderDTO 及相关 DTO 上标注 @Desensitize，覆盖所有 PII 字段 | 1.5d | 所有对外 DTO 脱敏标注 |
| **P5 日志脱敏** | LogMaskingConverter + AuditLogMaskingAspect + Logback 配置 | 1d | 日志零 PII 泄露 |
| **P6 Apollo 动态配置** | DesensitizeConfigListener + 配置模板 + 熔断开关 | 1d | 运行时切换脱敏规则 |
| **P7 测试 + 审计** | 脱敏策略单元测试 + 角色集成测试 + 性能压测 + 安全审计 | 2d | 测试报告 + 审计日志 |

**总计：9.5 人天**

---

## 上线检查清单

### 功能验证
- [ ] 每种脱敏策略在单元测试中覆盖所有边界条件
- [ ] 每种角色（admin/finance/ops/cs）在集成测试中验证脱敏结果
- [ ] 无 `@Desensitize` 注解的字段不受影响
- [ ] null 值不 NPE，按 `nullPlaceholder` 规则处理

### 角色传递
- [ ] Gateway JWT 解析正确提取 role 并设置到 DesensitizeContext
- [ ] Dubbo Consumer → Provider 角色传递不丢失
- [ ] 内部服务调用（x-internal-call=true）跳过脱敏
- [ ] 异步线程（MQ 消费/XXL-Job）角色上下文正确初始化

### 性能
- [ ] 单字段脱敏 P99 < 0.02ms
- [ ] 分页查询（20 条含 5 个 PII 字段）额外延迟 < 1ms
- [ ] 脱敏缓存命中率 > 60%（本地压测验证）
- [ ] 脱敏熔断开关有效（Apollo masking.enabled=false → 零开销）

### 安全
- [ ] 日志中不出现手机号/身份证/银行卡等 PII 明文
- [ ] 审计日志 @AuditLog 参数已经脱敏
- [ ] 外部调用方无法通过响应头/错误信息推断角色和脱敏规则
- [ ] 脱敏规则被 Apollo 保护（仅 admin 可修改）

### 兼容性
- [ ] 现有 API 响应中未标注 @Desensitize 的字段格式不变
- [ ] 单元测试覆盖所有已有 Controller 的响应结构
- [ ] 与现有 @AuditLog 注解兼容
- [ ] 与 ShardingSphere Mask 共存不冲突

---

## 与其他 ADR 的关系

| ADR | 关系 |
|-----|------|
| **ADR-019**（异步任务中心） | 导出模块的 FieldPermissionFilter 与本方案的角色字段权限定义合并，统一脱敏策略 |
| **ADR-018**（监控大盘） | 新增 metrics: `masking_execution_total`（按角色和脱敏类型统计） |
| **ADR-022**（全链路灰度） | 灰度版本的 DTO 可能增加新 PII 字段，需同步标注 @Desensitize |
| **ADR-017**（业务线隔离） | 各业务线扩展表的 PII 字段（taxId / invoiceTitle）需补充脱敏规则 |
| **security.puml** | 本 ADR 是 `@Desensitize` 注解的完整设计实现 |
| **state-machine.md** | 状态机中定义的各角色操作权限与脱敏配置对齐 |

---

## 附录：基于注解的脱敏流程时序

```
角色: admin/finance/ops/cs
═══════════════════════════════════════════════════

场景：客服（cs）查询订单详情

  CS (Browser)             Gateway                  order-core (Provider)
      │                       │                          │
      │  GET /api/orders/123  │                          │
      │  Authorization: JWT   │                          │
      │──────────────────────→│                          │
      │                       │                          │
      │      ① JWT 解析 → role="cs"                      │
      │      ② DesensitizeContext.setRole("cs")          │
      │      ③ RpcContext.setAttachment("x-role", "cs")  │
      │                       │                          │
      │                       │  Dubbo invoke            │
      │                       │─────────────────────────→│
      │                       │  ④ DesensitizeProviderFilter │
      │                       │     x-role="cs" → Context    │
      │                       │                          │
      │                       │    ⑤ OrderDTO 构建       │
      │                       │    buyerName  = "张三"    │
      │                       │    buyerPhone = "13812341234" │
      │                       │                          │
      │                       │    ⑥ Jackson 序列化       │
      │                       │    @Desensitize(NAME)     │
      │                       │      → buyerName = "张*"  │
      │                       │    @Desensitize(PHONE)    │
      │                       │      → buyerPhone = "138****1234" │
      │                       │                          │
      │                       │  JSON Response            │
      │                       │←─────────────────────────│
      │                       │                          │
      │     ⑦ DesensitizeContext.clear()                 │
      │                       │                          │
      │  {"orderId":"123",                                │
      │   "buyerName":"张*",       ← 脱敏                 │
      │   "buyerPhone":"138****1234",  ← 脱敏              │
      │   "receiverAddress":"北京市朝阳区建国路88号"} ← 地址完整（cs 白名单）│
      │←──────────────────────│                          │
```

---

## 附录：脱敏热更新管控

```
                    Apollo Config
                    ┌──────────────────────┐
                    │ masking.enabled=true  │
                    │ roleOverrides: {...}  │
                    │ logMasking: {...}     │
                    └──────────┬───────────┘
                               │ ConfigChangeListener
                               ▼
                    ┌──────────────────────┐
                    │ DesensitizeConfig    │──→ 更新 ROLE_FIELD_MASKING
                    │ RefreshListener      │──→ 更新日志脱敏 Pattern
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │ Apollo 灰度发布       │
                    │ 先灰到 admin 验证      │
                    │ 再全量推送到生产        │
                    └──────────────────────┘

变更管控原则：
  1. 脱敏规则的修改必须走 Apollo 灰度发布
  2. 先灰到 admin 角色验证规则正确性
  3. 灰度期间对比新旧脱敏结果，确保一致
  4. 全量推送前通知安全团队
  5. 所有脱敏规则变更记录到审计日志
```
