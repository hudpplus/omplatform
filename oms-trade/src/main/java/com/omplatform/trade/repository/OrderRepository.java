package com.omplatform.trade.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omplatform.common.api.PageParam;
import com.omplatform.common.api.PageResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.entity.OrderEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储。
 * <p>
 * 封装 order 表的读写操作，含乐观锁 CAS 更新和卡单检测查询。
 */
@Repository
public class OrderRepository extends ServiceImpl<OrderMapper, OrderEntity> {

    /**
     * 悲观锁查询（状态机转换时使用）。
     */
    public OrderEntity findByIdForUpdate(String orderNo) {
        return baseMapper.selectByIdForUpdate(orderNo);
    }

    /**
     * CAS 状态更新。
     *
     * @return 受影响行数（0 = 乐观锁冲突）
     */
    public int updateStatusWithVersionCheck(String orderNo, OrderStatus expectedCurrent,
                                            OrderStatus target, Long version) {
        return baseMapper.updateStatusWithVersionCheck(orderNo, expectedCurrent, target,
                LocalDateTime.now(), version);
    }

    /**
     * 查询卡在某状态的超时订单（ADR-039 卡单检测）。
     */
    public List<OrderEntity> findStuckOrders(OrderStatus status, int maxDurationMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(maxDurationMinutes);
        return lambdaQuery()
                .eq(OrderEntity::getStatus, status)
                .lt(OrderEntity::getStatusChangedAt, deadline)
                .orderByAsc(OrderEntity::getStatusChangedAt)
                .last("LIMIT 100")
                .list();
    }

    /**
     * 分页查询。
     */
    public PageResult<OrderEntity> pageQuery(LambdaQueryWrapper<OrderEntity> wrapper,
                                              PageParam pageParam) {
        IPage<OrderEntity> page = page(
                new Page<>(pageParam.getPageNo(), pageParam.getPageSize()), wrapper);
        return PageResult.<OrderEntity>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .pageNo((int) page.getCurrent())
                .pageSize((int) page.getSize())
                .build();
    }
}
