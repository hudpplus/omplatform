package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDate;

/**
 * 批次库存实体（多维库存核心表）。
 * <p>
 * 对应 oms_wms.wms_inventory 表。
 * 唯一键：(sku_id + warehouse_code + location_code + batch_no + inventory_status + owner_code)
 */
@TableName("wms_inventory")
public class WmsInventoryEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String skuId;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String ownerCode;
    private String inventoryStatus;  // QUALIFIED / DEFECTIVE / INSPECTING / FROZEN
    private Integer quantity;
    private Integer lockQuantity;
    private LocalDate inboundDate;
    private LocalDate expireDate;
    private LocalDate produceDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public String getOwnerCode() { return ownerCode; }
    public void setOwnerCode(String ownerCode) { this.ownerCode = ownerCode; }

    public String getInventoryStatus() { return inventoryStatus; }
    public void setInventoryStatus(String inventoryStatus) { this.inventoryStatus = inventoryStatus; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getLockQuantity() { return lockQuantity; }
    public void setLockQuantity(Integer lockQuantity) { this.lockQuantity = lockQuantity; }

    public LocalDate getInboundDate() { return inboundDate; }
    public void setInboundDate(LocalDate inboundDate) { this.inboundDate = inboundDate; }

    public LocalDate getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }

    public LocalDate getProduceDate() { return produceDate; }
    public void setProduceDate(LocalDate produceDate) { this.produceDate = produceDate; }
}
