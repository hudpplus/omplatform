package com.omplatform.wms.controller;

import com.omplatform.common.api.ApiResult;
import com.omplatform.wms.entity.*;
import com.omplatform.wms.service.WmsInventoryService;
import com.omplatform.wms.service.WmsLocationService;
import com.omplatform.wms.service.WmsWarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WMS 基础数据 REST API（Phase 1）。
 * <p>
 * 仓库/库区/库位管理 + 多维库存查询与操作。
 */
@Slf4j
@RestController
@RequestMapping("/api/wms/v1")
@RequiredArgsConstructor
public class WmsBaseController {

    private final WmsWarehouseService warehouseService;
    private final WmsLocationService locationService;
    private final WmsInventoryService inventoryService;

    // ========================================================================
    // 仓库
    // ========================================================================

    @GetMapping("/warehouses")
    public ApiResult<List<WmsWarehouseEntity>> listWarehouses() {
        return ApiResult.success(warehouseService.listWarehouses());
    }

    @GetMapping("/warehouses/{code}")
    public ApiResult<WmsWarehouseEntity> getWarehouse(@PathVariable String code) {
        WmsWarehouseEntity w = warehouseService.getWarehouse(code);
        return w != null ? ApiResult.success(w) : ApiResult.error("NOT_FOUND", "仓库不存在");
    }

    @PostMapping("/warehouses")
    public ApiResult<Void> createWarehouse(@RequestBody WmsWarehouseEntity entity) {
        warehouseService.createWarehouse(entity);
        return ApiResult.success();
    }

    @PutMapping("/warehouses")
    public ApiResult<Void> updateWarehouse(@RequestBody WmsWarehouseEntity entity) {
        warehouseService.updateWarehouse(entity);
        return ApiResult.success();
    }

    // ========================================================================
    // 库区
    // ========================================================================

    @GetMapping("/zones")
    public ApiResult<List<WmsZoneEntity>> listZones(@RequestParam String warehouseCode) {
        return ApiResult.success(warehouseService.listZones(warehouseCode));
    }

    @PostMapping("/zones")
    public ApiResult<Void> createZone(@RequestBody WmsZoneEntity entity) {
        warehouseService.createZone(entity);
        return ApiResult.success();
    }

    // ========================================================================
    // 库位
    // ========================================================================

    @GetMapping("/locations")
    public ApiResult<List<WmsLocationEntity>> listLocations(
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String zoneCode) {
        return ApiResult.success(locationService.listLocations(warehouseCode, zoneCode));
    }

    @PostMapping("/locations")
    public ApiResult<Void> createLocation(@RequestBody WmsLocationEntity entity) {
        locationService.createLocation(entity);
        return ApiResult.success();
    }

    @PostMapping("/locations/batch")
    public ApiResult<Void> createLocationBatch(@RequestBody List<WmsLocationEntity> locations) {
        locationService.createLocationBatch(locations);
        return ApiResult.success();
    }

    /**
     * 推荐上架库位。
     * GET /api/wms/v1/locations/suggest-putaway?warehouseCode=WH01&skuId=SKU001&type=PICK&limit=5
     */
    @GetMapping("/locations/suggest-putaway")
    public ApiResult<List<WmsLocationEntity>> suggestPutaway(
            @RequestParam String warehouseCode,
            @RequestParam String skuId,
            @RequestParam(defaultValue = "PICK") String type,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResult.success(
                locationService.suggestPutawayLocations(warehouseCode, skuId, type, limit));
    }

    // ========================================================================
    // 批次库存
    // ========================================================================

    /**
     * 查询 SKU 在各库位的库存明细。
     * GET /api/wms/v1/inventory?skuId=SKU001
     */
    @GetMapping("/inventory")
    public ApiResult<List<WmsInventoryEntity>> getSkuInventory(
            @RequestParam String skuId) {
        return ApiResult.success(inventoryService.getSkuInventory(skuId));
    }

    /**
     * 查询库位中的库存。
     * GET /api/wms/v1/inventory/by-location?locationCode=A-01-01-1-1
     */
    @GetMapping("/inventory/by-location")
    public ApiResult<List<WmsInventoryEntity>> getLocationInventory(
            @RequestParam String locationCode) {
        return ApiResult.success(inventoryService.getLocationInventory(locationCode));
    }

