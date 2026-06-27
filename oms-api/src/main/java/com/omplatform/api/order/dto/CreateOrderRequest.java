package com.omplatform.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 买家 ID */
    @NotBlank
    private String buyerId;

    /** 店铺 ID */
    @NotBlank
    private String shopId;

    /** 业务线标识（ecommerce / locallife / b2b）- ADR-017 路由 */
    private String businessType;

    /** 收货地址 ID */
    @NotBlank
    private String addressId;

    /** 备注 */
    private String remark;

    /** 订单项列表 */
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    /** 优惠券实例 ID（可选） */
    private String couponInstanceId;

    /** 渠道来源：APP / MINI_PROGRAM / WEB / CHANNEL */
    @NotBlank
    private String channelSource;

    /** 外部业务 ID（渠道订单 ID 用于幂等） */
    private String outBizId;

    /** 秒杀活动 ID（非空时表示该订单来自秒杀） */
    private Long seckillActivityId;

    /** 秒杀价（秒杀订单覆盖营销计价） */
    private BigDecimal seckillPrice;

    /** 秒杀批次 */
    private String seckillPipeline;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** SKU ID */
        @NotBlank
        private String skuId;

        /** 数量 */
        @NotNull
        @Positive
        private Integer quantity;

        /** 单价（前端传入，后台校验） */
        @NotNull
        @Positive
        private BigDecimal unitPrice;
    }
}
