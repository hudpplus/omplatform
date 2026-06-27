package com.omplatform.finance.aftersale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后服务（ADR-048）。
 * <p>
 * 处理退款、退货、换货申请。
 */
@Slf4j
@Service
public class AftersaleService {

    /**
     * 创建售后单。
     *
     * @param request 售后请求
     * @return 售后单号
     */
    public String createAftersale(CreateAftersaleRequest request) {
        log.info("创建售后单: orderNo={}, type={}, reason={}",
                request.orderNo, request.aftersaleType, request.reason);
        // 1. 校验订单状态
        // 2. 创建 aftersale_order 记录
        // 3. 触发状态机：ORDER → REFUNDING/RETURNING
        return "AS" + System.currentTimeMillis();
    }

    /**
     * 审核售后单。
     */
    public void approve(String aftersaleNo, String reviewer, boolean approved, String comment) {
        log.info("审核售后单: no={}, approved={}, reviewer={}", aftersaleNo, approved, reviewer);
    }

    /**
     * 执行退款（审核通过后调用）。
     */
    public void processRefund(String aftersaleNo, BigDecimal amount, String transactionId) {
        log.info("执行退款: no={}, amount={}", aftersaleNo, amount);
        // 调用 PaymentChannelManager.processRefund()
    }

    // ========== DTO ==========

    public record CreateAftersaleRequest(
            String orderNo,
            String buyerId,
            AftersaleType aftersaleType,
            String reason,
            String skuId,
            int quantity,
            BigDecimal refundAmount,
            String evidenceImages
    ) {}

    public enum AftersaleType {
        REFUND_ONLY,    // 仅退款
        RETURN_REFUND,  // 退货退款
        EXCHANGE        // 换货
    }
}
