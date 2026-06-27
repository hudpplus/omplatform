package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.entity.OrderEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 订单 MyBatis Mapper。
 * <p>
 * CAS 更新使用原生 SQL 保证原子性。
 * 表名 {@code order_ecommerce}，由 ShardingSphere 按 buyer_id 路由到物理分片。
 */
public interface OrderMapper extends BaseMapper<OrderEntity> {

    /**
     * 悲观锁查询（FOR UPDATE，事务内使用）。
     */
    @Select("SELECT * FROM order_ecommerce WHERE order_no = #{orderNo} FOR UPDATE")
    OrderEntity selectByIdForUpdate(@Param("orderNo") String orderNo);

    /**
     * CAS 状态更新（ADR-039 §1.8）。
     * <p>
     * SET status = target, version = version + 1
     * WHERE order_no = ? AND status = current AND version = ?
     */
    @Update("UPDATE order_ecommerce SET status = #{target}, previous_status = status, " +
            "status_changed_at = #{now}, status_expires_at = null, " +
            "version = version + 1 " +
            "WHERE order_no = #{orderNo} AND status = #{current} AND version = #{version}")
    int updateStatusWithVersionCheck(@Param("orderNo") String orderNo,
                                     @Param("current") OrderStatus current,
                                     @Param("target") OrderStatus target,
                                     @Param("now") LocalDateTime now,
                                     @Param("version") Long version);
}
