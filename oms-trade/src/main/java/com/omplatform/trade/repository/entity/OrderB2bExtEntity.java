package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * B2B 订单扩展表（order_b2b_ext）。
 * <p>
 * 存储审批流/合同/分期付款/发票等 B2B 特有字段。
 */
@TableName("order_b2b_ext")
public class OrderB2bExtEntity {

    @TableId(type = IdType.INPUT)
    private String orderNo;

    /** 审批流 ID */
    private String approvalFlowId;

    /** 审批状态 */
    private String approvalStatus;

    /** 当前审批节点 */
    private String approvalNode;

    /** 合同号 */
    private String contractNo;

    /** 分期计划 ID */
    private Long installmentPlanId;

    /** 分期总期数 */
    private Integer installmentCount;

    /** 企业 ID */
    private Long companyId;

    /** 发票类型 */
    private String invoiceType;

    /** 发票抬头 */
    private String invoiceTitle;

    /** 税号 */
    private String taxId;

    /** 采购单号 */
    private String purchaseOrderNo;

    // ====== Getters & Setters ======

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getApprovalFlowId() { return approvalFlowId; }
    public void setApprovalFlowId(String approvalFlowId) { this.approvalFlowId = approvalFlowId; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getApprovalNode() { return approvalNode; }
    public void setApprovalNode(String approvalNode) { this.approvalNode = approvalNode; }

    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }

    public Long getInstallmentPlanId() { return installmentPlanId; }
    public void setInstallmentPlanId(Long installmentPlanId) { this.installmentPlanId = installmentPlanId; }

    public Integer getInstallmentCount() { return installmentCount; }
    public void setInstallmentCount(Integer installmentCount) { this.installmentCount = installmentCount; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getInvoiceType() { return invoiceType; }
    public void setInvoiceType(String invoiceType) { this.invoiceType = invoiceType; }

    public String getInvoiceTitle() { return invoiceTitle; }
    public void setInvoiceTitle(String invoiceTitle) { this.invoiceTitle = invoiceTitle; }

    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }

    public String getPurchaseOrderNo() { return purchaseOrderNo; }
    public void setPurchaseOrderNo(String purchaseOrderNo) { this.purchaseOrderNo = purchaseOrderNo; }
}
