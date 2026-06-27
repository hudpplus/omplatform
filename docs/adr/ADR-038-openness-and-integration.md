# ADR-038：开放与集成能力设计

## 状态

已接受

---

## 背景

### 现状分析

订单中台的架构设计经过前序 ADR 体系化推进，已完成核心能力和可靠性设计。但在"对外开放能力"和"集成效率"方面仍存在以下不足：

**问题 1：API 设计标准不统一**

各服务的 API 风格、响应格式、错误码、分页方式各异——order-core 返回 `Result<OrderDTO>`，price-service 返回 `PriceResponse`，promotion-service 返回 `ApiResponse<PromotionVO>`。前端和第三方每次对接新接口都需要适配不同的返回结构。

**问题 2：事件能力分散、无统一管理中心**

现有事件体系仅覆盖了 Schema 治理（ADR-010），但缺少事件分类目录、事件订阅管理、事件投递保障和事件可追溯性。下游系统（物流、积分、营销、数据分析）各自处理事件消费逻辑，没有统一的事件接入规范。

**问题 3：集成视角的可观测性缺失**

ADR-027 覆盖了服务级的可观测性，但缺少**集成维度**的可观测性——无法按 AppKey/消费者组查看 API 使用情况和事件投递状态。当第三方集成出问题时，排查链路长、手段少。

**问题 4：中台能力对外输出缺少体系化设计**

当前已有散落的能力可以被"开放"（Open API、Webhook、领域事件），但缺少统一的产品化视角——API 目录、事件目录、集成文档、SDK、沙箱环境的体系化设计。

### 已有基础

| ADR | 覆盖内容 | 与本设计的关系 |
|-----|---------|--------------|
| ADR-010 | 事件 Schema 治理 | 事件中心的上层 Schema 管理 |
| ADR-025 | API 版本管理 + 外部 Gateway | 开放 API 的网关基础 |
| ADR-027 | 可观测性架构 | 集成可观测性的框架基础 |
| ADR-029 | 内部 Gateway 设计 | API 流量入口分类 |
| ADR-033 | Webhook 系统 | 事件中心的外部推送通道 |

### 设计目标

1. **统一开放 API**：为前端（商城、POS）提供标准化订单接口；为后端（财务、CRM）提供数据接口；统一响应格式、错误码、分页规范
2. **订单事件中心**：订单状态变化通过 RocketMQ 事件广播，下游系统订阅关心的事件，实现最终一致性解耦
3. **集成可观测性**：全链路日志、调用链追踪、监控告警，便于排查分布式订单集成问题

### 术语定义

| 术语 | 说明 |
|------|------|
| **Open API** | 对第三方开发者暴露的标准 API，通过 External Gateway 接入 |
| **Buyer API** | 对前端（商城 APP/POS）暴露的买家 API，通过 IGW Buyer 接入 |
| **Admin API** | 对运营后台暴露的管理 API，通过 IGW Admin 接入 |
| **Backend API** | 对后端系统（财务/CRM）暴露的数据 API，通过 IGW Admin + 额外认证接入 |
| **Internal API** | 服务间 Dubbo RPC 调用（不对网关暴露） |
| **事件中心** | 统一的事件管理平台，覆盖事件发布、订阅、投递、追溯 |
| **集成健康度** | 从 API 和事件两个维度衡量集成质量的指标体系 |

---

## 决策

### 整体架构方案

**方案：三层集成架构（API Layer + Event Layer + Observability Layer）**

```
                        ┌────────────────────────────────────────────┐
                        │              集成消费方                      │
                        │  ┌─────┐ ┌─────┐ ┌──────┐ ┌─────┐ ┌────┐  │
                        │  │商城  │ │POS  │ │财务  │ │CRM  │ │ISV  │  │
                        │  └──┬──┘ └──┬──┘ └──┬───┘ └──┬──┘ └──┬───┘  │
                        └─────┼───────┼────────┼────────┼───────┼──────┘
                              │       │        │        │       │
              ┌──────────API Layer──────────────┐        │       │
              │  ┌──────────┐ ┌──────────┐      │        │       │
              │  │IGW Buyer │ │IGW Admin │      │        │       │
              │  │JWT       │ │JWT+RBAC  │      │        │       │
              │  └─────┬────┘ └────┬─────┘      │        │       │
              │        │           │            │        │       │
              │  ┌─────▼───────────▼──────┐ ┌───▼────────▼──┐   │
              │  │   Internal API (Dubbo) │ │  External GW  │   │
              │  │   服务间调用，不透出     │ │  HMAC+AppKey  │   │
              │  └────────────────────────┘ └───────┬────────┘   │
              └─────────────────────────────────────┼─────────────┘
                                                     │
              ┌──────────Event Layer─────────────────┼─────────────┐
              │                                      │             │
              │  ┌──────────────────────────────────────┐          │
              │  │        订单事件中心 (Event Center)      │          │
              │  │                                      │          │
              │  │  ┌──────────┐  ┌──────────┐          │          │
              │  │  │事件发布SDK │  │事件订阅管理│          │          │
              │  │  └─────┬────┘  └────┬─────┘          │          │
              │  │        │            │                │          │
              │  │  ┌─────▼────────────▼──────┐          │          │
              │  │  │  RocketMQ (事务消息)      │          │          │
              │  │  └─────┬────────────┬──────┘          │          │
              │  │        │            │                 │          │
              │  │  ┌─────▼────┐ ┌─────▼──────┐          │          │
              │  │  │内部消费者  │ │ Webhook    │          │          │
              │  │  │物流/积分/ │ │ 投递引擎    │          │          │
              │  │  │营销/数据  │ │ (ADR-033)  │          │          │
              │  │  └──────────┘ └────────────┘          │          │
              │  └───────────────────────────────────────┘          │
              └──────────────────────────────────────────────────────┘

              ┌──────────Observability Layer─────────────────────────┐
              │  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  │
              │  │ API 可观测性  │  │ 事件可观测性   │  │集成健康大盘│  │
              │  │ QPS/延迟/    │  │ 生产/消费速率  │  │ 统一视图   │  │
              │  │ 错误率/AppKey│  │ 堆积/失败率/  │  │ 异常聚合   │  │
              │  │              │  │ 消费者组维度   │  │ 趋势分析   │  │
              │  └──────────────┘  └──────────────┘  └───────────┘  │
              └──────────────────────────────────────────────────────┘
```

