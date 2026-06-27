package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单操作日志实体（order_operation_log）。
 * <p>
 * 记录每次状态变更的完整审计轨迹。由 StateMachineEngine 在 transition() 成功后同步写入。
 */
@TableName("order_operation_log")
public class OrderOperationLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志 ID */
    @TableId(type = IdType.INPUT)
    private String logId;

    /** 订单号 */
    private String orderNo;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人类型：BUYER / ADMIN / SYSTEM */
    private String operatorType;

    /** 操作动作 */
    private String action;

    /** 来源状态 */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 操作详情（JSON） */
    private String detail;

    /** 创建时间 */
    private LocalDateTime gmtCreate;

    // ====== Getters & Setters ======

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getOperatorType() { return operatorType; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}
