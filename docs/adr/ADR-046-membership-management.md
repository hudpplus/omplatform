# ADR-046：会员管理 (Membership Management)

> **状态**: 已接受  
> **创建日期**: 2026-06-13  
> **影响范围**: member-service（会员管理中心）、order-core、price-service、promotion-service、notification-service
>
> **本文档系统设计订单中台的会员管理体系，涵盖会员等级模型、成长值积累、积分体系、权益矩阵、会员价集成、VIP 免运费以及数据模型。**

---

## 1. 背景

### 现状分析

会员管理在订单中台中完全缺失：

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| P1 | **无会员体系** | 无会员表、无等级定义、无权益配置 | 无法支持会员价、会员营销等场景 |
| P2 | **无成长值/积分** | 订单完成无成长值积累、无积分奖励 | 用户留存缺少激励机制 |
| P3 | **无会员价** | 计价引擎（ADR-045）缺少会员价步骤 | 无法实现不同等级差异化定价 |
| P4 | **无 VIP 免运费** | ADR-045 ShippingPricer 缺少会员免运费判定 | 高等级会员无法享受免运费权益 |

### 功能完整性报告引用

> **功能完整性报告 §5.2**：会员等级 / 价格体系 — 会员折扣、等级价、企业定制价、VIP 免运费（P3 优先级）

---

## 2. 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | 会员等级模型 | 6 级固定等级（L0-L5），平滑升降级 |
| G2 | 成长值体系 | 复合模型（金额×系数+频次奖励），自动升级 |
| G3 | 积分体系 | Earn rate tier-based (1%-5%)，100:1 抵扣，12 月滚动过期 |
| G4 | 会员价集成 | 作为 ADR-045 计价管道标准步骤，P99 ≤ 20ms |
| G5 | VIP 免运费 | 等级+金额双重判定，灵活配置 |
| G6 | 权益矩阵 | 每等级折扣率/免运费门槛/专属权益可配置 |
| G7 | 事件通知 | 等级变更/积分变动/降级预警事件 |

---

## 3. 决策

## 3. 战术 DDD 设计

### 3.1 聚合根

| 聚合根 | 数据库表 | 标识符 | 生命周期 |
|--------|---------|--------|---------|
| **Member** (会员) | member | user_id (Long) | 用户注册即有，永久存在 |
| **MemberPointsAccount** (积分账户) | member_points_account | account_id (Long, 1:1 with user_id) | 随会员创建 |

### 3.2 Entity vs Value Object

| 类型 | 名称 | 标识 | 原因 |
|------|------|------|------|
| **Entity** | Member | user_id（不变） | 生命周期贯穿平台始终，等级/成长值可变 |
| **Entity** | MemberTier | tier_id | 等级定义可配置变更 |
| **Entity** | MemberPointsAccount | account_id | 积分余额可变 |
| **Value Object** | GrowthValue | amount + category | 不可变记录，一次成长值事件对应一个 VO |
| **Value Object** | MemberBenefit | tier_id + benefit_type + config | 权益配置快照，替换即更新 |
| **Value Object** | PointsTransaction | type + amount + source | 积分流水行，写入后不可变 |
| **Value Object** | MemberPriceConfig | tier_id + category_id + discount_rate | 会员价配置，替换即生效 |

### 3.3 领域事件

| 事件 | 触发点 | 消费者 |
|------|--------|--------|
| `MemberLevelChanged` | 等级升降 | notification（推送恭喜/降级提醒） |
| `MemberLevelDowngradeWarning` | 即将降级（季度评估） | notification（降级预警通知） |
| `MemberPointsChanged` | 积分增减 | — |
| `MemberBenefitActivated` | 权益生效 | price-service（刷新会员价标签） |
| `MemberGrowthRecorded` | 成长值变更 | — |

### 3.4 不变条件（Invariants）

| 规则 | 约束 | 保障方式 |
|------|------|---------|
| 等级区间不重叠 | 各级成长值 [min, max] 连续无空洞 | Apollo 配置校验 |
| 积分不超发 | points_transaction 总额 = points_account.balance | 对账任务每日校验 |
| 积分不重复使用 | 积分抵扣的订单取消后积分回退 | 幂等回滚 |
| 等级不跳级 | 只能逐级升降 | 等级计算引擎强制 |

### 3.5 Repository 模式

```
MemberRepository (Interface)
  └── MemberDBRepository (OB)
     └── 缓存: Redis String (member:{userId} → JSON, TTL 1h)

MemberPointsRepository (Interface)
  └── MemberPointsDBRepository (OB)
```