**三大层次职责：**

| 层次 | 职责 | 消费方 | 技术选型 |
|------|------|--------|---------|
| **API Layer** | 标准化接口输出，统一认证、限流、文档 | 商城/POS/ISV/财务/CRM | Spring Cloud Gateway × 4 + Knife4j |
| **Event Layer** | 异步事件广播，最终一致性解耦 | 物流/积分/营销/数据分析/外部 Webhook | RocketMQ 事务消息 + Event SDK |
| **Observability Layer** | 集成全链路可观测 | SRE/业务方/第三方开发者 | Prometheus + Loki + SkyWalking + Grafana |

---

## 1. 统一开放 API 设计

### 1.1 API 分层与职责

根据消费方类型将 API 分为四层：

```
                 ┌──────────────────────────────────────────────────┐
                 │               API 消费方分类                       │
                 │                                                  │
                 │   Buyer API    Admin API    Backend API  Open API │
                 │   ┌────────┐  ┌────────┐  ┌─────────┐ ┌────────┐ │
                 │   │ 商城   │  │运营后台 │  │财务/CRM │ │第三方   │ │
                 │   │ POS    │  │客服工作台│  │对账系统 │ │开发者   │ │
                 │   └───┬────┘  └───┬────┘  └────┬────┘ └───┬────┘ │
                 └───────┼──────────┼─────────────┼──────────┼───────┘
                         │          │             │          │
                 ┌───────▼──────────▼─────────────▼──────────▼──────┐
                 │              统一 API 规范层                       │
                 │  统一响应格式 | 错误码体系 | 分页规范 | 审计日志    │
                 └───────┬──────────┬─────────────┬──────────┬──────┘
                         │          │             │          │
                 ┌───────▼──┐  ┌───▼──────┐ ┌────▼────┐ ┌───▼────────┐
                 │IGW Buyer │  │IGW Admin │ │IGW Admin│ │Ext Gateway │
                 │          │  │          │ │(内部认证)│ │            │
                 │ JWT 用户  │  │ JWT 用户  │ │ JWT 用户 │ │ HMAC 签名  │
                 │ 认证      │  │ 认证+角色  │ │ +内部白  │ │ AppKey 认证│
                 │          │  │          │ │ 名单     │ │            │
                 └──────────┘  └──────────┘ └─────────┘ └────────────┘
```

| API 分类 | 入口 | 认证方式 | 消费方 | 典型接口 |
|----------|------|---------|--------|---------|
| **Buyer API** | `IGW Buyer` | JWT（用户 Token） | 商城 APP、小程序、POS | 下单、订单列表、取消订单 |
| **Admin API** | `IGW Admin` | JWT + RBAC（角色权限） | 运营后台、客服工作台 | 订单查询、改价、备注、审核 |
| **Backend API** | `IGW Admin` | JWT + RBAC + IP 白名单 | 财务系统、CRM、ERP | 对账数据导出、批量查询、统计 |
| **Open API** | `Ext Gateway` | HMAC-SHA256 + AppKey | 第三方 ISV、合作方 | 订单查询、轨迹查询、退货发起 |

### 1.2 统一响应规范

#### 响应体格式

所有 API 统一返回 `ApiResult<T>` 结构：

```java
@Data
@Builder
public class ApiResult<T> {
    private int code;          // 业务码，200=成功
    private String message;    // 提示信息
    private T data;            // 数据负载
    private String traceId;    // 链路追踪 ID
    private long timestamp;    // 服务器 Unix 时间戳（毫秒）

    // 静态工厂方法
    public static <T> ApiResult<T> success(T data) {
        return ApiResult.<T>builder()
            .code(200).message("ok")
            .data(data)
            .traceId(MDC.get("traceId"))
            .timestamp(System.currentTimeMillis())
            .build();
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return ApiResult.<T>builder()
            .code(code).message(message)
            .traceId(MDC.get("traceId"))
            .timestamp(System.currentTimeMillis())
            .build();
    }
}
```

#### 分页响应格式

```java
@Data
@Builder
public class PageResult<T> {
    private List<T> content;       // 当前页数据
    private int page;              // 当前页码（从 1 开始）
    private int size;              // 每页大小
    private long total;            // 总记录数
    private int totalPages;        // 总页数
    private boolean hasMore;       // 是否还有更多
    private String sort;           // 排序字段（透传请求参数）
}
```

#### 分页请求参数规范

| 参数 | 类型 | 默认值 | 最大值 | 说明 |
|------|------|--------|--------|------|
| `page` | int | 1 | — | 页码，从 1 开始 |
| `size` | int | 20 | 100（Buyer API）/ 1000（Backend API） | 每页条数 |
| `sort` | string | — | — | 排序：`field,dir\|field,dir` 如 `createTime,desc\|amount,asc` |

**分页约束**：
- Buyer API：不允许 deep pagination（page > 100 时拒绝），需使用时间范围筛选
- Backend API：允许深度分页，但 page > 500 时自动降级为游标分页（`cursor` 参数）
- 大数据量导出走异步任务（ADR-019），不通过分页 API

#### 筛选查询规范

```java
// 时间段筛选（ISO 8601 格式）
GET /api/v1/orders?startTime=2026-01-01T00:00:00Z&endTime=2026-06-01T00:00:00Z

// 多值筛选
GET /api/v1/orders?status=PAID,SHIPPED

// 字段精确匹配
GET /api/v1/orders?buyerId=u_123456

// 关键词模糊搜索
GET /api/v1/orders?keyword=ORD202606
```

### 1.3 错误码体系

采用 4 位数字编码，按错误类型分组：

| 范围 | 分类 | 说明 | 示例 |
|------|------|------|------|
| `1xxx` | 认证与授权 | 身份验证、权限不足 | `1001` Token 过期、`1003` 权限不足 |
| `2xxx` | 请求参数 | 参数校验失败 | `2001` 必填参数缺失、`2004` 参数格式错误 |
| `3xxx` | 业务逻辑 | 订单状态不匹配、库存不足 | `3001` 订单状态不允许操作、`3003` 库存不足 |
| `4xxx` | 限流与配额 | 频率限制、额度超限 | `4001` QPS 超限、`4003` 日配额耗尽 |
| `5xxx` | 系统错误 | 内部异常、依赖超时 | `5001` 内部错误、`5003` 依赖服务超时 |

**错误响应体示例**：

