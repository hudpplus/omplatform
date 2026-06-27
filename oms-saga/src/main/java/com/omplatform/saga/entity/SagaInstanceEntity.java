package com.omplatform.saga.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * Saga 实例表实体（saga_instance）。
 * <p>
 * 对应 DDL：deploy/sql/init-all-databases.sql → oms_common.saga_instance
 */
@TableName("saga_instance")
public class SagaInstanceEntity extends BaseEntity {

    @TableId(type = IdType.INPUT)
    private String sagaId;

    private String sagaName;
    private String orderNo;
    private String status;
    private Integer currentStep;
    private Integer stepCount;
    private String failedStep;
    private String errorMessage;
    private String contextJson;
    private String initiator;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public String getSagaName() { return sagaName; }
    public void setSagaName(String sagaName) { this.sagaName = sagaName; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }

    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }

    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    public String getInitiator() { return initiator; }
    public void setInitiator(String initiator) { this.initiator = initiator; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
