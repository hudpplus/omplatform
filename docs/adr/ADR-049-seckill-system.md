# ADR-049: 秒杀系统设计与实现

## 状态

**已实现** (2026-06-26, V3: +三级库存)

## 上下文

项目已有 12+ ADR 文档定义了秒杀架构设计（FLASH_SALE 订单分类、seckill_activity_id、
C4 的 FlashSaleHandler、ADR-040 的秒杀限流 tier），但代码层面为零——无活动表、
无秒杀库存、无接口。底层基础设施（Redis Lua、Sentinel、Saga、RocketMQ）已就绪，
需编排成秒杀链路。

## 决策

### 架构选型

| 决策 | 方案 | 理由 |
|------|------|------|
| 秒杀库存位置 | oms-trade 直连 Redis（StringRedisTemplate + Lua），不经 Dubbo | 减少一次 RTT，秒杀场景延迟敏感 |
| Saga 复用 | 修改现有 `CreateOrderSagaDefinition`，步骤内判断 seckill 模式 | 避免重复 Saga 结构代码 |
| 订单表扩展 | OrderEntity 直接加 seckillActivityId + seckillPipeline | 避免 1:1 扩展表 Join |
| 秒杀价传递 | CreateOrderRequest.seckillPrice 覆盖正常计价 | 秒杀价在活动表已定，无需调用 marketing |

### 四层保护机制

| 层 | 机制 | 技术选型 | 原理 |
|----|------|---------|------|
| 1 | 网络层限流 | Sentinel `CONTROL_BEHAVIOR_RATE_LIMITER` 排队模式 | 请求在 Sentinel 中排队，匀速放行 |
| 2 | 应用层限流 | Redis Lua 令牌桶 | 活动级精确限流，突发削平，独立于 Sentinel |
| 3 | 削峰 | RocketMQ 异步化 | 入口只抢库存 + 发 MQ，下单 Saga 由消费端异步执行 |
| 4 | 防重 | Redis SET NX 幂等键 | 每次点击生成唯一 requestId，服务端去重 |

---

## 数据流

### 秒杀执行流程（MQ 异步）

```
POST /api/v1/seckill/{activityId}/execute
Header: X-User-Id (由 Gateway 解析 JWT 注入)
Body: { skuId, quantity, requestId }
  │
  ├─ [1] Sentinel 限流检查（网络层）
  │     ├─ 排队模式（默认 500 QPS，超时 500ms）
  │     ├─ 快速失败模式（默认 5000 QPS 硬上限）
  │     └─ 被限流 → SeckillBlockHandler → FLOW_LIMITED
  │
  ├─ [2] SeckillOrderHandler.execute()
  │     ├─ 2a. 校验活动（存在 + ACTIVE + 时间窗口）
  │     ├─ 2b. 令牌桶限流（Redis Lua，活动级）
  │     │     无令牌 → TOKEN_BUCKET_LIMITED
  │     ├─ 2c. 防重检查（SET NX seckill:dedup:{activityId}:{requestId} TTL 10s）
  │     │     已存在 → DUPLICATE_SUBMIT
  │     ├─ 2d. 买家限购（order 表查询已购数 ≥ limitPerUser）
  │     │     超限 → BUYER_LIMIT
  │     ├─ 2e. Redis Lua 原子抢库存
  │     │     DECR seckill:stock:{activityId}:{skuId}
  │     │     SET seckill:hold:{orderNo} TTL 15min
  │     │     售罄 → SOLD_OUT
  │     ├─ 2f. 发送 RocketMQ（seckill-ORDER_CREATE_REQUEST）
  │     │     发送失败 → 释放库存 + ORDER_FAILED
  │     └─ 2g. 返回 PROCESSING（抢购成功，订单处理中）
  │
  └─ [3] SeckillOrderConsumer（MQ 消费端，异步）
        ├─ 3a. 幂等检查（orderNo 查表，已存在则跳过）
        ├─ 3b. 构建 CreateOrderRequest
        └─ 3c. 执行 CreateOrderSaga（秒杀模式）
              ├─ createOrder：使用 seckillPrice，跳过 calculatePrice + lockCoupon
              ├─ deductInventory：跳过（库存已在入口预占）
              ├─ chargePayment：正常发起支付
              └─ confirmOrder：正常确认
                    ├─ 成功 → 发布 ORDER_CREATED 事件
                    └─ 失败 → releaseStock() + 日志告警
```

### Saga 补偿流程

```
Saga 失败 / MQ 发送失败 → SeckillOrderHandler.releaseStock()
  ├─ Redis Lua INCR seckill:stock:{activityId}:{skuId}
  └─ DEL seckill:hold:{orderNo}

兜底：hold TTL 15min 自动归还
```

