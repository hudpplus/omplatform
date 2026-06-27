package com.omplatform.cart.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 购物车 Redis 同步发件箱（Transactional Outbox）。
 * <p>
 * 记录需要同步到 Redis 的购物车，由后台 Worker 轮询处理。
 * 与业务数据在同一 DB 事务中写入，保证最终一致性。
 */
@TableName("cart_sync_outbox")
public class CartSyncOutboxEntity {

    @TableId
    private String id;

    /** 需要同步的购物车 ID */
    private String cartId;

    /** PENDING / DONE / FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public CartSyncOutboxEntity() {}

    public CartSyncOutboxEntity(String id, String cartId, String status,
                                Integer retryCount, LocalDateTime createdAt) {
        this.id = id;
        this.cartId = cartId;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
