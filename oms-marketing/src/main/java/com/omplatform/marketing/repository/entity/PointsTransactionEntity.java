package com.omplatform.marketing.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 积分流水实体（member_points_transaction）。
 */
@TableName("member_points_transaction")
public class PointsTransactionEntity {

    /** 流水 ID */
    private String txnId;

    /** 账户 ID */
    private String accountId;

    /** 类型 EARN/SPEND/EXPIRE/ADJUST */
    private String type;

    /** 变动积分 */
    private Long points;

    /** 来源订单号 */
    private String source;

    /** 创建时间 */
    private LocalDateTime gmtCreate;

    // ====== Getters & Setters ======

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getPoints() { return points; }
    public void setPoints(Long points) { this.points = points; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}
