package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

@TableName("idempotent_record")
public class IdempotentRecordEntity extends BaseEntity {

    @TableId
    private String idempotentKey; // sagaId:stepName or sagaId:compensate:stepName

    private String sagaId;
    private String stepName;
    private String status; // EXECUTING / SUCCEEDED
    private String resultPayload;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String resultPayload) { this.resultPayload = resultPayload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
}