---

## 限流 — Sentinel（网络层）

### 规则配置

由 `SeckillSentinelConfig` 在启动时注册：

| 规则 | 模式 | QPS | 排队超时 | 作用 |
|------|------|-----|---------|------|
| 排队模式 | `CONTROL_BEHAVIOR_RATE_LIMITER` | 500（可配） | 500ms（可配） | 削第一波峰值 |
| 快速失败 | `CONTROL_BEHAVIOR_DEFAULT` | 5000（10×排队QPS） | — | 硬上限：超出直接拒绝 |

**环境变量覆盖：**
- `SECKILL_SENTINEL_QPS` — 排队模式 QPS（默认 500）
- `SECKILL_SENTINEL_QUEUE_TIMEOUT_MS` — 排队超时毫秒（默认 500）

指标：Sentinel 控制台 `localhost:8088` 可实时查看 `seckill:execute` 资源的
pass / block QPS。

---

## 限流 — Redis 令牌桶（应用层）

### 原理

```
  令牌以固定速率(200/s)注入桶中
  ↓ ↓ ↓ ↓ ↓ ...
┌─────────────────────┐
│  令牌桶 (容量 500)   │  ← 每个请求消耗 1 个令牌
└─────────────────────┘
  │
  令牌不足 → 拒绝请求
```

每个秒杀活动独立一个桶，Key 格式：`seckill:token_bucket:{activityId}`。
使用 Redis Hash + Lua 原子操作，示数支持浮点精度（补水率可配为小数）。

### Lua 脚本算法

1. 读取桶状态 `{tokens, lastRefillTimeMilliseconds}`
2. 计算自上次补水以来的时间差 → `refill = rate × elapsed / 1000`
3. 更新令牌数 = `min(capacity, tokens + refill)`
4. `tokens ≥ requested` → 扣减并返回 `{1, remaining}`
5. 否则返回 `{0, available}`（令牌不足）
6. 即使拒绝也要更新 `lastRefill`，避免时间窗口浪费

### 配置

| 参数 | 环境变量 | 默认值 | 说明 |
|------|---------|--------|------|
| 桶容量 | `SECKILL_TOKEN_BUCKET_CAPACITY` | 500 | 最大突发请求数 |
| 补水率 | `SECKILL_TOKEN_BUCKET_REFILL_RATE` | 200 | 每秒注入令牌数 |

### 限流效果

```
瞬时洪峰 → Sentinel(500QPS) → 令牌桶(500容量, 200/s) → 抢库存
  ↑                          ↑
网络层削峰                  应用层削平突发
```

令牌桶与 Sentinel 相辅相成：
- Sentinel 控制**入口 QPS**（网络连接级）
- 令牌桶控制**应用处理速率**（活动级，独立于 Sentinel）

---

## 削峰 — RocketMQ

### 为什么用 MQ 做削峰

最初使用 Sentinel 排队模式同步削峰（请求排队等待放行），但存在不足：
1. **HTTP 线程阻塞** — 排队 + Saga（200ms+）使请求线程长期占用
2. **DB / Dubbo 压力不可控** — 放行后瞬时打到下游
3. **失败处理复杂** — 同步链路中任何步骤失败都需要额外兜底

改用 RocketMQ 异步削峰后：

```
入口（HTTP 线程，<10ms）
  ├─ 校验
  ├─ 令牌桶
  ├─ SET NX 防重
  ├─ Redis 抢库存（1-3ms）
  └─ 发 MQ（1-5ms）→ 立即返回 PROCESSING

消费端（异步，可控速率）
  └─ Saga（100-500ms）→ DB / Dubbo 压力平稳
```

### Topic

| Topic | Consumer Group | 说明 |
|-------|---------------|------|
| `seckill-ORDER_CREATE_REQUEST` | `trade-seckill-consumer` | 秒杀订单创建请求 |

### 消息体

```java
public record SeckillOrderMessage(
    Long activityId,
    String buyerId,
    String skuId,
    Integer quantity,
    String orderNo,
    BigDecimal seckillPrice,
    String requestId,
    LocalDateTime occurredAt
) {}
```

### 幂等消费

消费端通过 `orderRepository.getById(orderNo) != null` 做幂等检查：
MQ 至少一次投递，订单已存在则跳过。

### 失败处理

| 失败场景 | 处理方式 |
|----------|---------|
| MQ 发送失败 | 立即 releaseStock（INCR + DEL），返回 ORDER_FAILED |
| Saga 执行异常 | releaseStock + 日志告警，hold TTL 15min 兜底 |
| 活动不存在 | 释放库存（消费端 check） |
| RabbitMQ 宕机 | 入口 MQ 发送超时 → 释放库存，hold 15min 自动归还 |

