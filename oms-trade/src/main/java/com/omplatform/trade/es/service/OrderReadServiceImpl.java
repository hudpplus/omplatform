package com.omplatform.trade.es.service;

import com.omplatform.api.order.OrderReadService;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.OrderQueryRequest;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.api.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 订单读服务实现（CQRS 查询端）。
 * <p>
 * 优先查询 Elasticsearch，ES 不可用时回退 MySQL。
 */
@Slf4j
@DubboService
public class OrderReadServiceImpl implements OrderReadService {

    @Autowired
    private OrderEsQueryService queryService;

    @Override
    public ApiResult<OrderDTO> getByOrderNo(String orderNo) {
        return queryService.getByOrderNo(orderNo);
    }

    @Override
    public ApiResult<PageResult<OrderDTO>> queryByBuyer(OrderQueryRequest request) {
        return queryService.queryByBuyer(request);
    }

    @Override
    public ApiResult<PageResult<OrderDTO>> queryByShop(OrderQueryRequest request) {
        return queryService.queryByShop(request);
    }
}
