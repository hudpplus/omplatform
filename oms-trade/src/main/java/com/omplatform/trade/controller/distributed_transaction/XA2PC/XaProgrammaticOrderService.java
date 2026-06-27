package com.omplatform.trade.controller.distributed_transaction.XA2PC;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.api.inventory.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@Slf4j
@Service
public class XaProgrammaticOrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PlatformTransactionManager txManager;

    /* public XaProgrammaticOrderService(OrderRepository orderRepository,
                                      InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    } */

    public ApiResult<OrderDTO> createOrderProgrammatic(CreateOrderRequest request) {
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setTimeout(30);

        return txTemplate.execute(status -> {
            // Minimal example: insert order and call inventory hold.
            OrderEntity order = new OrderEntity();
            order.setOrderNo("EX-PGM-" + System.currentTimeMillis());
            order.setBuyerId(request.getBuyerId());
            order.setStatus(OrderStatus.PENDING_PAY);
            order.setTotalAmount(request.getItems().stream()
                    .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            orderRepository.save(order);

            var holds = request.getItems().stream()
                    .map(i -> new InventoryService.SkuHoldRequest(i.getSkuId(), i.getQuantity()))
                    .toList();
            var res = inventoryService.hold(holds, order.getOrderNo());
            if (res == null || !res.isSuccess() || Boolean.FALSE.equals(res.getData())) {
                status.setRollbackOnly();
                return ApiResult.error("STOCK_INSUFFICIENT", "库存不足");
            }

            return ApiResult.success(null);
        });
    }
}

