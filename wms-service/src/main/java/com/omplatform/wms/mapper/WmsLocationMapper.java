package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsLocationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库位 Mapper。
 */
public interface WmsLocationMapper extends BaseMapper<WmsLocationEntity> {

    /**
     * 查询指定仓库的空闲库位，按上架优先级排序。
     */
    @Select("SELECT * FROM wms_location WHERE warehouse_code = #{warehouseCode} " +
            "AND status IN ('EMPTY', 'OCCUPIED') AND location_type = #{locationType} " +
            "ORDER BY putaway_seq ASC LIMIT #{limit}")
    List<WmsLocationEntity> findAvailableLocations(
            @Param("warehouseCode") String warehouseCode,
            @Param("locationType") String locationType,
            @Param("limit") int limit);

    /**
     * 查询指定仓库的拣货库位，按拣货优先级排序。
     */
    @Select("SELECT l.* FROM wms_location l " +
            "INNER JOIN wms_inventory i ON l.location_code = i.location_code " +
            "WHERE l.warehouse_code = #{warehouseCode} " +
            "AND l.location_type = 'PICK' AND l.status = 'OCCUPIED' " +
            "AND i.sku_id = #{skuId} AND i.quantity > i.lock_quantity " +
            "ORDER BY l.pick_seq ASC, i.expire_date ASC")
    List<WmsLocationEntity> findPickLocationsBySku(
            @Param("warehouseCode") String warehouseCode,
            @Param("skuId") String skuId);
}
