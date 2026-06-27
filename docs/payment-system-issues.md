# 支付系统常见问题与防护清单

> 基于 oms-finance 支付中心现状梳理。
>
> 更新日期：2026-06-16
> 关联 ADR：[ADR-042 Payment](adr/ADR-042-payment.md)（待创建）

---

## 目录

1. [🔴 资金安全类 (P0)](#1-资金安全类-p0)
2. [🟠 资金差异类 (P1)](#2-资金差异类-p1)
3. [🟡 工程类 (P2)](#3-工程类-p2)
4. [现状总览表](#4-现状总览表)
5. [改进路线图](#5-改进路线图)

---

## 1. 资金安全类 (P0)

资金安全问题是支付系统的红线——出一次就可能造成直接经济损失。

### 1.1 重复支付

**问题**

同一个订单用户付了两次款。原因通常是：
- 用户连续点了两次"支付"按钮
- 前端未做防重复提交
- 支付回调网络超时后渠道重试
- MQ 重复投递

**影响**：订单被标记为 PAID 两次 → 多发一次货 → 平台垫付资金损失

**我们项目的防护**

| 防护层 | 状态 | 说明 | 位置 |
|--------|------|------|------|
| 订单状态机 | ✅ | `PENDING_PAY → PAID` 单向转换，已 PAID 状态拒绝重复回调 | `oms-trade` 状态机 |
| MQ 去重 | ✅ | RocketMQ 至少一次投递，`PaymentSuccessListener` 内 Dubbo 调用需幂等 | `PaymentEventListener.java:41` |
| 唯一索引 | ⚠️ | 需确认 `order_payment` 表是否有 `order_no` 唯一约束 | — |
| 渠道幂等键 | ❌ **缺失** | 未向支付宝/微信传入幂等键 | — |

**改进方案**

```
1. 支付发起时生成 idempotentKey（如 sagaId），传给三方渠道
2. 支付宝：在 out_biz_no 字段传入（AlipayTradePagePayModel.setOutBizNo()）
3. 微信：在 PrepayRequest 的 attach 字段传入，用于回调时去重
4. 本地 payment_record 表增加 channel_idempotent_key 唯一约束
```

### 1.2 回调伪造 / 验签漏洞

**问题**

攻击者伪造支付成功通知 POST 到回调接口，如果验签有漏洞，订单被虚假标记为已支付。

**影响**：未收到钱就发货 → 直接经济损失

**我们项目的防护**

| 防护层 | 状态 | 说明 | 位置 |
|--------|------|------|------|
| 支付宝 RSA2 验签 | ✅ | `AlipaySignature.rsaCheckV1()` | `AlipayChannel.java:140` |
| 微信 SDK 验签 | ✅ | `NotificationParser.parse()` | `WechatChannel.java:182` |
| 交易状态检查 | ✅ | 支付宝检查 `TRADE_SUCCESS` / `TRADE_FINISHED` | `AlipayChannel.java:166` |
| 回调 IP 白名单 | ❌ **缺失** | 未限制回调来源 IP | — |

**支付宝/微信官方回调 IP 范围**
- 支付宝：`110.75.0.0/16`，`121.0.0.0/8` 等（需定期从开放平台获取）
- 微信：`58.247.0.0/16`，`180.166.0.0/16` 等（需定期获取最新列表）

**改进方案**

```
1. 增加 IP 白名单过滤（仅允许支付宝/微信官方 IP 段访问回调接口）
2. 支付宝 RSA2 验签必须确认使用的是支付宝公钥，不是应用公钥
3. 微信回调必须校验 Wechatpay-Signature 等全部四个 header 非空
4. 所有验签失败记录告警（可能有人在试探攻击）
```

### 1.3 掉单（回调丢失）

**问题**

用户在支付渠道侧已扣款成功，但回调请求因网络抖动、服务重启等原因未到达，订单始终处于 `PENDING_PAY` 状态。

**影响**：用户付了钱收不到货 → 客诉 → 退款处理成本

**原因分析**

| 原因 | 概率 | 说明 |
|------|------|------|
| HTTP 回调超时 | 高 | 渠道发送 POST 后等待响应超时，重试但可能还没到 |
| 服务重启窗口 | 中 | 服务正在重启/部署，回调到达时端口未监听 |
| MQ 消费积压 | 低 | RocketMQ 宕机或消费能力跟不上 |
| 回调 URL 配置错误 | 低 | `notify_url` 写错（但上线前应已验证） |

**我们项目的防护**：**无。这是当前最大的缺口**

```
▸ 没有定时补单 Job
▸ 当回调丢失时，只能靠用户投诉被动发现
▸ 没有资金对账机制来发现差异
```

**改进方案**

```java
// XXL-Job 每 5 分钟执行
// 定位：状态 = PENDING_PAY 且 创建时间 > 30 分钟
// 动作：调支付宝 AlipayTradeQuery / 微信查询订单接口
// 发现渠道已支付 → 手动补单
@XxlJob("paymentReconciliationJob")
public void reconcile() {
    List<Order> pendingOrders = orderMapper.selectPendingPayOvertime();
    for (Order order : pendingOrders) {
        // 单机防重
        if (!tryLock("reconcile:" + order.getOrderNo())) continue;
        try {
            // 调用渠道查询
            String channel = order.getPayChannel(); // ALIPAY / WECHAT
            if (channel.equals("ALIPAY")) {
                AlipayTradeQueryResponse resp = alipayClient.execute(
                    new AlipayTradeQueryRequest(order.getOrderNo()));
                if ("TRADE_SUCCESS".equals(resp.getTradeStatus())) {
                    eventPublisher.paymentSuccess(
                        order.getOrderNo(), "ALIPAY",
                        resp.getTradeNo(), new BigDecimal(resp.getReceiptAmount()));
                }
            }
            // 微信类似...
        } finally {
            unlock("reconcile:" + order.getOrderNo());
        }
    }
}
```

**设计要点**

| 要点 | 说明 |
|------|------|
| 触发时间 | 订单创建后 30 分钟仍未收到回调 |
| 查询频率 | 每 5 分钟一次，持续 24 小时 |
| 防重 | 分布式锁，同一订单不并发 |
| 幂等 | `orderService.processPayment()` 必须幂等 |
| 告警 | 超过 24 小时仍未收到回调 → 人工介入 |

---

## 2. 资金差异类 (P1)

资金差异不会造成直接资金损失，但会积压大量未对平资金，月结对账时才发现问题。

### 2.1 金额不一致

**问题**

| 场景 | 说明 |
|------|------|
| 支付宝回调中有两套金额 | `total_amount`（用户付的）vs `receipt_amount`（平台实收，已扣手续费） |
| 微信金额是分 | `amount.total = 9900` 表示 99.00 元，需除以 100 |
| 优惠/折扣导致实付 ≠ 订单金额 | 用户下单 100 元，用了优惠券实付 80 元 |

**我们项目的防护**

```java
// AlipayChannel.java:155 — 优先用 receipt_amount，fallback 到 total_amount
String amountStr = params.getOrDefault("receipt_amount",
                      params.getOrDefault("total_amount", "0"));
```

**问题**：当前的逻辑把实收金额和实付金额混用了，应该分层存储。

**改进方案**

建立三层金额模型，全部持久化：

| 层级 | 字段 | 含义 | 来源 |
|------|------|------|------|
| 订单层 | `order_amount` | 下单金额 | `oms-trade` |
| 支付层 | `paid_amount` | 用户实际支付金额 | 回调 `total_amount` |
| 资金层 | `receipt_amount` | 平台实际到账金额（已扣手续费） | 回调 `receipt_amount` |

支付回调处理时三笔金额全部记录到 `payment_record` 表，后续对账以 `receipt_amount` 为准。

### 2.2 退款超付

**问题**

```
用户下单 100 元 → 支付成功
  → 申请退款 100 元 → AftersaleService 调 refund()
  → 退单号: REF20260616001
  → 支付宝退款 API 响应超时（但实际退款已成功）
  → 售后系统认为失败 → 再退一次 → 退了 200 元
```

**防护机制**

| 防护 | 说明 |
|------|------|
| 退款单号幂等 | 支付宝/微信承诺：同一个 `out_refund_no` 重复请求不重复退款 |
| 我们当前状态 | ⚠️ `outRefundNo = "REF" + orderNo` → 同一订单多次退款冲突 |

**我们项目的问题**：`WechatChannel.java:129`
```java
request.setOutRefundNo("REF" + request.orderNo());
```
如果同一订单分多次退款（先退 50，再退 30），第二次的 `outRefundNo` 与第一次相同 → 微信会返回第一次的退款结果，导致第二次退款实际上未发起。

**改进方案**

```
outRefundNo 规则: "REF" + orderNo + "_" + sequence
sequence 来源：
  - 数据库自增序列（从 aftersale 表获取）
  - 或 Redis INCR: "refund_seq:" + orderNo
  - 或时间戳毫秒: System.currentTimeMillis()
```

### 2.3 渠道手续费不一致

**问题**

支付渠道按不同费率收取手续费（支付宝通常 0.6%、微信 0.6%），但：
- 手续费不足 0.01 元时四舍五入导致舍入偏差
- 部分行业/场景有优惠费率
- 大客户有阶梯费率

**影响**：月结对账时出现小额差异（几角到十几元），累计起来对账要花大量时间排查。

**改进方案**

手续费计算统一到 `settlement` 模块，从 `receipt_amount` 反推，不依赖渠道回传。

---

## 3. 工程类 (P2)

### 3.1 回调解耦与性能

**现状**

我们的回调处理架构是正确的——回调 controller 只做验签 + 发 MQ，不做 DB 写入：

```
PaymentCallbackController
  → 验签（10ms）
  → 发 MQ（5ms）
  → 返回 "success" 给渠道（总计 < 50ms）
  ↓ 异步
PaymentEventListener → Dubbo → OrderService → DB
```

**常见陷阱**

| 陷阱 | 后果 |
|------|------|
| 回调里调了耗时 Dubbo 接口 | HTTP 响应延迟 → 渠道超时重试 → 重复回调 |
| 回调里写了 DB | 数据库抖动拖垮回调接收 → 大量超时 |
| 回调里抛了异常没 catch | 返回非 "success" → 渠道重试 → 重复回调 |
| MQ 消费失败无死信队列 | 回调永远丢失 |

**改进方案**

```
1. 回调 controller 全部 try-catch 包裹，永不抛异常
2. 增加 MQ 死信队列 + 人工处理面板
3. 监控回调响应时间 > 1s → 告警
```

### 3.2 测试环境回调不通

**问题**

开发环境 `localhost:8082` 无法接收支付宝/微信的 HTTP 回调。

**解决方式**

| 方式 | 说明 | 适用场景 |
|------|------|---------|
| 内网穿透 | ngrok / natapp 映射公网域名 | 开发联调 |
| 支付宝沙箱 | 沙箱环境有独立的回调地址 | 功能测试 |
| Mock 回调端点 | 本地手动触发支付成功 | 全流程测试 |
| **我们项目** | **以上三种都缺失** | — |

**改进方案**

增加 Mock 回调端点（供开发和测试使用）：

```java
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentMockController {

    @PostMapping("/mock-callback")
    public ApiResult<Void> mockCallback(@RequestBody MockCallbackRequest request) {
        // 1. 校验订单存在且为 PENDING_PAY
        // 2. 直接发布 PAYMENT_SUCCESS 事件
        // 3. 绕过渠道验签，仅用于开发测试
        eventPublisher.paymentSuccess(
            request.getOrderNo(),
            request.getChannel(),
            "MOCK_" + System.currentTimeMillis(),
            request.getAmount());
        return ApiResult.success();
    }
}
```

**安全约束**：Mock 端点只允许在开发/测试环境启用（通过 `@Profile("dev")` 控制）。

### 3.3 证书与密钥管理

**我们项目的密钥**

| 密钥 | 来源 | 到期风险 |
|------|------|---------|
| 支付宝应用私钥 | 自己生成 | 永不到期，但可以重新生成 |
| 支付宝公钥 | 支付宝开放平台下载 | 永不到期，但支付宝可以更换 |
| 微信商户 API 私钥 | 自己生成 | 永不到期 |
| 微信 API V3 密钥 | 自己设置 | 永不到期，但建议定期更换 |
| 微信平台证书 | SDK 自动管理 | ⚠️ 自动续期（`RSAAutoCertificateConfig` 内置） |

**常见问题**

```
1. 支付宝应用公钥上传错误
   → 配成了支付宝公钥而非应用公钥
   → 自己的应用私钥与上传的公钥不匹配
   → 结果：下单成功但验签永远失败

2. 支付宝公钥配置错误
   → 把应用公钥填到了 alipayPublicKey 字段
   → 结果：自签名的回调也能验签通过 → 安全漏洞

3. 更换密钥忘记更新配置
   → 老密钥被删除 → 支付全部失败
   → 需要配置中心热更新（Nacos）
```

**改进方案**

```
1. 密钥配置上线前做双向验证：
   - 用配置的私钥发起一笔 0.01 元支付
   - 用配置的公钥验签回调通知
   - 任一失败则阻止上线

2. 增加密钥到期切换告警（XXL-Job 每天检查证书有效期）

3. 支付宝公钥 / 应用私钥存储到 Nacos 配置中心
   - 支持热更新
   - 多环境隔离（dev/staging/prod）
```

### 3.4 数据库性能

**问题**

`PENDING_PAY` 订单随时间积累，全表扫描查询未支付订单会越来越慢。

**缓解措施**

```
1. 索引：CREATE INDEX idx_order_status_ctime ON order(status, created_at);
   （当前项目如有已存在则跳过）

2. 分表：按时间或按 user_id 分表
   （ShardingSphere 已在依赖中，但未配置分片规则）

3. 归档：超过 30 天的已支付订单迁移到历史表
```

### 3.5 日志与链路追踪

**支付链路过长，排查问题困难**

一个支付请求经过的路径：
```
前端 → Nginx → IGW → oms-trade (OrderController)
  → Dubbo → oms-finance (FinanceDubboService)
    → AlipayChannel.charge() → 支付宝 API
  ← 返回支付 URL
前端跳转支付宝 → 用户支付
  → 支付宝回调 → PaymentCallbackController
    → 验签 → MQ → PaymentEventListener
      → Dubbo → oms-trade (OrderService.processPayment)
        → DB 更新 → 完成
```

涉及 **4 个服务**、**2 次 Dubbo 调用**、**1 次 MQ**。

**问题**：如果中间某步失败，没有统一的 TraceId 串联全程，排查需要逐个服务翻日志。

**改进方案**

```
1. 支付发起时生成 traceId（可用 sagaId）
2. 通过 Dubbo 的 RpcContext / MQ header 透传
3. 所有日志输出 traceId（MDC.put("traceId", traceId)）
4. 对接 SkyWalking 或 Zipkin（依赖已有，需配置）
```

---

## 4. 现状总览表

| # | 问题 | 严重级别 | 当前状态 | 优先级 |
|---|------|---------|---------|--------|
| 1.1 | 重复支付 | 🔴 P0 | ⚠️ 有基础防护，缺幂等键 | 高 |
| 1.2 | 回调伪造/验签漏洞 | 🔴 P0 | ✅ 已防护 | — |
| 1.3 | **掉单**（回调丢失） | 🔴 P0 | ❌ **缺失定时补单** | **最高** |
| 2.1 | 金额不一致 | 🟠 P1 | ⚠️ 需分层存储 | 中 |
| 2.2 | 退款超付 | 🟠 P1 | ⚠️ outRefundNo 冲突隐患 | 高 |
| 2.3 | 手续费差异 | 🟠 P1 | ❌ 未处理 | 低 |
| 3.1 | 回调解耦/性能 | 🟡 P2 | ✅ 架构正确 | — |
| 3.2 | 测试回调不通 | 🟡 P2 | ❌ **缺 Mock 端点** | 中 |
| 3.3 | 证书密钥管理 | 🟡 P2 | ⚠️ 缺少检查/监控 | 中 |
| 3.4 | 数据库性能 | 🟡 P2 | ⚠️ 需确认索引 | 低 |
| 3.5 | 链路追踪 | 🟡 P2 | ❌ 未接入 | 低 |

---

## 5. 改进路线图

### Phase 1：资金安全（P0 修复）

```
1. 定时补单 Job (XXL-Job)
   └─ 扫 PENDING_PAY 超时订单 → 调渠道查询 → 补发事件
   └─ 预估：2 人天

2. 幂等键（渠道侧去重）
   └─ 支付宝 out_biz_no / 微信 attach
   └─ 预估：1 人天

3. 退款单号生成修复
   └─ outRefundNo + sequence
   └─ 预估：0.5 人天
```

### Phase 2：测试与可观测性

```
4. Mock 回调端点
   └─ @Profile("dev") 控制，仅开发环境可用
   └─ 预估：1 人天

5. TraceId 链路追踪
   └─ 支付全链路透传 traceId
   └─ 预估：1 人天
```

### Phase 3：对账与结算

```
6. 三层金额模型（order_amount / paid_amount / receipt_amount）
7. 日对账单 Job + 渠道对账
8. 手续费计算模块
```

---

## 附录 A：相关代码位置

| 文件 | 功能 |
|------|------|
| `AlipayChannel.java` | 支付宝支付渠道实现 |
| `WechatChannel.java` | 微信支付渠道实现 |
| `PaymentChannelManager.java` | 支付渠道 SPI 管理器 |
| `PaymentCallbackController.java` | 外部回调 HTTP 端点 |
| `PaymentEventPublisher.java` | 支付事件发布器（MQ） |
| `PaymentEventListener.java` | 支付事件监听器（MQ → Dubbo） |
| `FinanceDubboService.java` | 支付中心 Dubbo 服务实现 |
| `PaymentProperties.java` | 支付配置（密钥、回调 URL 等） |
| `AftersaleService.java` | 售后服务（退款入口） |
| `SettlementService.java` | 结算对账服务 |

## 附录 B：参考文档

- [支付宝异步通知文档](https://opendocs.alipay.com/open/203/105286)
- [支付宝公钥证书文档](https://opendocs.alipay.com/common/02morm)
- [微信支付回调通知文档](https://pay.weixin.qq.com/wiki/doc/apiv3/wechatpay/wechatpay4_1.shtml)
- [微信支付证书管理文档](https://pay.weixin.qq.com/wiki/doc/apiv3/wechatpay/wechatpay2_0.shtml)
