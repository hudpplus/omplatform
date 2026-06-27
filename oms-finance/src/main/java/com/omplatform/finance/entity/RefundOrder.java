package com.omplatform.finance.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体。
 * <p>
 * 对应 DDL: refund_order
 * status 枚举: PENDING / SUCCESS / FAILED
 */
@Data
@TableName("refund_order")
public class RefundOrder {

    @TableId
    private String refundNo;

    private String orderNo;

    private String paymentNo;

    private String channel;

    private BigDecimal amount;

    private String reason;

    private String status;

    private String channelRefundNo;

    @Version
    private Long version;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    @TableLogic
    private Integer deleted;
}
