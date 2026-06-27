package com.omplatform.finance.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.omplatform.common.api.ApiResult;
import com.omplatform.finance.dto.MockCallbackRequest;
import com.omplatform.finance.entity.PaymentOrder;
import com.omplatform.finance.event.PaymentEventPublisher;
import com.omplatform.finance.mapper.PaymentOrderMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Mock 支付回调端点（仅开发环境可用）。
 * <p>
 * 开发测试时无需支付宝/微信真实回调，直接调用此接口模拟支付成功。
 * 受 {@code @Profile("dev")} 保护，生产环境启动时不会加载。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@Profile("dev")
public class PaymentMockController {

    @Autowired
    private PaymentEventPublisher eventPublisher;

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    /**
     * 模拟支付成功回调。
     * <p>
     * 直接发布 PAYMENT_SUCCESS 事件，绕过支付宝/微信验签。
     * 幂等：同一 orderNo 重复调用只处理一次。
     */
    @PostMapping("/mock-callback")
    public ApiResult<Void> mockCallback(@RequestBody @Valid MockCallbackRequest request) {
        log.info("[Mock回调] 模拟支付成功: orderNo={}, channel={}, amount={}",
                request.orderNo(), request.channel(), request.amount());

        // 幂等检查
        long paidCount = paymentOrderMapper.selectCount(
                Wrappers.<PaymentOrder>lambdaQuery()
                        .eq(PaymentOrder::getOrderNo, request.orderNo())
                        .eq(PaymentOrder::getStatus, "SUCCESS"));
        if (paidCount > 0) {
            log.info("[Mock回调] 订单已支付成功，跳过重复处理: orderNo={}", request.orderNo());
            return ApiResult.success();
        }

        // 生成模拟渠道交易号
        String mockTxId = "MOCK_" + System.currentTimeMillis();

        // 发布支付成功事件（MQ → PaymentEventListener → orderService.processPayment）
        eventPublisher.paymentSuccess(
                request.orderNo(), request.channel(), mockTxId, request.amount());

        // 持久化支付单
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo("P" + System.currentTimeMillis());
        paymentOrder.setOrderNo(request.orderNo());
        paymentOrder.setChannel(request.channel());
        paymentOrder.setAmount(request.amount());
        paymentOrder.setStatus("SUCCESS");
        paymentOrder.setChannelTradeNo(mockTxId);
        paymentOrder.setPaidAt(LocalDateTime.now());
        paymentOrder.setGmtCreate(LocalDateTime.now());
        paymentOrder.setGmtModified(LocalDateTime.now());
        try {
            paymentOrderMapper.insert(paymentOrder);
        } catch (Exception e) {
            log.warn("[Mock回调] 支付单持久化失败(不影响事件): err={}", e.getMessage());
        }

        log.info("[Mock回调] 支付模拟完成: orderNo={}, mockTxId={}", request.orderNo(), mockTxId);
        return ApiResult.success();
    }
}
