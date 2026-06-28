package com.omplatform.trade.service;

import com.omplatform.api.order.OrderService;
import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.trade.sharding.BusinessContext;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.omplatform.trade.event.OrderEventPublisher;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import com.omplatform.trade.service.atomic.OrderCreateService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单 Dubbo 服务实现（ADR-038）。
 * <p>
 * 被 oms-finance（支付回调）、oms-fulfillment（发货通知）、oms-channel-adapter（渠道下单）调用。
 * 所有方法委托给状态机引擎 + 原子服务执行。
 */
@Slf4j
@DubboService
public class OrderDubboService implements OrderService {

    @Autowired
    private StateMachineEngine stateMachineEngine;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderCreateService orderCreateService;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderEventPublisher eventPublisher;

    /* public OrderDubboService(StateMachineEngine stateMachineEngine,
                             OrderRepository orderRepository,
                             OrderCreateService orderCreateService) {
        this.stateMachineEngine = stateMachineEngine;
        this.orderRepository = orderRepository;
        this.orderCreateService = orderCreateService;
    } */

    @Override
    @SentinelResource(value = "trade.createOrder",
            blockHandler = "createOrderBlock",
            blockHandlerClass = com.omplatform.trade.sentinel.OrderDubboBlockHandler.class)
    public ApiResult<OrderDTO> createOrder(CreateOrderRequest request, TransitionContextDTO context) {
        log.info("[Dubbo] 创建订单: buyerId={}", request.getBuyerId());
        // 设置业务线上下文（ADR-017 路由用）
        String businessType = request.getBusinessType() != null
                ? request.getBusinessType() : "ecommerce";
        BusinessContext.setAll(businessType, request.getBuyerId(), null);
        try {
            String orderNo = "ORD" + System.currentTimeMillis();
            TransitionContext tctx = TransitionContext.fromDTO(context);
            orderCreateService.execute(orderNo, tctx);
            return ApiResult.success(toDTO(orderRepository.getById(orderNo)));
        } finally {
            BusinessContext.clear();
        }
    }

    @Override
    @SentinelResource(value = "trade.processPayment",
            blockHandler = "processPaymentBlock",
            blockHandlerClass = com.omplatform.trade.sentinel.OrderDubboBlockHandler.class)
    public ApiResult<OrderDTO> processPayment(String orderNo, BigDecimal paidAmount,
                                               String payChannel, String transactionId,
                                               TransitionContextDTO context) {
        log.info("[Dubbo] 支付回调: orderNo={}, amount={}, channel={}, txId={}",
                orderNo, paidAmount, payChannel, transactionId);

        // 1. 查询订单
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        if (entity == null) {
            log.warn("订单不存在: orderNo={}", orderNo);
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }

        // 2. 幂等判断：已支付或终态直接返回成功
        if (entity.getStatus() == OrderStatus.PAID
                || entity.getStatus() == OrderStatus.COMPLETED
                || entity.getStatus() == OrderStatus.REFUNDED) {
            log.info("订单状态无需变更 [幂等跳过]: orderNo={}, status={}, paid={}, txId={}",
                    orderNo, entity.getStatus(), entity.getPayAmount(), entity.getTransactionId());
            return ApiResult.success(toDTO(entity));
        }

        // 3. 金额校验：回调金额不能小于订单总金额（允许商家手动改价等场景的差额）
        if (paidAmount.compareTo(entity.getTotalAmount()) < 0) {
            log.warn("支付金额异常（低于订单金额）: orderNo={}, paid={}, total={}",
                    orderNo, paidAmount, entity.getTotalAmount());
            // 不阻断，仅告警 — 可能是部分支付场景或渠道手续费差异
        }

        // 4. 执行状态转换 PENDING_PAY → PAID
        TransitionContext tctx = TransitionContext.fromDTO(context);
        try {
            stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.PAID, tctx);
        } catch (Exception e) {
            log.error("状态转换失败: orderNo={}, current={}, target=PAID, err={}",
                    orderNo, entity.getStatus(), e.getMessage());
            return ApiResult.error("TRANSITION_FAILED", "状态转换失败: " + e.getMessage());
        }

        // 5. 持久化支付信息
        entity.setPayAmount(paidAmount);
        entity.setPayChannel(payChannel);
        entity.setTransactionId(transactionId);
        entity.setStatusChangedAt(java.time.LocalDateTime.now());
        orderRepository.updateById(entity);