```json
{
  "code": 3001,
  "message": "订单状态不允许取消：当前状态 SHIPPED",
  "data": {
    "orderId": "ORD20260612001",
    "currentStatus": "SHIPPED",
    "allowedActions": ["TRACK", "CONFIRM_RECEIPT"]
  },
  "traceId": "tid.abc123xyz",
  "timestamp": 1750000000123
}
```

**Open API 额外标准头**：

| 头 | 说明 | 示例 |
|----|------|------|
| `X-RateLimit-Remaining` | 当前时间窗口剩余配额 | `X-RateLimit-Remaining: 42` |
| `X-RateLimit-Reset` | 配额重置时间（Unix 秒） | `X-RateLimit-Reset: 1750000200` |
| `X-Trace-Id` | 请求链路 ID | `X-Trace-Id: tid.abc123xyz` |
| `Sunset` | API 版本废弃时间 | `Sunset: Sat, 01 Jan 2027 00:00:00 GMT` |

### 1.4 API 接口设计

#### Buyer API（`/api/v1/`）

面向商城 APP、小程序、POS 等前端：

| 方法 | 路径 | 说明 | 备注 |
|------|------|------|------|
| `GET` | `/api/v1/orders` | 我的订单列表 | 按时间排序，支持状态筛选 |
| `GET` | `/api/v1/orders/{orderId}` | 订单详情 | 含商品、物流、支付信息 |
| `POST` | `/api/v1/orders` | 创建订单 | 简单场景，批量走流程引擎 |
| `POST` | `/api/v1/orders/{orderId}/cancel` | 取消订单 | 状态机校验 |
| `POST` | `/api/v1/orders/{orderId}/confirm` | 确认收货 | 提前完成 |
| `GET` | `/api/v1/orders/{orderId}/track` | 物流轨迹 | 调用 logistics-service |

#### Admin API（`/api/admin/v1/`）

面向运营后台、客服工作台：

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| `GET` | `/api/admin/v1/orders` | 高级订单查询 | `ops:order:query` |
| `GET` | `/api/admin/v1/orders/{orderId}` | 订单详情（含敏感信息） | `ops:order:detail` |
| `PUT` | `/api/admin/v1/orders/{orderId}/remark` | 修改商家备注 | `ops:order:edit` |
| `POST` | `/api/admin/v1/orders/{orderId}/adjust-price` | 改价 | `finance:order:adjust` |
| `POST` | `/api/admin/v1/orders/{orderId}/flag` | 标记异常订单 | `cs:order:flag` |

#### Backend API（`/api/backend/v1/`）

面向财务系统、CRM、ERP 等内部后端：

| 方法 | 路径 | 说明 | 备注 |
|------|------|------|------|
| `GET` | `/api/backend/v1/orders` | 批量订单查询 | 支持复杂筛选 + 游标分页 |
| `GET` | `/api/backend/v1/orders/reconciliation` | 对账数据 | 按日期范围返回 |
| `POST` | `/api/backend/v1/orders/export` | 异步导出 | 触发异步导出任务 |
| `GET` | `/api/backend/v1/orders/statistics` | 订单统计 | 聚合统计（金额/数量/状态分布） |
| `GET` | `/api/backend/v1/orders/{orderId}/audit` | 操作审计日志 | 订单的完整变更历史 |

#### Open API（`/open/v1/`）

面向第三方 ISV（通过 External Gateway，ADR-025）：

| 方法 | 路径 | 说明 | 配额 |
|------|------|------|------|
| `GET` | `/open/v1/orders` | 订单列表 | 1000次/分钟 |
| `GET` | `/open/v1/orders/{orderId}` | 订单详情 | 2000次/分钟 |
| `POST` | `/open/v1/orders/{orderId}/after-sales` | 发起售后 | 100次/分钟 |
| `GET` | `/open/v1/logistics/track` | 物流查询 | 500次/分钟 |

### 1.5 API 文档与 SDK

#### 文档聚合（Knife4j）

```
┌───────────────────────────────────────────────────────────┐
│                    API 文档门户                             │
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Buyer API    │  │ Admin API    │  │ Open API         │  │
│  │ 商城接口      │  │ 管理接口      │  │ 第三方开放接口    │  │
│  │              │  │              │  │                  │  │
│  │ ─ GET /orders│  │ ─ GET /orders│  │ ─ GET /v1/orders  │  │
│  │ ─ POST /order│  │ ─ PUT /order │  │ ─ POST /v1/...  │  │
│  │ ─ ...        │  │ ─ ...        │  │ ─ ...            │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                           │
│  统一：ApiResult 响应格式 | 错误码枚举 | 鉴权方式说明       │
└───────────────────────────────────────────────────────────┘
```

- **分组策略**：Knife4j 多模块 Docket，按 `groupName` 分为 `buyer-api`、`admin-api`、`backend-api`、`open-api`
- **文档元数据**：每个接口标注 `@Operation(summary="...", tags="...")` + `@ApiResponses` 列出可能错误码
- **版本标签**：每个 API 标注 `@ApiSupport(order = N)` + `since/version` 标签，废弃接口加 `@Deprecated`
- **Open API 额外**：提供 OpenAPI 3.0 规范 JSON 下载，用于 SDK 自动生成

#### SDK 生成策略

| 语言 | 生成工具 | 维护方式 |
|------|---------|---------|
| **Java** | OpenAPI Generator | 每次 API 发布 CI 自动生成，发布到内部 Maven 仓库 |
| **Python** | OpenAPI Generator | 同上，发布到内部 PyPI |
| **PHP** | OpenAPI Generator | 同上，发布到内部 Packagist |
| **其他** | 手动或按需生成 | 提供 OpenAPI 3.0 规范文档自行生成 |

SDK 封装内容：
- 请求签名逻辑（HMAC-SHA256）
- 重试策略（指数退避 + jitter）
- 响应反序列化 + 错误码封装
- 限流客户端侧平滑（Token Bucket）

---

## 2. 订单事件中心

### 2.1 事件分类与目录

订单事件按领域分为以下类别：

