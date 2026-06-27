package com.omplatform.risk.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 风控检查记录（ADR-047）。
 */
@TableName("risk_check_record")
public class RiskCheckRecordEntity {

    @TableId(type = IdType.INPUT)
    private String recordId;

    /** PRE_CHECK / REFUND_CHECK */
    private String checkType;

    private String buyerId;
    private String deviceId;
    private String orderNo;

    /** PASS / REVIEW / REJECT */
    private String decision;

    /** 风险等级 LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 风控评分 0-100 */
    private Integer score;

    /** 外部风控平台追踪 ID */
    private String externalTraceId;

    /** 检查原因/备注 */
    private String reason;

    /** 降级等级 L0/L1/L2 */
    private String degradationLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime gmtCreate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime gmtModified;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

    // ========== 构造 ==========

    public RiskCheckRecordEntity() {}

    public RiskCheckRecordEntity(String recordId, String checkType, String buyerId,
                                 String deviceId, String orderNo, String decision,
                                 String riskLevel, Integer score, String externalTraceId,
                                 String reason, String degradationLevel) {
        this.recordId = recordId;
        this.checkType = checkType;
        this.buyerId = buyerId;
        this.deviceId = deviceId;
        this.orderNo = orderNo;
        this.decision = decision;
        this.riskLevel = riskLevel;
        this.score = score;
        this.externalTraceId = externalTraceId;
        this.reason = reason;
        this.degradationLevel = degradationLevel;
    }

    // ========== getter/setter ==========

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getExternalTraceId() { return externalTraceId; }
    public void setExternalTraceId(String externalTraceId) { this.externalTraceId = externalTraceId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDegradationLevel() { return degradationLevel; }
    public void setDegradationLevel(String degradationLevel) { this.degradationLevel = degradationLevel; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }

    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
