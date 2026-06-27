package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsPickingTaskEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拣货任务 Mapper。
 */
public interface WmsPickingTaskMapper extends BaseMapper<WmsPickingTaskEntity> {

    /**
     * 查询波次下所有拣货任务。
     */
    @Select("SELECT * FROM wms_picking_task WHERE wave_no = #{waveNo} ORDER BY gmt_create")
    List<WmsPickingTaskEntity> findByWaveNo(@Param("waveNo") String waveNo);

    /**
     * 更新拣货任务统计（每次拣完一项后调用）。
     */
    @Update("UPDATE wms_picking_task SET picked_items = picked_items + #{itemDelta}, " +
            "picked_quantity = picked_quantity + #{qtyDelta}, " +
            "status = CASE WHEN picked_items + #{itemDelta} >= total_items THEN 'COMPLETED' " +
            "               WHEN picked_quantity + #{qtyDelta} > 0 THEN 'PICKING' " +
            "               ELSE status END, " +
            "completed_at = CASE WHEN picked_items + #{itemDelta} >= total_items " +
            "                    THEN #{now} ELSE NULL END " +
            "WHERE task_no = #{taskNo}")
    int updatePickProgress(@Param("taskNo") String taskNo,
                           @Param("itemDelta") int itemDelta,
                           @Param("qtyDelta") int qtyDelta,
                           @Param("now") LocalDateTime now);
}
