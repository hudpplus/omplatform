# ADR-043：库存管理服务 (Inventory Management Service)

> **状态**: 已接受  
> **创建日期**: 2026-06-13  
> **影响范围**: inventory-service（库存管理中心）、order-core、workflow-service、payment-core、channel-adapter
>
> **本文档系统设计订单中台的库存管理服务，涵盖两阶段预占协议、Redis Lua 原子扣减、库存状态机、渠道库存隔离、冻结/解冻、批量查询、对账与延迟任务以及非功能设计。**

---

## 1. 背景

### 现状分析

当前库存能力分散在 15+ 份 ADR 中，缺乏统一设计，存在以下问题：

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| P1 | **无统一库存设计** | 库存预占/扣减/释放逻辑散落在 ADR-020/037/039/040 中 | 每个新功能重新发现库存语义，实现不一致风险高 |
| P2 | **超卖防护机制不完整** | ADR-016 识别 AZ 故障转移时库存超卖风险，但无具体方案 | 极端情况下库存超卖导致资金损失 |
| P3 | **无冻结/解冻能力** | ADR-039 HOLD 仅覆盖预占不足场景，无管理端强制冻结 | 不能及时下架商品，活动运营不可控 |
| P4 | **无渠道库存隔离** | ADR-036 仅定义 sync_type=INVENTORY，所有渠道共享库存池 | 渠道超卖，一个渠道耗尽所有库存 |
| P5 | **无批量库存查询** | ADR-040 仅定义单 SKU queryStock SLA，批量查库存需 N+1 次调用 | 大订单/购物车场景性能差 |
| P6 | **无库存 DDL 定义** | ADR-035 仅引用 `003-inventory-tables.xml` 但无具体 schema | 数据库模型无法落地 |

### 现有能力分散位置

| 已有能力 | 分散位置 | 现状 |
|---------|---------|------|
| 预占/扣减/释放 Saga 步骤 | ADR-020 §3 | 仅编排定义，无内部实现 |
| HOLD 生命周期 + HoldReleaseJob | ADR-039 §3.3 | 订单侧状态完备，库存侧无设计 |
| reserve_inventory YAML 步骤 | ADR-037 §4 | 仅 Bean 映射，无实现细节 |
| Redis Lua 预占 → 确认协议 | ADR-040 Part C | 一句话提及，无 Lua 脚本 |
| 库存预扣对账 | reconciliation-matrix.md Row 8 | 对账逻辑有定义，无数据模型 |
| inventory-core SLI / Sentinel | ADR-040 §3-4 | 仅 SLA 数字，无设计细节 |
| 库存容量估算 | ADR-015 | "2 Pods 够了"，热点风险未解决 |
| AZ 故障转移风险 | ADR-016 | 风险描述，无原子性方案 |
| 渠道库存同步 | ADR-036 | sync_type 已定义，无协议细节 |
| 多租户库存 schema | ADR-035 | changelog 引用存在，无 DDL |
| 延迟任务：预占释放 | ADR-021 | 仅 Tier 2 任务定义 |
| 库存降级策略 | degradation-strategy.md | P1-保留，Sentinel 429 |

---

## 2. 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | 两阶段预占协议（预占→确认/释放） | Redis Lua 原子执行，零超卖，P99 30ms |
| G2 | Redis Lua 防超卖 | 极端并发无超卖，单 SKU 5000 QPS 不丢精度 |
| G3 | 库存冻结/解冻 | 管理 API 1s 内生效，冻结后零新预占 |
| G4 | 渠道库存隔离 | 三种隔离模式（SHARED/DEDICATED/RATIO），不相互影响 |
| G5 | 批量库存查询 | 100 SKU 批量查询 P99 ≤ 50ms |
| G6 | 自动释放超时预占 | HOLD 15min 超时释放，48h P2 告警 |
| G7 | 统一库存数据模型 | 5 张表 DDL 完整定义，多租户兼容 |

---

## 3. 决策

### 决策 1：库存数据模型

| 方案 | 评估 |
|------|------|
| **列式模型**（available_qty / reserved_qty / deducted_qty / frozen_qty 列） | ✅ **选中** — 订单中台库存类型固定，Lua 脚本简洁，单行读取性能好 |
| **多维行模型**（stock_type 行记录） | 扩展性强但查询需聚合，Lua 复杂，多行事务难保证 |

**理由**：订单中台库存类型（可用/预占/已扣减/冻结/次品）稳定不变，列式模型读写性能最优、Lua 脚本可验证性最强。渠道隔离通过独立表实现，不压入主表。

### 决策 2：两阶段预占协议

| 方案 | 评估 |
|------|------|
| **单阶段 DB 直接扣减** | 一致性高但吞吐 < 500 TPS（DB 行锁） |
| **Redis Lua 两阶段（预占→确认/释放）** | ✅ **选中** — 匹配 ADR-040 Part C 既有设计；Redis Lua 原子性解决 ADR-016 超卖风险；Phase 1 下单预占，Phase 2 支付确认 |

**理由**：Redis 单线程保证 Lua 脚本原子执行，无需分布式锁；两阶段设计使预占（下单）和确认（支付）解耦。

### 决策 3：防超卖机制

| 方案 | 评估 |
|------|------|
| **乐观锁 + CAS 重试** | 高并发下重试率高，AZ 故障转移仍可能超卖 |
| **Redis Lua 原子脚本** | ✅ **选中** — Lua 在 Redis 单线程执行，DECRBY 原子减；Redis Cluster 主从同步消除单点风险 |
| **分布式锁** | 加锁/解锁增加延迟，AZ 切换锁丢失风险（ADR-016 已识别） |

### 决策 4：持久化同步策略

| 方案 | 评估 |
|------|------|
| **同步双写（Redis + DB）** | 延迟翻倍，DB 抖动影响库存服务可用性 |
| **Canal/binlog 异步同步** | ✅ **选中** — Redis 优先保证性能，Canal 监听库存表 binlog 同步到 DB，5s 内最终一致 |

### 决策 5：渠道隔离模式

| 方案 | 评估 |
|------|------|
| **全共享（当前）** | 简单但渠道间相互影响，一个渠道超卖拉低所有渠道 |
| **三种模式（SHARED/DEDICATED/RATIO）** | ✅ **选中** — 灵活适配 JD/Tmall/POS 等不同需求；默认 SHARED 兼容现有 |

### 决策 6：Redis Key 分片策略