---

## 4. 决策

### 决策 1：会员等级模型

| 方案 | 评估 |
|------|------|
| **动态等级**（不固定等级数，按成长值区间实时计算） | 灵活但用户感知弱，无法差异化运营 |
| **固定 6 级（L0-L5）** | ✅ **选中** — L0 普通→L1 白银→L2 黄金→L3 铂金→L4 钻石→L5 黑卡；等级名与成长值区间绑定，可配置 |

**等级定义**：

| 等级 | 名称 | 成长值区间 | 折扣率 | 免运费门槛 | 专属权益 |
|------|------|-----------|--------|-----------|---------|
| L0 | 普通会员 | 0 | 无折扣 | 满 99 元 | - |
| L1 | 白银会员 | 1-999 | 9.8 折 | 满 69 元 | 生日优惠券 |
| L2 | 黄金会员 | 1,000-4,999 | 9.5 折 | 满 49 元 | 生日优惠券+专属客服 |
| L3 | 铂金会员 | 5,000-19,999 | 9.2 折 | 免运费 | 生日礼包+专属客服+延迟退货 |
| L4 | 钻石会员 | 20,000-99,999 | 9.0 折 | 免运费 | 全部+L4 专属促销+极速退款 |
| L5 | 黑卡会员 | 100,000+ | 8.5 折 | 免运费 | 全部+黑卡专享价+1v1 客服+新品优先购 |

### 决策 2：成长值积累

| 方案 | 评估 |
|------|------|
| **仅消费金额** | 简单但只激励花钱，不激励频次和品类探索 |
| **复合模型：金额×类目系数+频次奖励** | ✅ **选中** — 消费金额（1 元=1 成长值）× 类目系数 + 每月 N 单额外奖励 |

**成长值计算公式**：
```
growth = sum(order_amount) × category_multiplier + frequency_bonus
```

**类目系数**：

| 类目 | 系数 | 说明 |
|------|------|------|
| 默认 | ×1.0 | 大部分商品 |
| 电子 | ×1.5 | 高客单价促进 |
| 美妆 | ×1.2 | 高频品类 |
| 生鲜 | ×0.8 | 低客单价不放大 |

**频次奖励**（每月）：月订单 ≥ 3 单 → +100 成长值；≥ 5 单 → +300 成长值；≥ 10 单 → +500 成长值

### 决策 3：升降级规则

| 方案 | 评估 |
|------|------|
| **仅升级不降级** | 长期看全员顶级，权益贬值 |
| **立即升级 + 每季度降级评估** | ✅ **选中** — 达到阈值即时升级（提升满足感）；每季度末重新评估（近 90d 成长值），不足则降级；新人保护期 90d 不降级 |

**升降级触发时机**：
- **升级**：实时触发 — 成长值达到更高等级门槛时立即升级，发 `member.level_changed` 事件
- **降级**：每季度末 — 取近 90d 成长值判定，不足则降一级；发 `member.level_downgrade_warning` 提前 7d 预警

### 决策 4：积分体系

| 方案 | 评估 |
|------|------|
| **统一 Earn rate** | 简单但高等级无区分，缺乏激励 |
| **Tier-based Earn rate** | ✅ **选中** — 每个等级消费 1 元获得不同积分，100 积分=1 元抵扣，12 月滚动过期 |

**积分规则**：

| 等级 | Earn rate | 抵扣比例 | 说明 |
|------|-----------|---------|------|
| L0 | 1% | 100:1 | 消费 ¥100 = 100 积分 = ¥1 |
| L1 | 1% | 100:1 | |
| L2 | 2% | 100:1 | |
| L3 | 3% | 100:1 | |
| L4 | 4% | 100:1 | |
| L5 | 5% | 100:1 | |

### 决策 5：会员价集成

| 方案 | 评估 |
|------|------|
| **会员价独立计算** | 促销/券无法基于会员价，用户感知混乱 |
| **插入 ADR-045 计价管道（basic→member→promotion→coupon→shipping）** | ✅ **选中** — 先计算会员价（如 9 折），再在会员价基础上计算满减/折扣/券；Order: BasicPricer → MemberPricer → PromoPricer → CouponPricer → ShippingPricer |

**注意**：会员价与单品直降互斥（见 ADR-045 §10 互斥矩阵），会员价不与拼团/秒杀叠加。

### 决策 6：VIP 免运费

| 方案 | 评估 |
|------|------|
| **免运费写死在 shipping 逻辑中** | 不可配置，改门槛需发版 |
| **整合到 ADR-045 ShippingPricer + 门槛配置** | ✅ **选中** — ShippingPricer 调用 member-service 查等级免运费门槛；Apollo 可配置各等级的免运费策略 |

