# 数据对账矩阵

> 本文档基于 [ADR-040](./adr/ADR-040-high-performance-high-availability.md) Part C §4 和 [ADR-042](./adr/ADR-042-payment-settlement-center.md) §6 定义，列出各数据源之间的定期对账策略、工具和修复动作。由 XXL-Job 定时调度。

---

## 对账总览

| # | 对账对 | 频率 | 调度方式 | 对账维度 | 自动修复 | 告警级别 | 负责团队 |
|---|--------|------|---------|---------|---------|---------|---------|
| 1 | OB ↔ ES (订单数据) | 每日 02:00 | XXL-Job | 文档数 / 关键字段 | ✅ 自动重建 ES index | P3 | 订单 |
| 2 | OB ↔ Redis 热缓存 | 每小时 | XXL-Job | 买家列表 / 详情字段 | ✅ 自动回填 Redis | P3 | 订单 |
| 3 | OB ↔ 支付网关 | 每 15min | XXL-Job | 支付单状态 / 金额 | ⚠️ 自动 + 人工 | P1 | 支付 |
| 4 | OB ↔ 渠道 (Tmall/JD) | 每 30min | XXL-Job | 订单状态 / 物流单号 | ⚠️ 自动补推 | P2 | 履约 |
| 5 | Saga 实例 ↔ 订单状态 | 每 5min | XXL-Job SagaRecoveryJob | 一致性 / 卡住实例 | ✅ 自动推进 | P1 | 订单 |
| 6 | 退款状态 ↔ 订单状态 | 每 30min | XXL-Job RefundReconcileJob | 退款单完成 / 订单态 | ✅ 自动推进 | P1 | 退款 |
| 7 | event_outbox ↔ RocketMQ | 每 5s | event_outbox delivery job | 未投递事件 | ✅ 自动投递 | P1 (超 max retries) | 订单 |
| 8 | 库存预扣 ↔ 订单状态 | 每 5min | XXL-Job | 预扣库存 vs 已支付订单 | ✅ 自动释放超时预扣 | P2 | 库存 |
| 9 | 优惠券发放 ↔ 订单状态 | 每 30min | XXL-Job | 已取消订单的优惠券归还 | ✅ 自动归还 | P2 | 营销 |
| 10 | 结算数据 ↔ 支付单 (内部) | 每日 04:00 | XXL-Job SettlementReconJob | 结算金额 / 交易笔数 | ✅ 自动修正 | P1 | 支付 |
| 11 | 结算出款 ↔ 银行回执 | 每日 06:00 | XXL-Job | 出款状态 / 到账金额 | ⚠️ 自动 + 人工 | P1 | 支付 |
| 12 | Redis 库存 ↔ DB 库存量 | 每 30min | XXL-Job stock-recon-job | available / reserved / deducted 三量对比 | ✅ 自动以 Redis 为准同步到 DB | P2 | 库存 |
| 13 | 渠道库存 ↔ 平台内部库存 | 每 1h | XXL-Job channel-stock-recon-job | 渠道库存同步状态 | ⚠️ 自动 + 人工 | P2 | 库存 |
| 14 | ~~成员积分 ↔ 积分流水~~ | ~~每日 03:00~~ | ~~XXL-Job points-recon-job~~ | ~~余额 vs 流水 sum~~ | ✅ 自动修正 | P2 | 会员 | (ADR-046)
| 15 | 优惠券实例 ↔ 订单状态 | 每 30min | XXL-Job coupon-recon-job | LOCKED/USED 实例 vs 订单态 | ✅ 自动释放异常锁券 | P2 | 营销 (ADR-045) |
| 16 | 风控检查 ↔ 审核记录 | 每 1h | XXL-Job risk-recon-job | PRE_CHECK+REVIEW 记录完整性 | ⚠️ 自动 + 人工 | P2 | 风控 (ADR-047) |
| 17 | 售后单 ↔ 退款记录 | 每 30min | XXL-Job aftersale-recon-job | 售后单 COMPLETED 但 payment 无退款记录 | ✅ 自动补发退款 | P1 | 售后 (ADR-048) |
| 18 | 售后服务 ↔ 质检记录 | 每 1h | XXL-Job aftersale-inspect-recon-job | INSPECTING 超过 48h 无质检结果 | ⚠️ P2 告警，催办仓库 | P2 | 售后 (ADR-048) |
| 19 | 换货新订单 ↔ 原售后单 | 每 1h | XXL-Job exchange-recon-job | 新订单已发货但原售后单未 REFUNDED | ✅ 自动推进 | P2 | 售后 (ADR-048) |

---

## 详细对账定义