---

## 三级库存模型

秒杀库存拆分为三级，分别对应生命周期中的不同阶段：

```
总库存 = 可用(available) + 预留(held) + 已售(sold)
         ↓                  ↓               ↓
     用户可以抢          已抢未支付       支付确认
```

### Redis Key

| Key | 格式 | 说明 |
|-----|------|------|
| 可用库存 | `seckill:stock:{activityId}:{skuId}:available` | 预热时从 DB 加载，用户可抢 |
| 预留库存 | `seckill:stock:{activityId}:{skuId}:held` | 已抢但未支付，预热时为 0 |
| hold 明细 | `seckill:hold:{orderNo}` | SET with TTL（默认 15min） |
| 幂等键 | `seckill:dedup:{activityId}:{requestId}` | SET NX with TTL（10s） |
| 令牌桶 | `seckill:token_bucket:{activityId}` | Redis Hash，活动级限流 |

### 库存流转

```
抢购成功：
  available  -1
  held       +1
  hold       写明细 (TTL)

支付确认（Saga 成功）：
  held       -1     ← 三级库存扣减
  hold       删除
  DB deductDbStock   ← 记录已售

取消/超时：
  available  +1
  held       -1
  hold       删除
```

### Lua 脚本

**seckill_grab.lua** — 可用→预留：

```lua
-- KEYS[1]: available key   (seckill:stock:{aid}:{sku}:available)
-- KEYS[2]: held key        (seckill:stock:{aid}:{sku}:held)
-- KEYS[3]: hold detail     (seckill:hold:{orderNo})
-- ARGV[1]: quantity
-- ARGV[2]: hold_ttl_seconds
-- ARGV[3]: orderNo

local available = tonumber(redis.call('GET', KEYS[1]))
if not available then return {0, 'ACTIVITY_NOT_READY', 0} end
if available < tonumber(ARGV[1]) then return {0, 'SOLD_OUT', available} end

redis.call('DECRBY', KEYS[1], ARGV[1])          -- available -1
redis.call('INCRBY', KEYS[2], ARGV[1])           -- held +1
redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[2]) -- hold 明细
return {1, 'OK', available - tonumber(ARGV[1])}
```

**seckill_release.lua** — 预留→可用（含 hold 过期兜底）：

```lua
-- hold 存在且匹配：available +1, held -1
-- hold 已过期：available +1, held = max(0, held - qty)  ← 不丢库存
-- hold 不匹配：返回 HOLD_MISMATCH（异常）
```

**seckill_deduct.lua** — 预留→已售（支付确认）：

```lua
-- KEYS[1]: held key
-- KEYS[2]: hold detail
-- held -1, 删除 hold
-- 已售由 DB deductDbStock 跟踪
```

### 恒等式

三级库存始终满足（秒杀活动粒度）：

```
available(Redis) + held(Redis) + sold(DB) = total_stock(DB)
  ↑                    ↑              ↑
预热时 total       预热时 0       预热时 0
```

- MySQL 端：`total_stock` 不变，`available_stock` 随支付递减（记录已售）
- Redis 端：`available` + `held` = MySQL 剩余总量

---

### 设计

```
客户端                           服务端
  │                                │
  ├─ 用户点击秒杀                    │
  ├─ 生成 requestId = UUID()        │
  ├─ POST + requestId ──────────→   │
  │                           SET NX dedup:{activityId}:{requestId}
  │                           TTL 10s
  │                           ├─ 成功 → 继续抢库存
  │                           └─ 失败 → DUPLICATE_SUBMIT
  │                                │
  ├─ (用户双击)同一 requestId ──→   │
  │                           SET NX 失败 → DUPLICATE_SUBMIT
  │                                │
  ├─ 用户主动重试（新 requestId）→   │
  │                           SET NX 成功 → 正常处理
```

### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 幂等键 TTL | 10s | 覆盖双击间隔，允许 10s 后重试 |
| Key 格式 | `seckill:dedup:{activityId}:{requestId}` | 按活动 + 请求粒度 |
| 值 | 买家 ID | 便于追溯 |

---

## 秒杀库存模型

### Redis Key 命名（三级库存）

| Key | 格式 | 说明 |
|-----|------|------|
| 可用库存 | `seckill:stock:{activityId}:{skuId}:available` | 预热时从 DB 加载，抢购时 DECR |
| 预留库存 | `seckill:stock:{activityId}:{skuId}:held` | 已抢未支付，抢购时 INCR |
| 持有记录 | `seckill:hold:{orderNo}` | SET with TTL（默认 15min） |
| 幂等键 | `seckill:dedup:{activityId}:{requestId}` | SET NX with TTL（10s） |
| 令牌桶 | `seckill:token_bucket:{activityId}` | Redis Hash，活动级限流 |

