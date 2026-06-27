package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 拣货任务实体。
 * <p>
 * 对应 oms_wms.wms_picking_task 表。
 * 一个波次可拆分为多个拣货任务（按库区/通道分组），每个任务分配给一个拣货员。
 */
@TableName("wms_picking_task")
public class WmsPickingTaskEntity extends BaseEntity {

    private String taskNo;
    private String waveNo;
    private String warehouseCode;
    private String zoneCode;
    private String assignee;

    /** NEW / PICKING / PARTIALLY_PICKED / COMPLETED / CANCELLED */
    private String status;

    /** PICKING / REPLENISHMENT */
    private String type;

    private Integer totalLocations;
    private Integer totalItems;
    private Integer totalQuantity;
    private Integer pickedLocations;
    private Integer pickedItems;
    private Integer pickedQuantity;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // ========== Getters / Setters ==========

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }

    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getTotalLocations() { return totalLocations; }
    public void setTotalLocations(Integer totalLocations) { this.totalLocations = totalLocations; }

    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public Integer getPickedLocations() { return pickedLocations; }
    public void setPickedLocations(Integer pickedLocations) { this.pickedLocations = pickedLocations; }

    public Integer getPickedItems() { return pickedItems; }
    public void setPickedItems(Integer pickedItems) { this.pickedItems = pickedItems; }

    public Integer getPickedQuantity() { return pickedQuantity; }
    public void setPickedQuantity(Integer pickedQuantity) { this.pickedQuantity = pickedQuantity; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
