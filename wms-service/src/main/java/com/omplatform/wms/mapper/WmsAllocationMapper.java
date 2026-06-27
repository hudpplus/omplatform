package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsAllocationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 分配记录 Mapper。
 */
public interface WmsAllocationMapper extends BaseMapper<WmsAllocationEntity> {

    /**
     * 查询出库单的所有分配记录。
     */
    @Select("SELECT * FROM wms_allocation WHERE outbound_no = #{outboundNo} " +
            "ORDER BY location_code, batch_no")
    List<WmsAllocationEntity> findByOutboundNo(@Param("outboundNo") String outboundNo);

    /**
     * 查询波次内所有出库单的分配记录（通过 wms_wave_order 关联）。
     */
    @Select("SELECT a.* FROM wms_allocation a " +
            "INNER JOIN wms_wave_order wo ON a.outbound_no = wo.outbound_no " +
            "WHERE wo.wave_no = #{waveNo} AND a.status = 'ALLOCATED' " +
            "ORDER BY a.location_code, a.batch_no")
    List<WmsAllocationEntity> findByWaveNo(@Param("waveNo") String waveNo);

    /**
     * 更新分配记录的状态和已拣数量。
     */
    @Update("UPDATE wms_allocation SET picked_qty = picked_qty + #{qty}, " +
            "status = CASE WHEN picked_qty + #{qty} >= allocated_qty THEN 'PICKED' ELSE 'PICKING' END " +
            "WHERE id = #{id} AND (allocated_qty - picked_qty) >= #{qty}")
    int addPickedQty(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 发货确认：状态改为 SHIPPED。
     */
    @Update("UPDATE wms_allocation SET status = 'SHIPPED' WHERE outbound_no = #{outboundNo} AND status IN ('ALLOCATED', 'PICKED')")
    int shipByOutboundNo(@Param("outboundNo") String outboundNo);
}
