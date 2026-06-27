package com.omplatform.finance.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.finance.entity.PaymentOrder;
import com.omplatform.finance.entity.ReconciliationRecord;
import com.omplatform.finance.event.PaymentEventPublisher;
import com.omplatform.finance.mapper.PaymentOrderMapper;
import com.omplatform.finance.mapper.ReconciliationRecordMapper;
import com.omplatform.finance.payment.AlipayChannel;
import com.omplatform.finance.payment.WechatChannel;
import com.omplatform.finance.reconciliation.AlipayBillParser;
import com.omplatform.finance.reconciliation.BillDownloadService;
import com.omplatform.finance.reconciliation.BillFileParser;
import com.omplatform.finance.reconciliation.WechatBillParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付对账定时任务（ADR-043 §4）。
 * <p>
 * 功能一：在线补单 — 每 5 分钟扫描超时未回调支付单，主动查询渠道状态，防止掉单。
 * 功能二：隔日对账 — 每天凌晨下载渠道账单，与系统记录逐笔匹配，生成对账差异报告。
 */
@Slf4j
@Component
public class PaymentReconciliationJob {

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private ReconciliationRecordMapper reconciliationRecordMapper;

    @Autowired
    private AlipayChannel alipayChannel;

    @Autowired
    private WechatChannel wechatChannel;

    @Autowired
    private PaymentEventPublisher eventPublisher;

    @Autowired
    private BillDownloadService billDownloadService;

    @Autowired(required = false)
    private AlipayBillParser alipayBillParser;

    @Autowired(required = false)
    private WechatBillParser wechatBillParser;

    /** 正在处理的订单（单机防重） */
    private final Set<String> processingOrders = new HashSet<>();

    // ========================================================================
    // 功能一：在线补单（每 5 分钟）
    // ========================================================================

    @Scheduled(fixedRate = 300_000)
    public void reconcilePendingPayments() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        log.info("[补单] 开始扫描超时未回调支付单, deadline={}", deadline);

