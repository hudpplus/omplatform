package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsWaveEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 波次 Mapper。
 */
public interface WmsWaveMapper extends BaseMapper<WmsWaveEntity> {

    /**
     * 查询待处理波次。
     */
    @Select("SELECT * FROM wms_wave WHERE warehouse_code = #{warehouseCode} " +
            "AND status IN ('OPEN', 'ALLOCATED') ORDER BY gmt_create ASC")
    List<WmsWaveEntity> findPendingWaves(@Param("warehouseCode") String warehouseCode);
}
