package com.omplatform.trade.service.atomic;

import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.omplatform.tcc.TccParticipantStateClient;

import java.time.LocalDateTime;

/**
 * 支付处理原子服务（ADR-039 §2.3）。
 * <p>
 * 转换：PENDING_PAY → PAID
 * 触发：三方支付回调
 * Saga 步骤名：confirmPayment
 */
@Slf4j
@Service
public class PaymentProcessService extends AbstractAtomicOrderService {

    @Autowired
    private TccParticipantStateClient tccClient;

    /* public PaymentProcessService(TccParticipantStateClient tccClient) {
        this.tccClient = tccClient;
    } */

    @Override
    protected void validate(String orderId, TransitionContext context) {
        log.debug("校验支付处理: orderId={}", orderId);
        // 1. 幂等校验（payment_transaction_id 唯一索引）
        // 2. 金额一致性校验
        // 3. 渠道可信校验（签名验证）
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.PAID;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        log.info("处理支付: orderId={}", orderId);

        // If this payment processing is part of a TCC flow, a txId may be present
        String txId = null;
        String participantId = null;
        if (context != null && context.getExtras() != null) {
            Object txObj = context.getExtras().get("txId");
            if (txObj instanceof String) txId = (String) txObj;
            Object partObj = context.getExtras().get("participantId");
            if (partObj instanceof String) participantId = (String) partObj;
        }

        if (txId != null) {
            if (participantId == null) participantId = "payment-service";
            try {
                String status = tccClient.getStatus(txId, participantId);
                if ("CONFIRMED".equals(status)) {
                    log.info("Payment already CONFIRMED txId={} participant={}", txId, participantId);
                    return; // idempotent
                }
            } catch (Exception ex) {
                log.warn("TCC client unavailable when processing payment, proceeding: {}", ex.getMessage());
            }
        }

        // 1. 写入 payment_record 表
        // 2. 确认预占库存（预占 → 扣减）
        // 3. 发送发货提醒（MQ 通知商家）

        if (txId != null) {
            try {
                tccClient.upsertStatus(txId, participantId, "CONFIRMED", orderId, LocalDateTime.now());
            } catch (Exception ex) {
                log.warn("Failed to write TCC participant CONFIRMED state txId={} participant={}, err={}", txId, participantId, ex.getMessage());
            }
        }
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus,
                                 TransitionContext context) {
        log.info("发布支付成功事件: orderId={}", orderId);
        // 发布 OrderPaidEvent → RocketMQ
    }
}