### Lua 脚本

**seckill_grab.lua** — 原子抢库存（三级库存）：

```lua
-- KEYS[1]: available   seckill:stock:{aid}:{sku}:available
-- KEYS[2]: held        seckill:stock:{aid}:{sku}:held
-- KEYS[3]: hold detail seckill:hold:{orderNo}
-- ARGV[1]: quantity
-- ARGV[2]: hold_ttl_seconds
-- ARGV[3]: orderNo

local available = tonumber(redis.call('GET', KEYS[1]))
if not available then return {0, 'ACTIVITY_NOT_READY', 0} end
local qty = tonumber(ARGV[1])
if available < qty then return {0, 'SOLD_OUT', available} end

redis.call('DECRBY', KEYS[1], qty)           -- available -1
redis.call('INCRBY', KEYS[2], qty)           -- held +1
redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[2])
return {1, 'OK', available - qty}
```

**seckill_release.lua** — 原子释放（含 hold 过期兜底）：

```lua
-- KEYS[1]: available  KEYS[2]: held  KEYS[3]: hold detail
-- hold 存在且匹配: available +1, held -1
-- hold 过期:      available +1, held = max(0, held - qty)
-- hold 不匹配:    return HOLD_MISMATCH（不释放）
```

**seckill_deduct.lua** — 支付确认扣减（三级库存 → 已售）：

```lua
-- KEYS[1]: held key   KEYS[2]: hold detail
-- ARGV[1]: quantity   ARGV[2]: orderNo
-- held -1, 删除 hold
-- 已售由 DB deductDbStock 跟踪
```

### 库存预热

`SeckillActivityService.warmUpOnStartup()` 在应用启动时自动将所有
`ACTIVE` 状态的秒杀活动可用库存写入 Redis：

```
@PostConstruct warmUpOnStartup
  └─ 遍历 SeckillActivity WHERE status = 'ACTIVE' AND deleted = 0
       └─ SET seckill:stock:{activityId}:{skuId} = available_stock
```

---

## DB 模型

### seckill_activity 表

```sql
CREATE TABLE IF NOT EXISTS seckill_activity (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT '活动 ID',
    activity_name     VARCHAR(128)  NOT NULL COMMENT '活动名称',
    sku_id            VARCHAR(64)   NOT NULL COMMENT '秒杀 SKU',
    seckill_price     DECIMAL(12,2) NOT NULL COMMENT '秒杀价',
    total_stock       INT           NOT NULL DEFAULT 0 COMMENT '总库存',
    available_stock   INT           NOT NULL DEFAULT 0 COMMENT '当前可用库存',
    limit_per_user    INT           NOT NULL DEFAULT 1 COMMENT '每人限购',
    start_time        DATETIME      NOT NULL COMMENT '开始时间',
    end_time          DATETIME      NOT NULL COMMENT '结束时间',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/PAUSED/ENDED',
    version           INT           NOT NULL DEFAULT 0,
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    INDEX idx_status (status),
    INDEX idx_time_window (start_time, end_time),
    INDEX idx_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';
```

### order 表扩展

```sql
ALTER TABLE `order`
    ADD COLUMN seckill_activity_id BIGINT     DEFAULT NULL COMMENT '秒杀活动 ID' AFTER coupon_instance_id,
    ADD COLUMN seckill_pipeline    VARCHAR(16) DEFAULT NULL COMMENT '秒杀批次'    AFTER seckill_activity_id;
```

---

## 接口

### 秒杀抢购

```
POST /api/v1/seckill/{activityId}/execute
Content-Type: application/json
X-User-Id: {buyerId}

Request:
{
  "skuId": "SKU001",
  "quantity": 1,
  "requestId": "uuid-string"          // 客户端生成，防重提交
}

Response (异步处理中 — 默认成功路径):
{
  "success": true,
  "data": {
    "status": "PROCESSING",
    "message": "抢购成功，订单处理中",
    "orderNo": "SECKILL1719360000123",
    "order": null                     // 异步，订单尚未创建
  }
}

客户端通过 GET /api/v1/orders/{orderNo} 轮询订单状态。

Response (Sentinel 限流):
{
  "success": false,
  "errorCode": "FLOW_LIMITED",
  "errorMsg": "当前抢购人数过多，请稍后重试"
}

Response (令牌桶限流):
{
  "success": false,
  "errorCode": "TOKEN_BUCKET_LIMITED",
  "errorMsg": "系统繁忙，请稍后重试"
}

Response (售罄):
{
  "success": false,
  "errorCode": "SOLD_OUT",
  "errorMsg": "SOLD_OUT"
}

Response (重复提交):
{
  "success": false,
  "errorCode": "DUPLICATE_SUBMIT",
  "errorMsg": "请勿重复提交"
}

Response (已购买):
{
  "success": false,
  "errorCode": "BUYER_LIMIT",
  "errorMsg": "已达到该活动购买上限: 1"
}

Response (活动无效):
{
  "success": false,
  "errorCode": "ACTIVITY_INVALID",
  "errorMsg": "活动不存在或未开始"
}
```

