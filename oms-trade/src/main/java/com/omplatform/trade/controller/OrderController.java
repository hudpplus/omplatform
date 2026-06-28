package com.omplatform.trade.controller;

import com.omplatform.api.cart.CartService;
import com.omplatform.api.inventory.InventoryService;
import com.omplatform.api.order.OrderReadService;
import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.OrderQueryRequest;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.api.payment.PaymentService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.api.PageResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.event.OrderEventPublisher;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.omplatform.trade.saga.SagaContext;
import com.omplatform.trade.saga.SagaDefinition;
import com.omplatform.trade.saga.SagaExecutor;
import com.omplatform.trade.saga.SagaResult;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 买家订单 API（ADR-038）。
 * <p>
 * 通过 IGW 路由，JWT 认证由 Gateway 层完成。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private SagaExecutor sagaExecutor;
    @Autowired
    private SagaDefinition createOrderSaga;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private StateMachineEngine stateMachineEngine;
    @Autowired
    private OrderEventPublisher eventPublisher;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @DubboReference
    private CartService cartService;

    @DubboReference
    private InventoryService inventoryService;

    @DubboReference
    private PaymentService paymentService;

    @DubboReference
    private OrderReadService orderReadService;

    /* 构造器注入暂未启用（当前使用 @Autowired 字段注入，后续可迁移到此模式）
    public OrderController(SagaExecutor sagaExecutor,
                           SagaDefinition createOrderSaga,
                           OrderRepository orderRepository,
                           StateMachineEngine stateMachineEngine,
                           OrderEventPublisher eventPublisher,
                           CartService cartService,
                           InventoryService inventoryService,
                           PaymentService paymentService) {
        this.sagaExecutor = sagaExecutor;
        this.createOrderSaga = createOrderSaga;
        this.orderRepository = orderRepository;
        this.stateMachineEngine = stateMachineEngine;
        this.eventPublisher = eventPublisher;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
    }
    */

    /**
     * 创建订单（通过 Saga 编排）。
     * POST /api/v1/orders
     */
    @PostMapping
    public ApiResult<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("创建订单: buyerId={}, items={}", request.getBuyerId(), request.getItems().size());

        // 1. 构建 Saga 上下文
        String orderNo = generateOrderNo();
        SagaContext sagaContext = SagaContext.create(
                "saga-" + orderNo, "createOrder", orderNo);
        sagaContext.setStepArg("createRequest", request);

        // 2. 执行 Saga（步骤执行器由 CreateOrderSagaDefinition 在启动时注册）
        SagaResult result = sagaExecutor.execute(createOrderSaga, sagaContext);

        if (!result.isSuccess()) {
            log.error("下单失败: orderNo={}, step={}, error={}",
                    orderNo, result.getFailedStep(), result.getErrorMessage());
            return ApiResult.error("ORDER_FAILED", "下单失败: " + result.getErrorMessage());
        }

        // 3. 清理购物车（已下单商品）
        try {
            if (request.getBuyerId() != null) {
                cartService.clearCheckedItems(request.getBuyerId());
            }
        } catch (Exception e) {
            log.warn("清理购物车失败: {}", e.getMessage());
        }

        // 4. 返回订单信息
        OrderEntity entity = orderRepository.getById(orderNo);
        eventPublisher.orderCreated(orderNo, request.getBuyerId(),
                entity != null ? entity.getStatus() : OrderStatus.PENDING_PAY,
                request.getBusinessType() != null ? request.getBusinessType() : "ecommerce");
        return ApiResult.success(toDTO(entity));
    }

    /**
     * 查询订单列表（CQRS — 优先 ES，兜底 MySQL）。
     * GET /api/v1/orders?pageNo=1&pageSize=20&statusList=PAID&keyword=xxx
     */
    @GetMapping
    public ApiResult<PageResult<OrderDTO>> listOrders(OrderQueryRequest queryRequest) {
        return orderReadService.queryByBuyer(queryRequest);
    }

    /**
     * 查询订单详情。
     * GET /api/v1/orders/{orderNo}
     */
    @GetMapping("/{orderNo}")
    public ApiResult<OrderDTO> getOrder(@PathVariable String orderNo) {
        return orderReadService.getByOrderNo(orderNo);
    }

    /**
     * 取消订单。
     * POST /api/v1/orders/{orderNo}/cancel
     */
    @PostMapping("/{orderNo}/cancel")
    public ApiResult<Void> cancelOrder(@PathVariable String orderNo,
                                       @RequestParam(defaultValue = "买家取消") String reason) {
        log.info("取消订单: orderNo={}, reason={}", orderNo, reason);
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }

        // 1. 校验当前状态是否可取消
        OrderStatus current = entity.getStatus();
        if (!stateMachineEngine.canTransition(current, OrderStatus.CANCELLED)) {
            return ApiResult.error("STATUS_INVALID",
                    "当前状态不允许取消: " + current);
        }

        // 2. 状态机转换
        TransitionContext ctx = new TransitionContext();
        ctx.setOperatorId("BUYER");
        ctx.setReason(reason);
        stateMachineEngine.transition(orderNo, current, OrderStatus.CANCELLED, ctx);

        // 3. 如果已预占库存，释放预占（通过 Dubbo）
        if (current == OrderStatus.PENDING_PAY || current == OrderStatus.PAID) {
            try {
                inventoryService.releaseHold(orderNo);
            } catch (Exception e) {
                log.warn("释放库存失败(不影响取消): {}", e.getMessage());
            }
        }

        // 4. 如果已支付，发起退款
        if (current == OrderStatus.PAID && entity.getPayAmount() != null
                && entity.getPayAmount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                TransitionContextDTO dto = new TransitionContextDTO();
                dto.setOperatorId("SYSTEM");
                dto.setOperatorType("SYSTEM");
                dto.setSource("ORDER_CANCEL");
                paymentService.processRefund(orderNo, entity.getPayAmount(), "", dto);
            } catch (Exception e) {
                log.warn("退款处理失败(不影响取消状态): {}", e.getMessage());
            }
        }

        // 5. 发布事件
        eventPublisher.orderCancelled(orderNo, reason, entity.getBusinessType());
        return ApiResult.success();
    }

    /**
     * 确认收货。
     * POST /api/v1/orders/{orderNo}/confirm
     */
    @PostMapping("/{orderNo}/confirm")
    public ApiResult<Void> confirmReceipt(@PathVariable String orderNo) {
        log.info("确认收货: orderNo={}", orderNo);
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }
        if (entity.getStatus() != OrderStatus.SHIPPED) {
            return ApiResult.error("STATUS_INVALID",
                    "当前状态不允许确认收货: " + entity.getStatus());
        }

        TransitionContext ctx = TransitionContext.systemContext("买家确认收货");
        ctx.setOperatorId(entity.getBuyerId());
        stateMachineEngine.transition(orderNo, OrderStatus.SHIPPED, OrderStatus.DELIVERED, ctx);
        eventPublisher.orderCompleted(orderNo, entity.getBusinessType());
        log.info("确认收货成功: orderNo={}", orderNo);
        return ApiResult.success();
    }

    // ========== 辅助 ==========

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis();
    }

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
        dto.setSeckillActivityId(entity.getSeckillActivityId());
        dto.setSeckillPipeline(entity.getSeckillPipeline());

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
