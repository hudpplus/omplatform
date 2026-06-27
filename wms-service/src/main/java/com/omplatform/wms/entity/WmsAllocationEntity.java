package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * 分配记录实体。
 * <p>
 * 对应 oms_wms.wms_allocation 表。
 * 出库分配时按 FEFO 策略锁定具体库位的库存，一个 SKU 可能从多个库位分配。
 */
@TableName("wms_allocation")
public class WmsAllocationEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String allocationNo;
    private String outboundNo;
    private Long itemId;
    private String skuId;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String ownerCode;
    private Long inventoryId;
    private Integer allocatedQty;
    private Integer pickedQty;

    /** ALLOCATED / PICKING / PICKED / SHIPPED / CANCELLED */
    private String status;

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAllocationNo() { return allocationNo; }
    public void setAllocationNo(String allocationNo) { this.allocationNo = allocationNo; }

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

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

    public Long getInventoryId() { return inventoryId; }
    public void setInventoryId(Long inventoryId) { this.inventoryId = inventoryId; }

    public Integer getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(Integer allocatedQty) { this.allocatedQty = allocatedQty; }

    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
