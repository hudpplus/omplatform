package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电商订单扩展表（order_ecommerce_ext）。
 * <p>
 * 与 order_ecommerce 同分片（Binding Table），零跨库 JOIN。
 * 存储电商特有的秒杀/预售/优惠券分摊/物流信息。
 */
@TableName("order_ecommerce_ext")
public class OrderEcommerceExtEntity {

    @TableId(type = IdType.INPUT)
    private String orderNo;

    /** 秒杀活动 ID */
    private Long seckillActivityId;

    /** 秒杀批次 */
    private String seckillPipeline;

    /** 预售活动 ID */
    private Long preSaleId;

    /** 预售阶段（定金/尾款） */
    private String preSaleStage;

    /** 定金金额(分) */
    private BigDecimal preSaleDeposit;

    /** 优惠券 ID */
    private Long couponId;

    /** 优惠券分摊金额 */
    private BigDecimal couponSplitAmount;

    /** 营销活动 ID */
    private String promotionId;

    /** 配送方式 */
    private String deliveryType;

    /** 预计送达时间 */
    private LocalDateTime expectedDeliveryTime;

    /** 签收时间 */
    private LocalDateTime signTime;

    // ====== Getters & Setters ======

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getSeckillActivityId() { return seckillActivityId; }
    public void setSeckillActivityId(Long seckillActivityId) { this.seckillActivityId = seckillActivityId; }

    public String getSeckillPipeline() { return seckillPipeline; }
    public void setSeckillPipeline(String seckillPipeline) { this.seckillPipeline = seckillPipeline; }

    public Long getPreSaleId() { return preSaleId; }
    public void setPreSaleId(Long preSaleId) { this.preSaleId = preSaleId; }

    public String getPreSaleStage() { return preSaleStage; }
    public void setPreSaleStage(String preSaleStage) { this.preSaleStage = preSaleStage; }

    public BigDecimal getPreSaleDeposit() { return preSaleDeposit; }
    public void setPreSaleDeposit(BigDecimal preSaleDeposit) { this.preSaleDeposit = preSaleDeposit; }

    public Long getCouponId() { return couponId; }
    public void setCouponId(Long couponId) { this.couponId = couponId; }

    public BigDecimal getCouponSplitAmount() { return couponSplitAmount; }
    public void setCouponSplitAmount(BigDecimal couponSplitAmount) { this.couponSplitAmount = couponSplitAmount; }

    public String getPromotionId() { return promotionId; }
    public void setPromotionId(String promotionId) { this.promotionId = promotionId; }

    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }

    public LocalDateTime getExpectedDeliveryTime() { return expectedDeliveryTime; }
    public void setExpectedDeliveryTime(LocalDateTime expectedDeliveryTime) { this.expectedDeliveryTime = expectedDeliveryTime; }

    public LocalDateTime getSignTime() { return signTime; }
    public void setSignTime(LocalDateTime signTime) { this.signTime = signTime; }
}
