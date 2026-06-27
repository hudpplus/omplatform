package com.omplatform.trade.controller;

import com.omplatform.common.api.ApiResult;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.es.job.OrderEsFullSyncJob;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.statemachine.StateMachineEngine;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 客服/管理员订单操作 API（ADR-039 §3.5）。
 * <p>
 * 需要 ADMIN 角色权限（由 IGW 鉴权）。
 */
@RestController
@RequestMapping("/api/admin/v1/orders")
public class OrderAdminController {

    @Autowired
    private StateMachineEngine stateMachineEngine;
    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private OrderEsFullSyncJob esFullSyncJob;

    /* public OrderAdminController(StateMachineEngine stateMachineEngine,
                                OrderRepository orderRepository) {
        this.stateMachineEngine = stateMachineEngine;
        this.orderRepository = orderRepository;
    } */

    /**
     * 冻结订单。
     * POST /api/admin/v1/orders/{orderNo}/freeze
     */
    @PostMapping("/{orderNo}/freeze")
    public ApiResult<Void> freeze(@PathVariable String orderNo,
                                   @RequestBody FreezeRequest request) {
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }
        TransitionContext ctx = TransitionContext.systemContext("管理员冻结");
        ctx.setOperatorId("ADMIN");
        ctx.setReason(request.getReason());
        stateMachineEngine.transition(orderNo, entity.getStatus(), OrderStatus.FROZEN, ctx);
        return ApiResult.success();
    }

    /**
     * 解冻订单。
     * POST /api/admin/v1/orders/{orderNo}/unfreeze
     */
    @PostMapping("/{orderNo}/unfreeze")
    public ApiResult<Void> unfreeze(@PathVariable String orderNo,
                                     @RequestBody UnfreezeRequest request) {
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }
        OrderStatus restoreStatus = entity.getPreviousStatus() != null
                ? entity.getPreviousStatus() : OrderStatus.PENDING_PAY;
        TransitionContext ctx = TransitionContext.systemContext("管理员解冻");
        ctx.setOperatorId("ADMIN");
        ctx.setReason(request.getReason());
        stateMachineEngine.transition(orderNo, OrderStatus.FROZEN, restoreStatus, ctx);
        return ApiResult.success();
    }

    /**
     * 强制状态转换（跳过守卫，需要 SUPER_ADMIN 角色）。
     * POST /api/admin/v1/orders/{orderNo}/force-transition
     */
    @PostMapping("/{orderNo}/force-transition")
    public ApiResult<Void> forceTransition(@PathVariable String orderNo,
                                            @RequestBody ForceTransitionRequest request) {
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) {
            return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        }
        TransitionContext ctx = TransitionContext.systemContext("管理员强制转换");
        ctx.setSkipGuards(true);
        ctx.setReason(request.getReason());
        stateMachineEngine.transition(orderNo, entity.getStatus(), request.getTargetStatus(), ctx);
        return ApiResult.success();
    }

    @Data
    public static class FreezeRequest {
        private String reason;
    }

    @Data
    public static class UnfreezeRequest {
        private String reason;
    }

    @Data
    public static class ForceTransitionRequest {
        private OrderStatus targetStatus;
        private String reason;
    }

    /**
     * 手动触发 ES 全量同步。
     * POST /api/admin/v1/orders/es-sync
     */
    @PostMapping("/es-sync")
    public ApiResult<Void> triggerEsFullSync() {
        if (esFullSyncJob == null) {
            return ApiResult.error("ES_NOT_CONFIGURED", "ES 未配置");
        }
        esFullSyncJob.execute();
        return ApiResult.success();
    }
}