```
订单事件中心
├── 订单生命周期事件（order.*）
│   ├── order.created          — 订单创建
│   ├── order.paid             — 订单支付成功
│   ├── order.shipped          — 订单发货
│   ├── order.delivered        — 订单签收
│   ├── order.completed        — 订单完成
│   ├── order.cancelled        — 订单取消
│   ├── order.closed           — 超时关闭
│   └── order.modified         — 订单信息变更
├── 支付事件（payment.*）
│   ├── payment.success        — 支付成功
│   ├── payment.failed         — 支付失败
│   ├── payment.refunded       — 退款完成
│   └── payment.refund_failed  — 退款失败
├── 物流事件（logistics.*）
│   ├── logistics.shipped      — 已发货
│   ├── logistics.delivered    — 已签收
│   └── logistics.exception    — 物流异常
├── 售后事件（aftersale.*）
│   ├── aftersale.created      — 售后单创建
│   ├── aftersale.approved     — 售后审核通过
│   └── aftersale.completed    — 售后完成
└── 内部事件（internal.*）
    ├── internal.saga.timeout  — Saga 超时
    └── internal.risk.alert    — 风控告警
```

**事件定义规范**（每个事件需包含）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventId` | String(UUID) | 全局唯一事件 ID |
| `eventType` | String | 事件类型，如 `order.paid` |
| `schemaVersion` | int | Schema 版本号（ADR-010） |
| `source` | String | 事件来源服务名 |
| `timestamp` | long | 事件发生时间（Unix 毫秒） |
| `traceId` | String | 链路追踪 ID |
| `businessKey` | String | 业务主键（如 orderId） |
| `bizScope` | String | 业务线（ecommerce/locallife/b2b） |
| `payload` | Object | 事件数据负载 |

### 2.2 事件发布 SDK

#### 核心接口

```java
/**
 * 订单事件发布器
 * 每个应用的唯一入口，通过 Spring Boot AutoConfiguration 自动注入
 */
public interface OrderEventPublisher {

    /**
     * 发布领域事件（使用事务消息）
     * @param event 事件对象，需实现 OrderEvent 接口
     */
    void publish(OrderEvent event);

    /**
     * 发布带 routing key 的事件
     * @param event 事件对象
     * @param routingKey 路由键（用于 Topic 级别过滤）
     */
    void publishWithKey(OrderEvent event, String routingKey);

    /**
     * 批量发布事件
     * @param events 事件列表（同一事务上下文）
     */
    void publishBatch(List<? extends OrderEvent> events);
}

/**
 * 事件监听器注解
 * 标记在 Spring Bean 上，自动注册到事件中心
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrderEventListener {

    /** 订阅的事件类型（支持通配符：order.* 匹配所有订单事件） */
    String[] eventTypes();

    /** SpEL 过滤条件，如 "payload.amount > 1000" */
    String condition() default "";

    /** 业务线过滤，默认全部 */
    String bizScope() default "*";

    /** 是否异步消费，默认 true */
    boolean async() default true;
}
```

#### 事件发布流程

```
业务服务发布事件
    │
    ├── 1. 生成 eventId (UUID)
    ├── 2. 填充元数据（source/timestamp/traceId）
    ├── 3. 写入事件存储表 event_store（可选，用于追溯）
    │
    ├── 4. 发送 RocketMQ 事务消息
    │   ├── 4a. 发送半消息
    │   ├── 4b. 执行本地业务事务
    │   ├── 4c. 提交/回滚半消息
    │   └── 4d. 事务回查（本地事务状态）
    │
    └── 5. 记录发送日志
```

#### 事务消息保障

利用 RocketMQ 事务消息确保事件与本地事务的最终一致性：

```java
@Component
public class OrderEventPublisherImpl implements OrderEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final EventStoreRepository eventStore;

    // RocketMQTransactionListener 实现事务回查
    @RocketMQTransactionListener(txProducerGroup = "order-event-producer")
    public class OrderEventTransactionListener implements RocketMQLocalTransactionListener {

        @Override
        public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            // 本地事务已在业务方法中执行，此处直接查询事务状态
            OrderEvent event = (OrderEvent) arg;
            return checkLocalTransaction(event);
        }

        @Override
        public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
            // 事务回查：查询本地事务是否已提交
            String eventId = msg.getHeaders().get("event_id").toString();
            return eventStore.findById(eventId)
                .map(e -> RocketMQLocalTransactionState.COMMIT)
                .orElse(RocketMQLocalTransactionState.ROLLBACK);
        }
    }
}
```

### 2.3 事件订阅管理

#### 订阅注册

事件订阅通过 DB 表 + Apollo 配置双重管理：

```sql
-- event_subscription：事件订阅注册表
CREATE TABLE `event_subscription` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `subscription_name` VARCHAR(128) NOT NULL COMMENT '订阅名称',
  `event_types`   VARCHAR(512) NOT NULL COMMENT '订阅事件类型（逗号分隔，支持 * 通配）',
  `consumer_group` VARCHAR(128) NOT NULL COMMENT 'RocketMQ Consumer Group',
  `description`   VARCHAR(256) COMMENT '订阅描述',
  `biz_scope`     VARCHAR(64) DEFAULT '*' COMMENT '业务线过滤',
  `condition`     VARCHAR(256) COMMENT 'SpEL 过滤条件',
  `status`        VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED / DISABLED',
  `owner`         VARCHAR(64) COMMENT '负责人',
  `notify_url`    VARCHAR(512) COMMENT 'Webhook 回调 URL（外部订阅）',
  `retry_policy`  VARCHAR(128) DEFAULT '{"maxAttempts":5,"backoff":"EXPONENTIAL"}' COMMENT '重试策略 JSON',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_consumer_group` (`consumer_group`),
  KEY `idx_event_types` (`event_types`(128))
) COMMENT='事件订阅注册表';
```

#### 订阅管理 API

```java
@RestController
@RequestMapping("/api/admin/v1/event-subscriptions")
public class EventSubscriptionController {

    @GetMapping    // 查询订阅列表
    @PostMapping   // 创建订阅
    @PutMapping("/{id}")  // 修改订阅
    @DeleteMapping("/{id}")  // 删除订阅
    @PostMapping("/{id}/pause")   // 暂停消费
    @PostMapping("/{id}/resume")  // 恢复消费
}
```

#### 事件路由流程

