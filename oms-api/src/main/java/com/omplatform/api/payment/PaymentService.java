package com.omplatform.api.payment;

import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;

import java.math.BigDecimal;

/**
 * 支付服务接口（Dubbo，由 oms-finance 实现）。
 */
public interface PaymentService {

    /**
     * 发起支付请求。
     *
     * @param orderNo    订单号
     * @param amount     支付金额
     * @param payChannel 支付渠道
     * @param context    转换上下文
     * @return 支付跳转 URL / 支付参数
     */
    ApiResult<String> requestPayment(String orderNo, BigDecimal amount,
                                      String payChannel, TransitionContextDTO context);

    /**
     * 发起退款。
     */
    ApiResult<String> processRefund(String orderNo, BigDecimal amount,
                                     String transactionId, TransitionContextDTO context);

    /**
     * 查询退款状态。
     */
    ApiResult<String> queryRefundStatus(String orderNo, String transactionId);

    /**
     * 支付回调通知处理（由 IGW/Ext GW 转发三方回调）。
     */
    ApiResult<Void> handlePaymentCallback(String orderNo, String channel,
                                           String callbackParamsJson);
}
