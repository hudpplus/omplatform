package com.omplatform.api.order.dto;

import com.omplatform.common.constant.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单聚合 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String parentOrderNo;     // 父订单号（子订单场景）
    private String buyerId;
    private String shopId;
    private OrderStatus status;
    private OrderStatus previousStatus;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private String addressId;
    private String remark;
    private String holdReason;
    private String frozenReason;
    private LocalDateTime statusChangedAt;
    private LocalDateTime statusExpiresAt;
    private Long version;

    /** 秒杀活动 ID */
    private Long seckillActivityId;

    /** 秒杀批次 */
    private String seckillPipeline;

    private List<OrderItemDTO> items;
    private PaymentInfoDTO paymentInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String itemId;
        private String skuId;
        private String skuName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private BigDecimal discountAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfoDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String paymentId;
        private String payChannel;
        private BigDecimal paidAmount;
        private LocalDateTime paidAt;
        private String transactionId;
    }
}