```
RocketMQ Topic: ORDER_EVENT
    │
    ├── 事件路由器 (EventRouter)
    │   ├── 解析事件 header（eventType / bizScope / source）
    │   ├── 匹配 event_subscription 表
    │   │   ├── eventTypes 通配匹配
    │   │   ├── bizScope 业务线过滤
    │   │   └── SpEL condition 条件过滤
    │   └── 投递到匹配的消费者
    │
    ├── order.* 事件
    │   ├── 物流服务 (logistics-consumer)      → 生成物流单
    │   ├── 积分服务 (points-consumer)          → 赠送积分
    │   ├── 营销服务 (marketing-consumer)       → 更新营销数据
    │   └── 数据分析 (analytics-consumer)       → 同步数据仓库
    │
    ├── payment.* 事件
    │   ├── 财务系统 (finance-consumer)         → 记账
    │   └── 结算服务 (settlement-consumer)      → 商家结算
    │
    └── aftersale.* 事件
        ├── CRM (crm-consumer)                 → 更新客户信息
        └── 财务系统 (finance-consumer)          → 账务调整
```

### 2.4 事件投递保障

#### 消费重试策略

```
消息消费失败
    │
    ├── 第 1 次失败 → 立即重试
    ├── 第 2 次失败 → 延迟 10s 重试
    ├── 第 3 次失败 → 延迟 30s 重试
    ├── 第 4 次失败 → 延迟 1min 重试
    ├── 第 5 次失败 → 延迟 5min 重试
    ├── 第 6 次失败 → 延迟 15min 重试
    ├── 第 7-10 次失败 → 延迟 30min 重试
    │
    └── 超过最大重试次数 → 投递死信队列 (DLQ)
        ├── XXL-Job 每小时扫描 DLQ，人工处理后重投
        └── DLQ 深度 > 100 触发 P1 告警
```

**RocketMQ 重试配置**：

```yaml
# RocketMQ 消费者配置
order-event-consumer:
  group: "order-event-consumer"
  topic: "ORDER_EVENT"
  messageModel: "CLUSTERING"
  consumeTimeout: 15  # 消息处理超时（分钟）
  maxReconsumeTimes: 10  # 最大重试次数
  retryDelayLevel: "1s,5s,10s,30s,1m,2m,3m,5m,10m,20m"  # 自定义延迟级别
```

#### 事件投递日志

每次事件消费记录持久化到 `event_delivery_log` 表，用于追溯和对账：

```java
@Data
public class EventDeliveryLog {
    private Long id;
    private String eventId;         // 事件 ID
    private String eventType;       // 事件类型
    private String consumerGroup;   // 消费者组
    private String status;          // SUCCESS / FAILED / RETRYING / DLQ
    private Integer attempt;        // 当前重试次数
    private String errorMessage;    // 失败原因
    private Long durationMs;        // 消费耗时
    private String traceId;         // 链路追踪 ID
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 事件保序策略

要求保序的场景（如同一个订单的操作必须按顺序消费）：

| 场景 | 保序级别 | 方案 |
|------|---------|------|
| 订单状态变更 | 必须保序 | 按 orderId hash 到同一 MessageQueue |
| 支付事件 | 必须保序 | 按 paymentTrxId hash 到同一 MessageQueue |
| 物流事件 | 无需保序 | 随机分发 |
| 统计事件 | 无需保序 | 随机分发，最终一致 |

```java
// 按 orderId 路由到固定分区，保证同一个订单的事件有序消费
public void publish(OrderEvent event) {
    String routingKey = event.getBusinessKey();  // orderId
    // RocketMQ MessageQueue 选择器：key.hashCode() % queueCount
    rocketMQTemplate.syncSend("ORDER_EVENT", event, routingKey);
}
```

### 2.5 事件与 Webhook 的衔接

内部事件消费和外部 Webhook 推送共享同一事件源：

```
事件发布
    │
    ├── RocketMQ ORDER_EVENT
    │   ├── 内部消费者（物流/积分/营销）
    │   │   └── 直接订阅，内部处理
    │   │
    │   └── Webhook Dispatch Consumer
    │       ├── 查询 event_subscription 表（notify_url 非空）
    │       ├── 匹配事件类型
    │       ├── HMAC-SHA256 签名（ADR-033）
    │       └── HTTP POST 投递到外部 URL
    │           ├── 成功 → 更新 delivery_log
    │           ├── 失败 → 重试（指数退避，最多 10 次）
    │           └── 最终失败 → DLQ + 告警
    │
    └── 事件归档
        └── 写入 event_store 表（按月分区，保留 90 天）
```

---

## 3. 集成可观测性

ADR-027 覆盖了服务级可观测性，本节聚焦**集成维度**的可观测性——从 API 调用和事件流两个维度衡量集成质量。

### 3.1 API 可观测性指标

#### 接入方维度指标

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `omplatform_integration_api_requests_total` | Counter | `api_group, method, path, app_key, status` | 各 AppKey 的 API 调用总量 |
| `omplatform_integration_api_duration_seconds` | Histogram | `api_group, method, path, app_key` | 请求延迟分布 |
| `omplatform_integration_api_error_total` | Counter | `api_group, method, path, app_key, error_code` | 各错误码出现次数 |
| `omplatform_integration_api_rate_limited_total` | Counter | `api_group, app_key, limit_type` | 限流触发次数（QPS/日配额） |
| `omplatform_integration_api_quota_usage_ratio` | Gauge | `api_group, app_key` | 当前配额使用率（0-1） |

#### 端到端延迟追踪

对于关键订单操作（下单、支付回调），追踪端到端延迟：

```
API Request (Gateway 入口)
    ├── gateway_latency    — Gateway 路由处理时间
    ├── business_latency   — 业务逻辑处理时间（调用链总和）
    │   ├── order-core     — 订单创建
    │   ├── inventory      — 库存预占
    │   └── payment        — 支付请求
    ├── db_latency         — 数据库操作时间
    └── total_latency      — 总耗时（客户端感知）

SkyWalking Trace 自动捕获所有 Span，通过 traceId 关联
```

#### API 异常诊断

```yaml
# API 错误实时聚合告警规则
groups:
  - name: api-integration
    rules:
      - alert: OpenApiErrorRateBurst
        expr: |
          sum(rate(omplatform_integration_api_errors_total{api_group="open_api"}[5m]))
          / sum(rate(omplatform_integration_api_requests_total{api_group="open_api"}[5m]))
          > 0.05
        for: 2m
        labels:
          severity: ticket  # P2 告警
        annotations:
          summary: "Open API 错误率超过 5%（最近 5 分钟）"
          description: "错误率异常，请检查 External Gateway 和 order-core 状态"

      - alert: QuotaExhaustion
        expr: |
          omplatform_integration_api_quota_usage_ratio{api_group="open_api"} > 0.95
        labels:
          severity: ticket
        annotations:
          summary: "AppKey {{ $labels.app_key }} 配额即将耗尽（{{ $value | humanizePercentage }}）"
