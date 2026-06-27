# ADR-042：支付结算中心

> **状态**: 已接受  
> **创建日期**: 2026-06-13  
> **影响范围**: payment-core（支付结算中心）、order-core、workflow-service
>
> **本文档系统设计订单中台的支付结算中心，涵盖多渠道支付网关、支付核心处理、退款引擎、结算系统、对账会计、支付安全以及非功能设计。**

---

## 背景

### 现状分析

当前支付能力分散在 7+ 份 ADR 中，缺乏统一设计，存在以下问题：

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| P1 | **无统一支付网关** | 支付接入硬编码在 order-core 中，新增渠道需改核心代码 | 扩展性差，每次对接新渠道涉及 3+ 服务修改 |
| P2 | **支付与订单状态机耦合** | ADR-039 PaymentProcessService 嵌入 order-core，支付回调强依赖订单服务可用性 | 高峰期支付回调雪崩效应，影响订单写入 |
| P3 | **结算系统空白** | ADR-038 仅定义了 settlement-consumer 概念，无结算模型、周期、出款设计 | 财务结算需人工处理，存在资金风险 |
| P4 | **对账能力有限** | reconciliation-matrix.md 仅有 OB↔支付网关 15min 对账，无渠道账单下载、自动化匹配 | 对账依赖人工，发现差异延迟 >24h |
| P5 | **退款无统一引擎** | 退款逻辑分散在 ADR-039 §6 和 Saga 定义中，无自动审核规则、无部分退支持 | 售后处理效率低，客服工作量高 |
| P6 | **手续费无管理** | 各渠道费率硬编码，无统一的费率表管理和分账计算 | 对账差异难以追踪，财务核算困难 |

### 目标

| # | 目标 | 衡量标准 |
|---|------|---------|
| G1 | 建设多渠道支付网关，SPI 架构支持快速接入新渠道 | 新渠道接入 ≤ 5 人天，不改核心流程 |
| G2 | 支付与订单解耦，独立支付单管理 | 支付服务独立部署，回调峰值 5000 TPS 不拖垮订单服务 |
| G3 | 覆盖全流程结算（T+1/T+7/T+30） | 结算自动化率 100%，出款准确率 100% |
| G4 | 自动化对账匹配引擎 | 自动匹配率 ≥ 99.5%，差异发现 ≤ 15min |
| G5 | 统一退款引擎，支持自动审核 | 小额退款（≤¥200）自动审核率 ≥ 95% |
| G6 | 支付安全合规 | 密钥加密存储、通信签名、敏感数据脱敏 |

---

## 决策

### 决策 1：支付网关架构模式

| 方案 | 评估 |
|------|------|
| **直接集成 (硬编码)** | 简单但不可扩展，每新渠道改 order-core，❌ |
| **SPI 适配器模式** | ✅ **选中** — 类似 ADR-036 ChannelAdapter SPI，新渠道仅需实现接口；统一签名/路由/回调框架 |
| **独立网关服务** | 隔离性好但增加了调用链延迟，可作为后续演进方向 |

**理由**：与 ADR-036 的全渠道 SPI 框架一致，团队已有成熟经验；新渠道只需实现 `PaymentChannelAdapter` 接口，不改核心流程。

### 决策 2：支付单与订单的关系

| 方案 | 评估 |
|------|------|
| **支付单嵌入订单表** | 当前方式，字段混乱，查询复杂，❌ |
| **独立支付单（1:N）** | ✅ **选中** — 一个订单可对应多笔支付单（部分支付/分期）；支付状态独立管理 |
| **独立支付单（1:1）** | 简单但无法支持分期、分次支付场景 |

**理由**：支持分期付款和部分退款场景；支付单与订单通过 `order_no` 关联但生命周期解耦。

### 决策 3：结算周期策略

| 方案 | 评估 |
|------|------|
| **固定 T+1 结算** | 简单但对部分商户（高退货率）结算风险高，❌ |
| **统一 T+7 结算** | 折中方案，但大型商户期望 T+1，❌ |
| **可配置结算周期** | ✅ **选中** — Apollo 配置 `merchant.settle.cycle`，支持 T+1/T+7/T+30 按商户级别配置 |

**理由**：不同商户信用级别不同；Apollo 动态配置无需重启。

### 决策 4：对账匹配策略

| 方案 | 评估 |
|------|------|
| **逐笔实时对账** | 实时性高但实现复杂，对账压力大，❌ |
| **T+1 批量对账** | ✅ **选中** — 每日凌晨下载渠道账单，批量匹配；支持自动处理长短款 |
| **仅人工对账** | 无开发成本但效率低、易出错，❌ |

**理由**：支付宝/微信均提供 T+1 日账单下载，行业标准做法；批量匹配效率高，长短款规则引擎可处理 99%+ 的差异。

---

## 详细设计

### 1. 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                      API Gateway 层 (IGW)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐              │
│  │ IGW Buyer   │  │ IGW Admin   │  │ External GW  │              │
│  │ /api/v1/pay │  │ /api/admin/ │  │ /open/v1/pay │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘              │
└─────────┼─────────────────┼─────────────────┼──────────────────────┘
          │                 │                 │
