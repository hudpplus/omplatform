package com.omplatform.finance.controller;

import com.omplatform.common.api.ApiResult;
import com.omplatform.finance.entity.ReconciliationRecord;
import com.omplatform.finance.entity.SettlementOrder;
import com.omplatform.finance.job.PaymentReconciliationJob;
import com.omplatform.finance.settlement.SettlementService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结算与对账 REST API（ADR-043）。
 * <p>
 * 提供日结算报告生成、结算确认、对账执行和对账结果查询功能。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final PaymentReconciliationJob reconciliationJob;

    // ========================================================================
    // 结算 API
    // ========================================================================

    /**
     * 生成指定日期的结算报告。
     * POST /api/v1/settlement/generate?date=2026-06-14
     */
    @PostMapping("/generate")
    public ApiResult<SettlementService.SettlementReport> generateReport(
            @RequestParam @NotBlank String date) {
        log.info("[API] 生成结算报告: date={}", date);

        SettlementService.SettlementReport report = settlementService.generateDailyReport(date);

        return ApiResult.success(report);
    }

    /**
     * 获取结算汇总统计。
     * GET /api/v1/settlement/summary?date=2026-06-14
     */
    @GetMapping("/summary")
    public ApiResult<SettlementService.SettlementSummary> getSummary(
            @RequestParam @NotBlank String date) {
        return ApiResult.success(settlementService.getSummary(date));
    }

    /**
     * 获取结算单明细。
     * GET /api/v1/settlement/orders?date=2026-06-14
     */
    @GetMapping("/orders")
    public ApiResult<List<SettlementOrder>> getOrders(
            @RequestParam @NotBlank String date) {
        return ApiResult.success(settlementService.getSettlementOrders(date));
    }

    /**
     * 获取指定店铺的结算单。
     * GET /api/v1/settlement/orders/shop?shopId=SHOP001&date=2026-06-14
     */
    @GetMapping("/orders/shop")
    public ApiResult<List<SettlementOrder>> getShopOrders(
            @RequestParam @NotBlank String shopId,
            @RequestParam @NotBlank String date) {
        return ApiResult.success(settlementService.getShopSettlements(shopId, date));
    }

    /**
     * 确认单笔结算。
     * PUT /api/v1/settlement/{settleNo}/confirm
     */
    @PutMapping("/{settleNo}/confirm")
    public ApiResult<Boolean> confirmSettlement(@PathVariable String settleNo) {
        boolean ok = settlementService.confirmSettlement(settleNo);
        if (ok) {
            return ApiResult.success(true);
        }
        return ApiResult.error("CONFIRM_FAILED", "结算确认失败，请检查状态");
    }

    /**
     * 批量确认结算。
     * PUT /api/v1/settlement/confirm-batch?date=2026-06-14
     */
    @PutMapping("/confirm-batch")
    public ApiResult<Integer> batchConfirm(@RequestParam @NotBlank String date) {
        int count = settlementService.batchConfirmSettlement(date);
        return ApiResult.success(count);
    }

    // ========================================================================
    // 对账 API
    // ========================================================================

    /**
     * 执行渠道对账。
     * POST /api/v1/settlement/reconciliation/run?date=2026-06-14&channel=ALIPAY
     */
    @PostMapping("/reconciliation/run")
    public ApiResult<PaymentReconciliationJob.ReconciliationResult> runReconciliation(
            @RequestParam @NotBlank String date,
            @RequestParam @NotBlank String channel) {
        log.info("[API] 执行对账: date={}, channel={}", date, channel);

        PaymentReconciliationJob.ReconciliationResult result =
                reconciliationJob.reconcileChannel(date, channel);

        return ApiResult.success(result);
    }

    /**
     * 查询对账记录。
     * GET /api/v1/settlement/reconciliation/records?date=2026-06-14&channel=ALIPAY
     */
    @GetMapping("/reconciliation/records")
    public ApiResult<List<ReconciliationRecord>> getReconciliationRecords(
            @RequestParam @NotBlank String date,
            @RequestParam(required = false) String channel) {
        return ApiResult.success(reconciliationJob.getRecords(date, channel));
    }

    /**
     * 查询未处理差异。
     * GET /api/v1/settlement/reconciliation/discrepancies
     */
    @GetMapping("/reconciliation/discrepancies")
    public ApiResult<List<ReconciliationRecord>> getDiscrepancies() {
        return ApiResult.success(reconciliationJob.getUnresolvedDiscrepancies());
    }

    /**
     * 标记差异为已处理。
     * PUT /api/v1/settlement/reconciliation/{id}/resolve?note=人工核对无误
     */
    @PutMapping("/reconciliation/{id}/resolve")
    public ApiResult<Void> resolveDiscrepancy(
            @PathVariable Long id,
            @RequestParam(required = false) String note) {
        reconciliationJob.resolveRecord(id, note);
        return ApiResult.success();
    }
}
