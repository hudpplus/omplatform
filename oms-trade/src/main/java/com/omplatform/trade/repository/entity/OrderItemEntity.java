package com.omplatform.trade.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.math.BigDecimal;

/**
 * 订单商品行实体（order_items）。
 * <p>
 * 存储下单时每个 SKU 的快照信息，包括价格分摊（优惠、实付）。
 * order 表的汇总金额 = SUM(order_items) 兜底核验。
 */
@TableName("order_items")
public class OrderItemEntity extends BaseEntity {

    /** 行 ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 订单号 */
    private String orderNo;

    /** 业务线标识（冗余，用于 ShardingSphere 路由）- ADR-017 */
    private String businessType;

    /** SKU ID */
    private String skuId;

    /** SKU 名称（下单时快照） */
    private String skuName;

    /** SKU 规格（下单时快照，如"颜色:黑,尺寸:L"） */
    private String skuSpec;

    /** 商品图片（下单时快照） */
    private String imageUrl;

    /** 数量 */
    private Integer quantity;

    /** 下单时单价 */
    private BigDecimal unitPrice;

    /** 行总价 = unitPrice × quantity */
    private BigDecimal totalAmount;

    /** 本行分摊优惠 */
    private BigDecimal discountAmount;

    /** 本行实付 = totalAmount - discountAmount */
    private BigDecimal payAmount;

    /** 类目 ID */
    private String categoryId;

    /** 行类型：NORMAL / GIFT / EXCHANGE */
    private String lineType;

    /** 行状态：PENDING / SHIPPED / RECEIVED / RETURNING / RETURNED */
    private String status;

    /** 促销信息（快照） */
    private String promotionInfo;

    // ====== Getters & Setters ======

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public String getSkuSpec() { return skuSpec; }
    public void setSkuSpec(String skuSpec) { this.skuSpec = skuSpec; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getLineType() { return lineType; }
    public void setLineType(String lineType) { this.lineType = lineType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPromotionInfo() { return promotionInfo; }
    public void setPromotionInfo(String promotionInfo) { this.promotionInfo = promotionInfo; }
}
