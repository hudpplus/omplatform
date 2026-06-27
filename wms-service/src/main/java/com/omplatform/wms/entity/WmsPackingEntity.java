package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打包记录实体。
 * <p>
 * 对应 oms_wms.wms_packing 表。
 * 记录拣货完成后每个包裹/箱的打包信息，包括包裹内商品和尺寸重量。
 */
@TableName("wms_packing")
public class WmsPackingEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outboundNo;
    private String taskNo;
    private String packageNo;
    private String skuId;
    private String skuName;
    private Integer quantity;
    private BigDecimal weight;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;

    /** BOX / BAG / PALLET */
    private String packageType;

    private String operator;
    private LocalDateTime packedAt;

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }

    public String getPackageNo() { return packageNo; }
    public void setPackageNo(String packageNo) { this.packageNo = packageNo; }

    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public BigDecimal getLength() { return length; }
    public void setLength(BigDecimal length) { this.length = length; }

    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }

    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }

    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getPackedAt() { return packedAt; }
    public void setPackedAt(LocalDateTime packedAt) { this.packedAt = packedAt; }
}
