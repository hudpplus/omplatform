package com.omplatform.fulfillment.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 库存预占记录表实体（ADR-043 §7.2）。
 * <p>
 * 对应 oms_fulfillment.inventory_hold 表。
 * 记录每笔预占的生命周期：RESERVED → CONFIRMED / RELEASED / TIMEOUT。
 */
@TableName("inventory_hold")
public class InventoryHoldEntity extends BaseEntity {

    @TableId
    private String holdId;
    private String requestId;
    private String sagaId;
    private String skuId;
    private String orderNo;
    private String channelCode;
    private Integer quantity;
    private String holdType;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime releasedAt;
    private Integer retryCount;
    private String lastError;

    public String getHoldId() { return holdId; }
    public void setHoldId(String holdId) { this.holdId = holdId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getHoldType() { return holdType; }
    public void setHoldType(String holdType) { this.holdType = holdType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
