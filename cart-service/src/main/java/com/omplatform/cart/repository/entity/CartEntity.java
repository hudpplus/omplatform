package com.omplatform.cart.repository.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 购物车主表（ADR-044）。
 */
@TableName("cart_cart")
public class CartEntity {

    @TableId(type = IdType.INPUT)
    private String cartId;

    /** 登录用户 ID（已登录时非空） */
    private String userId;

    /** 设备指纹（匿名购物车时非空） */
    private String deviceId;

    /** ACTIVE / MERGED / EXPIRED */
    private String status;

    /** 商品总数 */
    private Integer itemCount;

    /** 匿名购物车过期时间 */
    private LocalDateTime expiredAt;

    // ========== BaseEntity 字段 ==========

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

    // ========== 构造 & getter/setter ==========

    public CartEntity() {}

    public CartEntity(String cartId, String userId, String deviceId, String status,
                      Integer itemCount, LocalDateTime expiredAt) {
        this.cartId = cartId;
        this.userId = userId;
        this.deviceId = deviceId;
        this.status = status;
        this.itemCount = itemCount;
        this.expiredAt = expiredAt;
    }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }

    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }

    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }

    public LocalDateTime getGmtModified() { return gmtModified; }
    public void setGmtModified(LocalDateTime gmtModified) { this.gmtModified = gmtModified; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
