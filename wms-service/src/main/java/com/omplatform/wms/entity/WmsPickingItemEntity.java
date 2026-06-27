package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 拣货任务明细实体。
 * <p>
 * 对应 oms_wms.wms_picking_item 表。
 * 记录每个拣货任务中各库位的应拣和已拣数量，支持逐行确认。
 */
@TableName("wms_picking_item")
public class WmsPickingItemEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long allocationId;
    private String skuId;
    private String locationCode;
    private String batchNo;
    private Integer expectedQty;
    private Integer pickedQty;

    /** PENDING / PICKED / SKIPPED */
    private String status;

    private LocalDateTime pickedAt;
    private String pickedBy;

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }

    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public Integer getExpectedQty() { return expectedQty; }
    public void setExpectedQty(Integer expectedQty) { this.expectedQty = expectedQty; }

    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPickedAt() { return pickedAt; }
    public void setPickedAt(LocalDateTime pickedAt) { this.pickedAt = pickedAt; }

    public String getPickedBy() { return pickedBy; }
    public void setPickedBy(String pickedBy) { this.pickedBy = pickedBy; }
}