| 方案 | 评估 |
|------|------|
| **单 key 单 SKU** | 热点 SKU 集中在一台 Redis 节点，ADR-015 已识别风险 |
| **key 分片 `stock:{sku_id}:{shard%100}`** | ✅ **选中** — 相同 ADR-014 hot key sharding 模式；100 分片保证热点 SKU 均摊到不同槽位 |

---

## 4. 系统架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                        API Gateway Layer                             │
│     IGW Buyer / IGW Admin / External Gateway / Channel Gateway        │
│     /api/v1/inventory/* /api/admin/v1/inventory/*                    │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ Dubbo
┌──────────────────────────────▼───────────────────────────────────────┐
│                          inventory-service                            │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │ Layer 1: Inventory Core 库存核心                                 │   │
│  │  ┌─────────────────────┐  ┌────────────────────────────────┐   │   │
│  │  │ InventoryReserver    │  │ InventoryDeductionService      │   │   │
│  │  │ (预占/确认/撤销)     │  │ (正式扣减/归还/冻结/解冻)      │   │   │
│  │  └─────────┬───────────┘  └──────────────┬─────────────────┘   │   │
│  │            │                              │                     │   │
│  │  ┌─────────▼──────────────────────────────▼─────────────────┐   │   │
│  │  │ Layer 2: Redis Lua Engine 原子扣减引擎                     │   │   │
│  │  │ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │   │   │
│  │  │ │reserve_stock │ │confirm_deduct│ │ release_hold     │   │   │   │
│  │  │ │.lua (预占)   │ │.lua (确认)   │ │.lua (释放)       │   │   │   │
│  │  │ └──────────────┘ └──────────────┘ └──────────────────┘   │   │   │
│  │  └──────────────────────────────────────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │ Layer 3: Channel Isolation 渠道隔离层                          │   │
│  │  ┌─────────────────────┐  ┌────────────────────────────────┐   │   │
│  │  │ ChannelStockManager  │  │ ChannelQuotaAllocator          │   │   │
│  │  │ (渠道库存池管理)     │  │ (配额分配/回收/模式路由)       │   │   │
│  │  └─────────────────────┘  └────────────────────────────────┘   │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │ Layer 4: Admin & Query 管理与查询                               │   │
│  │  ┌────────────┐ ┌───────────────┐ ┌────────────┐ ┌──────────┐  │   │
│  │  │StockQuery  │ │BulkStock      │ │FreezeThaw  │ │StockAlert│  │   │
│  │  │(单 SKU)    │ │(批量聚合)     │ │(冻结/解冻)  │ │(预警)    │  │   │
│  │  └────────────┘ └───────────────┘ └────────────┘ └──────────┘  │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐   │
│  │ Layer 5: Integration & Jobs 集成层                             │   │
│  │  ┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐   │   │
│  │  │ SagaAdapter      │ │ EventPublisher│ │ XXL-Jobs         │   │   │
│  │  │ (5x 重试幂等)    │ │ (库存事件)   │ │ HoldRelease等    │   │   │
│  │  └──────────────────┘ └──────────────┘ └──────────────────┘   │   │
│  └───────────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
   ┌─────▼──────┐      ┌──────▼──────┐      ┌───────▼────────┐
   │ Redis      │      │  OceanBase  │      │  Apollo/Nacos  │
   │ Cluster    │      │  (持久层)   │      │  (配置中心)    │
   │ Lua 预占   │      │  5 张表     │      │  Sentinel/阈值 │
   │ Key 分片   │      │  Canal 同步 │      │  渠道/冻结配置  │
   └────────────┘      └─────────────┘      └────────────────┘
```

### 组件职责

| 组件 | 职责 | 技术实现 |
|------|------|---------|
| **InventoryReserver** | 两阶段协议入口：reserve / confirm / release | Java Service + Redis Lua 调用 |
| **InventoryDeductionService** | 正式库存操作：扣减、归还、调整 | 状态机 + 事务日志 |
| **Redis Lua Engine** | 原子库存操作：4 个核心脚本 | Lua 脚本注入 RedisTemplate |
| **ChannelStockManager** | 渠道库存配置 CRUD + 隔离模式路由 | Apollo 配置 + Redis 渠道 key |
| **StockQueryService** | 单 SKU + 批量库存查询 | Caffeine L1 + Redis L2 + DB fallback |
| **FreezeThawService** | 库存冻结/解冻 + 渠道同步暂停 | 状态机 + 事件发布 |
| **HoldReleaseJob** | 超时预占自动释放 | XXL-Job + RocketMQ 双重保证 |
| **SagaAdapter** | Saga 步骤调用 + 5x 重试 + 幂等 | ADR-020/030 集成 |

---

## 5. Redis Lua 核心协议

### 5.1 reserve_stock.lua（预占库存）

```lua
-- KEYS[1]: stock:{sku_id}:available     (总可用库存)
-- KEYS[2]: stock:{sku_id}:reserved      (已预占库存)
-- ARGV[1]: sku_id
-- ARGV[2]: quantity (预占数量)
-- ARGV[3]: request_id (幂等键, ADR-030)
-- ARGV[4]: hold_expire_seconds (预占过期, 默认 900s=15min)

-- 库存不足检查
local available = redis.call('GET', KEYS[1])
if not available or tonumber(available) < tonumber(ARGV[2]) then
    return {code = 4001, message = "库存不足",
            available = available, requested = ARGV[2]}
end

-- 幂等检查：预占记录已存在则直接返回
local hold_key = 'stock:hold:' .. ARGV[3]
if redis.call('EXISTS', hold_key) == 1 then
    return {code = 200, message = "已预占（幂等）",
            hold_id = redis.call('GET', hold_key)}
end

-- 原子库存移动：available → reserved
redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('INCRBY', KEYS[2], ARGV[2])

-- 记录预占信息
local hold_id = 'HOLD_' .. ARGV[3]
redis.call('SET', hold_key, hold_id)
redis.call('EXPIRE', hold_key, ARGV[4])

-- 预占详情 Hash（用于 HoldReleaseJob 扫描）
local hold_detail_key = 'stock:hold_detail:' .. hold_id
redis.call('HMSET', hold_detail_key,
    'sku_id', ARGV[1],
    'quantity', ARGV[2],
    'request_id', ARGV[3],
    'status', 'RESERVED',
    'expire_at', redis.call('TIME')[1] + ARGV[4]
)
redis.call('EXPIRE', hold_detail_key, ARGV[4] + 86400)  -- 多保留 1d 用于补偿

return {code = 200, message = "预占成功",
        hold_id = hold_id, quantity = ARGV[2]}
```

### 5.2 confirm_deduct.lua（确认扣减）

```lua
-- KEYS[1]: stock:{sku_id}:reserved
-- KEYS[2]: stock:{sku_id}:deducted
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id (预占 ID)
-- ARGV[3]: quantity
-- ARGV[4]: request_id (幂等键)

-- 查询预占详情
local hold_detail_key = 'stock:hold_detail:' .. ARGV[2]
local status = redis.call('HGET', hold_detail_key, 'status')

if not status then
    return {code = 4004, message = "预占记录不存在或已过期"}
end

if status ~= 'RESERVED' then
    if status == 'CONFIRMED' then
        return {code = 200, message = "已确认（幂等）"}
    end
    return {code = 4005, message = "预占状态异常", status = status}
end

-- 原子移动：reserved → deducted
redis.call('DECRBY', KEYS[1], ARGV[3])
redis.call('INCRBY', KEYS[2], ARGV[3])
redis.call('HSET', hold_detail_key, 'status', 'CONFIRMED')
redis.call('EXPIRE', hold_detail_key, 86400)  -- 延长到 24h

return {code = 200, message = "确认扣减成功"}
```

### 5.3 release_hold.lua（释放预占）

```lua
-- KEYS[1]: stock:{sku_id}:available
-- KEYS[2]: stock:{sku_id}:reserved
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id
-- ARGV[3]: quantity
-- ARGV[4]: request_id (幂等键)

local hold_detail_key = 'stock:hold_detail:' .. ARGV[2]
local status = redis.call('HGET', hold_detail_key, 'status')

if not status then
    return {code = 4004, message = "预占记录不存在"}
end

if status == 'RELEASED' then
    return {code = 200, message = "已释放（幂等）"}
end

if status == 'CONFIRMED' then
    -- 已确认的预占不能简单释放，需走 undoDeduct
    return {code = 4006, message = "预占已确认，无法释放"}
end

-- 原子归还：reserved → available
redis.call('INCRBY', KEYS[1], ARGV[3])
redis.call('DECRBY', KEYS[2], ARGV[3])
redis.call('HSET', hold_detail_key, 'status', 'RELEASED')
redis.call('EXPIRE', hold_detail_key, 86400)

return {code = 200, message = "预占释放成功"}
```

### 5.4 undo_deduct.lua（Saga 补偿 - 归还已扣减库存）

```lua
-- KEYS[1]: stock:{sku_id}:available
-- KEYS[2]: stock:{sku_id}:deducted
-- ARGV[1]: sku_id
-- ARGV[2]: hold_id
-- ARGV[3]: quantity
-- ARGV[4]: request_id

local hold_detail_key = 'stock:hold_detail:' .. ARGV[2]
local status = redis.call('HGET', hold_detail_key, 'status')

if status ~= 'CONFIRMED' then
    return {code = 4005, message = "预占未确认，不可执行 undo_deduct",
            status = status}
end

-- 原子归还：deducted → available
redis.call('INCRBY', KEYS[1], ARGV[3])
redis.call('DECRBY', KEYS[2], ARGV[3])
redis.call('HSET', hold_detail_key, 'status', 'UNDONE')
redis.call('EXPIRE', hold_detail_key, 86400)

return {code = 200, message = "扣减已撤销"}
```

### Lua 脚本幂等保证

| 脚本 | 幂等策略 | 重复调用结果 |
|------|---------|-------------|
| reserve_stock | request_id 存在则返回已有 hold_id | 返回成功，不重复扣减 |
| confirm_deduct | status=CONFIRMED 返回成功 | 返回成功，不重复扣减 |
| release_hold | status=RELEASED 返回成功 | 返回成功，不重复释放 |
| undo_deduct | status=UNDONE | 返回成功，不重复归还 |

---

## 6. 库存状态机

### 6.1 库存条目状态（Stock Item Status）

```
                   ┌──────────┐
                   │  ACTIVE   │ ← 正常可售
                   └────┬─────┘
                        │
                  ┌─────┴──────┐
                  │            │
             ┌────▼────┐  ┌───▼────────┐
             │  FROZEN  │  │  DISABLED   │ ← 永久停售
             │(临时冻结)  │  │(管理员下架)  │
             └────┬─────┘  └───┬────────┘
                  │            │
                  │       ┌────▼────┐
                  │       │ARCHIVED │ ← 终态
                  │       └─────────┘
                  │
             ┌────▼────┐
             │  ACTIVE  │ ← 解冻恢复
             └─────────┘
```

| 转换 | 触发条件 | 校验规则 |
|------|---------|---------|
| ACTIVE → FROZEN | 管理员冻结 | 库存存在，非 FROZEN/DISABLED |
| FROZEN → ACTIVE | 管理员解冻 | 库存存在，状态为 FROZEN |
| ACTIVE → DISABLED | 商品下架 | 非终态 |
| DISABLED → ARCHIVED | 数据归档 | 终态，不可逆 |
| ACTIVE → ARCHIVED | 商品生命周期结束 | 终态，不可逆 |

### 6.2 预占记录状态（Hold Record Status）

```
          ┌───────────┐
          │  RESERVED  │ ← 预占成功（下单时）
          └─────┬─────┘
                │
         ┌──────┼──────┐
         │      │      │
    ┌────▼──┐ ┌─▼──┐   │
    │CONFIRM│ │TIMEOUT│ │ ← 15min 自动释放（RocketMQ 延迟消息）
    │(支付) │ └──────┘   │
    └───┬───┘           │
        │               │
   ┌────▼────┐   ┌──────▼──────┐
   │  DEDUCTED│   │  RELEASED   │ ← 主动释放（订单取消/补偿）
   │(已扣减)  │   │ / UNDONE    │
   └─────────┘   └─────────────┘
```

| 转换 | 触发条件 |
|------|---------|
| RESERVED → CONFIRMED | 支付成功调用 confirm_deduct |
| RESERVED → TIMEOUT | 15min 超时（HoldReleaseJob 或 RocketMQ） |
| RESERVED → RELEASED | 订单取消调用 release_hold |
| CONFIRMED → DEDUCTED | DB 异步同步完成 |
| CONFIRMED → UNDONE | Saga 补偿调用 undo_deduct |

### 6.3 HOLD 状态守卫集成（ADR-039 §3.3）

| 当前状态 | 目标状态 | 守卫条件 | 说明 |
|---------|---------|---------|------|
| PENDING_PAY | HOLD | `inventoryService.getAvailableQty(skuId) < orderQty` | 下单时库存不足 |
| PAID | HOLD | `inventoryService.getAvailableQty(skuId) < orderQty` | 支付时库存不足（预占已过期） |
| HOLD | PENDING_PAY | `inventoryService.getAvailableQty(skuId) >= orderQty` | 库存已补充 |
| HOLD | PAID | `inventoryService.getAvailableQty(skuId) >= orderQty` | 库存已补充 + 已支付 |
| HOLD | CANCELLED | 48h 超时自动取消 | 库存长期未补充 |

---

## 7. 数据模型（DDL）

### 7.1 stock_item — 库存主表

```sql
CREATE TABLE stock_item (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    sku_id            VARCHAR(32)   NOT NULL COMMENT 'SKU ID',
    warehouse_id      VARCHAR(32)   NOT NULL COMMENT '仓库编码（ADR-037 by_warehouse 拆单）',
    tenant_id         VARCHAR(32)   NOT NULL COMMENT '租户 ID（ADR-035）',

    -- 库存数量（列式模型，每种类型独立列）
    total_quantity    INT           NOT NULL DEFAULT 0 COMMENT '总库存量（含所有类型）',
    available_qty     INT           NOT NULL DEFAULT 0 COMMENT '可用库存（可销售）',
    reserved_qty      INT           NOT NULL DEFAULT 0 COMMENT '已预占库存（下单未支付）',
    deducted_qty      INT           NOT NULL DEFAULT 0 COMMENT '已扣减库存（支付完成）',
    frozen_qty        INT           NOT NULL DEFAULT 0 COMMENT '冻结库存（管理员操作）',
    defective_qty     INT           NOT NULL DEFAULT 0 COMMENT '次品/残次库存',

    -- 库存状态
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE'
                      COMMENT 'ACTIVE / FROZEN / DISABLED / ARCHIVED',
    frozen_reason     VARCHAR(255)  DEFAULT NULL COMMENT '冻结原因',
    frozen_at         DATETIME      DEFAULT NULL COMMENT '冻结时间',
    unfrozen_at       DATETIME      DEFAULT NULL COMMENT '解冻时间',

    version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_sku_warehouse (sku_id, warehouse_id, tenant_id),
    KEY idx_warehouse (warehouse_id),
    KEY idx_status_available (status, available_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存主表';
```

### 7.2 inventory_hold — 预占记录表

```sql
CREATE TABLE inventory_hold (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    hold_id           VARCHAR(64)   NOT NULL COMMENT '预占 ID（HOLD_ + request_id）',
    request_id        VARCHAR(64)   NOT NULL COMMENT '幂等请求 ID（ADR-030）',
    saga_id           VARCHAR(64)   DEFAULT NULL COMMENT 'Saga 事务 ID（ADR-020）',

    sku_id            VARCHAR(32)   NOT NULL COMMENT 'SKU ID',
    warehouse_id      VARCHAR(32)   NOT NULL COMMENT '仓库编码',
    tenant_id         VARCHAR(32)   NOT NULL COMMENT '租户 ID',
    order_no          VARCHAR(32)   NOT NULL COMMENT '关联订单号',
    channel_code      VARCHAR(16)   DEFAULT NULL COMMENT '渠道编码（渠道隔离用）',

    quantity          INT           NOT NULL COMMENT '预占/扣减数量',
    hold_type         VARCHAR(16)   NOT NULL COMMENT 'RESERVE / CONFIRM / RELEASE / UNDO_DEDUCT',
    status            VARCHAR(16)   NOT NULL DEFAULT 'RESERVED'
                      COMMENT 'RESERVED / CONFIRMED / RELEASED / UNDONE / TIMEOUT',

    expire_at         DATETIME      NOT NULL COMMENT '预占过期时间（创建+15min）',
    confirmed_at      DATETIME      DEFAULT NULL COMMENT '确认扣减时间',
    released_at       DATETIME      DEFAULT NULL COMMENT '释放时间',

    retry_count       INT           DEFAULT 0 COMMENT 'Saga 重试次数（最大 5x）',
    last_error        VARCHAR(512)  DEFAULT NULL COMMENT '上次错误信息',

    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_hold_id (hold_id),
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_order_no (order_no),
    KEY idx_sku_status (sku_id, status),
    KEY idx_expire_status (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占/扣减记录表';
```

### 7.3 channel_stock_config — 渠道库存配置表

```sql
CREATE TABLE channel_stock_config (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    sku_id            VARCHAR(32)   NOT NULL COMMENT 'SKU ID',
    channel_code      VARCHAR(16)   NOT NULL COMMENT '渠道编码（JD / TMALL / POS / ...）',
    tenant_id         VARCHAR(32)   NOT NULL COMMENT '租户 ID',

    isolation_mode    VARCHAR(16)   NOT NULL DEFAULT 'SHARED'
                      COMMENT 'SHARED / DEDICATED / RATIO',
    -- SHARED:    使用公共 pool
    -- DEDICATED: 独立库存池，优先消耗 dedicated_qty
    -- RATIO:     按比例从公共 pool 分配，上限 allocation_ratio

    dedicated_qty     INT           DEFAULT 0 COMMENT '独立池库存量（DEDICATED 模式）',
    allocation_ratio  DECIMAL(5,4)  DEFAULT 0 COMMENT '分配比例（RATIO 模式，如 0.30=30%）',

    sync_enabled      TINYINT(1)    DEFAULT 1 COMMENT '是否启用库存同步到渠道（ADR-036）',
    status            VARCHAR(8)    DEFAULT 'ENABLED' COMMENT 'ENABLED / DISABLED',

    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_sku_channel (sku_id, channel_code, tenant_id),
    KEY idx_channel (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道库存配置表';
```

### 7.4 inventory_transaction — 库存流水表

```sql
CREATE TABLE inventory_transaction (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    transaction_no    VARCHAR(32)   NOT NULL COMMENT '流水号（Snowflake 生成）',
    hold_id           VARCHAR(64)   DEFAULT NULL COMMENT '关联预占 ID',
    request_id        VARCHAR(64)   DEFAULT NULL COMMENT '幂等请求 ID',

    sku_id            VARCHAR(32)   NOT NULL COMMENT 'SKU ID',
    warehouse_id      VARCHAR(32)   NOT NULL COMMENT '仓库编码',
    tenant_id         VARCHAR(32)   NOT NULL COMMENT '租户 ID',
    order_no          VARCHAR(32)   DEFAULT NULL COMMENT '关联订单号',
    channel_code      VARCHAR(16)   DEFAULT NULL COMMENT '渠道编码',

    operation_type    VARCHAR(16)   NOT NULL
                      COMMENT 'RESERVE / CONFIRM / RELEASE / UNDO_DEDUCT / FREEZE / UNFREEZE / ADJUST / RESTORE',
    quantity          INT           NOT NULL COMMENT '变动数量（正=增加可用，负=减少可用）',

    before_available  INT           NOT NULL COMMENT '变动前可用库存',
    after_available   INT           NOT NULL COMMENT '变动后可用库存',

    status            VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS'
                      COMMENT 'SUCCESS / FAILED / PENDING',
    error_code        VARCHAR(32)   DEFAULT NULL,
    error_msg         VARCHAR(512)  DEFAULT NULL,

    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_transaction_no (transaction_no),
    KEY idx_sku_id (sku_id),
    KEY idx_order_no (order_no),
    KEY idx_hold_id (hold_id),
    KEY idx_operation_time (operation_type, gmt_create)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';
```

### 7.5 stock_alert_config — 库存预警配置表

```sql
CREATE TABLE stock_alert_config (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    sku_id              VARCHAR(32)   NOT NULL COMMENT 'SKU ID',
    warehouse_id        VARCHAR(32)   NOT NULL COMMENT '仓库编码',
    tenant_id           VARCHAR(32)   NOT NULL COMMENT '租户 ID',

    low_stock_threshold   INT         NOT NULL DEFAULT 10 COMMENT '低库存预警阈值',
    high_stock_threshold  INT         DEFAULT NULL COMMENT '高库存预警阈值（滞销预警）',
    enable_alert          TINYINT(1)  DEFAULT 1 COMMENT '启用预警',
    alert_level           VARCHAR(8)  DEFAULT 'P2' COMMENT '告警级别',

    gmt_create          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_sku_warehouse (sku_id, warehouse_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警配置表';
```

### 数据关系图

```
stock_item (1) ─────── (N) inventory_hold
    │                        │
    │                   inventory_transaction
    │
    └── channel_stock_config (0..N per SKU)
    └── stock_alert_config (0..1 per SKU+warehouse)
```

---

## 8. API 设计

遵循 ADR-038 `ApiResult<T>` 规范，响应格式 `{ code, message, data, traceId, timestamp }`。

### 8.1 Buyer / Order API（Dubbo 内部调用）

| 方法 | 端点 | 说明 | 幂等键 | 节流 |
|------|------|------|--------|------|
| POST | `/api/v1/inventory/reserve` | 预占库存（Saga forward） | requestId | 5000 QPS |
| POST | `/api/v1/inventory/confirm` | 确认扣减（payment 回调） | requestId | 5000 QPS |
| POST | `/api/v1/inventory/release` | 释放预占（Saga compensate） | requestId | 5000 QPS |
| GET | `/api/v1/inventory/stock/{skuId}` | 单 SKU 库存查询 | — | 10000 QPS |
| POST | `/api/v1/inventory/stock/batch` | 批量库存查询（解决 N+1） | — | 2000 QPS |

### 8.2 Admin API（管理后台）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/admin/v1/inventory/stock/{skuId}/freeze` | 冻结商品库存 |
| POST | `/api/admin/v1/inventory/stock/{skuId}/unfreeze` | 解冻商品库存 |
| PUT | `/api/admin/v1/inventory/stock/{skuId}` | 更新库存数量 |
| POST | `/api/admin/v1/inventory/stock/adjust` | 手动调账（+/-） |
| GET | `/api/admin/v1/inventory/stock` | 库存列表（分页） |
| GET | `/api/admin/v1/inventory/holds` | 预占记录列表 |
| GET | `/api/admin/v1/inventory/transactions` | 流水记录 |
| GET | `/api/admin/v1/inventory/stock/alerts` | 低库存预警列表 |
| POST | `/api/admin/v1/inventory/channel/config` | 配置渠道隔离策略 |
| GET | `/api/admin/v1/inventory/channel/config/{skuId}` | 查询渠道配置 |

### 8.3 Backend API（Saga 适配器）

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/backend/v1/inventory/saga/deduct` | Saga 预占（带 5x 重试） |
| POST | `/api/backend/v1/inventory/saga/undo-deduct` | Saga 补偿释放 |
| POST | `/api/backend/v1/inventory/sync/channel` | 渠道库存同步回调 |

### 8.4 Open API（外部查询）

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/open/v1/inventory/stock/{skuId}` | 外部库存查询 |
| POST | `/open/v1/inventory/stock/batch` | 外部批量查询 |

### API 示例

```
POST /api/v1/inventory/reserve
Request:
{
  "skuId": "SKU20260613001",
  "warehouseId": "WH_BJ_01",
  "quantity": 2,
  "orderNo": "ORD20260613001",
  "requestId": "REQ_f3a2c1b0"
}

Response (Success):
{
  "code": 200,
  "message": "预占成功",
  "data": {
    "holdId": "HOLD_REQ_f3a2c1b0",
    "quantity": 2
  },
  "traceId": "ac7f3b1e2d",
  "timestamp": 1750233600000
}

Response (库存不足):
{
  "code": 4001,
  "message": "库存不足",
  "data": {
    "available": 1,
    "requested": 2
  },
  "traceId": "ac7f3b1e2d",
  "timestamp": 1750233600000
}
```

---

## 9. 渠道库存隔离

### 9.1 三种隔离模式

| 模式 | 说明 | Redis Key | 适用场景 |
|------|------|-----------|---------|
| **SHARED**（默认） | 使用公共库存池 `stock:{sku}:available` | 无渠道专用 key | 不分渠道的小商家 |
| **DEDICATED** | 独立库存池，先消耗专用额度 | `stock:dedicated:{channel}:{sku}` | 大渠道签了保量协议 |
| **RATIO** | 按比例从公共池分配，上限 allocation_ratio | `stock:channel_used:{channel}:{sku}` | 渠道间按比例分配 |

### 9.2 渠道预占逻辑

```
reserveChannelStock(skuId, channelCode, quantity):
    config = channelStockConfigDao.findBySkuAndChannel(skuId, channelCode)

    if config.isolationMode == SHARED:
        return reserveStock(skuId, quantity)              // 公共 Lua

    else if config.isolationMode == DEDICATED:
        dedicatedKey = "stock:dedicated:" + channelCode + ":" + skuId
        dedicatedAvail = redis.GET(dedicatedKey)
        if dedicatedAvail >= quantity:
            redis.DECRBY(dedicatedKey, quantity)           // 专用池扣减
            return OK
        else:
            shortfall = quantity - dedicatedAvail
            redis.DEL(dedicatedKey)                        // 专用池清0
            return reserveStock(skuId, shortfall)          // 差额从公共池扣

    else if config.isolationMode == RATIO:
        totalAvailable = redis.GET("stock:" + skuId + ":available")
        channelLimit = totalAvailable * config.allocationRatio
        channelUsedKey = "stock:channel_used:" + channelCode + ":" + skuId
        channelUsed = redis.GET(channelUsedKey) ?? 0
        if channelUsed + quantity > channelLimit:
            return FAIL("渠道库存配额不足")
        redis.INCRBY(channelUsedKey, quantity)
        return reserveStock(skuId, quantity)                // 从公共池扣
```

### 9.3 渠道库存同步（ADR-036 集成）

当 `channel_stock_config.sync_enabled = 1` 时，以下事件触发渠道库存同步：

| 触发事件 | 同步内容 | 目标渠道 |
|---------|---------|---------|
| stock_item.available_qty 变化 > 5% | 同步最新可用量 | 所有启用 sync 的渠道 |
| inventory.frozen 事件 | 同步冻结状态 | 所有启用 sync 的渠道 |
| 定时同步（XXL-Job，每 30min） | 全量库存对账 | 所有启用 sync 的渠道 |

---

## 10. 冻结/解冻

### 10.1 冻结类型

| 类型 | 说明 | 自动解冻 |
|------|------|---------|
| TEMPORARY | 临时冻结（如活动结束暂时停售） | Apollo 配置时长到期自动解冻 |
| PERMANENT | 永久冻结（商品下架） | 需手动解冻 |
| CHANNEL | 仅针对特定渠道冻结 | 不影响其他渠道销售 |

### 10.2 冻结流程

```
POST /api/admin/v1/inventory/stock/{skuId}/freeze
  Request: { type: "TEMPORARY", reason: "活动结束", durationMin: 1440 }

  Step 1: 校验库存存在且状态为 ACTIVE
  Step 2: UPDATE stock_item SET status='FROZEN', frozen_reason=..., frozen_at=NOW()
  Step 3: Redis 标记冻结（阻止新预占）
  Step 4: 发布 inventory.frozen 事件
  Step 5: 若为 CHANNEL 类型 → 禁用对应 channel_stock_config
  Step 6: 若为 TEMPORARY → 注册延迟任务到期自动解冻
```

### 10.3 Redis 冻结标记

```
Lua: freeze_stock.lua 在预占前检查冻结标记
  pre_reserve_check(skuId):
    frozen_key = "stock:frozen:" + skuId
    if redis.call('EXISTS', frozen_key) == 1
        return {code = 4003, message = "商品已冻结"}
    end
    return OK
```

冻结只阻止新预占，已预占的订单不受影响（走正常 expire 或支付确认）。

---

## 11. Saga 集成

### 11.1 Saga 步骤定义（ADR-020 兼容）

```yaml
# createOrder Saga 步骤
deductInventory:
  forward:
    service: "inventory-service"
    method: "deduct"                   # 调用 reserveStock
    retry: 5                           # 5x 重试
    retry_intervals: [1s, 2s, 5s, 10s, 30s]
    idempotent_key: "saga:{saga_id}:deductInventory"  # ADR-030
    timeout_ms: 5000
  compensate:
    service: "inventory-service"
    method: "undoDeduct"               # 调用 releaseStock
    retry: 5
    retry_intervals: [1s, 2s, 5s, 10s, 30s]
    idempotent_key: "saga:{saga_id}:undoDeductInventory"
```

### 11.2 Saga 流程时序

```
createOrder Saga:
  Step 1: order-core.createOrder()    → PENDING_PAY
  Step 2: inventory.deduct()          → RESERVED     ← 若失败 → compensate Step1
  Step 3: payment.charge()            → PROCESSING   ← 若失败 → compensate Step2
  Step 4: inventory.confirmDeduct()   → CONFIRMED    ← 若失败 → publish 事件
  Step 5: order-core.confirmPaid()    → PAID         ← 若失败 → 对账自动修复
```

### 11.3 补偿场景

| 失败步骤 | 补偿动作 | 库存状态 |
|---------|---------|---------|
| Step 2 预占失败 | 无需补偿（订单回滚） | 未变动 |
| Step 3 支付失败 | release_hold（Step 2 补偿） | RESERVED → RELEASED |
| Step 4 确认失败 | release_hold（Step 2 补偿） + 取消订单 | RESERVED → RELEASED |
| Step 4 确认后熔断 | undo_deduct（Step 4 补偿） | CONFIRMED → UNDONE |
| Step 5 状态推进失败 | undo_deduct + payment.refund | CONFIRMED → UNDONE + 退款 |

---

## 12. 事件定义

遵循 ADR-010/ADR-038 事件规范，通过 RocketMQ 事务消息发布。

| 事件 | 说明 | 生产者 | 消费者 | 投递保证 |
|------|------|--------|--------|---------|
| `inventory.reserved` | 库存预占成功 | inventory-service | order-core（状态机推进） | 至少一次 |
| `inventory.confirmed` | 库存确认扣减 | inventory-service | order-core（PAID）, fulfillment | 至少一次 |
| `inventory.released` | 预占释放 | inventory-service | order-core（CANCELLED）, notification | 至少一次 |
| `inventory.undo_deduct` | Saga 补偿归还库存 | inventory-service | order-core, payment | 至少一次 |
| `inventory.low_stock` | 库存低于阈值 | inventory-service | notification（P2 告警） | 最多一次 |
| `inventory.frozen` | 库存冻结 | inventory-service | channel-adapter（暂停同步） | 最多一次 |
| `inventory.unfrozen` | 库存解冻 | inventory-service | channel-adapter（恢复同步） | 最多一次 |
| `inventory.channel_sync` | 渠道库存同步请求 | inventory-service | channel-adapter（JD/POS 同步） | 至少一次 |

### 事件 Schema

```json
// inventory.reserved 事件 payload
{
  "eventId": "evt_xxxx",
  "eventType": "inventory.reserved",
  "producer": "inventory-service",
  "timestamp": 1750233600000,
  "data": {
    "holdId": "HOLD_REQ_f3a2c1b0",
    "skuId": "SKU20260613001",
    "warehouseId": "WH_BJ_01",
    "quantity": 2,
    "orderNo": "ORD20260613001",
    "channelCode": "TMALL",
    "expireAt": 1750234500000
  },
  "traceId": "ac7f3b1e2d"
}
```

---

## 13. 非功能设计

### 13.1 SLA 矩阵

| 操作 | P99 目标 | P99 告警 | 吞吐量 | Sentinel QPS | Sentinel RT |
|------|---------|---------|--------|-------------|-------------|
| reserve（预占） | 30ms | 80ms | 3000 TPS | 5000 | 50ms |
| confirm（确认扣减） | 30ms | 80ms | 3000 TPS | 5000 | 50ms |
| release（释放预占） | 30ms | 80ms | 3000 TPS | 5000 | 50ms |
| undo_deduct（撤销） | 30ms | 80ms | 3000 TPS | 5000 | 50ms |
| queryStock（单 SKU） | 20ms | 50ms | 10000 QPS | 10000 | 20ms |
| bulkQuery（批量 100） | 50ms | 100ms | 2000 QPS | 5000 | 50ms |
| freeze/unfreeze | 100ms | 200ms | 200 TPS | 500 | 200ms |

### 13.2 Sentinel 配置

```yaml
# Apollo: inventory.sentinel
inventory:
  sentinel:
    reserve:
      qps: 5000
      rt_ms: 50
      degrade:
        min_request: 100
        ratio: 0.15
        window_ms: 10000
    queryStock:
      qps: 10000
      rt_ms: 20
    bulkQuery:
      qps: 5000
      rt_ms: 50
```

### 13.3 多级缓存

| 层级 | 技术 | TTL | 命中率预期 | 说明 |
|------|------|-----|-----------|------|
| L1 | Caffeine | 5s | ~60% | 本地缓存，消除热点 SKU 的重复 Redis 查询 |
| L2 | Redis | 实时 | ~99% | 库存实时数据，Lua 直接操作 |
| L3 | OceanBase | — | — | DB 持久化，降级回退 |

### 13.4 可用性目标

| 组件 | 目标 | 部署 |
|------|------|------|
| inventory-service | 99.99% | 3 pods（2 AZ-A + 1 AZ-B）|
| Redis Cluster | 99.99% | 跨 AZ 复制 |
| OceanBase | 99.999% | 5 节点 Paxos |

### 13.5 降级策略（整合 degradation-strategy.md）

| 降级级别 | inventory-service 行为 |
|---------|----------------------|
| L0（正常） | 全功能：reserve/confirm/release + 渠道隔离 + 预警 |
| L1（性能劣化） | 启用 L1 Caffeine 拦截查询；跳过渠道隔离计算 |
| L2（部分受限） | 关闭 bulkQuery + 渠道隔离；仅保留 reserve/confirm/release |
| L3（核心保障） | 仅 reserve 通过 REST API 响应；跳过事件发布 + DB 异步同步 |
| L4（保护模式） | 只读：仅 queryStock 响应，其余返回 429 |

---

## 14. 对账与延迟任务

### 14.1 HoldReleaseJob（超时预占释放）

每 5 分钟扫描超时预占记录，自动释放并更新状态。

```java
@Component
public class HoldReleaseJob {

    /**
     * XXL-Job, cron: "0 */5 * * * ?"
     * 扫描 inventory_hold 中 status=RESERVED AND expire_at < NOW()
     * 调用 Redis Lua release_hold 释放，更新 DB 状态为 TIMEOUT
     */
    @XxlJob("holdReleaseJob")
    public void releaseExpiredHolds() {
        // 1. 从 DB 分批查询过期预占记录（keyset 分页，每次 100 条）
        List<InventoryHold> expiredHolds = holdMapper.selectExpired(
            LocalDateTime.now(), 100);
        if (expiredHolds.isEmpty()) return;

        // 2. 逐个调用 Redis Lua 释放
        for (InventoryHold hold : expiredHolds) {
            try {
                LuaResult result = redisTemplate.execute(releaseHoldScript,
                    Arrays.asList(
                        "stock:" + hold.getSkuId() + ":available",
                        "stock:" + hold.getSkuId() + ":reserved"
                    ),
                    hold.getSkuId(),
                    hold.getHoldId(),
                    hold.getQuantity(),
                    hold.getRequestId()
                );

                // 3. 更新 DB 状态
                holdMapper.updateStatus(hold.getId(), "TIMEOUT", LocalDateTime.now());

                // 4. 发布事件
                eventPublisher.publish(InventoryHoldReleasedEvent.builder()
                    .holdId(hold.getHoldId())
                    .skuId(hold.getSkuId())
                    .quantity(hold.getQuantity())
                    .orderNo(hold.getOrderNo())
                    .reason("TIMEOUT")
                    .build());

            } catch (Exception e) {
                log.error("释放超时预占失败: holdId={}", hold.getHoldId(), e);
                holdMapper.updateRetry(hold.getId(), hold.getRetryCount() + 1, e.getMessage());
            }
        }
    }
}
```

### 14.2 15min 延迟任务（RocketMQ 延迟消息）

- **任务类型**: INVENTORY_RESERVATION（ADR-021 Tier 2）
- **实现**: RocketMQ 延迟消息 Level 14（≈15min）
- **取消时机**: 支付成功回调 confirm_deduct 时删除延迟消息
- **兜底**: HoldReleaseJob 每 5min 扫描 DB，保证延迟消息丢失时仍可自动释放

### 14.3 库存对账（新增 3 对）

| # | 对账对 | 频率 | 调度 | 检查项 | 自动修复 |
|---|--------|------|------|--------|---------|
| 8 | 库存预扣 vs 订单状态（已有） | 每 5min | XXL-Job hold-release-job | 预占 vs 订单状态 | 自动释放取消订单的预占 |
| 8a | Redis vs DB 库存量 | 每 30min | XXL-Job stock-recon-job | available / reserved / deducted 三量对比 | 以 Redis 为准同步到 DB |
| 8b | 渠道库存 vs 平台内部库存 | 每 1h | XXL-Job channel-stock-recon-job | 渠道库存同步状态 | 同步渠道库存与平台实际库存 |

---

## 15. 实施计划

| 阶段 | 内容 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | Core Lua 脚本（reserve/confirm/release/undo）+ InventoryReserveService + SagaAdapter（5x 重试幂等） | 4 个 Lua 脚本 + 3 个 Java Service | 2d |
| **Phase 2** | Freeze/Thaw 服务：freezeStock / unfreezeStock API + 状态机 + frozen 事件 + 渠道冻结 | StockFreezeService + AdminController | 1d |
| **Phase 3** | 渠道库存隔离：channel_stock_config CRUD + SHARED/DEDICATED/RATIO 三种模式 | ChannelStockManager + QuotaAllocator | 1.5d |
| **Phase 4** | 批量库存查询：Redis MGET 管道 + Caffeine L1 + BatchController + 降级回退 DB | BulkStockQueryService + CacheAside | 0.5d |
| **Phase 5** | HoldReleaseJob + RocketMQ 15min 延迟消息 + stock-recon-job + channel-stock-recon-job | 3 个 XXL-Job + 延迟消息 | 1d |
| **Phase 6** | 5 张表 DDL + Liquibase 多租户 changelog + 索引 + 初始化脚本 | DDL 文件 + Liquibase 模板 | 1d |
| **Phase 7** | Sentinel 阈值配置 + 指标/告警 + Grafana dashboard + ADR 交叉引用 + 文档更新 | Apollo 配置 + Prometheus + 文档 | 1.5d |

**总计：~8.5 人天**

---

## 16. 交叉引用矩阵

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-039 §3.3** | HOLD 状态生命周期 | inventory-service 作为 HOLD 守卫条件评估器，提供 getAvailableQty |
| **ADR-039 §3.3** | HoldReleaseJob | Adr-043 实现库存侧释放逻辑，双方协同 |
| **ADR-037 §4** | reserve_inventory YAML 步骤 | `inventoryReserver` 和 `inventoryUndoReserver` 实现 |
| **ADR-037 §4** | by_warehouse 拆分策略 | stock_item 按 warehouse_id 维度存储 |
| **ADR-040 Part C** | Redis Lua 两阶段协议 | 从一句话设计扩展为完整 4 个 Lua 脚本 |
| **ADR-040 §3-4** | SLA / Sentinel 阈值 | deduct/restore/queryStock SLI 值继承并扩展 |
| **ADR-020 §3** | Saga 步骤 | deductInventory / undoDeduct 5x 重试幂等实现 |
| **ADR-030** | 幂等框架 | request_id 作为操作幂等键，saga_id 作为补偿幂等键 |
| **ADR-016** | AZ 故障转移超卖 | Redis Lua 原子性解决 ADR-016 识别的锁丢失超卖风险 |
| **ADR-021** | 延迟任务 | INVENTORY_RESERVATION Tier 2（15min）实现 |
| **ADR-035** | 多租户 | 5 张表 DDL 兼容 tenant_id 字段 + Liquibase 租户模板 |
| **ADR-036** | 渠道库存同步 | channel_stock_config.sync_enabled 集成渠道同步 |
| **ADR-042 §2.3** | 支付回调 confirmDeduct | payment-service 支付成功后调用 inventory confirmDeduct |
| **ADR-038** | ApiResult\<T\> | 所有库存 API 返回标准格式 {code, message, data, traceId} |
| **ADR-015** | 容量规划 | Redis key 分片 `stock:{sku}:{shard%100}` 解决热点风险 |
| **ADR-022** | 全链路灰度 | inventory-service 在 serviceGrayList 中参与灰度 |
| **degradation-strategy.md** | 降级策略 | P1-保留，Sentinel 超限返回 429 |

---

## 17. 风险矩阵

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Redis 宕机导致库存服务不可用 | 低 | 高 | Redis Cluster 跨 AZ 复制（ADR-016）；降级到 L3 仅提供 REST API |
| AZ 故障切换时库存 double | 低 | 高 | Redis Lua 原子性确保单线程执行；CANAL 同步到 DB 可对账修复 |
| 热点 SKU Redis key 集中 | 中 | 中 | Key 分片 `stock:{sku}:{shard%100}` + L1 Caffeine 5s TTL 吸收 |
| 预占过期但支付成功 | 中 | 低 | 15min TTL 足够（支付通常在 30s-5min）；过期后 confirm 返回 4004 触发 Saga 补偿 |
| 渠道隔离配置错误导致超卖 | 中 | 中 | 默认 SHARED 模式安全；DEDICATED/RATIO 需 Admin 显式配置 |
| 冻结后仍有用户在途下单 | 低 | 低 | 冻结只阻止新预占；已预占用户正常完成支付 |
| 并发预占相同 SKU 超卖 | 低 | 高 | Redis Lua 单线程原子执行，DECRBY 检查返回值，无并发写冲突 |

---

## 附录：Apollo 配置命名空间

| 命名空间 | 配置项 | 默认值 | 说明 |
|---------|--------|--------|------|
| `inventory.sentinel` | reserve.qps | 5000 | 预占 QPS 阈值 |
| | reserve.rt_ms | 50 | 预占 RT 阈值 |
| | queryStock.qps | 10000 | 查询 QPS 阈值 |
| `inventory.hold` | hold.ttl_seconds | 900 | 预占超时时间（秒） |
| | hold.max_alert_hours | 48 | HOLD 超时 P2 告警时间 |
| | release.batch_size | 100 | HoldReleaseJob 每批处理数 |
| `inventory.channel` | default.isolation_mode | SHARED | 默认渠道隔离模式 |
| `inventory.freeze` | temporary.default_minutes | 1440 | 临时冻结默认时长（分钟） |
| `inventory.alert` | low_stock.threshold | 10 | 低库存预警阈值 |
| `inventory.retry` | saga.max_retries | 5 | Saga 最大重试次数 |
| | saga.intervals | 1s,2s,5s,10s,30s | 重试间隔序列 |

---

> **相关文档**：
> - [功能完整性检查报告](../functionality-completeness-report.md) §4.2
> - [数据对账矩阵](../reconciliation-matrix.md) Row 8/8a/8b
> - [待优化点记录](../optimization-opportunities.md) #32
> - [订单中台功能全景](../feature-overview.md) §2.6
> - 时序图：[两阶段预占协议](../diagrams/sequence/inventory-two-phase.puml)
> - 组件图：[库存服务组件架构](../diagrams/c4/inventory-component.puml)
