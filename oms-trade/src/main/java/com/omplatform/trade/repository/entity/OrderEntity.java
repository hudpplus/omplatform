package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表实体。
 * <p>
 * 映射 order 表，含 ADR-039 定义的生命周期管理字段。
 * <p>
 * ADR-017 多业务线物理隔离后，此实体映射逻辑表名 {@code order_ecommerce}，
 * ShardingSphere 根据 {@link #businessType} + {@link #buyerId} 路由到实际物理分片。
 */
@TableName("order_ecommerce")
public class OrderEntity extends BaseEntity {

    /** 订单号（Snowflake ID） */
    @TableId(type = IdType.INPUT)
    private String orderNo;

    /** 父订单号（子订单场景） */
    private String parentOrderNo;

    /** 买家 ID */
    private String buyerId;

    /** 店铺 ID */
    private String shopId;

    /** 业务线标识（ecommerce / locallife / b2b）- ADR-017 路由字段 */
    private String businessType;

    /** 订单状态（13 态） */
    private OrderStatus status;

    /** 前一状态（追溯用） */
    private OrderStatus previousStatus;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 优惠总金额 */
    private BigDecimal discountAmount;

    /** 收货地址 ID */
    private String addressId;

    /** 买家备注 */
    private String remark;

    /** 渠道来源 */
    private String channelSource;

    /** 优惠券实例 ID */
    private String couponInstanceId;

    /** 秒杀活动 ID */
    private Long seckillActivityId;

    /** 秒杀批次 */
    private String seckillPipeline;

    /** 支付渠道 (ALIPAY / WECHAT / …) */
    private String payChannel;

    /** 渠道交易号 */
    private String transactionId;

    /** 挂起原因 */
    private String holdReason;

    /** 冻结原因 */
    private String frozenReason;

    /** 上次状态变更时间 */
    private LocalDateTime statusChangedAt;

    /** 当前状态的超时时间 */
    private LocalDateTime statusExpiresAt;

    // ====== Getters & Setters (手动, 不使用 Lombok) ======

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getParentOrderNo() { return parentOrderNo; }
    public void setParentOrderNo(String parentOrderNo) { this.parentOrderNo = parentOrderNo; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public OrderStatus getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(OrderStatus previousStatus) { this.previousStatus = previousStatus; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }
    public BigDecimal getFreightAmount() { return freightAmount; }
    public void setFreightAmount(BigDecimal freightAmount) { this.freightAmount = freightAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getAddressId() { return addressId; }
    public void setAddressId(String addressId) { this.addressId = addressId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getChannelSource() { return channelSource; }
    public void setChannelSource(String channelSource) { this.channelSource = channelSource; }
    public String getCouponInstanceId() { return couponInstanceId; }
    public void setCouponInstanceId(String couponInstanceId) { this.couponInstanceId = couponInstanceId; }
    public Long getSeckillActivityId() { return seckillActivityId; }
    public void setSeckillActivityId(Long seckillActivityId) { this.seckillActivityId = seckillActivityId; }
    public String getSeckillPipeline() { return seckillPipeline; }
    public void setSeckillPipeline(String seckillPipeline) { this.seckillPipeline = seckillPipeline; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getHoldReason() { return holdReason; }
    public void setHoldReason(String holdReason) { this.holdReason = holdReason; }
    public String getFrozenReason() { return frozenReason; }
    public void setFrozenReason(String frozenReason) { this.frozenReason = frozenReason; }
    public LocalDateTime getStatusChangedAt() { return statusChangedAt; }
    public void setStatusChangedAt(LocalDateTime statusChangedAt) { this.statusChangedAt = statusChangedAt; }
    public LocalDateTime getStatusExpiresAt() { return statusExpiresAt; }
    public void setStatusExpiresAt(LocalDateTime statusExpiresAt) { this.statusExpiresAt = statusExpiresAt; }
}
