package com.omplatform.risk.service;

import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

/**
 * 风控 Dubbo 服务实现（ADR-047）。
 * <p>
 * 被 oms-trade 在下单时同步调用，以及对账、退款场景调用。
 */
@Slf4j
@DubboService
public class RiskDubboService {

    @Autowired
    private RiskCheckService riskCheckService;

    /*public RiskDubboService(RiskCheckService riskCheckService) {
        this.riskCheckService = riskCheckService;
    }*/

    /**
     * 下单风控预检查（L0/L1/L2 三级降级）。
     */
    public ApiResult<RiskCheckService.RiskResult> preCheck(String buyerId,
                                                            String deviceId,
                                                            String orderNo,
                                                            TransitionContextDTO context) {
        log.info("[Dubbo] 风控预检查: buyerId={}, orderNo={}", buyerId, orderNo);
        RiskCheckService.RiskResult result = riskCheckService.preCheck(buyerId, deviceId, orderNo);
        return ApiResult.success(result);
    }

    /**
     * 退款风控评分（大额退款 / 高频退款触发人工审核）。
     */
    public ApiResult<RiskCheckService.RiskResult> refundCheck(String buyerId,
                                                               String orderNo,
                                                               BigDecimal refundAmount) {
        log.info("[Dubbo] 退款风控评分: buyerId={}, orderNo={}, amount={}",
                buyerId, orderNo, refundAmount);
        RiskCheckService.RiskResult result = riskCheckService.evaluateRefundRisk(
                buyerId, orderNo, refundAmount);
        return ApiResult.success(result);
    }

    /**
     * 获取当前风控降级等级。
     */
    public ApiResult<String> getDegradationLevel() {
        return ApiResult.success(riskCheckService.getCurrentLevel().name());
    }
}
