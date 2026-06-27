package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 仓库实体。
 * <p>
 * 对应 oms_wms.wms_warehouse 表。
 */
@TableName("wms_warehouse")
public class WmsWarehouseEntity extends BaseEntity {

    @TableId
    private String warehouseCode;
    private String warehouseName;
    private String type;        // NORMAL / OVERSEA / VIRTUAL
    private String status;      // ACTIVE / INACTIVE
    private String address;
    private String contact;
    private String contactPhone;

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