---

## 文件清单

### 新增（oms-trade 18 个）

| 文件 | 类别 |
|------|------|
| `seckill/entity/SeckillActivityEntity.java` | 实体 |
| `seckill/mapper/SeckillActivityMapper.java` | Mapper |
| `seckill/service/SeckillActivityService.java` | 活动管理 |
| `seckill/service/SeckillOrderHandler.java` | 秒杀编排 |
| `seckill/controller/SeckillController.java` | 接口 |
| `seckill/controller/SeckillBlockHandler.java` | Sentinel 降级 |
| `seckill/config/SeckillSentinelConfig.java` | Sentinel 流控规则 |
| `seckill/config/RedisTokenBucketRateLimiter.java` | 令牌桶限流器 |
| `seckill/dto/SeckillExecuteRequest.java` | 请求 DTO |
| `seckill/dto/SeckillExecuteResult.java` | 响应 DTO |
| `seckill/mq/SeckillOrderMessage.java` | MQ 消息体 |
| `seckill/mq/SeckillOrderProducer.java` | MQ 生产者 |
| `seckill/mq/SeckillOrderConsumer.java` | MQ 消费者 |
| `resources/lua/seckill_grab.lua` | 抢库存 Lua |
| `resources/lua/seckill_release.lua` | 释放库存 Lua |
| `resources/lua/seckill_token_bucket.lua` | 令牌桶 Lua |
| `resources/lua/seckill_deduct.lua` | 支付确认扣减 Lua |

### 修改（5 个）

| 文件 | 改动 |
|------|------|
| `oms-api/.../CreateOrderRequest.java` | +3 秒杀字段 |
| `oms-api/.../OrderDTO.java` | +2 秒杀字段 |
| `oms-trade/.../OrderEntity.java` | +2 字段 |
| `oms-trade/.../OrderController.java` | toDTO 映射 |
| `oms-trade/.../OrderCreateService.java` | 持久化秒杀字段 |
| `oms-trade/.../CreateOrderSagaDefinition.java` | 秒杀跳过营销/库存 |
| `deploy/sql/init-all-databases.sql` | 活动表 + 字段 |

---

## 验证

1. **正常抢购（MQ 异步）**：
   ```bash
   curl -X POST http://localhost:8080/api/v1/seckill/1/execute \
     -H "X-User-Id: buyer001" \
     -H "Content-Type: application/json" \
     -d '{"skuId":"SKU001","quantity":1,"requestId":"uuid-1"}'
   # → 200 { status: "PROCESSING", orderNo: "SECKILL..." }
   # → 轮询 GET /api/v1/orders/SECKILL... 可查订单状态
   ```

2. **重复提交**：
   ```bash
   # 同一 requestId 再发一次
   # → 200 { errorCode: "DUPLICATE_SUBMIT" }
   ```

3. **售罄**：
   - 等库存耗尽后请求 → `SOLD_OUT`

4. **Sentinel 限流**：
   - 并发超过 500 QPS → `FLOW_LIMITED`

5. **令牌桶限流**：
   - 短时间大量请求超过桶容量 → `TOKEN_BUCKET_LIMITED`

6. **MQ 宕机降级**：
   - RocketMQ Broker 不可用时请求 → `ORDER_FAILED`（hold TTL 15min 自动归还库存）

7. **编译**：
   ```bash
   mvn compile -pl oms-trade,oms-api -am -DskipTests
   # → BUILD SUCCESS
   ```

---

## 参考

- [ADR-037: 可配置订单流程引擎](ADR-037-configurable-order-process-engine.md) — FLASH_SALE 订单分类
- [ADR-040: 高性能高可用](ADR-040-high-performance-high-availability.md) — 秒杀限流 tier
- [ADR-045: 价格优惠券](ADR-045-price-promotion-coupon.md) — 秒杀互斥规则
- [degradation-strategy.md](../degradation-strategy.md) — 降级体系
