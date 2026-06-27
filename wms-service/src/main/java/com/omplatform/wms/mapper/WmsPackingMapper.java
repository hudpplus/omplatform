package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsPackingEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打包记录 Mapper。
 */
public interface WmsPackingMapper extends BaseMapper<WmsPackingEntity> {

    /**
     * 查询出库单的所有包裹。
     */
    @Select("SELECT * FROM wms_packing WHERE outbound_no = #{outboundNo} ORDER BY gmt_create")
    List<WmsPackingEntity> findByOutboundNo(@Param("outboundNo") String outboundNo);
}