```

### 3.2 事件可观测性指标

#### 事件流维度指标

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `omplatform_integration_event_published_total` | Counter | `event_type, source, status` | 事件生产速率 |
| `omplatform_integration_event_consumed_total` | Counter | `event_type, consumer_group, status` | 事件消费速率 |
| `omplatform_integration_event_consume_duration_seconds` | Histogram | `event_type, consumer_group` | 消费耗时分布 |
| `omplatform_integration_event_backlog` | Gauge | `event_type, consumer_group` | 消费堆积深度 |
| `omplatform_integration_event_dead_letter_total` | Counter | `event_type, consumer_group` | 死信投递次数 |
| `omplatform_integration_event_retry_total` | Counter | `event_type, consumer_group, attempt` | 重试次数分布 |

#### 事件链路端到端延迟

```
事件发布 (EventPublish timestamp T1)
    ├── MQ 延迟 (T2 - T1)     —— RocketMQ 存储转发时间
    ├── 消费调度延迟 (T3 - T2)  —— 从 MQ 到消费者的排队时间
    ├── 业务处理延迟 (T4 - T3)  —— 消费者处理事件的时间
    └── 总端到端延迟 (T4 - T1)  —— 事件从发布到处理完成全链路
```

每个事件携带 `timestamp`（发布时间），消费者在处理后记录处理完成时间，延迟指标按事件类型聚合。

### 3.3 集成健康大盘设计

#### 看板 1：集成总览（集成负责人视角）

```
┌─────────────────────────────────────────────────────────────┐
│                 集成健康总览                          [刷新] │
├─────────────────────────────────────────────────────────────┤
│ ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│ │ API 总调用量   │  │ 事件总吞吐量   │  │ 集成健康评分       │  │
│ │ 125.3K/日    │  │ 892.7K/日    │  │ ★★★★☆ (92/100)    │  │
│ │ +12.5% ↑     │  │ +8.3% ↑     │  │ 较昨日 +2          │  │
│ └──────────────┘  └──────────────┘  └────────────────────┘  │
│                                                              │
│ ┌─── API 调用 Top5 AppKey ────┐  ┌─── 事件消费堆积 Top5 ──┐  │
│ │ app_key_001: 45.2K  ━━━━━  │  │ logistics:  1,230    ━━ │  │
│ │ app_key_002: 32.1K  ━━━━   │  │ points:       456   ━  │  │
│ │ app_key_003: 18.7K  ━━━    │  │ finance:      120   ━  │  │
│ │ app_key_004: 12.3K  ━━     │  │ analytics:     89   ━  │  │
│ │ app_key_005:  8.9K  ━      │  │ crm:           12   ━  │  │
│ └─────────────────────────────┘  └────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### 看板 2：API 详情（AppKey/接口维度）

```
┌─── Open API 调用趋势（最近 24h） ─────────────────────────────┐
│  ┌────────────────────────────────────────────────────┐       │
│  │ ▁▂▃▄▅▆▇█▇▆▅▄▃▂▁▁▂▃▄▅▆▇█▇▆▅▄▃▂▁  QPS 曲线          │       │
│  └────────────────────────────────────────────────────┘       │
│                                                               │
│  QPS: 当前 125 | P50 18ms | P99 95ms | 错误率 0.23%           │
├───────────────────────────────────────────────────────────────┤
│ AppKey 排名 | QPS    | P99    | 错误率  | 配额使用率 | 限流次数  │
│ app_key_001 | 45.2/s | 82ms   | 0.12%  | 78%       | 12       │
│ app_key_002 | 32.1/s | 156ms  | 1.23%  | 92%       | 45       │
│ app_key_003 | 18.7/s | 45ms   | 0.01%  | 35%       | 0        │
│ app_key_004 | 12.3/s | 210ms  | 3.45%  | 88%       | 67       │
│ app_key_005 |  8.9/s | 63ms   | 0.05%  | 55%       | 3        │
└───────────────────────────────────────────────────────────────┘
```

#### 看板 3：事件详情（事件类型/消费者组维度）

```
┌─── 事件流健康状况（最近 24h） ─────────────────────────────────┐
│                                                               │
│  事件类型     | 生产速率   | 消费速率  | 堆积  | 失败率  | P99延迟│
│  order.paid  | 125/s     | 124/s    | 23   | 0.01%  | 45ms   │
│  order.created| 232/s    | 231/s    | 12   | 0.02%  | 32ms   │
│  order.shipped| 89/s     | 88/s     | 5    | 0.05%  | 78ms   │
│  payment.*   | 156/s     | 155/s    | 89   | 0.10%  | 120ms  │
│  logistics.* | 78/s      | 72/s     | 1230 | 2.30%  | 3500ms │  ← 异常
│  aftersale.* | 34/s      | 34/s     | 0    | 0.00%  | 25ms   │
│                                                               │
│  ⚠ 物流消费者 (logistics-consumer) 消费延迟偏高               │
│  最后 100 条失败原因: 物流 API 超时 (78%), 参数异常 (12%)     │
└───────────────────────────────────────────────────────────────┘
```

### 3.4 告警规则

| 告警名称 | 条件 | 级别 | 响应 |
|---------|------|------|------|
| API 错误率突增 | 最近 5 分钟错误率 > 5% | P2 | 群消息 |
| 单一 AppKey 错误率突增 | 特定 AppKey 错误率 > 10% | P2 | 群消息 + 通知开发者 |
| AppKey 配额耗尽 | 配额使用率 > 95% | P3 | 日报推送 |
| 事件消费堆积 | 堆积深度 > 10,000 持续 5min | P1 | 群 @oncall |
| 事件消费失败率突增 | 失败率 > 5% 持续 2min | P2 | 群消息 |
| DLQ 深度超过阈值 | DLQ 消息数 > 100 | P1 | 群 @oncall |
| Webhook 投递成功率低 | 成功率 < 80% 持续 10min | P2 | 群消息 |
| 端到端延迟突增 | 事件端到端 P99 > 5s | P2 | 群消息 |

---

## 4. 数据模型

### 4.1 event_store（事件存储表）

事件持久化存储，用于追溯、对账和问题排查：