### 1. OB ↔ ES 每日对账

```yaml
name: ob-es-daily-reconciliation
schedule: "0 2 * * ?"           # 每日 02:00
executor: data-consistency-job  # 复用 ADR-014 §12 的对账任务

scope: 
  - 当天有变更的订单（gmt_modified >= today）
  - 按 buyer_id 分片，每批 1000 条

check-fields:
  - order_no (唯一标识)
  - order_status (状态一致性)
  - pay_amount (金额)
  - gmt_create (创建时间)
  
inconsistency-action:
  - type: AUTO_REBUILD
    action: 以 OB 为准重建 ES 文档
    alert: P3 (日报汇总)

metrics:
  - reconciliation.ob_es.total_docs
  - reconciliation.ob_es.consistent_ratio
  - reconciliation.ob_es.auto_fixed
```

### 2. OB ↔ Redis 热缓存每小时对账

```yaml
name: ob-redis-hourly-reconciliation
schedule: "0 0-59/5 * * * ?"    # 每 5 分钟（增量）
executor: hot-cache-checker

scope:
  - 上次检查后有变更的 buyer_id
  - 从 event_outbox 或 binlog 追踪变更

check:
  - 比较 ES(order) 与 Redis(hot:order:list:{buyer}) 的首条摘要
  - 如果 status/payAmount 不一致 → 重建该 buyer 的缓存

inconsistency-action:
  - type: AUTO_REBUILD_KEY
    action: 从 ES 拉取最新数据覆盖写入 Redis
    alert: P3 (累计超过 100 条不一致时)
```

### 3. OB ↔ 支付网关对账

```yaml
name: payment-reconciliation
schedule: "0 */15 * * * ?"       # 每 15 分钟
executor: payment-recon-job

scope:
  - 30 分钟前创建的、状态为 PENDING 的支付单
  - 最近 1h 内状态变化的支付单

check:
  - 查 order.payment_id → 查 payment.payment_no → 查网关交易记录
  - 对比: 支付金额 / 支付状态 / 完成时间

inconsistency-action:
  - type: GATEWAY_QUERY
    action: 调用支付网关查询接口确认真实状态
    auto-fix: 
      - 网关成功但 payment 表 PENDING → 推进为 SUCCESS
      - 网关失败但 payment 表 SUCCESS → P1 人工
    manual:
      - 不一致需人工确认 → P1 群 @oncall
      - 提供：订单号 + 网关流水号 + 金额差异
```

### 4. Saga 实例对账

```yaml
name: saga-instance-reconciliation
schedule: "0 */5 * * * ?"        # 每 5 分钟
executor: saga-recovery-job      # ADR-020 SagaRecoveryJob

scope:
  - saga_instance.status IN (INIT, EXECUTING, COMPENSATING)
  - saga_instance.gmt_create > NOW() - 72h

check:
  - 比较 saga_instance 定义步骤 vs 实际 saga_step_log 记录
  - 识别: 卡住步骤 / 补偿失败 / 超时无更新

inconsistency-action:
  - type: AUTO_RETRY
    retry: 3x (1s, 5s, 30s)
    on-fail: 
      - 告警 P1
      - 写入 DLQ
  - staleness-threshold: 30min  # 超过 30 分钟无更新视为卡住
```

### 5. 退款状态对账

```yaml
name: refund-status-reconciliation
schedule: "0 */30 * * * ?"       # 每 30 分钟
executor: refund-reconcile-job   # ADR-039 RefundReconcileJob

scope:
  - order.status IN (REFUNDING, RETURNING)
  - order.status_expires_at < NOW()

check:
  - 对每个 REFUNDING/RETURNING 订单查询支付网关退款接口
  - 对比: 网关退款状态 vs 订单状态

inconsistency-action:
  - gateway SUCCESS:
      action: transition(order, REFUNDED) 自动完成
  - gateway FAILED:
      action: P1 告警 + 标记人工处理
  - gateway PENDING:
      action: 跳过（下次再检）
```

### 6. 库存预扣对账

```yaml
name: inventory-hold-reconciliation
schedule: "0 */5 * * * ?"
executor: hold-release-job        # ADR-039 HoldReleaseJob

scope:
  - order.status = PAID 但超过 24h 未发货
  - 预扣库存表 (inventory_hold) 中存在但订单已取消

check:
  - 比较: inventory_hold.hold_qty vs order.status
  - 如果订单已取消但预扣未释放 → 自动释放

inconsistency-action:
  - order CANCELLED + hold 存在:
      action: 自动释放预扣库存
  - PAID > 24h + hold 存在:
      action: P2 告警（催促发货）
```

### 7. event_outbox 对账

