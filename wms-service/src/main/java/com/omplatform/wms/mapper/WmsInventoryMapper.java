package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsInventoryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 批次库存 Mapper。
 */
public interface WmsInventoryMapper extends BaseMapper<WmsInventoryEntity> {

    /**
     * 聚合查询 SKU 在仓库的总可用量（合格品 quantity - lock_quantity）。
     */
    @Select("SELECT COALESCE(SUM(quantity - lock_quantity), 0) FROM wms_inventory " +
            "WHERE sku_id = #{skuId} AND inventory_status = 'QUALIFIED'")
    int aggregateAvailable(@Param("skuId") String skuId);

    /**
     * 按仓库聚合查询 SKU 总可用量。
     */
    @Select("SELECT COALESCE(SUM(quantity - lock_quantity), 0) FROM wms_inventory " +
            "WHERE sku_id = #{skuId} AND warehouse_code = #{warehouseCode} " +
            "AND inventory_status = 'QUALIFIED'")
    int aggregateAvailableByWarehouse(
            @Param("skuId") String skuId,
            @Param("warehouseCode") String warehouseCode);

    /**
     * 按 FEFO 查询可分配库存（最早到期日优先）。
     */
    @Select("SELECT * FROM wms_inventory WHERE sku_id = #{skuId} " +
            "AND warehouse_code = #{warehouseCode} " +
            "AND inventory_status = 'QUALIFIED' AND quantity > lock_quantity " +
            "ORDER BY expire_date ASC NULLS LAST, location_code ASC " +
            "LIMIT #{limit}")
    List<WmsInventoryEntity> findAvailableByFefo(
            @Param("skuId") String skuId,
            @Param("warehouseCode") String warehouseCode,
            @Param("limit") int limit);

    /**
     * 查询库位中的库存明细。
     */
    @Select("SELECT * FROM wms_inventory WHERE location_code = #{locationCode} " +
            "AND quantity > 0 ORDER BY batch_no")
    List<WmsInventoryEntity> findByLocation(@Param("locationCode") String locationCode);

    /**
     * 更新锁定数量。
     */
    @Update("UPDATE wms_inventory SET lock_quantity = lock_quantity + #{qty} " +
            "WHERE id = #{id} AND (quantity - lock_quantity) >= #{qty}")
    int addLock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * 释放锁定并扣减库存（出库确认）。
     */
    @Update("UPDATE wms_inventory SET quantity = quantity - #{qty}, " +
            "lock_quantity = lock_quantity - #{qty} " +
            "WHERE id = #{id} AND lock_quantity >= #{qty}")
    int deductAndUnlock(@Param("id") Long id, @Param("qty") int qty);
}
