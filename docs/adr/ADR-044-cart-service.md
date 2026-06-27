# ADR-044：购物车服务 (Cart Service)

> **状态**: 已接受  
> **创建日期**: 2026-06-13  
> **影响范围**: cart-service（购物车）、order-core、price-service、promotion-service、inventory-service、member-service
>
> **本文档系统设计订单中台的购物车服务，涵盖 Redis 数据结构选型、匿名/登录合并策略、TTL/过期策略、并发控制、API 设计、事件定义以及与其他服务的集成。**

---

## 1. 背景

### 现状分析

当前购物车仅 feature-overview.md §2 一行提及（"Redis 高并发购物车"），无独立 ADR、无数据模型、无 API 定义：

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| P1 | **无数据模型设计** | 无 DDL、无 Redis 结构定义 | 实现时数据结构随意，难以维护 |
| P2 | **无合并策略** | 匿名购物车（未登录）→ 登录后如何合并未定义 | 用户换设备后购物车丢失 |
| P3 | **无 TTL/过期策略** | 匿名购物车存多久未定义 | Redis 容量不可控 |
| P4 | **无并发控制** | 多端同时操作购物车可能覆盖 | 用户同一时间在 APP 和 Web 加购、修改可能冲突 |
| P5 | **无价格联动** | 促销变更后购物车价签不会自动刷新 | 价格与结算时不一致 |

### 现有引用

| 引用 | 位置 | 内容 |
|------|------|------|
| 购物车管理 | feature-overview.md §2 | "Redis 高并发购物车（cart-service）" |
| Redis 容量预算 | ADR-014 | 12GB Redis 含购物车占用 |
| inventory-service ADR-043 背景 | ADR-043 §1 P5 | "大订单/购物车场景性能差" |
| C4 容器图 | container-diagram.puml | `Container(cart, "cart-service", "Spring Boot", "购物车管理")` 连接 Redis |
| P1 缺口 | completeness-report §4.3 | "购物车服务缺少独立 ADR" |

---

## 2. 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | Redis 数据结构 | Hash per item + Sorted Set，单操作 P99 ≤ 10ms |
| G2 | 匿名→登录合并策略 | 智能合并（同 SKU+活动合并，不同追加），不影响用户感知 |
| G3 | TTL/过期策略 | 匿名 30d 自动过期，登录用户持久化，过期前发事件 |
| G4 | 并发控制 | Redis Lua 原子执行所有写操作，无竞态冲突 |
| G5 | API 覆盖 | 8 个 Buyer API + 3 个 Admin API + 2 个 Internal API |
| G6 | 价格联动 | 促销变更后购物车价签 5min 内刷新 |
| G7 | 过期清理 | XXL-Job 每日扫描过期匿名购物车，清理前发 cart.expired 事件 |

---

## 3. 战术 DDD 设计

### 3.1 聚合根

| 聚合根 | 数据库表 | 标识符 | 生命周期 |
|--------|---------|--------|---------|
| **Cart** (购物车) | cart_cart | cart_id (String, Leaf Snowflake) | 匿名: 30d TTL / 登录: 持久化直到下单清空 |

### 3.2 Entity vs Value Object

| 类型 | 名称 | 标识 | 原因 |
|------|------|------|------|
| **Entity** | Cart | cart_id (不变) | 有独立生命周期，状态从 ACTIVE→MERGED→EXPIRED |
| **Entity** | CartItem | cart_id + sku_id (复合键) | quantity/selected 可变，追踪每个 SKU 的状态 |
| **Value Object** | PriceInfo | 无身份标识 | 由价格、促销信息组成，不可变，替换而非修改 |
| **Value Object** | SkuSnapshot | 无身份标识 | 商品名、图片、规格的快照，不可变 |

### 3.3 领域事件

