package com.omplatform.trade.service.atomic;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.tcc.TccParticipantStateClient;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class OrderCreateServiceTest {

    static class DummyTccClient implements TccParticipantStateClient {
        @Override
        public String getStatus(String txId, String participantId) { return null; }
        @Override
        public void upsertStatus(String txId, String participantId, String status, String tryData, java.time.LocalDateTime lastAttempt) {}
    }

    static class DummyStateMachine implements StateMachineEngine {
        @Override
        public com.omplatform.common.constant.OrderStatus transition(String orderId, OrderStatus current, OrderStatus target, TransitionContext context) {
            return target;
        }
        @Override public java.util.List<OrderStatus> transitionBatch(java.util.List<String> orderIds, OrderStatus current, OrderStatus target, TransitionContext context) { return null; }
        @Override public boolean canTransition(OrderStatus current, OrderStatus target) { return true; }
        @Override public java.util.Set<OrderStatus> allowedTargets(OrderStatus current) { return java.util.Set.of(); }
        @Override public void registerGuard(OrderStatus current, OrderStatus target, com.omplatform.trade.statemachine.StateGuard guard) {}
        @Override public void registerEntryAction(OrderStatus state, com.omplatform.trade.statemachine.EntryAction action) {}
        @Override public void registerExitAction(OrderStatus state, com.omplatform.trade.statemachine.ExitAction action) {}
    }

    static class InMemoryOrderRepository extends OrderRepository {
        private final Map<String, OrderEntity> map = new HashMap<>();
        @Override public boolean save(OrderEntity entity) {
            map.put(entity.getOrderNo(), entity);
            return true;
        }
        @Override
        public OrderEntity getById(java.io.Serializable id) {
            if (id == null) return null;
            return map.get(String.valueOf(id));
        }
    }

    @Test
    public void testCreateOrderFlow() {
        DummyTccClient tcc = new DummyTccClient();
        OrderCreateService svc = new OrderCreateService(tcc);

        // wire protected fields
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        TestUtils.setField(svc, "orderRepository", repo);
        TestUtils.setField(svc, "stateMachineEngine", new DummyStateMachine());

        // set objectMapper so publishEvent uses it (optional)
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        TestUtils.setField(svc, "objectMapper", om);

        // Build request
        CreateOrderRequest req = CreateOrderRequest.builder()
                .buyerId("buyer-1")
                .shopId("shop-1")
                .addressId("addr-1")
                .channelSource("APP")
                .items(java.util.List.of(
                        new CreateOrderRequest.OrderItemRequest("sku-1", 2, new BigDecimal("10.00")),
                        new CreateOrderRequest.OrderItemRequest("sku-2", 1, new BigDecimal("20.00"))
                ))
                .build();

        String orderNo = "ORD-TEST-" + System.currentTimeMillis();
        TransitionContext ctx = TransitionContext.systemContext("test");
        ctx.getExtras().put("createRequest", req);

        OrderStatus status = svc.execute(orderNo, ctx);
        Assertions.assertEquals(OrderStatus.PENDING_PAY, status);

        OrderEntity saved = repo.getById(orderNo);
        Assertions.assertNotNull(saved, "order should be persisted");
        Assertions.assertEquals("buyer-1", saved.getBuyerId());
        Assertions.assertEquals(new BigDecimal("40.00"), saved.getTotalAmount());
    }
}

