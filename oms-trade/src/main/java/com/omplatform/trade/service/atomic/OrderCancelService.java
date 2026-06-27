package com.omplatform.trade.service.atomic;

import com.omplatform.api.inventory.InventoryService;
import com.omplatform.api.payment.PaymentService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 订单取消原子服务（ADR-039 §2.7）。
 * <p>
 * 转换：PENDING_PAY/PAID → CANCELLED
 * 触发：买家取消 / 商家取消 / 客服强制取消
 */
@Slf4j
@Service
public class OrderCancelService extends AbstractAtomicOrderService {

    @DubboReference
    private InventoryService inventoryService;

    @DubboReference
    private PaymentService paymentService;

    @Override
    protected void validate(String orderId, TransitionContext context) {
        log.debug("校验订单取消: orderId={}", orderId);
        OrderEntity entity = orderRepository.getById(orderId);
        if (entity == null) {
            throw new com.omplatform.common.exception.BizException("ORDER_NOT_FOUND", "订单不存在");
        }
        // PENDING_PAY → 买家直接取消
        // PAID → 需商家确认或客服介入（简化实现：也允许取消）
        // SHIPPED/DELIVERED → 不允许直接取消，应走售后流程
        OrderStatus status = entity.getStatus();
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new com.omplatform.common.exception.BizException("STATUS_INVALID",
                    "已发货订单不允许取消，请走售后流程");
        }
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.CANCELLED;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        log.info("取消订单: orderId={}", orderId);
        OrderEntity entity = orderRepository.getById(orderId);
        OrderStatus prevStatus = entity != null ? entity.getStatus() : null;

        // 1. 释放预占库存（Dubbo）
        try {
            inventoryService.releaseHold(orderId);
        } catch (Exception e) {
            log.warn("释放库存失败(不影响取消): {}", e.getMessage());
        }

        // 2. 如已支付 PAID，发起退款（Dubbo）
        if (prevStatus == OrderStatus.PAID && entity != null
                && entity.getPayAmount() != null
                && entity.getPayAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                ApiResult<String> refundResult = paymentService.processRefund(
                        orderId, entity.getPayAmount(), "",
                        new com.omplatform.api.order.dto.TransitionContextDTO());
                if (refundResult.isSuccess()) {
                    log.info("退款成功: orderNo={}, refundNo={}", orderId, refundResult.getData());
                }
            } catch (Exception e) {
                log.warn("退款失败(不影响取消状态): {}", e.getMessage());
            }
        }
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus,
                                 TransitionContext context) {
        log.info("发布订单取消事件: orderId={}", orderId);
        String reason = context != null ? context.getReason() : "取消订单";
        try {
            var map = new java.util.HashMap<String, Object>();
            map.put("orderId", orderId);
            map.put("status", newStatus != null ? newStatus.name() : "CANCELLED");
            map.put("reason", reason);
            map.put("cancelledAt", java.time.LocalDateTime.now().toString());
            String payload = objectMapper != null
                    ? objectMapper.writeValueAsString(map)
                    : "{\"orderId\":\"" + orderId + "\"}";
            writeOutbox("ORDER_CANCELLED", payload);
        } catch (Exception e) {
            log.warn("写入 OrderCancelled outbox 失败: {}", e.getMessage());
        }
    }
}
