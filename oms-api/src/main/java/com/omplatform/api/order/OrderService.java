package com.omplatform.api.order;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.OrderQueryRequest;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.api.PageResult;

import java.util.List;

/**
 * 订单写服务（Dubbo 接口）。
 * <p>
 * 定义订单核心生命周期操作，由 oms-trade 实现。
 * 所有接口均支持幂等（Idempotency-Key 由调用方传入 RpcContext attachment）。
 */
public interface OrderService {

    /**
     * 创建订单。
     *
     * @param request 创建请求
     * @param context 转换上下文
     * @return 订单 DTO
     */
    ApiResult<OrderDTO> createOrder(CreateOrderRequest request, TransitionContextDTO context);

    /**
     * 支付回调处理（PENDING_PAY → PAID）。
     *
     * @param orderNo   订单号
     * @param paidAmount 实付金额
     * @param payChannel 支付渠道
     * @param transactionId 三方交易号
     * @param context   转换上下文
     * @return 订单 DTO
     */
    ApiResult<OrderDTO> processPayment(String orderNo, java.math.BigDecimal paidAmount,
                                        String payChannel, String transactionId,
                                        TransitionContextDTO context);

    /**
     * 确认发货（PAID/TO_SHIP → SHIPPED）。
     */
    ApiResult<OrderDTO> shipOrder(String orderNo, String logisticsCompany,
                                   String logisticsNo, TransitionContextDTO context);

    /**
     * 确认收货（SHIPPED → DELIVERED）。
     */
    ApiResult<OrderDTO> confirmReceipt(String orderNo, TransitionContextDTO context);

    /**
     * 取消订单（PENDING_PAY/PAID → CANCELLED）。
     */
    ApiResult<OrderDTO> cancelOrder(String orderNo, TransitionContextDTO context);

    /**
     * 退款处理（REFUNDING → REFUNDED）。
     */
    ApiResult<OrderDTO> refundOrder(String orderNo, TransitionContextDTO context);

    /**
     * 挂起订单（→ HOLD）。
     */
    ApiResult<OrderDTO> holdOrder(String orderNo, String reason, TransitionContextDTO context);

    /**
     * 解除挂起（HOLD → PENDING_PAY/PAID）。
     */
    ApiResult<OrderDTO> releaseHold(String orderNo, TransitionContextDTO context);

    /**
     * 冻结订单（→ FROZEN）。
     */
    ApiResult<OrderDTO> freezeOrder(String orderNo, String reason, TransitionContextDTO context);

    /**
     * 解冻订单（FROZEN → 原状态）。
     */
    ApiResult<OrderDTO> unfreezeOrder(String orderNo, TransitionContextDTO context);

    /**
     * 强制状态转换（仅超级管理员，跳过守卫）。
     */
    ApiResult<OrderDTO> forceTransition(String orderNo,
                                         com.omplatform.common.constant.OrderStatus targetStatus,
                                         TransitionContextDTO context);
}