```yaml
name: event-outbox-delivery
schedule: "0/5 * * * * ?"         # 每 5 秒
executor: event-outbox-job        # ADR-040 Part C §1.4

scope:
  - event_outbox.status = PENDING
  - event_outbox.next_retry_at <= NOW()
  - event_outbox.retry_count < max_retries

check:
  - 尝试投递到 RocketMQ（幂等：event_id 作为 keys）
  
action:
  - SEND_OK → mark as SENT
  - FAILED → retry_count+1, 指数退避 next_retry_at
  - ```

### 10. 结算数据与支付单对账

```yaml
name: settlement-data-reconciliation
schedule: "0 4 * * ?"           # 每日 04:00
executor: settlement-recon-job

scope:
  - 昨日产生的结算单（settlement_order.period_end = 昨天）
  - 结算周期内涉及的所有 payment_order

check:
  - 结算单 transaction_count = 周期内支付成功订单数 - 退款订单数
  - 结算单 net_amount = SUM(payment_order.amount) - SUM(refund_order.amount) - SUM(fee)

inconsistency-action:
  - type: AUTO_RECALCULATE
    action: 重新计算结算净额并更新结算单
    threshold: 差异 <= 0.01 自动修正 > 0.01 P1 告警
    alert: P1
```

### 11. 结算出款与银行回执对账

```yaml
name: settlement-payout-reconciliation
schedule: "0 6 * * ?"           # 每日 06:00
executor: payout-recon-job

scope:
  - 昨日已出款的结算单（status = TRANSFERRED）
  - 银行回执文件（从银行 FTP 下载）

check:
  - settlement_order.payout_no vs 银行回执交易流水号
  - 出款金额一致, 到账状态确认

inconsistency-action:
  - type: BANK_QUERY
    action: 调用银行交易查询接口
    auto-fix:
      - 银行成功但系统 PENDING: 推进为 SUCCESS
      - 银行失败但系统 SUCCESS: P1 告警 + 人工
    manual:
      - 金额不一致: P1 群 @finance
```

### 12. Redis 库存与 DB 库存对账

```yaml
name: redis-db-stock-reconciliation
schedule: "0 */30 * * * ?"       # 每 30 分钟
executor: stock-recon-job

scope:
  - 所有有变更的 stock_item 记录（gmt_modified > last_run）
  - Redis 中存在的库存 key（stock:{sku}:available / reserved / deducted）

check:
  - 对比: Redis available_qty vs DB stock_item.available_qty
  - 对比: Redis reserved_qty vs DB stock_item.reserved_qty
  - 对比: Redis deducted_qty vs DB stock_item.deducted_qty
  - 汇总: total = available + reserved + deducted 校验一致性

inconsistency-action:
  - type: AUTO_SYNC
    priority: Redis > DB  # 以 Redis 为准（Redis 是实时数据源）
    action: 以 Redis 值为基准覆盖 DB 对应字段
    threshold: 差异 <= 5 自动修正，> 5 P2 告警
    alert: P2
    auto-fix:
      - UPDATE stock_item SET available_qty = redis_value WHERE sku_id = ?
```

### 13. 渠道库存与平台内部库存对账

```yaml
name: channel-stock-reconciliation
schedule: "0 */60 * * * ?"      # 每 1 小时
executor: channel-stock-recon-job

scope:
  - channel_stock_config.sync_enabled = 1 的渠道-SKU 组合
  - 渠道侧库存 API 返回的可用量

check:
  - 对比: 平台 stock_item.available_qty vs 渠道侧可用库存
  - 对比: 平台 channel 同步记录 vs 渠道侧同步确认

inconsistency-action:
  - type: CHANNEL_SYNC
    action: 调用渠道库存同步 API 推送平台库存
    auto-fix:
      - 差异 <= 10: 自动同步平台库存覆盖渠道库存
      - 差异 > 10: P2 告警 + 人工确认
    manual:
      - 渠道侧库存被外部修改: P2 告警 + 标记人工核查
    alert: P2
```

### 14. 支付单与渠道账单对账（ADR-042 §6.1）

```yaml
name: channel-bill-reconciliation
schedule: "0 2 * * ?"           # 每日 02:00
executor: channel-bill-recon-job

scope:
  - 支付宝/微信 T+1 日账单文件
  - 平台所有支付单（日期范围匹配）

check:
  - 逐笔匹配: payment_request_no / order_no vs 账单交易号
  - 汇总对比: 总金额 / 总手续费 / 总笔数

inconsistency-action:
  - type: AUTO_MATCH
    auto-fix:
      - 金额差异 <= 0.01: 自动通过（浮点精度）
      - SHORT_PAY: 渠道退款未同步 -> 补数据
      - EXCESS_PAY: 回调丢失 -> 补单
      - FEE_MISMATCH: 对比费率配置
    manual:
      - MISSING_IN_CHANNEL: 人工核查
      - MISSING_IN_PLATFORM: 补单
      - 差异 > 100: P1 告警不自动修复
```