**判定逻辑**：
```
IF member.tier >= L3 (铂金及以上)
   → 无条件免运费
ELSE IF member.tier == L2 (黄金)
   → 订单金额 ≥ 49 元免运费
ELSE IF member.tier == L1 (白银)
   → 订单金额 ≥ 69 元免运费
ELSE (L0 普通)
   → 订单金额 ≥ 99 元免运费
```

---

## 4. 系统架构

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        API Gateway Layer                                  │
│     IGW Buyer / IGW Admin                                                  │
│     /api/v1/members/*  /api/admin/v1/members/*                            │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ Dubbo
┌──────────────────────────────────▼───────────────────────────────────────┐
│                           member-service                                   │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 1: Member Core 会员核心                                      │    │
│  │  ┌──────────┐ ┌────────────┐ ┌─────────────┐ ┌────────────────┐  │    │
│  │  │MemberInfo│ │MemberTier  │ │MemberUpgrade│ │MemberDowngrade │  │    │
│  │  │(信息查询) │ │(等级判定)  │ │(升级处理)   │ │(降级评估)      │  │    │
│  │  └──────────┘ └────────────┘ └─────────────┘ └────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 2: Points & Growth 积分与成长值                              │    │
│  │  ┌──────────────┐ ┌───────────────┐ ┌─────────────┐ ┌──────────┐  │    │
│  │  │PointsEarn    │ │PointsSpend    │ │GrowthAccum  │ │GrowthCalc│  │    │
│  │  │(积分获得)    │ │(积分抵扣)     │ │(成长值累计)  │ │(季度评估) │  │    │
│  │  └──────────────┘ └───────────────┘ └─────────────┘ └──────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ Layer 3: Benefits & Integration 权益与集成                        │    │
│  │  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────┐  │    │
│  │  │BenefitConfig │ │MemberPrice   │ │FreeShipping │ │Event     │  │    │
│  │  │(权益配置)    │ │(会员价查询)  │ │(免运费判定)  │ │Publisher  │  │    │
│  │  └──────────────┘ └──────────────┘ └─────────────┘ └──────────┘  │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
         ┌─────────────────────┼───────────────────┐
         │                     │                   │
   ┌─────▼──────┐      ┌──────▼──────┐     ┌──────▼──────────────┐
   │ OceanBase  │      │  Redis      │     │  Integration        │
   │ 7 张表     │      │  Cache L2   │     │                     │
   │ member/    │      │  tier defs  │     │ price-service       │
   │ tier/points│      │  member     │     │ (会员价管道步骤)     │
   │ growth/    │      │  info TTL   │     │ order-core           │
   │ benefit    │      │  (60s)      │     │ (事件消费)           │
   └────────────┘      └─────────────┘     │ notification         │
                                           └──────────────────────┘
```

---

## 5. 数据模型

```sql
-- 会员主表
CREATE TABLE member (
    user_id             BIGINT PRIMARY KEY,
    tier_id             VARCHAR(10) NOT NULL DEFAULT 'L0' COMMENT 'L0-L5',
    growth_value        BIGINT NOT NULL DEFAULT 0 COMMENT '当前成长值',
    total_points        BIGINT NOT NULL DEFAULT 0 COMMENT '总积分余额',
    total_earned_points BIGINT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    total_spent_points  BIGINT NOT NULL DEFAULT 0 COMMENT '累计消耗积分',
    version             INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 会员等级定义表
CREATE TABLE member_tier (
    id              VARCHAR(10) PRIMARY KEY COMMENT 'L0-L5',
    name            VARCHAR(50) NOT NULL COMMENT '等级名称',
    level           INT NOT NULL COMMENT '数值等级 0-5',
    min_growth      BIGINT NOT NULL COMMENT '成长值下限',
    max_growth      BIGINT COMMENT '成长值上限（NULL=无上限）',
    benefits_json   JSON NOT NULL COMMENT '权益JSON: {discount_rate, free_shipping_threshold, exclusive_benefits}',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 成长值流水表
CREATE TABLE member_growth_transaction (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(30) NOT NULL COMMENT 'ORDER(消费)/BONUS(频次奖励)/EXPIRE(过期)/ADJUST(调整)',
    amount          BIGINT NOT NULL COMMENT '成长值变更量（正/负）',
    source_order_no VARCHAR(64) COMMENT '来源订单号',
    source_detail   VARCHAR(200) COMMENT '来源说明',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_order (source_order_no)
);

-- 会员权益配置表
CREATE TABLE member_benefit (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tier_id         VARCHAR(10) NOT NULL,
    benefit_type    VARCHAR(30) NOT NULL COMMENT 'DISCOUNT(折扣)/SHIPPING(免运费)/EXCLUSIVE(专属)/BIRTHDAY(生日)',
    benefit_config  JSON NOT NULL COMMENT '权益配置JSON',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_tier (tier_id)
);

-- 会员价配置表
CREATE TABLE member_price_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    tier_id         VARCHAR(10) NOT NULL,
    category_id     BIGINT COMMENT '类目ID(NULL=全局)',
    discount_rate   DECIMAL(3,2) NOT NULL COMMENT '折扣率 0.85=85折',
    priority        INT DEFAULT 0 COMMENT '优先级（SKU级>类目级>全局）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_tier_category (tier_id, category_id)
);

-- 积分账户表
CREATE TABLE member_points_account (
    user_id         BIGINT PRIMARY KEY,
    balance         BIGINT NOT NULL DEFAULT 0 COMMENT '可用积分',
    total_earned    BIGINT NOT NULL DEFAULT 0 COMMENT '累计获得',
    total_spent     BIGINT NOT NULL DEFAULT 0 COMMENT '累计消耗',
    last_expire_at  DATETIME COMMENT '上次积分过期时间',
    version         INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 积分流水表
CREATE TABLE member_points_transaction (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    type            VARCHAR(20) NOT NULL COMMENT 'EARN(获得)/SPEND(消耗)/EXPIRE(过期)/ADJUST(调整)/REFUND(退款退还)',
    points          BIGINT NOT NULL COMMENT '积分变更量',
    balance_after   BIGINT NOT NULL COMMENT '变更后余额',
    source          VARCHAR(100) COMMENT '来源(订单号/活动)',
    expire_at       DATETIME COMMENT '这批积分的过期时间',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user (user_id),
    INDEX idx_expire (expire_at)
);
```

---

## 6. 会员等级状态机

```
      等级提升（达到门槛即时升级）
  L0 ──────→ L1 ──────→ L2 ──────→ L3 ──────→ L4 ──────→ L5
   ↑          ↑          ↑          ↑          ↑          ↑
   │          │          │          │          │          │
   └────L5────┘────L4────┘────L3────┘────L2────┘────L1────┘
             等级降级（每季度末评估，逐级下降）
```

### 升级事件流

```
订单完成 → order-core → 发 order.completed 事件
    → member-service 消费
        → 计算成长值（金额×类目系数）
        → 写入 member_growth_transaction
        → 更新 member.growth_value
        → 判断是否达到下一级阈值
            → 是：更新 tier_id → 发 member.level_changed 事件
            → 否：仅更新成长值
```

### 积分事件流

```
订单完成 → order-core → 发 order.completed 事件
    → member-service 消费
        → 计算积分（金额×earn_rate）
        → 写入 member_points_transaction
        → 更新 member_points_account.balance
        → 发 member.points_changed 事件
```

---

## 7. API 设计

| 层 | 方法 | 端点 | 说明 |
|----|------|------|------|
| Buyer | 会员信息 | `GET /api/v1/members/{userId}/info` | 等级、成长值、积分余额 |
| Buyer | 等级查询 | `GET /api/v1/members/{userId}/tier` | 当前等级+下一等级信息+差距 |
| Buyer | 权益查询 | `GET /api/v1/members/{userId}/benefits` | 当前等级权益列表 |
| Buyer | 积分流水 | `GET /api/v1/members/{userId}/points/transactions` | 分页查询积分变更记录 |
| Buyer | 成长值流水 | `GET /api/v1/members/{userId}/growth/history` | 成长值变更记录 |
| Buyer | 积分抵扣计算 | `POST /api/v1/members/points/estimate` | 估算可用积分可抵扣金额 |
| Order | 会员价查询 | `GET /api/backend/v1/members/{userId}/price-rate` | 查会员折扣率（供 price-service） |
| Order | 免运费判定 | `GET /api/backend/v1/members/{userId}/shipping-policy` | 查免运费门槛（供 shipping-service） |
| Order | 成长值授予 | `POST /api/backend/v1/members/growth/grant` | 订单完成后授予成长值 |
| Order | 积分授予 | `POST /api/backend/v1/members/points/grant` | 订单完成后授予积分 |
| Admin | 等级定义管理 | `POST /api/admin/v1/members/tiers` | 等级 CRUD + 权益配置 |
| Admin | 会员价配置 | `PUT /api/admin/v1/members/price-config` | 配置各等级的折扣率和类目范围 |
| Admin | 积分调整 | `POST /api/admin/v1/members/points/adjust` | 人工调整积分 |
| Admin | 统计看板 | `GET /api/admin/v1/members/stats` | 等级分布、积分发放/消耗统计 |

---

## 8. 事件定义

| 事件 | 生产者 | 消费者 | 触发条件 |
|------|--------|--------|---------|
| `member.level_changed` | member-service | notification（推送恭喜升级） | 达到升级阈值 |
| `member.level_downgrade_warning` | member-service | notification（降级提醒） | 降级评估前 7d |
| `member.level_downgraded` | member-service | notification | 季度评估降级 |
| `member.points_changed` | member-service | - | 积分增减变化 |
| `member.points_expiring` | member-service | notification（积分即将过期提醒） | 过期前 30d |
| `member.growth_recorded` | member-service | - | 成长值记录 |
| `member.benefit_activated` | member-service | price-service | 等级升级后激活新权益 |
| `member.info_updated` | member-service | cart-service | 个人信息更新 |

---

## 9. 非功能设计

| 指标 | 目标 | 实现 |
|------|------|------|
| 会员信息查询 P99 | ≤ 20ms | Caffeine L1 (60s TTL) + Redis L2 (300s TTL)；使用 memberId 分片 |
| 等级判定 P99 | ≤ 30ms | Caffeine 缓存 tier 定义 + Redis 缓存会员基本信息 |
| 成长值授予 P99 | ≤ 200ms | 异步事件消费 + OB 写入 |
| 积分抵扣计算 P99 | ≤ 50ms | Redis 读取余额 + 简单计算 |
| 季度降级评估 | 完成 ≤ 10min | XXL-Job + 分批处理（每次 1000 用户） |

---

## 10. 实施计划

| 阶段 | 内容 | 人天 |
|------|------|------|
| Phase 1: 等级模型 + CRUD | member/member_tier 表 DDL + 等级配置 + 基础 API | 2d |
| Phase 2: 成长值体系 | 成长值计算引擎 + 事务流水 + 升级/降级 Job + 事件 | 1.5d |
| Phase 3: 积分体系 | 积分账户 + 流水 + 获得/消费/过期 + 对账 | 1.5d |
| Phase 4: 会员价 + 免运费集成 | MemberPricer 适配器 + ShippingPricer 集成 + price-service 管道对接 | 1.5d |
| Phase 5: 事件 + 文档 | 8 个事件定义 + 交叉引用更新 + 文档 | 1d |

**总计：~7.5 人天**

---

## 11. 交叉引用矩阵

| ADR | 关系 | 说明 |
|-----|------|------|
| **ADR-045** §5 | 计价管道成员价步骤 | MemberPricer 插入计价管道，顺序 basic→member→promotion→coupon→shipping |
| **ADR-045** §10 | VIP 免运费 | ShippingPricer 调用 member-service 查询免运费门槛 |
| **ADR-045** §3 | 互斥矩阵 | 会员价与单品直降互斥，不与满减/折扣互斥 |
| **ADR-045** | API 依赖 | MemberPriceConfig 查询：price-service 调用 member-service 获取折扣率 |
| **ADR-037** §4 | after_complete hook | 订单完成后触发成长值和积分授予 |
| **ADR-039** | 状态机 | order.completed 事件消费，触发成长值/积分发放 |
| **ADR-042** | 支付完成 | 支付完成后发事件触发积分发放 |
| **ADR-038** | 事件规范 | 所有事件走 event center |
| **ADR-038** | API 规范 | ApiResult\<T\> 标准返回 |
| **ADR-040** | 缓存策略 | Caffeine + Redis L2 缓存 tier 定义和会员信息 |
| **ADR-044** | 购物车价签 | 购物车展示会员价标签 |

---

## 12. 风险矩阵

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 积分账户数据不一致（余额≠流水合计） | 中 | 中 | 乐观锁 + 每日对账 Job + points_account vs points_transaction 比对 |
| 成长值计算延迟（订单已完成但会员未升级） | 中 | 低 | 异步事件处理，用户下次访问时可见已升级 |
| 等级降级导致用户不满 | 中 | 中 | 7d 预警 + 新人保护期 90d + 降级回 L0 有保底 |
| 会员价 + 促销叠加导致定价过低 | 低 | 高 | ADR-045 互斥矩阵管控 + 最低售价保护（Apollo 可配置最低折扣率上限） |
