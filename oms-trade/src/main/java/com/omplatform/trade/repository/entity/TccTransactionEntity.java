package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * TCC 全局事务表实体。
 */
@TableName("tcc_transaction")
public class TccTransactionEntity extends BaseEntity {

    @TableId
    private String txId;

    private String orderNo;

    private String status; // INIT / TRIED_ALL / CONFIRMED / CANCELLED

    private LocalDateTime gmtCreate;

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}