        // 6. 发布订单已支付事件（驱动下游履约：扣库存、券核销、ES 同步）
        eventPublisher.orderPaid(orderNo, entity.getBuyerId(), transactionId, entity.getBusinessType());

        log.info("支付处理完成: orderNo={}, paid={}, channel={}, txId={}",
                orderNo, paidAmount, payChannel, transactionId);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    @SentinelResource(value = "trade.shipOrder",
            blockHandler = "shipOrderBlock",
            blockHandlerClass = com.omplatform.trade.sentinel.OrderDubboBlockHandler.class)
    public ApiResult<OrderDTO> shipOrder(String orderNo, String logisticsCompany,
                                          String logisticsNo, TransitionContextDTO context) {
        log.info("[Dubbo] 发货: orderNo={}", orderNo);
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.SHIPPED, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> confirmReceipt(String orderNo, TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.DELIVERED, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    @SentinelResource(value = "trade.cancelOrder",
            blockHandler = "cancelOrderBlock",
            blockHandlerClass = com.omplatform.trade.sentinel.OrderDubboBlockHandler.class)
    public ApiResult<OrderDTO> cancelOrder(String orderNo, TransitionContextDTO context) {
        log.info("[Dubbo] 取消订单: orderNo={}", orderNo);
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.CANCELLED, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    @SentinelResource(value = "trade.refundOrder",
            blockHandler = "refundOrderBlock",
            blockHandlerClass = com.omplatform.trade.sentinel.OrderDubboBlockHandler.class)
    public ApiResult<OrderDTO> refundOrder(String orderNo, TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.REFUNDED, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> holdOrder(String orderNo, String reason,
                                          TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.HOLD, tctx);
        entity.setHoldReason(reason);
        orderRepository.updateById(entity);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> releaseHold(String orderNo, TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        // HOLD → 恢复为 previous_status
        OrderStatus restoreStatus = entity.getPreviousStatus();
        tctx.setOperatorId(context.getOperatorId());
        tctx.setReason("解除挂起");
        stateMachineEngine.transition(orderNo, OrderStatus.HOLD,
                restoreStatus != null ? restoreStatus : OrderStatus.PENDING_PAY, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> freezeOrder(String orderNo, String reason,
                                            TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.FROZEN, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> unfreezeOrder(String orderNo, TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        OrderStatus restoreStatus = entity.getPreviousStatus();
        stateMachineEngine.transition(orderNo, OrderStatus.FROZEN,
                restoreStatus != null ? restoreStatus : OrderStatus.PENDING_PAY, tctx);
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<OrderDTO> forceTransition(String orderNo, OrderStatus targetStatus,
                                                TransitionContextDTO context) {
        TransitionContext tctx = TransitionContext.fromDTO(context);
        tctx.setSkipGuards(true);
        OrderEntity entity = orderRepository.findByIdForUpdate(orderNo);
        stateMachineEngine.transition(orderNo, entity.getStatus(), targetStatus, tctx);
        return ApiResult.success(toDTO(entity));
    }

    // ========== 辅助 ==========

    private OrderDTO toDTO(OrderEntity entity) {
        if (entity == null) return null;
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(entity.getOrderNo());
        dto.setBuyerId(entity.getBuyerId());
        dto.setShopId(entity.getShopId());
        dto.setStatus(entity.getStatus());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setPayAmount(entity.getPayAmount());
        dto.setFreightAmount(entity.getFreightAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setRemark(entity.getRemark());
        dto.setStatusChangedAt(entity.getStatusChangedAt());
        dto.setStatusExpiresAt(entity.getStatusExpiresAt());

        // 加载订单商品行
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItemEntity> iw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            iw.eq(OrderItemEntity::getOrderNo, entity.getOrderNo());
            List<OrderItemEntity> items = orderItemMapper.selectList(iw);
            if (items != null && !items.isEmpty()) {
                dto.setItems(items.stream().map(this::toItemDTO).toList());
            }
        } catch (Exception e) {
            log.warn("加载订单商品行失败: orderNo={}, {}", entity.getOrderNo(), e.getMessage());
        }

        return dto;
    }

    private OrderDTO.OrderItemDTO toItemDTO(OrderItemEntity item) {
        OrderDTO.OrderItemDTO dto = new OrderDTO.OrderItemDTO();
        dto.setItemId(String.valueOf(item.getItemId()));
        dto.setSkuId(item.getSkuId());
        dto.setSkuName(item.getSkuName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getTotalAmount());
        dto.setDiscountAmount(item.getDiscountAmount());
        return dto;
    }
}