┌─────────▼─────────────────▼─────────────────▼──────────────────────┐
│                     支付结算中心                                      │
│                                                                      │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  Layer 1: 支付网关 (Payment Gateway)                    │           │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────────┐ │           │
│  │  │ AlipaySPI  │ │ WechatSPI  │ │ PaymentRouter     │ │           │
│  │  │ adapter    │ │ adapter    │ │ (路由/签名/验签)   │ │           │
│  │  └────────────┘ └────────────┘ └────────────────────┘ │           │
│  └──────────────────────────────────────────────────────┘           │
│                                                                      │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  Layer 2: 支付核心 (Payment Core)                      │           │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐ │           │
│  │  │ 支付单管理    │ │ 支付状态机    │ │ 幂等/超时管理   │ │           │
│  │  │ (CRUD/查询)  │ │ (6 态流转)   │ │ (Idempotent)   │ │           │
│  │  └──────────────┘ └──────────────┘ └────────────────┘ │           │
│  └──────────────────────────────────────────────────────┘           │
│                                                                      │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  Layer 3: 退款引擎 (Refund Engine)                     │           │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐ │           │
│  │  │ 退款申请     │ │ 审核规则引擎  │ │ 退款执行/重试   │ │           │
│  │  │ (全退/部分退) │ │ (自动/人工)   │ │ (渠道调用/补偿) │ │           │
│  │  └──────────────┘ └──────────────┘ └────────────────┘ │           │
│  └──────────────────────────────────────────────────────┘           │
│                                                                      │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  Layer 4: 结算中心 (Settlement Engine)                │           │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐ │           │
│  │  │ 结算单生成   │ │ 分账/手续费   │ │ 出款管理       │ │           │
│  │  │ (周期汇总)   │ │ (净额计算)   │ │ (银行代付)     │ │           │
│  │  └──────────────┘ └──────────────┘ └────────────────┘ │           │
│  └──────────────────────────────────────────────────────┘           │
│                                                                      │
│  ┌──────────────────────────────────────────────────────┐           │
│  │  Layer 5: 对账会计 (Reconciliation & Accounting)      │           │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐ │           │
│  │  │ 渠道账单下载  │ │ 批量匹配引擎  │ │ 会计分录生成   │ │           │
│  │  │ (T+1 日账单) │ │ (长短款处理)  │ │ (借貸平衡)     │ │           │
│  │  └──────────────┘ └──────────────┘ └────────────────┘ │           │
│  └──────────────────────────────────────────────────────┘           │
└──────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
│  order-core  │   │ inventory    │   │ Apollo/Nacos/    │
│ (状态机/事件) │   │ 库存/物流    │   │ 密钥/配置中心    │
└──────────────┘   └──────────────┘   └──────────────────┘
```

### 2. 支付网关层 (Payment Gateway Layer)

#### 2.1 SPI 适配器接口

```java
/**
 * 支付渠道适配器 SPI — 新渠道只需实现此接口。
 * 
 * @see ADR-036 ChannelAdapter 类似机制
 */
public interface PaymentChannelAdapter {

    /** 渠道唯一标识: ALIPAY / WECHAT / ... */
    String channelCode();

    /** 发起支付 */
    PaymentResponse charge(PaymentRequest request);

    /** 查询支付状态 */
    PaymentQueryResult query(String transactionId);

    /** 发起退款 */
    RefundResponse refund(RefundRequest request);

    /** 查询退款状态 */
    RefundQueryResult queryRefund(String refundTransactionId);

    /** 取消支付（未支付订单撤销） */
    CancelResponse cancel(CancelRequest request);

    /** 验证异步回调签名 */
    boolean verifyCallback(Map<String, String> params, String signature);

    /** 解析回调为统一支付结果 */
    PaymentResult parseCallback(Map<String, String> params);

    /** 下载对账账单（T+1） */
    BillFile downloadBill(Date billDate);
}
```

#### 2.2 支付路由规则

```yaml
# Apollo 配置: payment.routing.rules
payment:
  routing:
    rules:
      - name: "大额走支付宝"
        priority: 1
        condition: "amount > 5000"
        channel: "ALIPAY"
      - name: "微信优先"
        priority: 2
        condition: "channelSource == 'WECHAT_MP'"
        channel: "WECHAT"
      - name: "默认支付宝"
        priority: 99
        condition: "true"
        channel: "ALIPAY"
    fallback: "ALIPAY"        # 路由失败默认渠道
    timeout: 5000              # 渠道调用超时 (ms)
```

#### 2.3 异步回调处理流程

```
支付渠道回调
    │
    ▼
① 签名验证 (RSA256/HMAC-SHA256)
    │ 失败 ──→ 401 + 告警日志
    │ 成功
    ▼
② 渠道路由：从回调参数提取 channel_code
    │
    ▼
③ 幂等检查 (payment_request_no → Redis SETNX + IdempotentStore)
    │ 已存在 ──→ 200 (已处理)
    │ 不存在
    ▼
④ 回调解析：adapter.parseCallback(params)
    │
    ▼
⑤ 状态转换：
    ├── 网关 SUCCESS → 支付成功处理
    ├── 网关 FAILED  → 更新支付单为 FAILED
    └── 网关 PENDING → 更新为 PROCESSING，注册延时查单
    │
    ▼
⑥ 支付成功处理：
    a. 更新 payment_order → SUCCESS
    b. 通知 order-core → 状态机 PENDING_PAY→PAID
    c. 通知 inventory-service → 预占转正式扣减
    d. 发布 OrderPaidEvent (event_outbox + 事务消息)
    e. 发送渠道回调 HTTP 200
```

#### 2.4 渠道费率管理

```yaml
# Apollo 配置: payment.channel.fees
payment:
  channel:
    fees:
      - channel: "ALIPAY"
        fee_rate: 0.006               # 0.6%
        fee_cap: 100                   # 单笔封顶 ¥100
        fee_min: 0.01                  # 单笔最低 ¥0.01
      - channel: "WECHAT"
        fee_rate: 0.006
        fee_cap: 100
        fee_min: 0.01
```

---

### 3. 支付核心 (Payment Core)

#### 3.1 支付单状态机

```
           ┌──────────┐
           │  INIT     │  ← 发起支付时创建
           └────┬─────┘
                │
          ┌─────▼──────┐
          │ PROCESSING │  已调起三方支付，等待异步结果
          └──┬──────┬──┘
             │      │
     ┌───────▼┐  ┌──▼────────┐
     │SUCCESS │  │  FAILED   │  ← 支付失败/超时/取消
     └───┬────┘  └───────────┘
         │
    ┌────▼──────┐
    │ REFUNDING │  ← 发起退款
    └────┬──────┘
         │
    ┌────▼──────┐
    │ REFUNDED  │  ← 退款完成
    └───────────┘
