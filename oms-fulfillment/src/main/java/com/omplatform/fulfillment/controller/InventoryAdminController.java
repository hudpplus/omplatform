package com.omplatform.fulfillment.controller;

import com.omplatform.common.api.ApiResult;
import com.omplatform.fulfillment.inventory.FreezeThawService;
import com.omplatform.fulfillment.inventory.InventoryService;
import com.omplatform.fulfillment.repository.entity.InventoryEntity;
import com.omplatform.fulfillment.repository.entity.InventoryHoldEntity;
import com.omplatform.fulfillment.repository.entity.InventoryTransactionEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库存管理后台 REST API（ADR-043 §8.2）。
 * <p>
 * 提供运营人员管理库存的接口：查询/调整/冻结/解冻/查看预占与流水。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1/inventory")
@RequiredArgsConstructor
public class InventoryAdminController {

    private final InventoryService inventoryService;
    private final FreezeThawService freezeThawService;

    // ========================================================================
    // 库存查询
    // ========================================================================

    /**
     * 库存列表（分页暂简化，全量返回）。
     * GET /api/admin/v1/inventory/stock
     */
    @GetMapping("/stock")
    public ApiResult<List<InventoryEntity>> listStock() {
        return ApiResult.success(inventoryService.listAllStock());
    }

    /**
     * 单 SKU 库存查询。
     * GET /api/admin/v1/inventory/stock/{skuId}
     */
    @GetMapping("/stock/{skuId}")
    public ApiResult<Map<String, Object>> getStock(@PathVariable String skuId) {
        int available = inventoryService.getAvailable(skuId);
        // 从 DB 查询完整记录
        List<InventoryEntity> list = inventoryService.listAllStock();
        InventoryEntity entity = list.stream()
                .filter(e -> e.getSkuId().equals(skuId))
                .findFirst().orElse(null);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("skuId", skuId);
        data.put("availableQuantity", available);
        data.put("frozen", freezeThawService.isFrozen(skuId));
        if (entity != null) {
            data.put("totalQuantity", entity.getTotalQuantity());
            data.put("holdQuantity", entity.getHoldQuantity());
            data.put("deductedQuantity", entity.getDeductedQuantity());
        }
        return ApiResult.success(data);
    }

    /**
     * 批量库存查询。
     * POST /api/admin/v1/inventory/stock/batch
     * Body: ["SKU001", "SKU002"]
     */
    @PostMapping("/stock/batch")
    public ApiResult<Map<String, Integer>> batchQuery(@RequestBody List<String> skuIds) {
        Map<String, Integer> stockMap = inventoryService.batchGetAvailable(skuIds);
        return ApiResult.success(stockMap);
    }

    // ========================================================================
    // 库存调整
    // ========================================================================

    /**
     * 调整库存。
     * PUT /api/admin/v1/inventory/stock/{skuId}/adjust
     * Body: { "delta": 100, "reason": "补货" }
     */
    @PutMapping("/stock/{skuId}/adjust")
    public ApiResult<Map<String, Object>> adjustStock(
            @PathVariable String skuId,
            @RequestBody AdjustRequest request) {
        try {
            int after = inventoryService.adjust(skuId, request.delta(), request.reason());
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("skuId", skuId);
            result.put("afterQuantity", after);
            result.put("delta", request.delta());
            result.put("reason", request.reason());
            return ApiResult.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResult.error("INVALID_ADJUST", e.getMessage());
        }
    }

    /**
     * 从 MySQL 重载 Redis 库存（修复不一致）。
     * POST /api/admin/v1/inventory/stock/{skuId}/reload
     */
    @PostMapping("/stock/{skuId}/reload")
    public ApiResult<Integer> reloadStock(@PathVariable String skuId) {
        int available = inventoryService.reloadFromDb(skuId);
        return ApiResult.success(available);
    }

    // ========================================================================
    // 冻结/解冻
    // ========================================================================

    /**
     * 冻结商品库存。
     * POST /api/admin/v1/inventory/stock/{skuId}/freeze
     * Body: { "reason": "活动结束停售", "durationMin": 1440 }
     */
    @PostMapping("/stock/{skuId}/freeze")
    public ApiResult<Boolean> freezeStock(
            @PathVariable String skuId,
            @RequestBody(required = false) FreezeRequest request) {
        String reason = request != null ? request.reason() : "管理员操作";
        Integer durationMin = request != null ? request.durationMin() : null;
        boolean ok = freezeThawService.freeze(skuId, reason, durationMin);
        if (ok) {
            return ApiResult.success(true);
        }
        return ApiResult.error("FREEZE_FAILED", "冻结失败");
    }

    /**
     * 解冻商品库存。
     * POST /api/admin/v1/inventory/stock/{skuId}/unfreeze
     */
    @PostMapping("/stock/{skuId}/unfreeze")
    public ApiResult<Boolean> unfreezeStock(@PathVariable String skuId) {
        boolean ok = freezeThawService.unfreeze(skuId);
        if (ok) {
            return ApiResult.success(true);
        }
        return ApiResult.error("UNFREEZE_FAILED", "解冻失败");
    }

    // ========================================================================
    // 预占记录与流水
    // ========================================================================

    /**
     * 查询预占记录。
     * GET /api/admin/v1/inventory/holds?orderNo=ORD001&skuId=SKU001
     */
    @GetMapping("/holds")
    public ApiResult<List<InventoryHoldEntity>> getHolds(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String skuId) {
        if (orderNo != null) {
            return ApiResult.success(inventoryService.getOrderHolds(orderNo));
        }
        if (skuId != null) {
            return ApiResult.success(inventoryService.getSkuHolds(skuId));
        }
        return ApiResult.error("PARAM_MISSING", "请提供 orderNo 或 skuId");
    }

    /**
     * 查询库存流水。
     * GET /api/admin/v1/inventory/transactions?skuId=SKU001&orderNo=ORD001&type=RESERVE&limit=50
     */
    @GetMapping("/transactions")
    public ApiResult<List<InventoryTransactionEntity>> getTransactions(
            @RequestParam(required = false) String skuId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") @Min(1) int limit) {
        return ApiResult.success(inventoryService.queryTransactions(skuId, orderNo, type, limit));
    }

    // ========================================================================
    // DTO
    // ========================================================================

    /**
     * 调账请求。
     */
    public record AdjustRequest(
            @Min(Integer.MIN_VALUE) int delta,
            @NotBlank String reason
    ) {}

    /**
     * 冻结请求。
     */
    public record FreezeRequest(
            String reason,
            Integer durationMin
    ) {}
}
