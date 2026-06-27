package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsPickingItemEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拣货任务明细 Mapper。
 */
public interface WmsPickingItemMapper extends BaseMapper<WmsPickingItemEntity> {

    /**
     * 查询任务下所有拣货明细，按库位排序（路径优化）。
     */
    @Select("SELECT * FROM wms_picking_item WHERE task_no = #{taskNo} " +
            "ORDER BY location_code, gmt_create")
    List<WmsPickingItemEntity> findByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 按分配记录查询拣货明细。
     */
    @Select("SELECT * FROM wms_picking_item WHERE allocation_id = #{allocationId}")
    List<WmsPickingItemEntity> findByAllocationId(@Param("allocationId") Long allocationId);

    /**
     * 拣货确认：更新已拣数量。
     */
    @Update("UPDATE wms_picking_item SET picked_qty = picked_qty + #{qty}, " +
            "status = CASE WHEN picked_qty + #{qty} >= expected_qty THEN 'PICKED' ELSE status END, " +
            "picked_at = #{now}, picked_by = #{picker} " +
            "WHERE id = #{id} AND (expected_qty - picked_qty) >= #{qty}")
    int confirmPick(@Param("id") Long id, @Param("qty") int qty,
                    @Param("picker") String picker, @Param("now") LocalDateTime now);
}
