package com.omplatform.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.wms.entity.WmsOutboundOrderEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单 Mapper。
 */
public interface WmsOutboundOrderMapper extends BaseMapper<WmsOutboundOrderEntity> {

    /**
     * 查询待分配的出库单（NEW/ALLOCATING 状态）。
     */
    @Select("SELECT * FROM wms_outbound_order WHERE warehouse_code = #{warehouseCode} " +
            "AND status IN ('NEW', 'ALLOCATING') ORDER BY priority ASC, gmt_create ASC")
    List<WmsOutboundOrderEntity> findPendingAllocate(@Param("warehouseCode") String warehouseCode);

    /**
     * 更新出库单状态。
     */
    @Update("UPDATE wms_outbound_order SET status = #{status}, shipped_at = #{shippedAt} " +
            "WHERE outbound_no = #{outboundNo}")
    int updateShipStatus(@Param("outboundNo") String outboundNo,
                         @Param("status") String status,
                         @Param("shippedAt") LocalDateTime shippedAt);
}
