package com.omplatform.trade.dubbo;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.seckill.SeckillOrderSagaService;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.event.OrderEventPublisher;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.saga.SagaContext;
import com.omplatform.trade.saga.SagaDefinition;
import com.omplatform.trade.saga.SagaExecutor;
import com.omplatform.trade.saga.SagaResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 秒杀订单 Saga Dubbo 服务实现。
 * <p>
 * 包装 oms-trade 内部能力（SagaExecutor + OrderRepository + OrderEventPublisher），
 * 以 Dubbo 接口形式暴露给 seckill-service 调用。
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class SeckillOrderSagaDubboService implements SeckillOrderSagaService {

    private final SagaExecutor sagaExecutor;
    private final SagaDefinition createOrderSaga;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    @Override
    public ApiResult<Boolean> createSeckillOrder(CreateOrderRequest request) {
        log.info("[秒杀Saga] 执行订单创建: activityId={}, buyerId={}",
                request.getSeckillActivityId(), request.getBuyerId());

        // 构建 SagaContext
        SagaContext sagaContext = SagaContext.create(
                "saga-" + System.currentTimeMillis(), "createOrder", "seckill");
        sagaContext.setStepArg("createRequest", request);

        SagaResult sagaResult;
        try {
            sagaResult = sagaExecutor.execute(createOrderSaga, sagaContext);
        } catch (Exception e) {
            log.error("[秒杀Saga] 执行异常: {}", e.getMessage(), e);
            return ApiResult.success(false);
        }

        if (sagaResult.isSuccess()) {
            log.info("[秒杀Saga] 执行成功");
            return ApiResult.success(true);
        } else {
            log.warn("[秒杀Saga] 执行失败: step={}, error={}",
                    sagaResult.getFailedStep(), sagaResult.getErrorMessage());
            return ApiResult.success(false);
        }
    }

    @Override
    public ApiResult<OrderDTO> getByOrderNo(String orderNo) {
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.success(null);
        }
        return ApiResult.success(toDTO(entity));
    }

    @Override
    public ApiResult<Long> countBuyerOrders(String buyerId, Long activityId) {
        long count = orderRepository.lambdaQuery()
                .eq(OrderEntity::getBuyerId, buyerId)
                .eq(OrderEntity::getSeckillActivityId, activityId)
                .in(OrderEntity::getStatus, List.of("PENDING_PAY", "PAID", "TO_SHIP", "SHIPPED"))
                .count();
        return ApiResult.success(count);
    }

    @Override
    public ApiResult<Void> publishOrderCreated(String orderNo, String buyerId, String status) {
        try {
            eventPublisher.orderCreated(orderNo, buyerId, OrderStatus.PENDING_PAY, "ecommerce");
            log.info("[秒杀Saga] 已发布订单创建事件: orderNo={}", orderNo);
        } catch (Exception e) {
            log.warn("[秒杀Saga] 发布事件异常（不影响主流程）: {}", e.getMessage());
        }
        return ApiResult.success(null);
    }

    // ========== DTO 转换 ==========

    private OrderDTO toDTO(OrderEntity entity) {
        if (entity == null) return null;
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(entity.getOrderNo());
        dto.setBuyerId(entity.getBuyerId());
        dto.setShopId(entity.getShopId());
        dto.setStatus(entity.getStatus());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setPayAmount(entity.getPayAmount());
        dto.setFreightAmount(entity.getFreightAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setRemark(entity.getRemark());
        dto.setAddressId(entity.getAddressId());
        dto.setStatusChangedAt(entity.getStatusChangedAt());
        dto.setSeckillActivityId(entity.getSeckillActivityId());
        dto.setSeckillPipeline(entity.getSeckillPipeline());
        return dto;
    }
}