---



### 14. 会员积分 ↔ 积分流水每日对账（ADR-046）

```yaml
name: member-points-reconciliation
schedule: "0 3 * * ?"           # 每日 03:00
executor: points-recon-job

scope:
  - 所有有积分变动的会员（昨日 gmt_modified）
  - 按 user_id 分片，每批 1000 条

check:
  - points_account.balance == sum(points_transaction.points where type in (EARN,SPEND,EXPIRE,ADJUST))
  - points_account.total_earned == sum(EARN points)
  - points_account.total_spent == sum(SPEND points)

inconsistency-action:
  - type: AUTO_FIX
    action: 以流水为准重算余额
    alert: P2
```

### 15. 优惠券实例 ↔ 订单状态对账（ADR-045）

```yaml
name: coupon-instance-reconciliation
schedule: "0/30 * * * ?"        # 每 30min
executor: coupon-recon-job

scope:
  - status=LOCKED 且 locked_at > 24h 的券实例（可能未正常释放）
  - status=USED 且对应订单已取消/退款的券实例（需回退）

check:
  - LOCKED coupons → 对应订单是否仍处于 PENDING_PAY/PAID（有效状态）
    - 若订单已 CANCELLED/CLOSED → 自动释放
  - USED coupons → 对应订单是否已退款
    - 若已退款但券未 REFUNDED → 触发回退

inconsistency-action:
  - type: AUTO_RELEASE
    auto-fix:
      - 订单取消但券仍 LOCKED → 自动 release_coupon（幂等）
      - 订单已退款但券仍 USED → 自动 rollback_coupon（幂等）
    alert: P2 (无法自动修复时)
```

### 16. 风控检查 ↔ 审核记录对账（ADR-047）

```yaml
name: risk-check-reconciliation
schedule: "0 * * * ?"           # 每 1h
executor: risk-recon-job

scope:
  - 昨日所有 risk_check_record + risk_review_record
  - 检查决策为 REVIEW 的订单必须有对应的 review_record

check:
  - 每笔 REVIEW 的 check_record 必须有一条 review_record（PENDING/APPROVED/REJECTED）
  - review_record.status=REJECTED 的订单状态必须为 HOLD/CANCELLED
  - review_record.status=APPROVED 的订单状态不能为 HOLD

inconsistency-action:
  - type: AUTO + MANUAL
    auto-fix:
      - 有 review_record 但订单未 HOLD → 触发 HOLD
      - 审核通过但订单仍 HOLD → 触发 RELEASE
    manual:
      - 缺少 review_record → 补发审核队列消息
      - 状态矛盾（已 APPROVED 但订单已 CANCELLED）→ 风控团队核查
    alert: P2
```

---

## 相关 ADR

| 对账任务 | ADR 来源 |
|---------|---------|
| OB ↔ ES | ADR-014 §12 数据一致性兜底 |
| OB ↔ Redis | ADR-014 §12 + ADR-040 Part A §5 |
| OB ↔ 支付网关 | ADR-040 Part C §4.2 (新增) |
| Saga 实例 | ADR-020 §5 SagaRecoveryJob |
| 退款状态 | ADR-039 Part C |
| 库存预扣 | ADR-039 Part C HoldReleaseJob |
| event_outbox | ADR-040 Part C §1.4 (新增) |
| 结算数据 ↔ 支付单 | ADR-042 §6.2 (新增) |
| 结算出款 ↔ 银行 | ADR-042 §6.2 (新增) |
| 支付单 ↔ 渠道账单 | ADR-042 §6.1 (新增) |
| Redis 库存 ↔ DB 库存 | ADR-043 §14.3 (新增) |
| 渠道库存 ↔ 平台库存 | ADR-043 §14.3 (新增) |
| 会员积分 ↔ 积分流水 | ADR-046 §11（积分体系） |
| 优惠券实例 ↔ 订单状态 | ADR-045 §7（优惠券生命周期） |
| 风控检查 ↔ 审核记录 | ADR-047 §6（数据模型） |
| 售后单 ↔ 退款记录 | ADR-048 §11（对账矩阵） |
| 售后服务 ↔ 质检记录 | ADR-048 §11（对账矩阵） |
| 换货新订单 ↔ 原售后单 | ADR-048 §11（对账矩阵） |
