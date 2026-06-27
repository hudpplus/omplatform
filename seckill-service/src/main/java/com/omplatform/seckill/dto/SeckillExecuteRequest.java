package com.omplatform.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 秒杀执行请求。
 */
@Data
public class SeckillExecuteRequest {

    /** SKU ID */
    @NotBlank
    private String skuId;

    /** 数量（默认 1） */
    @NotNull
    @Positive
    private Integer quantity = 1;

    /**
     * 幂等请求 ID（客户端生成 UUID）。
     * <p>
     * 同一 requestId 多次提交仅首次生效，防止重复下单。
     */
    @NotBlank
    private String requestId;
}
