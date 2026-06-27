package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * 出库单主表实体。
 * <p>
 * 对应 oms_wms.wms_outbound_order 表。
 * 每个出库单关联一个业务订单，包含收货地址、物流信息等。
 */
@TableName("wms_outbound_order")
public class WmsOutboundOrderEntity extends BaseEntity {

    private String outboundNo;
    private String orderNo;
    private String warehouseCode;

    /** NEW / ALLOCATING / ALLOCATED / PICKING / PICKED / PACKING / PACKED / SHIPPING / SHIPPED / CANCELLED */
    private String status;

    /** 优先级 1-10，数值越大越紧急 */
    private Integer priority;

    /** 配送方式 EXPRESS / EMS / AIR / LAND */
    private String deliveryMethod;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;

    private String logisticsCompany;
    private String logisticsNo;

    private Integer totalSkuCount;
    private Integer totalQuantity;

    /** 期望发货时间 */
    private LocalDateTime expectedAt;

    /** 实际发货时间 */
    private LocalDateTime shippedAt;

    private String cancelReason;

    // ========== Getters / Setters ==========

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public String getLogisticsCompany() { return logisticsCompany; }
    public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }

    public String getLogisticsNo() { return logisticsNo; }
    public void setLogisticsNo(String logisticsNo) { this.logisticsNo = logisticsNo; }

    public Integer getTotalSkuCount() { return totalSkuCount; }
    public void setTotalSkuCount(Integer totalSkuCount) { this.totalSkuCount = totalSkuCount; }

    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }

    public LocalDateTime getExpectedAt() { return expectedAt; }
    public void setExpectedAt(LocalDateTime expectedAt) { this.expectedAt = expectedAt; }

    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
