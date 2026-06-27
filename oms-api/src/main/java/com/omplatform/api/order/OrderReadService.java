package com.omplatform.api.order;

import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.OrderQueryRequest;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.api.PageResult;

/**
 * 订单读服务（CQRS 查询端，Dubbo 接口）。
 * <p>
 * 由 oms-trade CQRS 模块实现，查询 Elasticsearch（Canal binlog 同步）。
 */
public interface OrderReadService {

    /**
     * 按订单号查询。
     */
    ApiResult<OrderDTO> getByOrderNo(String orderNo);

    /**
     * 按买家 ID 查询订单列表（分页 + 筛选）。
     */
    ApiResult<PageResult<OrderDTO>> queryByBuyer(OrderQueryRequest request);

    /**
     * 按商家 ID 查询订单列表。
     */
    ApiResult<PageResult<OrderDTO>> queryByShop(OrderQueryRequest request);
}