| 事件 | 触发点 | 发布方式 | 消费者 |
|------|--------|---------|--------|
| `CartItemAdded` | 加购成功 | MQ (event_outbox) | price-service (刷新价签) |
| `CartItemRemoved` | 从购物车删除 | MQ | — |
| `CartMerged` | 匿名→登录合并完成 | MQ | — |
| `CartExpired` | TTL 超时清理 | MQ | notification (推送提醒) |
| `CartSkuPriceChanged` | 促销变更批量刷新 | MQ | WebSocket (实时更新) |

### 3.4 不变条件（Invariants）

| 规则 | 约束 | 保障方式 |
|------|------|---------|
| 同一 SKU 不可重复添加 | 同 cart + sku 仅一条记录 | Hash field=skuId 天然唯一 |
| 数量不能为 0 或负数 | quantity >= 1 | Lua 脚本校验 |
| 匿名购物车不可引用登录用户信息 | device_id 与 user_id 互斥 | 应用层校验 |
| 合并后不丢失商品 | 同 SKU 合并 quantity，不同 SKU 追加 | 智能合并策略 |

### 3.5 Repository 模式

```
CartRepository (Interface，定义在 Domain 层)
  ├── RedisCartRepository (Redis 实现，主要存储)
  └── DBCartRepository (OceanBase 实现，数据备份)
```

购物车采用 **Redis 优先 + DB 兜底** 的双写策略：所有写操作先走 Redis Lua 脚本，异步写 DB。

---

## 4. 决策

### 决策 1：Redis 数据结构

| 方案 | 评估 |
|------|------|
| **JSON Document（每用户一个 JSON）** | 加购/改数量需全量读写 JSON，并发写入冲突，大数据量性能差 |
| **Hash per item + Sorted Set** | ✅ **选中** — Hash 存每个 SKU 行（quantity, selected, promotionInfo），Sorted Set 存排序（score=添加时间）；行级原子操作，灵活 |
| **纯 Hash（cart:{userId} → JSON）** | 比 JSON Document 好但无法排序，购物车按时间排序是强需求 |

**Redis Key 设计**：
```
cart:{cartId}:items        → Hash     (field=skuId, value=item JSON)
cart:{cartId}:sorted_set   → ZSet     (member=skuId, score=addTime)
cart:anon:{deviceId}       → String   (value=cartId, TTL=30d, 匿名索引)
cart:idx:{userId}          → String   (value=cartId, 登录用户索引)
lock:cart:{cartId}:{skuId} → String   (分布式锁, 仅并发 merge 用, 1s TTL)
```

### 决策 2：匿名→登录合并策略

| 方案 | 评估 |
|------|------|
| **覆盖清空** | 简单但用户体验差，登录后购物车丢失 |
| **简单追加（append）** | 合并但同一 SKU 可能重复 |
| **智能合并** | ✅ **选中** — 同 SKU + 同一活动 = 合并数量；同 SKU 但不同活动（如秒杀价 vs 普通价）= 保留两条；不同 SKU = 追加 |

**合并触发时机**：登录成功后立即执行，合并结果通过事件通知客户端。

### 决策 3：TTL/过期策略

| 方案 | 评估 |
|------|------|
| **无过期（全部持久化）** | Redis 容量无限膨胀，需 DBA 手动清理 |
| **统一 7d TTL** | 短期有效但重度用户 7 天选品周期不够 |
| **分层策略：匿名 30d / 登录持久化** | ✅ **选中** — 匿名购物车 30d TTL + EXPIRE 命令；登录用户持久化（下单时清理已购 SKU）；下单未支付的购物车保留 |

### 决策 4：并发控制

| 方案 | 评估 |
|------|------|
| **乐观锁（version）** | 高并发下（多端同时操作）冲突率高 |
| **Redis Lua 原子脚本** | ✅ **选中** — 所有写操作封装为 Lua 脚本，Redis 单线程保证原子性；version CAS 作为兜底 |

**覆盖操作**：add_item.lua / modify_item.lua / delete_item.lua / clear_cart.lua / merge_cart.lua / batch_add.lua / toggle_select.lua

### 决策 5：过期清理机制

