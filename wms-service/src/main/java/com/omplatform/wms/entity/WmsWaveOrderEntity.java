package com.omplatform.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omplatform.common.model.BaseEntity;

/**
 * 波次与出库单关联实体。
 * <p>
 * 对应 oms_wms.wms_wave_order 表。
 * 一个波次包含多个出库单，出库单不可同时属于多个波次。
 */
@TableName("wms_wave_order")
public class WmsWaveOrderEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String waveNo;
    private String outboundNo;

    // ========== Getters / Setters ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWaveNo() { return waveNo; }
    public void setWaveNo(String waveNo) { this.waveNo = waveNo; }

    public String getOutboundNo() { return outboundNo; }
    public void setOutboundNo(String outboundNo) { this.outboundNo = outboundNo; }
}
