package com.omplatform.fulfillment.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * 库存主表实体（ADR-043 §7.1）。
 * <p>
 * 对应 oms_fulfillment.inventory 表。
 * 列式库存模型，每种库存类型独立列。
 */
@TableName("inventory")
public class InventoryEntity extends BaseEntity {

    @TableId
    private String skuId;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer holdQuantity;
    private Integer deductedQuantity;

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }

    public Integer getHoldQuantity() { return holdQuantity; }
    public void setHoldQuantity(Integer holdQuantity) { this.holdQuantity = holdQuantity; }

    public Integer getDeductedQuantity() { return deductedQuantity; }
    public void setDeductedQuantity(Integer deductedQuantity) { this.deductedQuantity = deductedQuantity; }
}