        try {
            List<PaymentOrder> pendingOrders = paymentOrderMapper.selectList(
                    Wrappers.<PaymentOrder>lambdaQuery()
                            .eq(PaymentOrder::getStatus, "PENDING")
                            .lt(PaymentOrder::getGmtCreate, deadline));

            if (pendingOrders.isEmpty()) {
                log.debug("[补单] 无超时支付单");
                return;
            }

            log.info("[补单] 发现 {} 笔超时支付单待处理", pendingOrders.size());

            for (PaymentOrder order : pendingOrders) {
                if (!processingOrders.add(order.getOrderNo())) {
                    log.debug("[补单] 订单正在处理中，跳过: orderNo={}", order.getOrderNo());
                    continue;
                }

                try {
                    processPendingOrder(order);
                } catch (Exception e) {
                    log.error("[补单] 处理异常: orderNo={}, err={}", order.getOrderNo(), e.getMessage(), e);
                } finally {
                    processingOrders.remove(order.getOrderNo());
                }
            }
        } catch (Exception e) {
            log.error("[补单] 扫描异常: {}", e.getMessage(), e);
        }
    }

    private void processPendingOrder(PaymentOrder order) {
        String channel = order.getChannel();
        if ("ALIPAY".equals(channel)) {
            processAlipayOrder(order);
        } else if ("WECHAT".equals(channel)) {
            processWechatOrder(order);
        } else {
            log.warn("[补单] 未知渠道: orderNo={}, channel={}", order.getOrderNo(), channel);
        }
    }

    private void processAlipayOrder(PaymentOrder order) {
        try {
            var resp = alipayChannel.queryTrade(order.getOrderNo());
            if (resp == null) return;

            String tradeStatus = resp.getTradeStatus();
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                String transactionId = resp.getTradeNo();
                BigDecimal amount = BigDecimal.ZERO;
                try { amount = new BigDecimal(resp.getReceiptAmount()); }
                catch (Exception ignored) {}

                log.info("[补单] 支付宝已支付，补发事件: orderNo={}", order.getOrderNo());
                eventPublisher.paymentSuccess(order.getOrderNo(), "ALIPAY", transactionId, amount);
                updatePaymentSuccess(order, transactionId, amount);
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                updatePaymentFailed(order);
            }
        } catch (Exception e) {
            log.error("[补单] 支付宝查询异常: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void processWechatOrder(PaymentOrder order) {
        try {
            String tradeState = wechatChannel.queryTradeState(order.getOrderNo());
            if ("SUCCESS".equals(tradeState)) {
                log.info("[补单] 微信已支付，补发事件: orderNo={}", order.getOrderNo());
                eventPublisher.paymentSuccess(order.getOrderNo(), "WECHAT", "", order.getAmount());
                updatePaymentSuccess(order, null, order.getAmount());
            } else if ("CLOSED".equals(tradeState) || "REVOKED".equals(tradeState)) {
                updatePaymentFailed(order);
            }
        } catch (Exception e) {
            log.error("[补单] 微信查询异常: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void updatePaymentSuccess(PaymentOrder order, String channelTradeNo, BigDecimal amount) {
        order.setStatus("SUCCESS");
        if (channelTradeNo != null) order.setChannelTradeNo(channelTradeNo);
        order.setPaidAt(LocalDateTime.now());
        order.setGmtModified(LocalDateTime.now());
        try { paymentOrderMapper.updateById(order); }
        catch (Exception e) { log.warn("[补单] 更新支付单异常: {}", e.getMessage()); }
    }

    private void updatePaymentFailed(PaymentOrder order) {
        order.setStatus("FAILED");
        order.setGmtModified(LocalDateTime.now());
        try { paymentOrderMapper.updateById(order); }
        catch (Exception ignored) {}
    }

    // ========================================================================
    // 功能二：隔日对账（每天凌晨 2:30 执行，T+1 对 T 日）
    // ========================================================================

    /**
     * 每日对账主入口。
     * <p>
     * 默认对 T-1 日（昨天）进行对账。
     * 分别处理支付宝和微信渠道。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void dailyReconciliation() {
        String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("[对账] 开始每日对账: date={}", date);

        try {
            // 支付宝对账
            reconcileChannel(date, "ALIPAY");
            // 微信对账
            reconcileChannel(date, "WECHAT");

            log.info("[对账] 完成: date={}", date);
        } catch (Exception e) {
            log.error("[对账] 异常: date={}, error={}", date, e.getMessage(), e);
        }
    }

    /**
     * 对单个渠道执行对账。
     *
     * @param date    对账日期 yyyy-MM-dd
     * @param channel 渠道 ALIPAY / WECHAT
     */
    public ReconciliationResult reconcileChannel(String date, String channel) {
        log.info("[对账] 开始渠道对账: date={}, channel={}", date, channel);

        // 1. 下载并解析渠道账单
        BillFileParser parser = getParser(channel);
        if (parser == null) {
            log.warn("[对账] {} 账单解析器不可用，跳过", channel);
            return new ReconciliationResult(date, channel, 0, 0, 0, 0, 0);
        }

        String billContent = billDownloadService.downloadBill(date, channel);
        if (billContent == null || billContent.isBlank()) {
            log.warn("[对账] {} 账单内容为空，跳过", channel);
            return new ReconciliationResult(date, channel, 0, 0, 0, 0, 0);
        }

        Iterable<BillFileParser.BillRecord> channelRecords = parser.parse(billContent);

        // 2. 查询系统支付记录（按日期 + 渠道 + SUCCESS）
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        List<PaymentOrder> systemPayments = paymentOrderMapper.selectList(
                Wrappers.<PaymentOrder>lambdaQuery()
                        .eq(PaymentOrder::getChannel, channel)
                        .eq(PaymentOrder::getStatus, "SUCCESS")
                        .between(PaymentOrder::getGmtCreate,
                                localDate.atStartOfDay(), localDate.plusDays(1).atStartOfDay()));

        log.info("[对账] 系统记录 {} 笔, 渠道记录待匹配", systemPayments.size());

        // 3. 建立索引
        Map<String, PaymentOrder> systemIndex = new HashMap<>();
        for (PaymentOrder p : systemPayments) {
            // 优先用 orderNo 索引，其次用 channelTradeNo
            if (p.getOrderNo() != null) systemIndex.put(p.getOrderNo(), p);
            if (p.getChannelTradeNo() != null && !p.getChannelTradeNo().isEmpty()) {
                systemIndex.put(p.getChannelTradeNo(), p);
            }
        }

        // 4. 逐笔匹配
        int matched = 0, mismatched = 0, channelOnly = 0;
        Set<String> matchedKeys = new HashSet<>();

        for (BillFileParser.BillRecord cr : channelRecords) {
            String key = cr.orderNo() != null ? cr.orderNo() : cr.channelTradeNo();
            if (key == null || key.isEmpty()) continue;

            PaymentOrder sp = systemIndex.get(key);
            if (sp == null) {
                // 尝试用另一字段匹配
                if (cr.orderNo() != null) sp = systemIndex.get(cr.channelTradeNo());
                if (sp == null && cr.channelTradeNo() != null) sp = systemIndex.get(cr.orderNo());
            }

            if (sp == null) {
                // 渠道有但系统无 → CHANNEL_ONLY
                saveReconciliationRecord(date, channel, cr.orderNo(), cr.channelTradeNo(),
                        null, cr.amount(), "CHANNEL_ONLY", cr.amount());
                channelOnly++;
                log.warn("[对账] 渠道有/系统无: orderNo={}, tradeNo={}, amount={}",
                        cr.orderNo(), cr.channelTradeNo(), cr.amount());
            } else {
                matchedKeys.add(key);
                // 比较金额（系统 vs 渠道），允许 0.01 的精度差异
                BigDecimal sysAmount = sp.getAmount() != null ? sp.getAmount() : BigDecimal.ZERO;
                BigDecimal chnAmount = cr.amount() != null ? cr.amount() : BigDecimal.ZERO;
                BigDecimal diff = sysAmount.subtract(chnAmount).abs();

                if (diff.compareTo(new BigDecimal("0.01")) <= 0) {
                    // 匹配
                    saveReconciliationRecord(date, channel, sp.getOrderNo(), cr.channelTradeNo(),
                            sysAmount, chnAmount, "MATCHED", BigDecimal.ZERO);
                    matched++;
                } else {
                    // 金额不一致
                    saveReconciliationRecord(date, channel, sp.getOrderNo(), cr.channelTradeNo(),
                            sysAmount, chnAmount, "MISMATCHED", diff);
                    mismatched++;
                    log.warn("[对账] 金额不一致: orderNo={}, sys={}, chn={}, diff={}",
                            sp.getOrderNo(), sysAmount, chnAmount, diff);
                }
            }
        }

        // 5. 系统有但渠道无 → SYSTEM_ONLY
        int systemOnly = 0;
        for (PaymentOrder sp : systemPayments) {
            String key = sp.getOrderNo();
            if (key != null && !matchedKeys.contains(key)
                    && sp.getChannelTradeNo() != null && !matchedKeys.contains(sp.getChannelTradeNo())) {
                saveReconciliationRecord(date, channel, sp.getOrderNo(), sp.getChannelTradeNo(),
                        sp.getAmount(), null, "SYSTEM_ONLY", sp.getAmount());
                systemOnly++;
                log.warn("[对账] 系统有/渠道无: orderNo={}, amount={}", sp.getOrderNo(), sp.getAmount());
            }
        }

        log.info("[对账] {} {} 完成: 匹配={}, 金额不一致={}, 系统独有={}, 渠道独有={}",
                channel, date, matched, mismatched, systemOnly, channelOnly);

        return new ReconciliationResult(date, channel, matched, mismatched, systemOnly, channelOnly,
                matched + mismatched + systemOnly + channelOnly);
    }

    // ========== 辅助方法 ==========

    private void saveReconciliationRecord(String date, String channel,
                                           String orderNo, String channelTradeNo,
                                           BigDecimal sysAmount, BigDecimal chnAmount,
                                           String status, BigDecimal difference) {
        try {
            ReconciliationRecord record = new ReconciliationRecord();
            record.setReconcileDate(date);
            record.setChannel(channel);
            record.setOrderNo(orderNo);
            record.setChannelTradeNo(channelTradeNo);
            record.setSystemAmount(sysAmount);
            record.setChannelAmount(chnAmount);
            record.setStatus(status);
            record.setDifference(difference);
            record.setResolved(false);
            record.setGmtCreate(LocalDateTime.now());
            record.setGmtModified(LocalDateTime.now());
            reconciliationRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[对账] 保存记录异常: orderNo={}, status={}, err={}", orderNo, status, e.getMessage());
        }
    }

    private BillFileParser getParser(String channel) {
        return switch (channel.toUpperCase()) {
            case "ALIPAY" -> alipayBillParser;
            case "WECHAT" -> wechatBillParser;
            default -> null;
        };
    }

    // ========== 查询方法 ==========

    /**
     * 查询指定日期的对账记录。
     */
    public List<ReconciliationRecord> getRecords(String date, String channel) {
        if (channel != null && !channel.isBlank()) {
            return reconciliationRecordMapper.selectList(
                    Wrappers.<ReconciliationRecord>lambdaQuery()
                            .eq(ReconciliationRecord::getReconcileDate, date)
                            .eq(ReconciliationRecord::getChannel, channel.toUpperCase())
                            .orderByAsc(ReconciliationRecord::getId));
        }
        return reconciliationRecordMapper.selectList(
                Wrappers.<ReconciliationRecord>lambdaQuery()
                        .eq(ReconciliationRecord::getReconcileDate, date)
                        .orderByAsc(ReconciliationRecord::getChannel)
                        .orderByAsc(ReconciliationRecord::getId));
    }

    /**
     * 查询所有未处理的差异记录。
     */
    public List<ReconciliationRecord> getUnresolvedDiscrepancies() {
        return reconciliationRecordMapper.selectList(
                Wrappers.<ReconciliationRecord>lambdaQuery()
                        .in(ReconciliationRecord::getStatus, "MISMATCHED", "SYSTEM_ONLY", "CHANNEL_ONLY")
                        .eq(ReconciliationRecord::getResolved, false)
                        .orderByDesc(ReconciliationRecord::getGmtCreate));
    }

    /**
     * 标记对账记录为已处理。
     */
    public void resolveRecord(Long id, String note) {
        ReconciliationRecord record = reconciliationRecordMapper.selectById(id);
        if (record != null) {
            record.setResolved(true);
            record.setResolveNote(note);
            record.setGmtModified(LocalDateTime.now());
            reconciliationRecordMapper.updateById(record);
            log.info("[对账] 标记已处理: id={}, status={}, note={}", id, record.getStatus(), note);
        }
    }

    // ========== DTO ==========

    /**
     * 对账结果汇总。
     */
    public record ReconciliationResult(
            String date,
            String channel,
            int matched,
            int mismatched,
            int systemOnly,
            int channelOnly,
            int total
    ) {}
}