```sql
CREATE TABLE `event_store` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `event_id`      VARCHAR(64) NOT NULL COMMENT '全局唯一事件 ID',
  `event_type`    VARCHAR(128) NOT NULL COMMENT '事件类型',
  `schema_version` INT DEFAULT 1 COMMENT 'Schema 版本号',
  `source`        VARCHAR(64) NOT NULL COMMENT '事件来源服务',
  `business_key`  VARCHAR(64) COMMENT '业务主键（orderId）',
  `biz_scope`     VARCHAR(32) COMMENT '业务线',
  `trace_id`      VARCHAR(64) COMMENT '链路追踪 ID',
  `payload`       JSON NOT NULL COMMENT '事件数据负载',
  `status`        VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED / FAILED',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_event_type` (`event_type`),
  INDEX `idx_business_key` (`business_key`),
  INDEX `idx_created_at` (`created_at`)
) COMMENT='事件存储表（按月 RANGE 分区）'
PARTITION BY RANGE (TO_DAYS(`created_at`)) (
  PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
  PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
  PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

**保留策略**：在线保留 90 天，超过 90 天迁移到 OSS Parquet 归档（ADR-031），归档后删除在线记录。

### 4.2 event_delivery_log（事件投递日志表）

```sql
CREATE TABLE `event_delivery_log` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `event_id`        VARCHAR(64) NOT NULL COMMENT '事件 ID',
  `event_type`      VARCHAR(128) NOT NULL COMMENT '事件类型',
  `consumer_group`  VARCHAR(128) NOT NULL COMMENT '消费者组',
  `subscription_id` BIGINT COMMENT '订阅 ID',
  `status`          VARCHAR(16) NOT NULL COMMENT 'SUCCESS / FAILED / RETRYING / DLQ',
  `attempt`         INT DEFAULT 1 COMMENT '当前重试次数',
  `max_attempts`    INT DEFAULT 5 COMMENT '最大重试次数',
  `error_message`   TEXT COMMENT '失败原因',
  `duration_ms`     BIGINT COMMENT '消费耗时',
  `trace_id`        VARCHAR(64) COMMENT '链路追踪 ID',
  `delivered_at`    DATETIME COMMENT '投递成功时间',
  `next_retry_at`   DATETIME COMMENT '下次重试时间',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_event_id` (`event_id`),
  INDEX `idx_consumer_group` (`consumer_group`),
  INDEX `idx_status_retry` (`status`, `next_retry_at`)
) COMMENT='事件投递日志表';
```

### 4.3 api_audit_log（API 审计日志表）

```sql
CREATE TABLE `api_audit_log` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `api_group`     VARCHAR(32) NOT NULL COMMENT 'open_api / admin_api / backend_api',
  `method`        VARCHAR(8) NOT NULL COMMENT 'GET / POST / PUT / DELETE',
  `path`          VARCHAR(256) NOT NULL COMMENT '请求路径',
  `app_key`       VARCHAR(64) COMMENT '调用方 AppKey（Open API）',
  `user_id`       VARCHAR(64) COMMENT '用户 ID（Buyer/Admin API）',
  `status_code`   INT NOT NULL COMMENT 'HTTP 状态码',
  `error_code`    INT COMMENT '业务错误码',
  `duration_ms`   BIGINT COMMENT '请求耗时',
  `trace_id`      VARCHAR(64) COMMENT '链路追踪 ID',
  `request_body`  TEXT COMMENT '请求体（脱敏后）',
  `response_body` TEXT COMMENT '响应体（脱敏后，仅记录 4xx/5xx）',
  `client_ip`     VARCHAR(64) COMMENT '客户端 IP',
  `user_agent`    VARCHAR(256) COMMENT 'User-Agent',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_api_group_created` (`api_group`, `created_at`),
  INDEX `idx_app_key` (`app_key`),
  INDEX `idx_trace_id` (`trace_id`)
) COMMENT='API 审计日志表（按天 RANGE 分区）';
```

**保留策略**：在线保留 30 天，超过 30 天归档到 OSS 冷存储（压缩后保留 1 年），用于安全审计。

### 4.4 integration_health_config（集成健康检查配置表）

```sql
CREATE TABLE `integration_health_config` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
  `target_type`     VARCHAR(16) NOT NULL COMMENT 'API / EVENT',
  `target_id`       VARCHAR(128) NOT NULL COMMENT 'AppKey / ConsumerGroup',
  `sli_latency_p99` BIGINT COMMENT '延迟 P99 基线（ms）',
  `sli_error_rate`  DECIMAL(5,4) COMMENT '错误率基线',
  `sli_min_throughput` INT COMMENT '最低吞吐基线（每分钟）',
  `alert_level`     VARCHAR(8) COMMENT '告警级别（P1/P2/P3）',
  `enabled`         TINYINT(1) DEFAULT 1,
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_target` (`target_type`, `target_id`)
) COMMENT='集成健康检查配置表';
```

---

## 5. Apollo 配置

### 5.1 命名空间规划

| 命名空间 | 内容 | 热生效 | 关联 |
|---------|------|--------|------|
| `integration.api-routes` | API 路由映射、分组配置 | ✅ | 各 Gateway |
| `integration.api-quota` | AppKey 配额、限流阈值 | ✅ | Ext Gateway |
| `integration.event-subscription` | 事件订阅规则（补充 DB） | ✅ | EventRouter |
| `integration.event-retry` | 事件重试策略（延迟级别、最大次数） | ✅ | EventConsumer |
| `integration.health` | 集成健康检查阈值 | ✅ | 告警引擎 |

### 5.2 API 配额配置（`integration.api-quota`）

```yaml
# integration.api-quota 命名空间
app_keys:
  app_key_001:
    name: "第三方 ERP 系统"
    qps: 1000                # 每秒最大请求数
    daily_quota: 100000      # 每日最大请求数
    allowed_apis:            # 允许访问的 API 列表
      - "/open/v1/orders/**"
      - "/open/v1/logistics/**"
    rate_limit_strategy: "sliding_window"  # 限流算法（sliding_window / token_bucket）
    
  app_key_002:
    name: "数据分析平台"
    qps: 500
    daily_quota: 50000
    allowed_apis:
      - "/open/v1/orders/**"
    rate_limit_strategy: "token_bucket"

# 默认配额（未在 app_keys 中配置的 AppKey）
default_quota:
  qps: 100
  daily_quota: 10000
  rate_limit_strategy: "sliding_window"
