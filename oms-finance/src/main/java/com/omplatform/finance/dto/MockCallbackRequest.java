package com.omplatform.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Mock 回调请求 DTO。
 * <p>
 * 用于开发环境下手动触发支付成功事件。
 */
public record MockCallbackRequest(
        @NotBlank(message = "orderNo 不能为空")
        String orderNo,

        @NotBlank(message = "channel 不能为空")
        String channel,

        @NotNull(message = "amount 不能为空")
        BigDecimal amount
) {}
