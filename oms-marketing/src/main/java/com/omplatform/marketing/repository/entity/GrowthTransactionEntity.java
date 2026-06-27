package com.omplatform.marketing.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 成长值流水实体（member_growth_transaction）。
 */
@TableName("member_growth_transaction")
public class GrowthTransactionEntity {

    /** 流水 ID */
    private String txnId;

    /** 用户 ID */
    private String userId;

    /** 类型 ORDER/BONUS/EXPIRY/ADJUST */
    private String type;

    /** 变动值（正/负） */
    private Long amount;

    /** 来源订单号 */
    private String source;

    /** 创建时间 */
    private LocalDateTime gmtCreate;

    // ====== Getters & Setters ======

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}