```

### 5.3 事件路由规则配置（`integration.event-subscription`）

```yaml
# integration.event-subscription 命名空间
# 补充 event_subscription 表的动态规则

routing_rules:
  - subscription_name: "logistics-service"
    match:
      event_types: ["order.paid", "order.shipped"]
      biz_scope: ["ecommerce", "locallife"]
    actions:
      - type: "forward_to_mq"
        target_topic: "ORDER_EVENT_LOGISTICS"
      - type: "add_header"
        header:
          priority: "high"
    retry:
      max_attempts: 5
      backoff: "EXPONENTIAL"
      
  - subscription_name: "points-service"
    match:
      event_types: ["order.paid", "order.completed"]
      condition: "payload.amount >= 0"  # 所有金额都赠送积分
    actions:
      - type: "forward_to_mq"
        target_queue: "consumer-points-group"
    retry:
      max_attempts: 3
      backoff: "FIXED"
      fixed_delay_ms: 10000
```

### 5.4 集成健康阈值配置（`integration.health`）

```yaml
# integration.health 命名空间
thresholds:
  api:
    error_rate_warning: 0.05       # 5% 错误率 → P2 告警
    error_rate_critical: 0.10      # 10% 错误率 → P1 告警
    latency_p99_warning_ms: 500    # P99 500ms → P2 告警
    latency_p99_critical_ms: 2000  # P99 2000ms → P1 告警
    
  event:
    backlog_warning: 10000         # 堆积 10000 → P1 告警
    backlog_critical: 50000        # 堆积 50000 → P0 告警
    fail_rate_warning: 0.05        # 5% 消费失败率 → P2 告警
    dlq_warning: 100               # DLQ 100 → P1 告警
    
  webhook:
    success_rate_warning: 0.80     # 80% 投递成功率 → P2 告警
    success_rate_critical: 0.50    # 50% 投递成功率 → P1 告警
```

---

## 6. 与现有 ADR 的关联

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-010** 事件 Schema 治理 | 依赖 | ADR-010 提供事件 Schema 版本管理和兼容性校验，事件中心的事件定义遵循其规范 |
| **ADR-019** 异步任务中心 | 依赖 | 大数据量导出走 ADR-019 的异步任务框架 |
| **ADR-025** API 版本 + 外部网关 | 增强 | ADR-025 定义外部 Gateway 和 API 版本化，本设计在此基础上增加 API 分层标准和统一响应规范 |
| **ADR-027** 可观测性架构 | 补充 | ADR-027 定义服务级可观测性，本设计补充集成维度的 API 和事件可观测性 |
| **ADR-029** 内部 Gateway | 引用 | 本设计的 API 分层基于 ADR-029 的 Internal/External Gateway 划分 |
| **ADR-031** 数据生命周期 | 引用 | 事件存储的归档策略与 ADR-031 的冷存储方案对齐 |
| **ADR-033** Webhook 系统 | 整合 | ADR-033 的 Webhook 投递引擎作为事件中心的外部推送通道，本设计定义事件→Webhook 的衔接规范 |

### 决策对比

本设计在以下方面与现有 ADR 做出不同决策或补充：

| 维度 | 本设计 | 原有 ADR | 理由 |
|------|--------|----------|------|
| **API 响应格式** | `ApiResult<T>` 统一格式 | 各服务自定义（`Result<T>` / `ApiResponse<T>` / 原始 JSON） | 统一消费体验，SDK 自动生成的基础 |
| **事件订阅管理** | DB 表 + Apollo 双管理 | ADR-010 仅有 Schema 周期管理 | DB 保证持久性和查询，Apollo 保证动态调整 |
| **集成可观测性** | 按 AppKey / ConsumerGroup 维度 | ADR-027 仅服务级维度 | 集成视角需要有接入方粒度的观测能力 |
| **事件归档** | event_store + OSS 冷存储 | ADR-010 仅提到事件归档表 | 明确分区策略和保留周期 |

---

## 7. 实施计划

| 阶段 | 核心任务 | 工时 | 产出 |
|------|---------|------|------|
| **P1 API 规范落地** | 统一 `ApiResult` 响应格式 + 错误码体系 + 分页规范 | 2d | 4 个服务 Core 模块 API 规范对齐 |
| **P2 API 分层** | Buyer/Admin/Backend API 接口拆分 + 路由配置 | 2d | IGW 路由规则 + 4 层 Knife4j 文档 |
| **P3 事件中心建设** | EventPublisher SDK + 事务消息 + event_store 表 | 3d | event-store 模块 + MQ 事务消息 |
| **P4 事件订阅管理** | event_subscription 表 + 管理 API + EventRouter | 2d | 订阅管理 CRUD + 路由引擎 |
| **P5 集成可观测性** | 集成指标埋点 + 3 个 Grafana 看板 + 告警规则 | 2d | 集成健康大盘 (API/事件/总览) |
| **P6 外部通道** | 事件→Webhook 衔接 + OpenAPI SDK 生成 | 1.5d | Webhook 消费者适配器 + SDK 自动发布 |

**合计**：12.5 人天

---

## 8. 上线检查清单

- [ ] 所有对外 API 返回统一 `ApiResult<T>` 格式
- [ ] 4 层 API（Buyer/Admin/Backend/Open）路由正确配置到对应 Gateway
- [ ] Knife4j 文档分组正确，每个接口标注完整 `@Operation` + `@ApiResponses`
- [ ] 错误码枚举完整（覆盖 1xxx-5xxx），错误响应包含 `traceId`
- [ ] `EventPublisher.publish()` 使用 RocketMQ 事务消息
- [ ] `event_store` 表按月分区，DDL 已执行
- [ ] 核心事件（order.created/paid/shipped）端到端消费验证
- [ ] `event_subscription` 管理 API 通过冒烟测试
- [ ] 集成指标（`omplatform_integration_*`）在 Prometheus 中可查
- [ ] 3 个 Grafana 看板（总览/API/事件）上线
- [ ] 告警规则（堆积/错误率/DLQ）配置验证
- [ ] 事件→Webhook 联动测试（Event → RocketMQ → WebhookDispatch → HTTP POST）
- [ ] Open API 配额限流验证（超限后返回 429 + `X-RateLimit-*` Header）
- [ ] API 审计日志记录（4xx/5xx 记录 request + response body）
