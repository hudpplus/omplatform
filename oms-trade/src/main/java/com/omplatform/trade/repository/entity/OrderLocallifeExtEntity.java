package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.time.LocalDateTime;

/**
 * 本地生活订单扩展表（order_locallife_ext）。
 * <p>
 * 存储核销码/门店/服务技师等本地生活特有字段。
 */
@TableName("order_locallife_ext")
public class OrderLocallifeExtEntity {

    @TableId(type = IdType.INPUT)
    private String orderNo;

    /** 核销码 */
    private String verificationCode;

    /** 核销状态 */
    private String verificationStatus;

    /** 核销时间 */
    private LocalDateTime verificationTime;

    /** 核销员 ID */
    private String verifierId;

    /** 门店 ID */
    private Long storeId;

    /** 门店名称 */
    private String storeName;

    /** 服务时间 */
    private LocalDateTime serviceTime;

    /** 服务时长(分钟) */
    private Integer serviceDuration;

    /** 服务地址 */
    private String serviceAddress;

    /** 技师 ID */
    private String technicianId;

    // ====== Getters & Setters ======

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public LocalDateTime getVerificationTime() { return verificationTime; }
    public void setVerificationTime(LocalDateTime verificationTime) { this.verificationTime = verificationTime; }

    public String getVerifierId() { return verifierId; }
    public void setVerifierId(String verifierId) { this.verifierId = verifierId; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public LocalDateTime getServiceTime() { return serviceTime; }
    public void setServiceTime(LocalDateTime serviceTime) { this.serviceTime = serviceTime; }

    public Integer getServiceDuration() { return serviceDuration; }
    public void setServiceDuration(Integer serviceDuration) { this.serviceDuration = serviceDuration; }

    public String getServiceAddress() { return serviceAddress; }
    public void setServiceAddress(String serviceAddress) { this.serviceAddress = serviceAddress; }

    public String getTechnicianId() { return technicianId; }
    public void setTechnicianId(String technicianId) { this.technicianId = technicianId; }
}
