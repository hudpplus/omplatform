package com.omplatform.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账记录实体（ADR-043 对账模型）。
 * <p>
 * 对应 DDL: reconciliation_record
 * status 枚举: MATCHED / MISMATCHED / SYSTEM_ONLY / CHANNEL_ONLY
 */
@Data
@TableName("reconciliation_record")
public class ReconciliationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对账日期 yyyy-MM-dd */
    private String reconcileDate;

    /** 渠道 ALIPAY / WECHAT */
    private String channel;

    /** 系统订单号 */
    private String orderNo;

    /** 渠道交易号 */
    private String channelTradeNo;

    /** 系统侧金额 */
    private BigDecimal systemAmount;

    /** 渠道侧金额 */
    private BigDecimal channelAmount;

    /** MATCHED / MISMATCHED / SYSTEM_ONLY / CHANNEL_ONLY */
    private String status;

    /** 差异金额 */
    private BigDecimal difference;

    /** 是否已处理 0/1 */
    private Boolean resolved;

    /** 处理备注 */
    private String resolveNote;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
