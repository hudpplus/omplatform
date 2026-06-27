package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * 库区实体。
 * <p>
 * 对应 oms_wms.wms_zone 表。
 */
@TableName("wms_zone")
public class WmsZoneEntity extends BaseEntity {

    @TableId
    private String zoneCode;
    private String warehouseCode;
    private String zoneType;   // BULK / PICK / TRANSIT / RECEIVING / QUARANTINE
    private String zoneName;
    private String description;

    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
