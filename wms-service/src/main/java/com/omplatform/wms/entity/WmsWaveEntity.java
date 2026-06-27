package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 波次实体。
 * <p>
 * 对应 oms_wms.wms_wave 表。
 * 将多个出库单按仓库/配送方式等维度聚合成波次，统一分配和拣货以提高效率。
 */
@TableName("wms_wave")
public class WmsWaveEntity extends BaseEntity {

    private String waveNo;
    private String warehouseCode;

    /** OPEN / ALLOCATING / ALLOCATED / PICKING / COMPLETED / CANCELLED */
    private String status;

    /** MANUAL / AUTO */
    private String type;

    private Integer totalOrderCount;
    private Integer totalSkuCount;
    private Integer totalQuantity;

    /** 波次生成时间 */
    private LocalDateTime waveAt;

    // ========== Getters / Setters ==========

    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getTotalOrderCount() { return totalOrderCount; }
    public void setTotalOrderCount(Integer totalOrderCount) { this.totalOrderCount = totalOrderCount; }

    public Integer getTotalSkuCount() { return totalSkuCount; }
    public void setTotalSkuCount(Integer totalSkuCount) { this.totalSkuCount = totalSkuCount; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public LocalDateTime getWaveAt() { return waveAt; }
    public void setWaveAt(LocalDateTime waveAt) { this.waveAt = waveAt; }
}
