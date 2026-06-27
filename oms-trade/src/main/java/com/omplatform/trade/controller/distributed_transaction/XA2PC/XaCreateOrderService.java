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

import java.math.BigDecimal;

/**
 * XA example service (lightweight stub for documentation/build).
 *
 * This class demonstrates how an XA-managed createOrder might look. It is
 * intentionally a harmless stub (no XA wiring here) so that the project can
 * compile without Atomikos/Narayana dependencies.
 */
@Slf4j
@Service
public class XaCreateOrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InventoryService inventoryService;

    /* public XaCreateOrderService(OrderRepository orderRepository,
                                InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    } */

    public ApiResult<OrderDTO> createOrder(CreateOrderRequest request) {
        log.info("[XA-EXAMPLE] createOrder demo (no XA manager is wired)");

        // Create order entity (local DB write)
        OrderEntity order = new OrderEntity();
        order.setOrderNo("EXAMPLE-" + System.currentTimeMillis());
        order.setBuyerId(request.getBuyerId());
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setTotalAmount(request.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        orderRepository.save(order);

        // Try to hold inventory (calls another service) — InventoryService API
        // accepts a list of SkuHoldRequest. We build the payload and invoke it.
        var holds = request.getItems().stream()
                .map(i -> new InventoryService.SkuHoldRequest(i.getSkuId(), i.getQuantity()))
                .toList();
        var res = inventoryService.hold(holds, order.getOrderNo());
        if (res == null || !res.isSuccess() || Boolean.FALSE.equals(res.getData())) {
            throw new RuntimeException("库存预占失败");
        }

        log.info("[XA-EXAMPLE] createOrder completed (example only): {}", order.getOrderNo());
        return ApiResult.success(new OrderDTO());
    }
}

