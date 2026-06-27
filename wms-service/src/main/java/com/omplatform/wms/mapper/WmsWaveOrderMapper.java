package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsWaveOrderEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 波次与出库单关联 Mapper。
 */
public interface WmsWaveOrderMapper extends BaseMapper<WmsWaveOrderEntity> {

    /**
     * 查询波次下的所有出库单号。
     */
    @Select("SELECT outbound_no FROM wms_wave_order WHERE wave_no = #{waveNo}")
    List<String> findOutboundNosByWaveNo(@Param("waveNo") String waveNo);
}
