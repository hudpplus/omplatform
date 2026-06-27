package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * 出库单明细实体。
 * <p>
 * 对应 oms_wms.wms_outbound_item 表。
 * 记录每个出库单中各 SKU 的期望/已分配/已拣货/已发货数量。
 */
@TableName("wms_outbound_item")
public class WmsOutboundItemEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outboundNo;
    private String skuId;
    private String skuName;
    private Integer expectedQty;
    private Integer allocatedQty;
    private Integer pickedQty;
    private Integer shippedQty;

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public Integer getExpectedQty() { return expectedQty; }
    public void setExpectedQty(Integer expectedQty) { this.expectedQty = expectedQty; }

    public Integer getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(Integer allocatedQty) { this.allocatedQty = allocatedQty; }

    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

    public Integer getShippedQty() { return shippedQty; }
    public void setShippedQty(Integer shippedQty) { this.shippedQty = shippedQty; }
}
