package com.omplatform.api.order;

import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;

/**
 * Saga 编排回调接口（Dubbo，由 oms-trade 实现）。
 * <p>
 * Saga 编排器在正向执行或补偿完成后，通过此接口通知订单服务更新状态。
 */
public interface SagaCallbackService {

    /**
     * Saga 正向执行完成通知。
     */
    ApiResult<Void> onSagaCompleted(String sagaId, String orderNo, TransitionContextDTO context);

    /**
     * Saga 补偿完成通知。
     */
    ApiResult<Void> onSagaCompensated(String sagaId, String orderNo, TransitionContextDTO context);

    /**
     * Saga 执行失败通知（需人工介入）。
     */
    ApiResult<Void> onSagaFailed(String sagaId, String orderNo, String failedStep,
                                  String errorMessage);
}