    /**
     * 查询 SKU 总可用量。
     * GET /api/wms/v1/inventory/available?skuId=SKU001
     */
    @GetMapping("/inventory/available")
    public ApiResult<Map<String, Object>> getAvailable(@RequestParam String skuId) {
        int available = inventoryService.getAvailableQuantity(skuId);
        return ApiResult.success(Map.of("skuId", skuId, "availableQuantity", available));
    }

    // ========================================================================
    // 库存操作
    // ========================================================================

    /**
     * 入库上架。
     * POST /api/wms/v1/inventory/receive
     */
    @PostMapping("/inventory/receive")
    public ApiResult<Map<String, Object>> receiveStock(@RequestBody ReceiveRequest req) {
        Long id = inventoryService.receiveStock(
                req.skuId(), req.warehouseCode(), req.locationCode(),
                req.batchNo(), req.quantity(), req.expireDate(),
                req.ownerCode(), req.opBy());
        return ApiResult.success(Map.of("id", id, "skuId", req.skuId(), "quantity", req.quantity()));
    }

    /**
     * 出库分配。
     * POST /api/wms/v1/inventory/allocate
     */
    @PostMapping("/inventory/allocate")
    public ApiResult<Map<String, Object>> allocateStock(@RequestBody AllocateRequest req) {
        int allocated = inventoryService.allocateStock(
                req.skuId(), req.warehouseCode(), req.quantity(),
                req.refNo(), req.opBy());
        return ApiResult.success(Map.of(
                "skuId", req.skuId(), "requested", req.quantity(),
                "allocated", allocated,
                "fullMatch", allocated >= req.quantity()));
    }

    /**
     * 确认出库（发货后扣减）。
     * POST /api/wms/v1/inventory/confirm-ship
     */
    @PostMapping("/inventory/confirm-ship")
    public ApiResult<Boolean> confirmShip(@RequestBody ConfirmShipRequest req) {
        boolean ok = inventoryService.confirmShip(req.inventoryId(), req.quantity(), req.refNo(), req.opBy());
        return ok ? ApiResult.success(true) : ApiResult.error("SHIP_FAILED", "出库确认失败");
    }

    /**
     * 库内移动。
     * POST /api/wms/v1/inventory/move
     */
    @PostMapping("/inventory/move")
    public ApiResult<Boolean> moveStock(@RequestBody MoveRequest req) {
        boolean ok = inventoryService.moveStock(req.inventoryId(), req.toLocation(), req.quantity(), req.opBy());
        return ok ? ApiResult.success(true) : ApiResult.error("MOVE_FAILED", "库内移动失败");
    }

    /**
     * 库存调整。
     * POST /api/wms/v1/inventory/adjust
     */
    @PostMapping("/inventory/adjust")
    public ApiResult<Void> adjustStock(@RequestBody AdjustRequest req) {
        inventoryService.adjustStock(req.inventoryId(), req.newQuantity(), req.reason(), req.opBy());
        return ApiResult.success();
    }

    // ========================================================================
    // 流水
    // ========================================================================

    @GetMapping("/transactions")
    public ApiResult<List<WmsInventoryTransactionEntity>> queryTransactions(
            @RequestParam(required = false) String skuId,
            @RequestParam(required = false) String refNo,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.success(inventoryService.queryTransactions(skuId, refNo, limit));
    }

    // ========================================================================
    // DTO
    // ========================================================================

    public record ReceiveRequest(
            String skuId, String warehouseCode, String locationCode,
            String batchNo, int quantity, java.time.LocalDate expireDate,
            String ownerCode, String opBy) {}

    public record AllocateRequest(
            String skuId, String warehouseCode, int quantity,
            String refNo, String opBy) {}

    public record ConfirmShipRequest(
            Long inventoryId, int quantity, String refNo, String opBy) {}

    public record MoveRequest(
            Long inventoryId, String toLocation, int quantity, String opBy) {}

    public record AdjustRequest(
            Long inventoryId, int newQuantity, String reason, String opBy) {}
}
