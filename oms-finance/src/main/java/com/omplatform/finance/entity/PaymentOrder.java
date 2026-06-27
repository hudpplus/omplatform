package com.omplatform.finance.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体。
 * <p>
 * 对应 DDL: payment_order
 * status 枚举: PENDING / SUCCESS / FAILED / REFUNDING / REFUNDED
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    @TableId
    private String paymentNo;

    private String orderNo;

    private String channel;

    private BigDecimal amount;

    private String status;

    private String channelTradeNo;

    private LocalDateTime paidAt;

    private String notifyRaw;

    @Version
    private Long version;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    @TableLogic
    private Integer deleted;
}