| 方案 | 评估 |
|------|------|
| **仅 Redis TTL** | 简单但无法在清理前发事件通知用户 |
| **XXL-Job 每日扫描 + 事件** | ✅ **选中** — 每天凌晨扫描即将过期的匿名购物车，过期前 7d 发 `cart.expiring` 事件，过期当天发 `cart.expired`+ 删除 |

---

## 4. 系统架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        API Gateway Layer                                  │
│     IGW Buyer / IGW Admin                                                  │
│     /api/v1/cart/*  /api/admin/v1/cart/*                                  │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ Dubbo
┌──────────────────────────────────▼───────────────────────────────────────┐
│                              cart-service                                  │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 1: Cart Manager 购物车核心                                    │    │
│  │  ┌───────────┐ ┌───────────────┐ ┌──────────────┐ ┌────────────┐  │    │
│  │  │CartAdd     │ │ CartModify    │ │ CartDelete   │ │ CartMerge  │  │    │
│  │  │(加购)      │ │ (改数量/规格) │ │ (删除单项)   │ │ (合并)     │  │    │
│  │  └───────────┘ └───────────────┘ └──────────────┘ └────────────┘  │    │
│  │  ┌───────────┐ ┌───────────────┐ ┌──────────────┐ ┌────────────┐  │    │
│  │  │CartQuery   │ │ CartClear     │ │ CartSelect   │ │ CartCount  │  │    │
│  │  │(查询)     │ │ (清空)        │ │ (勾选/全选)  │ │ (计数)     │  │    │
│  │  └───────────┘ └───────────────┘ └──────────────┘ └────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 2: Redis Lua Engine 原子操作引擎                              │    │
│  │  ┌───────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌──────┐  │    │
│  │  │add_item   │ │modify_   │ │delete_   │ │merge_cart  │ │toggle│  │    │
│  │  │.lua       │ │item.lua  │ │item.lua  │ │.lua        │ │.lua  │  │    │
│  │  └───────────┘ └──────────┘ └──────────┘ └────────────┘ └──────┘  │    │
│  │  ┌───────────┐ ┌──────────┐                                         │    │
│  │  │clear_cart │ │batch_add │                                         │    │
│  │  │.lua       │ │.lua      │                                         │    │
│  │  └───────────┘ └──────────┘                                         │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 3: Integration & Jobs 集成层                                 │    │
│  │  ┌──────────────────────┐ ┌──────────────┐ ┌──────────────────┐  │    │
│  │  │ PriceRefreshAdapter  │ │ EventPublisher│ │ CartCleanupJob   │  │    │
│  │  │ (促销 → 价签刷新)    │ │ (购物车事件) │ │ (过期清理)       │  │    │
│  │  └──────────────────────┘ └──────────────┘ └──────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
         ┌─────────────────────┼────────────────────┐
         │                     │                    │
   ┌─────▼──────┐      ┌──────▼───────┐     ┌──────▼──────────┐
   │ Redis      │      │  OceanBase   │     │  Integration    │
   │ Cluster    │      │  (持久化)    │     │                  │
   │ Hash/ZSet  │      │  2 张表      │     │ price-service   │
   │ Lua 脚本   │      │  cart+item   │     │ inventory-svc   │
   └────────────┘      └──────────────┘     │ promotion-svc   │
                                            │ member-service  │
                                            └─────────────────┘
```

---

## 5. Redis Lua 核心脚本

### 5.1 add_item.lua（加购）

```lua
-- KEYS[1]: cart:{cartId}:items (Hash)
-- KEYS[2]: cart:{cartId}:sorted_set (ZSet)
-- ARGV[1]: skuId
-- ARGV[2]: quantity
-- ARGV[3]: activityId (促销活动 ID, 0=无)
-- ARGV[4]: unitPrice (当前价, 用于价签)
-- ARGV[5]: addTime (Unix timestamp)
-- ARGV[6]: selected (1=选中, 0=不选)

local item_key = 'cart:' .. KEYS[1]
local full_key = ARGV[1] .. ':' .. ARGV[3]  -- sku:activity 组合

-- 查询是否已存在（同 SKU + 同活动）
local existed = redis.call('HEXISTS', KEYS[1], full_key)
if existed == 1 then
    -- 已存在：合并数量
    local old_json = redis.call('HGET', KEYS[1], full_key)
    local old_qty = cjson.decode(old_json).quantity
    local new_qty = old_qty + tonumber(ARGV[2])
    
    local item = {
        skuId = ARGV[1],
        quantity = new_qty,
        activityId = ARGV[3],
        unitPrice = ARGV[4],
        selected = ARGV[6],
        addTime = ARGV[5]
    }
    redis.call('HSET', KEYS[1], full_key, cjson.encode(item))
    return {code = 200, message = "加购成功（数量合并）", quantity = new_qty}
else
    -- 新 SKU：添加 + 记录排序
    local item = {
        skuId = ARGV[1],
        quantity = ARGV[2],
        activityId = ARGV[3],
        unitPrice = ARGV[4],
        selected = ARGV[6],
        addTime = ARGV[5]
    }
    redis.call('HSET', KEYS[1], full_key, cjson.encode(item))
    redis.call('ZADD', KEYS[2], ARGV[5], full_key)
    return {code = 200, message = "加购成功", quantity = ARGV[2]}
end
```

### 5.2 merge_cart.lua（匿名→登录合并）

```lua
-- KEYS[1]: cart:{sourceCartId}:items (源-匿名)
-- KEYS[2]: cart:{sourceCartId}:sorted_set
-- KEYS[3]: cart:{targetCartId}:items (目标-登录)
-- KEYS[4]: cart:{targetCartId}:sorted_set
-- KEYS[5]: cart:anon:{deviceId} (删除匿名索引)

-- 获取源购物车所有行
local source_items = redis.call('HGETALL', KEYS[1])
local i = 1
while i < #source_items do
    local key = source_items[i]
    local value = cjson.decode(source_items[i + 1])
    
    -- 尝试合并到目标购物车
    local target_key = key  -- sku:activity 组合
    local existed = redis.call('HEXISTS', KEYS[3], target_key)
    if existed == 1 then
        local target_json = redis.call('HGET', KEYS[3], target_key)
        local target_item = cjson.decode(target_json)
        target_item.quantity = target_item.quantity + value.quantity
        redis.call('HSET', KEYS[3], target_key, cjson.encode(target_item))
    else
        redis.call('HSET', KEYS[3], target_key, cjson.encode(value))
        redis.call('ZADD', KEYS[4], value.addTime, target_key)
    end
    i = i + 2
end

-- 删除源购物车和匿名索引
redis.call('DEL', KEYS[1], KEYS[2])
redis.call('DEL', KEYS[5])

return {code = 200, message = "合并成功"}
```

---

## 6. 数据模型

### DB 表（持久化）

```sql
-- 购物车主表
CREATE TABLE cart_cart (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT COMMENT '登录用户 ID（匿名时为 NULL）',
    device_id       VARCHAR(64) COMMENT '设备指纹（匿名购物车关联）',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CHECKED_OUT/DELETED',
    item_count      INT DEFAULT 0 COMMENT '商品种数',
    total_quantity  INT DEFAULT 0 COMMENT '商品总件数',
    checked_count   INT DEFAULT 0 COMMENT '选中商品种数',
    cart_type       VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL/PROMOTION',
    expired_at      DATETIME COMMENT '过期时间（匿名购物车）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_device (device_id),
    INDEX idx_expired (status, expired_at)
);

-- 购物车行表（持久化镜像，用于数据恢复和查询）
CREATE TABLE cart_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    cart_id         BIGINT NOT NULL,
    sku_id          BIGINT NOT NULL,
    activity_id     BIGINT DEFAULT 0 COMMENT '促销活动 ID',
    quantity        INT NOT NULL DEFAULT 1,
    unit_price      DECIMAL(12,2) COMMENT '加入时单价',
    selected        TINYINT(1) DEFAULT 1 COMMENT '是否选中',
    added_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_cart (cart_id),
    UNIQUE KEY uk_cart_sku (cart_id, sku_id, activity_id)
);
```

### Redis 运行时结构

| Key 模式 | 类型 | 说明 | TTL |
|----------|------|------|-----|
| `cart:{cartId}:items` | Hash | 购物车行数据，field=`{skuId}:{activityId}` | 登录: 无 / 匿名: 30d |
| `cart:{cartId}:sorted_set` | ZSet | 排序（member=同上, score=addTime） | 同上 |
| `cart:anon:{deviceId}` | String | 匿名购物车索引 → cartId | 30d |
| `cart:idx:{userId}` | String | 登录用户索引 → cartId | 无 |
| `cart:lock:{cartId}:{skuId}` | String | merge 分布式锁 | 1s |

---

## 7. API 设计

### Buyer API

| 方法 | 端点 | 说明 | 幂等 |
|------|------|------|------|
| 加购 | `POST /api/v1/cart/items` | 传入 skuId, quantity, activityId | ✅（quantity 合并） |
| 修改数量 | `PUT /api/v1/cart/items/{skuId}/quantity` | 修改指定 SKU 数量 | ❌ |
| 删除单项 | `DELETE /api/v1/cart/items/{skuId}` | 删除购物车中指定行 | ✅ |
| 查询列表 | `GET /api/v1/cart/items` | 返回购物车全量数据，含促销信息 | - |
| 勾选/全选 | `POST /api/v1/cart/items/select` | 选中/取消选中指定 SKU | ✅（幂等） |
| 合并 | `POST /api/v1/cart/merge` | 匿名→登录合并（登录成功后自动触发） | ✅（幂等） |
| 清空 | `DELETE /api/v1/cart/items` | 清空购物车（下单后调用） | ✅ |
| 计数 | `GET /api/v1/cart/count` | 返回购物车中商品种数和总件数 | - |

### Admin API

| 方法 | 端点 | 说明 |
|------|------|------|
| 查询用户购物车 | `GET /api/admin/v1/cart/users/{userId}` | 管理员查用户购物车 |
| 过期清理 | `DELETE /api/admin/v1/cart/expired` | 手动触发过期清理 Job |
| 强制清除 | `DELETE /api/admin/v1/cart/users/{userId}` | 管理员强制清空 |

### Internal API（服务间调用）

| 方法 | 端点 | 调用方 | 说明 |
|------|------|--------|------|
| 获取选中商品 | `GET /api/backend/v1/cart/{userId}/checked-items` | order-core | 下单时获取购物车选中商品 |
| 批量刷新价签 | `POST /api/backend/v1/cart/price-refresh` | price-service | 促销变更后被动刷新 |

---

## 8. 事件定义

| 事件 | 生产者 | 消费者 | 触发条件 |
|------|--------|--------|---------|
| `cart.item_added` | cart-service | price-service, promotion-service | 加购成功后刷新价签/促销标签 |
| `cart.item_removed` | cart-service | - | 删除 SKU |
| `cart.item_quantity_changed` | cart-service | price-service | 修改数量后刷新价签 |
| `cart.merged` | cart-service | member-service | 匿名→登录合并完成 |
| `cart.checked_out` | cart-service | order-core | 下单后购物车清理 |
| `cart.expiring` | cart-service | notification | 购物车过期前 7d 提醒 |
| `cart.expired` | cart-service | notification | 购物车过期清理 |
| `cart.price_refreshed` | cart-service | WebSocket | 价签刷新后通知前端更新 |

---

## 9. 服务集成

| 外部服务 | 集成点 | 说明 |
|---------|--------|------|
| **inventory-service** (ADR-043) | 购物车展示库存状态 | 调用批量库存查询 API，显示可售状态 |
| **price-service** (ADR-045) | 购物车价签计算 | 加购/改数量后刷新价格 |
| **promotion-service** (ADR-045) | 购物车促销标签 | 显示匹配的促销活动 |
| **member-service** (ADR-046) | 会员价展示 | 购物车中显示会员等级价 |
| **order-core** (ADR-039) | 下单拉取购物车数据 | checkout → 获取 checked-items |
| **WebSocket** | 实时推送 | 价格变动/促销变更/库存状态变更时推送前端 |

---

## 10. 非功能设计

| 指标 | 目标 | 实现 |
|------|------|------|
| 购物车读取 P99 | ≤ 20ms | Redis Hash HGETALL + ZSet ZRANGE（无 DB） |
| 购物车写入 P99 | ≤ 30ms | Redis Lua 脚本原子执行 |
| 匿名→登录合并 P99 | ≤ 100ms | Lua merge_cart 脚本 + 删除源 key |
| 批量价签刷新 | 1000 用户 ≤ 5s | 异步 MQ 广播 + 逐用户更新 |
| 并发冲突率 | 0%（原子 Lua） | 所有写操作通过 Lua 脚本 |
| Redis 容量 | < 3GB | 匿名 30d TTL + XXL-Job 过期清理 |
| 可用性 | 99.99% | Redis Cluster 3 副本，降级走缓存 |

### Redis 容量估算

| 用户类型 | 数量 | 单购物车大小 | 合计 |
|---------|------|------------|------|
| 日活登录用户 | 50 万 | ~2KB（平均 5 个 SKU） | ~1GB |
| 匿名购物车（TTL 30d） | 100 万 | ~1KB | ~1GB |
| 索引、锁等辅助 key | - | - | ~0.5GB |
| **合计** | | | **~2.5GB** |

---

## 11. 实施计划

| 阶段 | 内容 | 人天 |
|------|------|------|
| Phase 1: Redis Lua 7 脚本 | add/modify/delete/clear/merge/batch_add/toggle_select 脚本 + 单元测试 | 1.5d |
| Phase 2: CartManager + API CRUD | CartAddService / CartModifyService / CartQueryService + Buyer API | 2d |
| Phase 3: 合并策略 + TTL + 过期清理 | merge_cart 智能合并 + 匿名 TTL 30d + XXL-Job CleanupJob | 1.5d |
| Phase 4: 集成适配器 | PriceRefreshAdapter + 购物车→下单 checked-items 接口 + 库存展示 | 1.5d |
| Phase 5: 事件 + WebSocket + 文档 | 8 个事件定义 + WebSocket 推送 + 交叉引用更新 | 1.5d |

**总计：~8 人天**

---

## 12. 交叉引用矩阵

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-014** | Redis 容量 | 购物车分担 Redis 12GB 预算，cart-service 约 2.5GB |
| **ADR-037** §4 | 下单前步骤 | 购物车数据作为 checkout 的输入 |
| **ADR-039** | 状态机 | checkout → PENDING_PAY 后清理购物车选中 SKU |
| **ADR-040** | SLA | 读写 P99 指标对齐 ADR-040 高并发设计 |
| **ADR-030** | 幂等框架 | 加购/改数量等操作使用 request_id 幂等 |
| **ADR-038** | API 规范 | ApiResult\<T\> 标准返回 + 事件中心 |
| **ADR-043** | 库存查询 | 购物车批量渲染时调用 inventory-service 库存状态 |
| **ADR-045** | 价格计算 | 购物车价签依赖 price-service 计价管道 |
| **ADR-045** | 促销标签 | 购物车促销信息依赖 promotion-service |
| **ADR-046** | 会员价 | 购物车中展示会员价 |
| **ADR-047** | 风控预检 | 下单前风控预检查包含购物车数据 |

---

## 13. 风险矩阵

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Redis OOM 导致购物车不可用 | 低 | 高 | 匿名 TTL + CAP + Sentinel 内存告警 |
| 合并冲突（用户同时在多端操作） | 低 | 中 | Lua 原子脚本 + merge 时分布式锁 1s |
| 购物车价签与结算价格不一致 | 中 | 中 | 下单时实时计价（非购物车缓存价） |
| TTL 抖动导致购物车提前过期 | 低 | 高 | TTL + 7d 宽限期，过期前发事件提醒 |
