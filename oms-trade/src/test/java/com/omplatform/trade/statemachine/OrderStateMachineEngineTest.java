package com.omplatform.trade.statemachine;

import com.omplatform.common.constant.ErrorCode;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.common.exception.BizException;
import com.omplatform.common.exception.OptimisticLockException;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单状态机引擎单元测试（ADR-039 §1.3）。
 */
@ExtendWith(MockitoExtension.class)
class OrderStateMachineEngineTest {

    @Mock
    private OrderStateTransitionMatrix matrix;

    private OrderRepository orderRepository;  // 不 mock，用真实注入因 ByteBuddy 不支持 Java 25
    private OrderStateMachineEngine engine;
    private TransitionContext context;

    @BeforeEach
    void setUp() {
        orderRepository = mock(); // 用 mock() 而非 @Mock 减少 ByteBuddy 拦截层级
        engine = new OrderStateMachineEngine(matrix, orderRepository);
        context = new TransitionContext();
        context.setOperatorId("UT");
        context.setOperatorType("SYSTEM");
        context.setReason("单元测试");
    }

    // 辅助方法
    private void mockValidTransition(OrderStatus from, OrderStatus to) {
        when(matrix.isValid(from, to)).thenReturn(true);
        OrderEntity order = mockOrder(from, 0L);
        when(orderRepository.findByIdForUpdate(anyString())).thenReturn(order);
        when(orderRepository.updateStatusWithVersionCheck(anyString(), any(), any(), anyLong()))
                .thenReturn(1);
    }

    // ==================== 有效转换 ====================

    @Test
    @DisplayName("PENDING_PAY → PAID 有效转换应成功")
    void pendingPayToPaid_shouldSucceed() {
        mockValidTransition(OrderStatus.PENDING_PAY, OrderStatus.PAID);

        OrderStatus result = engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.PAID, context);

