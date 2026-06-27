package com.omplatform.cart.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车行表（ADR-044）。
 */
@TableName("cart_item")
public class CartItemEntity {

    @TableId(type = IdType.INPUT)
    private String itemId;

    private String cartId;

    private String skuId;

    /** SKU 快照名称 */
    private String skuName;

    /** SKU 快照图片 */
    private String imageUrl;

    /** 数量 */
    private Integer quantity;

    /** 加入时单价（快照，后续由 price-refresh 更新） */
    private BigDecimal unitPrice;

    /** 是否勾选（0-未勾选, 1-已勾选） */
    private Integer selected;

    /** 促销信息 JSON（叠加相关） */
    private String promotionInfo;

    /** 排序值（越小越靠前） */
    private Integer sortOrder;

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

    public CartItemEntity() {}

    public CartItemEntity(String itemId, String cartId, String skuId, String skuName,
                          String imageUrl, int quantity, BigDecimal unitPrice,
                          int selected, String promotionInfo, int sortOrder) {
        this.itemId = itemId;
        this.cartId = cartId;
        this.skuId = skuId;
        this.skuName = skuName;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.selected = selected;
        this.promotionInfo = promotionInfo;
        this.sortOrder = sortOrder;
    }

    // ========== getter/setter ==========

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Integer getSelected() { return selected; }
    public void setSelected(Integer selected) { this.selected = selected; }

    public String getPromotionInfo() { return promotionInfo; }
    public void setPromotionInfo(String promotionInfo) { this.promotionInfo = promotionInfo; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }

    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
