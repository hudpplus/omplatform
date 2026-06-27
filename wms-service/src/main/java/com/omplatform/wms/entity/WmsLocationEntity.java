package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.math.BigDecimal;

/**
 * 库位实体。
 * <p>
 * 对应 oms_wms.wms_location 表。
 * 编码格式：A-01-03-2-5（库区-巷道-货架-层-位）
 */
@TableName("wms_location")
public class WmsLocationEntity extends BaseEntity {

    @TableId
    private String locationCode;
    private String zoneCode;
    private String warehouseCode;
    private String locationType;  // PICK / BULK / RECEIVING / STAGING
    private String abcClass;      // A / B / C
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private Integer maxQuantity;
    private String status;        // EMPTY / OCCUPIED / LOCKED / FULL
    private Integer putawaySeq;
    private Integer pickSeq;
    private String containerCode; // 容器编码（托盘/箱子）

    /** 组合路径（冗余，用于快速排序） */
    @TableField(exist = false)
    private String warehouseZonePath;

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }

    public String getAbcClass() { return abcClass; }
    public void setAbcClass(String abcClass) { this.abcClass = abcClass; }

    public BigDecimal getMaxWeight() { return maxWeight; }
    public void setMaxWeight(BigDecimal maxWeight) { this.maxWeight = maxWeight; }

    public BigDecimal getMaxVolume() { return maxVolume; }
    public void setMaxVolume(BigDecimal maxVolume) { this.maxVolume = maxVolume; }

    public Integer getMaxQuantity() { return maxQuantity; }
    public void setMaxQuantity(Integer maxQuantity) { this.maxQuantity = maxQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPutawaySeq() { return putawaySeq; }
    public void setPutawaySeq(Integer putawaySeq) { this.putawaySeq = putawaySeq; }

    public Integer getPickSeq() { return pickSeq; }
    public void setPickSeq(Integer pickSeq) { this.pickSeq = pickSeq; }

    public String getContainerCode() { return containerCode; }
    public void setContainerCode(String containerCode) { this.containerCode = containerCode; }
}
