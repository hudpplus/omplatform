package com.omplatform.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单实体（ADR-043 结算模型）。
 * <p>
 * 对应 DDL: settlement_order
 * status 枚举: PENDING / SETTLED / FAILED
 */
@Data
@TableName("settlement_order")
public class SettlementOrder {

    @TableId(type = IdType.INPUT)
    private String settleNo;

    /** 订单号 */
    private String orderNo;

    /** 店铺 ID */
    private String shopId;

    /** 结算金额 */
    private BigDecimal amount;

    /** 平台佣金 */
    private BigDecimal commission;

    /** 佣金比例（如 0.006 = 0.6%） */
    private BigDecimal commissionRate;

    /** PENDING / SETTLED / FAILED */
    private String status;

    /** 结算时间 */
    private LocalDateTime settleAt;

    @Version
    private Long version;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    @TableLogic
    private Integer deleted;
}
