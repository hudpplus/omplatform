package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * WMS 库存流水实体。
 * <p>
 * 对应 oms_wms.wms_inventory_transaction 表。
 * 记录 WMS 每步操作的审计流水。
 */
@TableName("wms_inventory_transaction")
public class WmsInventoryTransactionEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionNo;
    private String skuId;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String ownerCode;
    private String refNo;
    private String refType;     // ASN / OUTBOUND / COUNT / MOVE / ADJUST
    private String opType;      // RECEIVE / PUTAWAY / ALLOCATE / PICK / SHIP / MOVE / COUNT / ADJUST
    private Integer quantity;
    private Integer beforeQty;
    private Integer afterQty;
    private String opBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }

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

    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public String getOpType() { return opType; }
    public void setOpType(String opType) { this.opType = opType; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getBeforeQty() { return beforeQty; }
    public void setBeforeQty(Integer beforeQty) { this.beforeQty = beforeQty; }

    public Integer getAfterQty() { return afterQty; }
    public void setAfterQty(Integer afterQty) { this.afterQty = afterQty; }

    public String getOpBy() { return opBy; }
    public void setOpBy(String opBy) { this.opBy = opBy; }
}