```

**状态转换表**

| 当前状态 | 目标状态 | 触发条件 | 守卫条件 |
|---------|---------|---------|---------|
| INIT | PROCESSING | 调起三方支付成功 | payment_request_no 幂等检查 |
| INIT | FAILED | 渠道返回支付失败 | — |
| PROCESSING | SUCCESS | 异步回调渠道确认成功 | 金额 ±0.01 以内 (PaymentAmountGuard) |
| PROCESSING | FAILED | 异步回调渠道确认失败 / 超时关单 | 超时 30min (Apollo 配置) |
| SUCCESS | REFUNDING | 发起退款 | 退款金额 ≤ 支付金额；订单状态允许退款 |
| REFUNDING | REFUNDED | 渠道退款成功 | 退款单状态均为 SUCCESS |
| REFUNDING | SUCCESS | 退款失败 / 撤销退款 | 至少有一笔退款失败 |
| ANY | FAILED | 风控拒绝 / 系统异常 | 风控规则 |

#### 3.2 支付单数据模型

```sql
-- 支付单主表
CREATE TABLE payment_order (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    payment_no        VARCHAR(32)   NOT NULL COMMENT '支付单号，Leaf Segment 生成',
    order_no          VARCHAR(32)   NOT NULL COMMENT '关联订单号',
    merchant_id       BIGINT        NOT NULL COMMENT '商户ID（多商户场景）',
    
    channel_code      VARCHAR(16)   NOT NULL COMMENT '支付渠道: ALIPAY/WECHAT',
    payment_method    VARCHAR(32)   DEFAULT NULL COMMENT '支付方式: ALIPAY_APP/WECHAT_JSAPI',
    
    currency          VARCHAR(3)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    amount            DECIMAL(12,2) NOT NULL COMMENT '支付金额（元）',
    fee               DECIMAL(12,2) DEFAULT 0.00 COMMENT '通道手续费',
    net_amount        DECIMAL(12,2) DEFAULT 0.00 COMMENT '净额 = amount - fee',
    
    status            VARCHAR(16)   NOT NULL DEFAULT 'INIT' COMMENT '支付单状态',
    transaction_id    VARCHAR(128)  DEFAULT NULL COMMENT '三方支付流水号',
    pay_time          DATETIME      DEFAULT NULL COMMENT '支付完成时间',
    
    request_no        VARCHAR(64)   NOT NULL COMMENT '请求号（幂等键）',
    notify_url        VARCHAR(512)  DEFAULT NULL COMMENT '异步通知地址',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_request_no (request_no),
    UNIQUE KEY uk_transaction_id (transaction_id),
    KEY idx_order_no (order_no),
    KEY idx_status_time (status, gmt_create)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单主表';
```

```sql
-- 支付流水表（记录每次三方交互）
CREATE TABLE payment_transaction (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    transaction_no    VARCHAR(32)   NOT NULL COMMENT '流水号，Snowflake 生成',
    payment_no        VARCHAR(32)   NOT NULL COMMENT '关联支付单号',
    
    channel_code      VARCHAR(16)   NOT NULL COMMENT '支付渠道',
    trade_type        VARCHAR(16)   NOT NULL COMMENT '交易类型: CHARGE/REFUND/QUERY/CANCEL',
    
    request_amount    DECIMAL(12,2) NOT NULL COMMENT '请求金额',
    fee               DECIMAL(12,2) DEFAULT 0.00 COMMENT '手续费',
    currency          VARCHAR(3)    DEFAULT 'CNY',
    
    request_body      TEXT          COMMENT '请求报文（脱敏）',
    response_body     TEXT          COMMENT '响应报文（脱敏）',
    raw_response      JSON          COMMENT '三方原始返回',
    
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    error_code        VARCHAR(64)   DEFAULT NULL COMMENT '三方错误码',
    error_msg         VARCHAR(512)  DEFAULT NULL COMMENT '三方错误信息',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    KEY idx_payment_no (payment_no),
    KEY idx_transaction_no (transaction_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';
```

```sql
-- 支付渠道配置表
CREATE TABLE payment_channel_config (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    channel_code      VARCHAR(16)   NOT NULL COMMENT '渠道编码: ALIPAY/WECHAT',
    channel_name      VARCHAR(64)   NOT NULL COMMENT '渠道名称',
    
    app_id            VARCHAR(64)   NOT NULL COMMENT '应用ID',
    sign_type         VARCHAR(16)   NOT NULL DEFAULT 'RSA256' COMMENT '签名类型',
    public_key        TEXT          COMMENT '渠道公钥（AES 加密存储）',
    private_key       TEXT          COMMENT '商户私钥（AES 加密存储）',
    app_secret        VARCHAR(256)  DEFAULT NULL COMMENT 'AppSecret（HMAC 用）',
    
    fee_rate          DECIMAL(6,4)  NOT NULL COMMENT '手续费率（如 0.006 = 0.6%）',
    fee_cap           DECIMAL(12,2) DEFAULT 100.00 COMMENT '单笔封顶',
    fee_min           DECIMAL(12,2) DEFAULT 0.01 COMMENT '单笔最低',
    
    priority          INT           DEFAULT 99 COMMENT '路由优先级（小优先）',
    status            VARCHAR(8)    DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_channel (channel_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付渠道配置（密钥 AES 加密存储）';
```

#### 3.3 支付超时处理

```java
/**
 * 支付超时关单逻辑
 * 由 XXL-Job 定时调度，每 30s 扫描
 * 
 * Apollo 配置:
 *   payment.timeout.order = 30   # 单位: 分钟，默认 30min
 *   payment.timeout.max = 60     # 最大超时时间
 */
@Component
public class PaymentTimeoutJob {
    
    @XxlJob("paymentTimeoutJob")
    public void process() {
        // 1. 查询 PROCESSING 状态且超过 N 分钟的支付单
        List<PaymentOrder> timeoutOrders = paymentOrderMapper
            .selectByStatusAndTimeout("PROCESSING", 
                Duration.ofMinutes(config.getTimeoutMinutes()));
        
        // 2. 逐个查询三方支付网关真实状态
        for (PaymentOrder order : timeoutOrders) {
            PaymentChannelAdapter adapter = channelFactory.getAdapter(order.getChannelCode());
            PaymentQueryResult result = adapter.query(order.getTransactionId());
            
            if (result.isSuccess()) {
                // 网关成功 → 走正常支付成功流程
                processPaymentSuccess(order, result);
            } else if (result.isFailed()) {
                // 网关明确失败 → 关单
                closePayment(order, "支付超时: 网关状态失败");
            } else {
                // 网关仍 PENDING → 通知订单侧关单
                notifyOrderCancel(order, "支付超时未到账");
            }
        }
    }
}
```

---

### 4. 退款引擎 (Refund Engine)

#### 4.1 退款流程

```
退款申请（买家/客服/自动）
    │
    ▼
① 退款检查
   ├── 订单状态允许退款 (PAID/SHIPPED/DELIVERED/REFUNDING)
   ├── 退款金额 ≤ 可退金额 (支付金额 - 已退金额)
   └── 未超过退款期限 (支付后 90 天)
    │
    ▼
② 审核路由
   ├── 自动审核: ≤¥200 + 未发货 + 非风控 → 自动通过
   ├── 人工审核: >¥200 / 已发货 / 风控命中 → 客服工作台审核
   └── 拒绝: 不符合退款条件 → 告知原因
    │
    ▼
③ 退款类型
   ├── 全额退款: 整单取消 + 全款原路返回
   ├── 部分退款: 按指定金额退款，订单部分完成
   └── 仅退款不退货: 无需退货，直接退款（虚拟商品/协商一致）
    │
    ▼
④ 退款执行
   a. 创建 refund_order (INIT)
   b. 更新 payment_order → REFUNDING
   c. 调用渠道 refund() 接口
   d. 渠道返回退款流水号
   e. 更新 refund_order → SUCCESS / FAILED
    │
    ▼
⑤ 退款后置动作
   a. 订单状态 → REFUNDED (全额) / 部分退款标记 (部分)
   b. 库存回滚 (已发货场景走退货流程再回滚)
   c. 优惠券回退 (已使用但未过期的券)
   d. 发布 RefundSuccessEvent (事件中心)
   e. 结算冲抵 (如果订单已结算)
```

#### 4.2 退款单数据模型

```sql
-- 退款单表
CREATE TABLE refund_order (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    refund_no         VARCHAR(32)   NOT NULL COMMENT '退款单号，Snowflake 生成',
    payment_no        VARCHAR(32)   NOT NULL COMMENT '关联支付单号',
    order_no          VARCHAR(32)   NOT NULL COMMENT '关联订单号',
    
    refund_type       VARCHAR(16)   NOT NULL COMMENT '退款类型: FULL/PARTIAL/NORETURN',
    amount            DECIMAL(12,2) NOT NULL COMMENT '退款金额',
    reason            VARCHAR(256)  DEFAULT NULL COMMENT '退款原因',
    reason_code       VARCHAR(32)   DEFAULT NULL COMMENT '退款原因分类编码',
    
    status            VARCHAR(16)   NOT NULL DEFAULT 'INIT' COMMENT '退款单状态',
    audit_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED',
    audit_by          VARCHAR(64)   DEFAULT NULL COMMENT '审核人（NULL=系统自动）',
    audit_time        DATETIME      DEFAULT NULL COMMENT '审核时间',
    audit_remark      VARCHAR(256)  DEFAULT NULL COMMENT '审核备注',
    
    channel_code      VARCHAR(16)   NOT NULL COMMENT '原支付渠道',
    refund_transaction_id VARCHAR(128) DEFAULT NULL COMMENT '三方退款流水号',
    refund_success_time   DATETIME      DEFAULT NULL COMMENT '退款成功时间',
    
    retry_count       INT           DEFAULT 0 COMMENT '已重试次数',
    max_retries       INT           DEFAULT 3 COMMENT '最大重试次数',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order_no (order_no),
    KEY idx_payment_no (payment_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';
```

#### 4.3 自动审核规则引擎

```java
/**
 * 退款自动审核规则引擎
 * Apollo 动态配置规则
 */
@Component
public class RefundAuditRuleEngine {
    
    /** Apollo 配置: 自动审核规则 */
    @Value("${refund.audit.rules}")
    private String auditRulesJson;  // JSON 规则列表
    
    public AuditResult evaluate(RefundRequest request) {
        List<AuditRule> rules = JsonUtils.parseArray(auditRulesJson, AuditRule.class);
        
        for (AuditRule rule : rules) {
            if (match(rule, request)) {
                return rule.getAction() == RuleAction.AUTO_APPROVE 
                    ? AuditResult.approve() 
                    : AuditResult.pendingManual(rule.getReason());
            }
        }
        return AuditResult.pendingManual("未匹配自动规则");
    }
    
    private boolean match(AuditRule rule, RefundRequest request) {
        // SpEL 表达式匹配，如:
        // "amount <= 200 && delivered == false && riskScore < 60"
        return expressionEngine.evaluate(rule.getCondition(), request);
    }
}
```

```yaml
# Apollo 配置: refund.audit.rules
refund:
  audit:
    rules:
      - condition: "amount <= 200 && delivered == false && riskScore < 60"
        action: "AUTO_APPROVE"
        priority: 1
      - condition: "amount <= 500 && delivered == false && riskScore < 30"
        action: "AUTO_APPROVE"
        priority: 2
      - condition: "riskScore >= 80"
        action: "REJECT"
        reason: "风控规则拒绝，请联客服"
        priority: 3
      - condition: "true"
        action: "MANUAL"
        reason: "转入人工审核"
        priority: 99
```

#### 4.4 退款补偿与重试

```yaml
# Apollo 配置: refund.retry
refund:
  retry:
    max_retries: 3
    intervals: [1m, 5m, 30m]    # 重试间隔
    dlq_alert: "P1"              # 超过最大重试 → P1 告警
```

---

### 5. 结算引擎 (Settlement Engine)

#### 5.1 结算周期管理

```java
/**
 * 结算周期计算器
 * Apollo 配置 merchant.settle.cycle，支持按商户级别配置
 */
@Component
public class SettlementCycleCalculator {
    
    public SettlementPeriod calculatePeriod(Long merchantId, Date now) {
        String cycle = apolloConfig.getMerchantConfig(merchantId, "settle.cycle", "T+1");
        
        switch (cycle) {
            case "T+1":
                // 每日结算前一日订单
                return new SettlementPeriod(
                    DateUtils.addDays(now, -1),  // start: 昨天 00:00:00
                    DateUtils.addDays(now, -1)   // end:   昨天 23:59:59
                );
            case "T+7":
                // 每周一结算上周一~周日
                return new SettlementPeriod(
                    DateUtils.getPreviousMonday(now),
                    DateUtils.getPreviousSunday(now)
                );
            case "T+30":
                // 每月 5 日结算上月
                return new SettlementPeriod(
                    DateUtils.getFirstDayOfPreviousMonth(now),
                    DateUtils.getLastDayOfPreviousMonth(now)
                );
        }
    }
}
```

#### 5.2 结算核心流程

```
Cron 触发结算
    │
    ▼
① 查询结算周期内已支付/已退款订单
    │
    ▼
② 净额计算
   有效交易总额 = SUM(已支付订单金额)
   - 平台佣金 = 交易总额 × merchant.commission_rate
   - 通道手续费 = SUM(每笔交易 fee)
   - 退款抵扣 = SUM(周期内全额退款金额，部分退按比例)
   ──────────────────────────────────
   净额 = 有效交易总额 - 佣金 - 手续费 - 退款 + 调整
    │
    ▼
③ 生成结算单 (settlement_order + settlement_bill)
    │
    ▼
④ 结算审核
   ├── 净额 < ¥500,000 + 商户信用良好 → 自动审核通过
   └── 净额 ≥ ¥500,000 → 财务运营人工审核
    │
    ▼
⑤ 出款到商户银行账户（银行代付接口）
    │
    ▼
⑥ 生成会计分录 (account_journal)
    │
    ▼
⑦ 发布 SettlementCompletedEvent
```

#### 5.3 结算单数据模型

```sql
-- 结算单主表
CREATE TABLE settlement_order (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    settlement_no     VARCHAR(32)   NOT NULL COMMENT '结算单号，Snowflake 生成',
    merchant_id       BIGINT        NOT NULL COMMENT '商户ID',
    merchant_name     VARCHAR(128)  DEFAULT NULL COMMENT '商户名称',
    
    cycle_type        VARCHAR(8)    NOT NULL COMMENT '结算周期: T+1/T+7/T+30',
    period_start      DATE          NOT NULL COMMENT '结算周期开始日期',
    period_end        DATE          NOT NULL COMMENT '结算周期结束日期',
    settle_date       DATE          NOT NULL COMMENT '实际结算日期',
    
    transaction_count INT           DEFAULT 0 COMMENT '交易笔数',
    total_amount      DECIMAL(14,2) DEFAULT 0.00 COMMENT '交易总额',
    
    commission_rate   DECIMAL(6,4)  DEFAULT 0.00 COMMENT '平台佣金率',
    commission_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '平台佣金金额',
    
    fee_amount        DECIMAL(12,2) DEFAULT 0.00 COMMENT '通道手续费总额',
    refund_amount     DECIMAL(12,2) DEFAULT 0.00 COMMENT '退款抵扣总额',
    adjustment_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '调整金额（±）',
    net_amount        DECIMAL(14,2) DEFAULT 0.00 COMMENT '结算净额',
    
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '结算状态',
    audit_status      VARCHAR(16)   DEFAULT 'PENDING' COMMENT '审核状态',
    audit_by          VARCHAR(64)   DEFAULT NULL,
    audit_time        DATETIME      DEFAULT NULL,
    
    payout_no         VARCHAR(64)   DEFAULT NULL COMMENT '银行出款流水号',
    payout_time       DATETIME      DEFAULT NULL COMMENT '出款时间',
    payout_status     VARCHAR(16)   DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_settlement_no (settlement_no),
    KEY idx_merchant_period (merchant_id, period_start, period_end),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单主表';
```

```sql
-- 结算明细表
CREATE TABLE settlement_bill (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    bill_no           VARCHAR(32)   NOT NULL COMMENT '明细号',
    settlement_no     VARCHAR(32)   NOT NULL COMMENT '关联结算单号',
    order_no          VARCHAR(32)   NOT NULL COMMENT '订单号',
    
    bill_type         VARCHAR(16)   NOT NULL COMMENT '类型: PAYMENT/REFUND/FEE/ADJUSTMENT',
    amount            DECIMAL(12,2) NOT NULL COMMENT '金额',
    fee               DECIMAL(12,2) DEFAULT 0.00 COMMENT '手续费',
    net_amount        DECIMAL(12,2) DEFAULT 0.00 COMMENT '净额',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    KEY idx_settlement_no (settlement_no),
    KEY idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算明细表';
```

---

### 6. 对账与会计 (Reconciliation & Accounting)

#### 6.1 渠道账单对账流程

```
Cron 每日 02:00 触发
    │
    ▼
① 下载渠道账单
   ├── 支付宝: alipay.data.dataservice.bill.downloadurl.query
   └── 微信: 下载交易账单 API
    │
    ▼
② 解析账单 → 标准化为 BillRecord
    │
    ▼
③ 批量匹配（按 payment_request_no / order_no）
   ├── 完全匹配: 平台=渠道 ✓
   ├── 金额不一致: 标记 SHORT_PAY / EXCESS_PAY / FEE_MISMATCH
   ├── 平台有 渠道无: 标记 MISSING_IN_CHANNEL
   └── 渠道有 平台无: 标记 MISSING_IN_PLATFORM
    │
    ▼
④ 差异处理
   ├── 金额差异 ≤ ¥0.01: 自动通过（浮点精度差异）
   ├── SHORT_PAY: 查原因，渠道有退款但平台未同步 → 补充处理
   ├── EXCESS_PAY: 查原因，平台重复记录 → 冲正
   ├── FEE_MISMATCH: 对比费率配置，标记为已知差异或未知
   ├── MISSING_IN_CHANNEL: 平台订单未发往渠道 → 人工核查
   └── MISSING_IN_PLATFORM: 渠道回调丢失 → 补单
    │
    ▼
⑤ 生成对账报告
   ├── 统计: 总笔数 / 匹配数 / 差异数 / 自动修复数
   ├── 差异明细 -> 对账结果表 (reconciliation_bill)
   └── 告警: 匹配率 < 99.5% → P1 告警
```

#### 6.2 对账结果表

```sql
-- 对账结果表
CREATE TABLE reconciliation_bill (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    bill_date         DATE          NOT NULL COMMENT '对账日期',
    channel_code      VARCHAR(16)   NOT NULL COMMENT '渠道编码',
    
    order_no          VARCHAR(32)   DEFAULT NULL COMMENT '订单号',
    payment_no        VARCHAR(32)   DEFAULT NULL COMMENT '支付单号',
    transaction_id    VARCHAR(128)  DEFAULT NULL COMMENT '三方流水号',
    
    platform_amount   DECIMAL(12,2) DEFAULT 0.00 COMMENT '平台侧金额',
    channel_amount    DECIMAL(12,2) DEFAULT 0.00 COMMENT '渠道侧金额',
    diff_amount       DECIMAL(12,2) DEFAULT 0.00 COMMENT '差异金额',
    platform_fee      DECIMAL(12,2) DEFAULT 0.00 COMMENT '平台手续费',
    channel_fee       DECIMAL(12,2) DEFAULT 0.00 COMMENT '渠道手续费',
    fee_diff          DECIMAL(12,2) DEFAULT 0.00 COMMENT '手续费差异',
    
    match_status      VARCHAR(16)   NOT NULL COMMENT 'MATCHED/MISMATCH/SHORT/EXCESS/MISSING_PLATFORM/MISSING_CHANNEL',
    fix_status        VARCHAR(16)   DEFAULT 'PENDING' COMMENT '修复状态: PENDING/AUTO_FIXED/MANUAL/P1_ALERT',
    fix_action        VARCHAR(256)  DEFAULT NULL COMMENT '修复动作描述',
    fix_time          DATETIME      DEFAULT NULL COMMENT '修复时间',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    KEY idx_bill_date (bill_date, channel_code),
    KEY idx_match_status (match_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账结果表';
```

#### 6.3 会计分录模型

```sql
-- 会计分录表
CREATE TABLE account_journal (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    journal_no        VARCHAR(32)   NOT NULL COMMENT '凭证号，Snowflake 生成',
    
    order_no          VARCHAR(32)   DEFAULT NULL COMMENT '关联订单号',
    payment_no        VARCHAR(32)   DEFAULT NULL COMMENT '关联支付单号',
    settlement_no     VARCHAR(32)   DEFAULT NULL COMMENT '关联结算单号',
    
    account_code      VARCHAR(32)   NOT NULL COMMENT '会计科目编码',
    account_name      VARCHAR(64)   DEFAULT NULL COMMENT '科目名称',
    
    debit_amount      DECIMAL(14,2) DEFAULT 0.00 COMMENT '借方金额',
    credit_amount     DECIMAL(14,2) DEFAULT 0.00 COMMENT '贷方金额',
    
    biz_type          VARCHAR(32)   NOT NULL COMMENT '业务类型: PAYMENT/REFUND/SETTLEMENT/FEE',
    summary           VARCHAR(256)  DEFAULT NULL COMMENT '摘要',
    
    journal_time      DATETIME      NOT NULL COMMENT '入账时间',
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_journal_no (journal_no),
    KEY idx_order_no (order_no),
    KEY idx_biz_type (biz_type, journal_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计分录表';
```

**会计科目与分录规则**

| 业务场景 | 借方 | 贷方 | 说明 |
|---------|------|------|------|
| 支付成功 | 应收账款 (1122) | 主营业务收入 (6001) | 全额确认收入 |
| 支付成功 | 应收账款 (1122) | 应付手续费 (2241) | 通道手续费 |
| 退款 | 主营业务收入 (6001) | 应收账款 (1122) | 原路冲回 |
| 结算出款 | 应付账款 (2202) | 银行存款 (1002) | 商家结算出款 |
| 订阅 | 应付手续费 (2241) | 银行存款 (1002) | 支付通道费给渠道 |

---

### 7. 支付安全 (Security)

#### 7.1 密钥管理体系

```java
/**
 * 密钥管理器 — 符合 ADR-028 Secrets Management 规范
 * 
 * 密钥存储层级:
 *   Level 1: 渠道密钥 (payment_channel_config 表，AES 加密)
 *   Level 2: AES 加密密钥 (Nacos 加密配置, KMS 托管)
 *   Level 3: KMS 主密钥 (硬件安全模块 HSM 或云 KMS)
 */
@Component
public class PaymentKeyManager {
    
    /** 解密渠道私钥 */
    public String decryptPrivateKey(Long channelConfigId) {
        // 1. 从 DB 读取加密后的 private_key (AES-256-GCM)
        PaymentChannelConfig config = channelConfigMapper.selectById(channelConfigId);
        
        // 2. 从 Nacos 读取 AES 密钥 (加密配置)
        String aesKey = nacosConfig.getSecret("payment.aes.key");
        
        // 3. AES-GCM 解密
        return AESGCM.decrypt(config.getPrivateKey(), aesKey);
    }
    
    /** 签名（发起支付请求时使用） */
    public String sign(String content, String channelCode) {
        String privateKey = decryptPrivateKey(channelCode);
        return RSA256.sign(content, privateKey);
    }
}
```

#### 7.2 密钥配置

```yaml
# Nacos 加密配置 (符合 ADR-028)
payment:
  aes:
    key: "{cipher}AQICAHj7..."    # AES-256 密钥，KMS 加密
  channel:
    credentials:
      ALIPAY:
        app_id: "202100xxxx"
        public_key: "{cipher}AQIC..."  # 渠道公钥 (加密存储)
        private_key: "{cipher}AQIC..."  # 商户私钥 (加密存储)
```

#### 7.3 安全防护措施

| 威胁 | 防护措施 | 实现方式 |
|------|---------|---------|
| 重放攻击 | Nonce + Timestamp 校验 | Redis SETNX nonce, TTL 300s；Timestamp ±5min |
| 回调伪造 | 数字签名验证 | RSA256/HMAC-SHA256 验证回调签名 |
| 密钥泄露 | 分级加密存储 | DB 中 AES 加密 → Nacos 加密配置 → KMS 主密钥 |
| 敏感数据泄露 | 数据脱敏 | 支付单查询脱敏：6228****8888 |
| 参数篡改 | HMAC 请求签名 | Open API HMAC-SHA256 签名 (ADR-025) |
| 渠道回调 DDoS | IP 白名单 + 限流 | 渠道回调解析前验证来源 IP；Sentinel 限流 |

---

### 8. 非功能设计 (Non-Functional)

#### 8.1 SLA 目标

| 操作 | P99 目标 | P999 上限 | 超时熔断 | 吞吐量 |
|------|----------|-----------|---------|--------|
| 发起支付 charge | 500ms（含外部网关） | 1s | 3s | 2000 TPS |
| 查询支付 queryPayment | 50ms | 200ms | 500ms | 5000 TPS |
| 退款 refund | 3s（含外部网关） | 5s | 8s | 500 TPS |
| 支付回调 callback | 1s（含订单状态变更） | 3s | 5s | 3000 TPS |
| 结算单生成 settlement | 30s（批处理） | 60s | — | 每日 1 次 |
| 对账 reconciliation | 5min（批处理） | 15min | — | 每日 1 次 |

#### 8.2 Sentinel 阈值

```yaml
# Apollo 配置: payment.sentinel
payment:
  sentinel:
    charge:
      qps: 2000
      rt_ms: 1000
      degrade:
        min_request: 100           # 熔断最小请求数
        ratio: 0.2                 # 慢调用比例阈值 (20%)
        window_ms: 10000           # 熔断时间窗 (10s)
    refund:
      qps: 500
      rt_ms: 3000
      degrade:
        min_request: 50
        ratio: 0.3
        window_ms: 15000
    callback:
      qps: 3000
      rt_ms: 3000
      degrade:
        min_request: 200
        ratio: 0.25
        window_ms: 10000
```

#### 8.3 多级缓存策略

| 缓存 | 数据类型 | TTL | 容量 | 说明 |
|------|---------|-----|------|------|
| Caffeine L1 | payment_order 频繁查询 | 5s | 1000 entries | 查支付单列表/详情 |
| Redis L2 | payment_order 状态 | 30s | 5000 keys | 支付单查询加速 |
| 无缓存 | charge/refund 操作 | — | — | 涉及资金操作不走缓存 |

#### 8.4 容灾与一致性

- **支付单写入**: OceanBase 同城三副本同步（与 ADR-040 Dual-Active 一致）
- **回调处理**: event_outbox 同库事务 + RocketMQ 事务消息双保险
- **幂等**: payment_request_no 唯一索引 + IdempotentStore (ADR-030)
- **对账兜底**: T+1 渠道对账，发现不一致自动修复或 P1 告警
- **结算容灾**: 结算单失败可重跑（幂等：settlement_no 唯一约束）

---

### 9. API 设计

遵循 ADR-038 `ApiResult<T>` 规范，响应格式 `{ code, message, data, traceId, timestamp }`。

#### 9.1 Buyer API

| 方法 | 端点 | 说明 | 请求体 (JSON) |
|------|------|------|--------------|
| POST | `/api/v1/payments/charge` | 发起支付 | `{ orderNo, channelCode, returnUrl }` |
| GET | `/api/v1/payments/{paymentNo}` | 查询支付单 | — |
| POST | `/api/v1/payments/{paymentNo}/cancel` | 取消支付 | — |
| POST | `/api/v1/payments/{paymentNo}/refund` | 申请退款 | `{ amount, reason, reasonCode }` |
| GET | `/api/v1/payments/{paymentNo}/refunds` | 退款记录查询 | — |

#### 9.2 Backend API

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/backend/v1/payments/callback/{channel}` | 支付异步回调（三方直接调用） |
| POST | `/api/backend/v1/refunds/audit` | 退款审核 `{ refundNo, action(APPROVE/REJECT), remark }` |
| GET | `/api/backend/v1/refunds/pending` | 待审核退款单列表 |
| POST | `/api/backend/v1/payments/{paymentNo}/retry` | 手动重试支付单（超时后） |

#### 9.3 Admin API

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/admin/v1/settlements` | 结算单列表（分页，支持按商户/日期筛选） |
| GET | `/api/admin/v1/settlements/{settleNo}` | 结算单详情（含明细） |
| POST | `/api/admin/v1/settlements/{settleNo}/approve` | 审核通过结算单 |
| POST | `/api/admin/v1/settlements/{settleNo}/reject` | 驳回结算单 |
| POST | `/api/admin/v1/settlements/{settleNo}/payout` | 手动出款 |
| GET | `/api/admin/v1/reconciliation/daily` | 日对账报告 |
| GET | `/api/admin/v1/reconciliation/differences` | 对账差异明细 |
| POST | `/api/admin/v1/reconciliation/fix` | 手动修复对账差异 |
| GET | `/api/admin/v1/channels` | 支付渠道配置列表 |
| PUT | `/api/admin/v1/channels/{channelCode}` | 更新渠道配置 |

#### 9.4 Open API

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/open/v1/payments/charge` | ISV 发起支付（HMAC 签名） |
| GET | `/open/v1/payments/{paymentNo}` | ISV 查询支付 |
| POST | `/open/v1/refunds/apply` | ISV 发起退款 |
| GET | `/open/v1/settlements/merchant/{merchantId}` | 商家查询结算单 |

---

### 10. 事件定义

遵循 ADR-038 事件中心规范。

| 事件 | 说明 | 生产者 | 消费者 | 保序要求 |
|------|------|--------|--------|---------|
| `payment.charged` | 发起支付 | payment-core | order-core (状态机) | 按 paymentNo hash |
| `payment.success` | 支付成功 | payment-core | order-core, inventory, notification, user-center, settlement | 按 paymentNo hash |
| `payment.failed` | 支付失败 | payment-core | order-core | 按 paymentNo hash |
| `payment.refunded` | 退款完成 | refund-core | order-core, inventory, coupon, settlement | 按 refundNo hash |
| `payment.refund_failed` | 退款失败 | refund-core | order-core (告警) | 按 refundNo hash |
| `settlement.completed` | 结算完成 | settlement-core | accounting, notification | 按 merchantId hash |
| `settlement.payout` | 出款完成 | settlement-core | notification | 按 merchantId hash |

---

### 11. 实施计划

| 阶段 | 内容 | 产出 | 人天 |
|------|------|------|------|
| **Phase 1** | 支付 SPI 适配器接口 + Alipay/WeChat 实现 + 支付路由 + 签名 | `PaymentChannelAdapter`, 2 个 adapter 实现 | 2d |
| **Phase 2** | 支付单状态机 + 支付核心 CRUD + 幂等 + 支付超时 Job | `PaymentOrderService`, `PaymentTimeoutJob` | 2d |
| **Phase 3** | 退款引擎 + 审核规则配置 + 退款 Saga 集成 | `RefundService`, `RefundAuditRuleEngine`, XXL-Job | 1.5d |
| **Phase 4** | 结算中心 + 结算单生成 + 出款 + 结算审核 API | `SettlementService`, `SettlementCycleCalculator` | 1.5d |
| **Phase 5** | 渠道账单对账 + 匹配引擎 + 会计分录 + 对账报告 | `ReconciliationService`, `AccountingService` | 1.5d |
| **Phase 6** | 密钥管理 + Sentinel 阈值 + 多级缓存 + 容灾集成 | `PaymentKeyManager`, Sentinel 配置 | 1d |
| **Phase 7** | 事件集成 + ADR 交叉引用 + 4 层 API 实现 + 文档联动 | API 控制器, event 发布/消费, 更新现有文档 | 1.5d |

**总计：~11 人天**

---

### 12. 监控与告警

#### 12.1 核心指标

```prometheus
# 支付核心指标
payment_charge_total{channel="ALIPAY", status="success"}
payment_charge_total{channel="ALIPAY", status="failed"}
payment_charge_duration_seconds{channel="ALIPAY"}
payment_refund_total{channel="WECHAT", status="success"}
payment_refund_failed_total{reason="timeout"}

# 支付单状态分布
payment_order_status{status="INIT"}
payment_order_status{status="PROCESSING"}
payment_order_status{status="SUCCESS"}
payment_order_status{status="FAILED"}

# 结算指标
settlement_order_status{status="PENDING"}
settlement_payout_total{status="success"}

# 对账指标
reconciliation_match_rate{channel="ALIPAY"}    # 匹配率
reconciliation_pending_diff{channel="ALIPAY"}   # 待处理差异数
```

#### 12.2 告警规则

| 指标 | 条件 | 级别 | 响应 | 说明 |
|------|------|------|------|------|
| payment_charge | P99 > 1s 持续 5min | P1 | 值班群 @oncall | 支付超时，影响用户下单 |
| payment_callback | P99 > 3s 持续 5min | P1 | 值班群 @oncall | 回调处理延迟，支付状态不一致 |
| payment_refund_failed | rate > 10/min | P1 | 值班群 @oncall | 退款大面积失败 |
| settlement_payout | status=failed | P1 | 财务群 @finance | 商家出款失败，需人工处理 |
| reconciliation_match_rate | < 99.5% | P1 | 支付群 @oncall | 对账匹配率低于阈值 |
| payment_charge_qps | > 1800 持续 1min | P2 | 值班群 | 接近 Sentinel 阈值 2000 |
| refund_pending_audit | > 50 持续 30min | P2 | 客服群 | 退款待审核积压 |
| payment_balance | 结算账户余额 < ¥100,000 | P2 | 财务群 @finance | 出款账户余额不足 |

---

### 13. 上线检查清单

- [ ] 支付 SPI 接口定义完整，Alipay/WeChat 双适配器已实现
- [ ] 支付单状态机（6 态）全路径覆盖：正向支付 + 部分退 + 全额退
- [ ] payment_request_no 唯一索引 + IdempotentStore 幂等检查已实现
- [ ] 支付回调验签（RSA256）已通过安全审计
- [ ] 退款自动审核规则（SpEL）已配置到位
- [ ] 结算周期 T+1/T+7/T+30 Apollo 配置优先于代码硬编码
- [ ] 结算净额计算验证：交易总额 - 佣金 - 手续费 - 退款 = 净额
- [ ] 渠道对账：支付宝/微信日账单下载 + 自动匹配引擎已实现
- [ ] 会计分录借貸平衡验证：借 = 贷
- [ ] 支付安全：密钥 AES-256 加密存储、敏感数据脱敏、Nonce 防重放
- [ ] Sentinel 阈值配置与 ADR-040 对齐
- [ ] event_outbox 表已扩展 payment 事件类型支持
- [ ] Grafana 支付看板 + 告警规则已配置
- [ ] 对账矩阵（reconciliation-matrix.md）已同步更新

---

### 14. 与现有 ADR 的关联

| ADR | 关联内容 |
|-----|---------|
| **ADR-039** §2.3, §6 | PaymentProcessService → 替换为支付结算中心统一 API；退款引擎状态对齐订单 REFUNDING/REFUNDED 状态 |
| **ADR-020** §3 | Saga chargePayment/refund 步骤 → 调用 ADR-042 支付接口，补偿逻辑引用退款引擎 |
| **ADR-037** §4 | 5 个订单模板的 request_payment 步骤 → 统一引用 ADR-042 的支付 API 而非硬编码 |
| **ADR-038** §4 | payment.* 事件 + settlement-consumer → 支付结算中心作为事件生产者和消费者 |
| **ADR-030** §4 | payment-callback 幂等 → 统一到 IdempotentStore，PaymentRequestNo 唯一键 |
| **ADR-040** §3-4 | payment-core SLA (charge 500ms/refund 3s) + Sentinel 阈值保持一致 |
| **ADR-040** Part C | event_outbox payment 事件类型 + OB↔支付网关对账 → 扩展结算对账 |
| **ADR-036** §6 | 渠道订单支付状态同步 → 对接支付结算中心的支付查询接口 |
| **ADR-025** §3 | External Gateway HMAC 签名 + Open API 统一认证 → 支付 Open API 遵循相同规范 |
| **ADR-028** §2 | 密钥管理 → 支付渠道密钥 AES 加密 + KMS 托管，不存明文 |
| **ADR-027** §3 | SLI 指标扩展 → 增加支付场景的燃烧率告警（payment_charge_p99） |
| **ADR-010** | 事件 Schema 治理 → payment.* 事件 Schema 版本统一管理 |

---

### 15. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 三方支付网关不稳定 | 中 | 高 | 渠道自动切换（路由 → fallback）；异步对账兜底 |
| 退款资金不足 | 低 | 高 | 结算账户余额实时监控；退款前预检查余额 |
| 结算金额计算错误 | 低 | 高 | 每笔结算单 Aggregation SQL → 人工抽查 → 自动对账验证 |
| 密钥泄露 | 低 | 极高 | 分级加密 + KMS + 密钥轮换策略 + 审计日志 |
| 对账差异处理不当 | 中 | 中 | 差异分类自动修复 + 差异 > ¥100 自动 P1 告警不自动修复 |
| 支付回调丢失 | 中 | 高 | 渠道主动查单（Cron 30min）+ XXL-Job payment-reconciliation |
| 并发扣款 | 低 | 高 | payment_request_no 唯一索引 + 幂等乐观锁 |

---

### 16. 补充设计：分账与多商户支持

#### 16.1 分账场景

平台模式需支持多方分账：订单金额拆分为平台收入 + 多个商家收入。

```yaml
# Apollo 配置: payment.split.rules
payment:
  split:
    rules:
      - order_type: "NORMAL"
        receivers:
          - type: "PLATFORM"
            ratio: 0.05                    # 平台抽成 5%
          - type: "MERCHANT"
            ratio: 0.95                    # 商家 95%
      - order_type: "MARKETPLACE"
        receivers:
          - type: "PLATFORM"
            amount: 1.00                   # 平台固定 ¥1
          - type: "MERCHANT_A"
            ratio: 0.60
          - type: "MERCHANT_B"
            ratio: 0.40
```

#### 16.2 分账表结构

```sql
-- 分账明细表
CREATE TABLE payment_split_detail (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    split_no          VARCHAR(32)   NOT NULL COMMENT '分账单号',
    payment_no        VARCHAR(32)   NOT NULL COMMENT '关联支付单号',
    receiver_type     VARCHAR(16)   NOT NULL COMMENT 'PLATFORM/MERCHANT',
    receiver_id       BIGINT        NOT NULL COMMENT '接收方ID',
    amount            DECIMAL(12,2) NOT NULL COMMENT '分账金额',
    status            VARCHAR(16)   DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED',
    
    gmt_create        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    KEY idx_payment_no (payment_no),
    KEY idx_receiver (receiver_type, receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分账明细表';
```

---

> **结论**：本文档系统设计了支付结算中心，通过 5 层架构（支付网关 → 支付核心 → 退款引擎 → 结算中心 → 对账会计）完整覆盖正向支付、逆向退款、结算出款、对账差异处理的资金全链路。与现有 8+ ADR 交叉集成，弥补了平台最高优先级的 P0 功能缺口。

---

### 附录 A：事件 Schema 定义

| 事件 | Schema 版本 | payload 示例 |
|------|------------|-------------|
| `payment.success` | 1.0 | `{ "paymentNo":"PAY20260613001", "orderNo":"ORD20260613001", "amount":199.00, "channel":"ALIPAY", "transactionId":"20260613220010000001", "payTime":"2026-06-13T10:30:00" }` |
| `payment.refunded` | 1.0 | `{ "refundNo":"REF20260613001", "paymentNo":"PAY20260613001", "orderNo":"ORD20260613001", "amount":199.00, "reason":"商品质量问题" }` |
| `settlement.completed` | 1.0 | `{ "settlementNo":"SET20260613001", "merchantId":10001, "netAmount":9500.00, "periodStart":"2026-06-12", "periodEnd":"2026-06-12" }` |