        assertEquals(OrderStatus.PAID, result);
        verify(orderRepository).updateStatusWithVersionCheck("ORD001",
                OrderStatus.PENDING_PAY, OrderStatus.PAID, 0L);
    }

    @Test
    @DisplayName("PAID → SHIPPED 有效转换应成功")
    void paidToShipped_shouldSucceed() {
        mockValidTransition(OrderStatus.PAID, OrderStatus.SHIPPED);

        OrderStatus result = engine.transition("ORD001", OrderStatus.PAID, OrderStatus.SHIPPED, context);

        assertEquals(OrderStatus.SHIPPED, result);
    }

    @Test
    @DisplayName("SHIPPED → DELIVERED 有效转换应成功")
    void shippedToDelivered_shouldSucceed() {
        mockValidTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED);

        OrderStatus result = engine.transition("ORD001", OrderStatus.SHIPPED, OrderStatus.DELIVERED, context);

        assertEquals(OrderStatus.DELIVERED, result);
    }

    @Test
    @DisplayName("PENDING_PAY → CANCELLED 有效转换应成功")
    void pendingPayToCancelled_shouldSucceed() {
        mockValidTransition(OrderStatus.PENDING_PAY, OrderStatus.CANCELLED);

        OrderStatus result = engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.CANCELLED, context);

        assertEquals(OrderStatus.CANCELLED, result);
    }

    // ==================== 非法转换 ====================

    @Test
    @DisplayName("PENDING_PAY → DELIVERED 非法转换应抛出 BizException")
    void invalidTransition_shouldThrow() {
        when(matrix.isValid(OrderStatus.PENDING_PAY, OrderStatus.DELIVERED)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.DELIVERED, context));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("COMPLETED → PENDING_PAY 回退应抛出 BizException")
    void completedToPendingPay_shouldThrow() {
        when(matrix.isValid(OrderStatus.COMPLETED, OrderStatus.PENDING_PAY)).thenReturn(false);

        assertThrows(BizException.class,
                () -> engine.transition("ORD001", OrderStatus.COMPLETED, OrderStatus.PENDING_PAY, context));
    }

    // ==================== 守卫条件 ====================

    @Test
    @DisplayName("守卫通过时转换应成功")
    void guardPass_shouldSucceed() {
        when(matrix.isValid(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        OrderEntity order = mockOrder(OrderStatus.PAID, 1L);
        when(orderRepository.findByIdForUpdate("ORD001")).thenReturn(order);
        when(orderRepository.updateStatusWithVersionCheck(anyString(), any(), any(), anyLong()))
                .thenReturn(1);

        engine.registerGuard(OrderStatus.PAID, OrderStatus.CANCELLED,
                (orderId, ctx) -> true);

        engine.transition("ORD001", OrderStatus.PAID, OrderStatus.CANCELLED, context);
    }

    @Test
    @DisplayName("守卫拒绝时转换应抛出 BizException")
    void guardReject_shouldThrow() {
        when(matrix.isValid(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);

        engine.registerGuard(OrderStatus.PAID, OrderStatus.CANCELLED,
                (orderId, ctx) -> false);

        BizException ex = assertThrows(BizException.class,
                () -> engine.transition("ORD001", OrderStatus.PAID, OrderStatus.CANCELLED, context));

        assertEquals(ErrorCode.ORDER_STATE_GUARD_REJECTED, ex.getCode());
    }

    // ==================== 乐观锁 ====================

    @Test
    @DisplayName("乐观锁版本不匹配时应抛出 OptimisticLockException")
    void versionMismatch_shouldThrow() {
        when(matrix.isValid(OrderStatus.PENDING_PAY, OrderStatus.PAID)).thenReturn(true);
        OrderEntity order = mockOrder(OrderStatus.PAID, 0L); // 已经是 PAID
        when(orderRepository.findByIdForUpdate("ORD001")).thenReturn(order);

        assertThrows(OptimisticLockException.class,
                () -> engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.PAID, context));
    }

    @Test
    @DisplayName("CAS 更新返回 0 行时应抛出 OptimisticLockException")
    void casUpdateZero_shouldThrow() {
        when(matrix.isValid(OrderStatus.PENDING_PAY, OrderStatus.PAID)).thenReturn(true);
        OrderEntity order = mockOrder(OrderStatus.PENDING_PAY, 0L);
        when(orderRepository.findByIdForUpdate("ORD001")).thenReturn(order);
        when(orderRepository.updateStatusWithVersionCheck(anyString(), any(), any(), anyLong()))
                .thenReturn(0);

        assertThrows(OptimisticLockException.class,
                () -> engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.PAID, context));
    }

    // ==================== 入口/出口动作 ====================

    @Test
    @DisplayName("入口动作应在状态转换后被调用")
    void entryAction_shouldBeCalled() {
        mockValidTransition(OrderStatus.PENDING_PAY, OrderStatus.PAID);

        final boolean[] entryCalled = {false};
        engine.registerEntryAction(OrderStatus.PAID,
                (orderId, from, ctx) -> entryCalled[0] = true);

        engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.PAID, context);
        assertTrue(entryCalled[0]);
    }

    @Test
    @DisplayName("出口动作应在状态转换前被调用")
    void exitAction_shouldBeCalled() {
        mockValidTransition(OrderStatus.PENDING_PAY, OrderStatus.CANCELLED);

        final boolean[] exitCalled = {false};
        engine.registerExitAction(OrderStatus.PENDING_PAY,
                (orderId, to, ctx) -> exitCalled[0] = true);

        engine.transition("ORD001", OrderStatus.PENDING_PAY, OrderStatus.CANCELLED, context);
        assertTrue(exitCalled[0]);
    }

    // ==================== 批量转换 ====================

    @Test
    @DisplayName("批量转换应全部成功")
    void batchTransition_shouldSucceed() {
        when(matrix.isValid(OrderStatus.PENDING_PAY, OrderStatus.PAID)).thenReturn(true);
        List<String> orderIds = List.of("ORD001", "ORD002");
        for (String id : orderIds) {
            OrderEntity order = mockOrder(OrderStatus.PENDING_PAY, 0L);
            when(orderRepository.findByIdForUpdate(id)).thenReturn(order);
        }
        when(orderRepository.updateStatusWithVersionCheck(anyString(), any(), any(), anyLong()))
                .thenReturn(1);

        List<OrderStatus> results = engine.transitionBatch(orderIds,
                OrderStatus.PENDING_PAY, OrderStatus.PAID, context);

        assertEquals(2, results.size());
        assertEquals(OrderStatus.PAID, results.get(0));
        assertEquals(OrderStatus.PAID, results.get(1));
    }

    // ==================== 其他 ====================

    @Test
    @DisplayName("canTransition 应委派给矩阵")
    void canTransition_shouldDelegate() {
        when(matrix.isValid(OrderStatus.PENDING_PAY, OrderStatus.PAID)).thenReturn(true);
        assertTrue(engine.canTransition(OrderStatus.PENDING_PAY, OrderStatus.PAID));

        when(matrix.isValid(OrderStatus.PAID, OrderStatus.PENDING_PAY)).thenReturn(false);
        assertFalse(engine.canTransition(OrderStatus.PAID, OrderStatus.PENDING_PAY));
    }

    @Test
    @DisplayName("skipGuards=true 时应跳过守卫检查")
    void skipGuards_shouldBypassGuard() {
        mockValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED);

        engine.registerGuard(OrderStatus.PAID, OrderStatus.CANCELLED,
                (orderId, ctx) -> false);

        context.setSkipGuards(true);

        OrderStatus result = engine.transition("ORD001", OrderStatus.PAID, OrderStatus.CANCELLED, context);
        assertEquals(OrderStatus.CANCELLED, result);
    }

    private OrderEntity mockOrder(OrderStatus status, long version) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNo("ORD001");
        entity.setStatus(status);
        entity.setVersion(version);
        return entity;
    }
}
