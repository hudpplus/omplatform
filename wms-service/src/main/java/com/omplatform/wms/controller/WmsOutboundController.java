package com.omplatform.wms.controller;

import com.omplatform.common.api.ApiResult;
import com.omplatform.wms.entity.*;
import com.omplatform.wms.service.WmsOutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WMS 出库流程 REST API（Phase 3）。
 * <p>
 * 端点前缀: {@code /api/wms/v1/outbound/}
 * <br>
 * 标准流程调用顺序:
 * <pre>
 * 1. POST /outbound/order            创建出库单
 * 2. POST /outbound/allocate         分配库存（FEFO）
 * 3. POST /outbound/wave             创建波次
 * 4. POST /outbound/wave/{no}/release 生成拣货任务
 * 5. PUT  /outbound/pick/{itemId}    拣货确认
 * 6. POST /outbound/pack             打包
 * 7. POST /outbound/ship             发货（扣库存 + 同步聚合）
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/wms/v1/outbound")
@RequiredArgsConstructor
public class WmsOutboundController {

    private final WmsOutboundService outboundService;

    // ========================================================================
    // 出库单 CRUD
    // ========================================================================

    /**
     * 创建出库单。
     * POST /api/wms/v1/outbound/order
     */
    @PostMapping("/order")
    public ApiResult<Map<String, String>> createOutboundOrder(@RequestBody CreateOrderRequest req) {
        WmsOutboundOrderEntity order = new WmsOutboundOrderEntity();
        order.setOrderNo(req.orderNo());
        order.setWarehouseCode(req.warehouseCode());
        order.setPriority(req.priority());
        order.setDeliveryMethod(req.deliveryMethod());
        order.setReceiverName(req.receiverName());
        order.setReceiverPhone(req.receiverPhone());
        order.setReceiverAddress(req.receiverAddress());
        order.setExpectedAt(req.expectedAt());

        String outboundNo = outboundService.createOutboundOrder(order, req.items());
        return ApiResult.success(Map.of("outboundNo", outboundNo));
    }

    /**
     * 查询出库单。
     * GET /api/wms/v1/outbound/order/{no}
     */
    @GetMapping("/order/{no}")
    public ApiResult<WmsOutboundOrderEntity> getOutboundOrder(@PathVariable("no") String outboundNo) {
        WmsOutboundOrderEntity order = outboundService.getOutboundOrder(outboundNo);
        return order != null ? ApiResult.success(order) : ApiResult.error("NOT_FOUND", "出库单不存在");
    }

    /**
     * 查询出库单明细。
     * GET /api/wms/v1/outbound/order/{no}/items
     */
    @GetMapping("/order/{no}/items")
    public ApiResult<List<WmsOutboundItemEntity>> getOutboundItems(@PathVariable("no") String outboundNo) {
        return ApiResult.success(outboundService.getOutboundItems(outboundNo));
    }

    /**
     * 查询待分配的出库单。
     * GET /api/wms/v1/outbound/pending?warehouseCode=WH01
     */
    @GetMapping("/pending")
    public ApiResult<List<WmsOutboundOrderEntity>> findPendingAllocate(
            @RequestParam String warehouseCode) {
        return ApiResult.success(outboundService.findPendingAllocate(warehouseCode));
    }

    // ========================================================================
    // 分配
    // ========================================================================

    /**
     * 为出库单分配库存（FEFO）。
     * POST /api/wms/v1/outbound/allocate
     */
    @PostMapping("/allocate")
    public ApiResult<Map<String, Object>> allocate(@RequestBody AllocateOrdersRequest req) {
        for (String outboundNo : req.outboundNos()) {
            outboundService.allocate(outboundNo, req.opBy());
        }
        return ApiResult.success(Map.of(
                "allocated", true,
                "count", req.outboundNos().size()));
    }

    /**
     * 查询出库单的分配记录。
     * GET /api/wms/v1/outbound/{no}/allocations
     */
    @GetMapping("/{no}/allocations")
    public ApiResult<List<WmsAllocationEntity>> getAllocations(@PathVariable("no") String outboundNo) {
        return ApiResult.success(outboundService.getAllocations(outboundNo));
    }

    // ========================================================================
    // 波次
    // ========================================================================

    /**
     * 创建波次。
     * POST /api/wms/v1/outbound/wave
     */
    @PostMapping("/wave")
    public ApiResult<Map<String, String>> createWave(@RequestBody CreateWaveRequest req) {
        WmsWaveEntity wave = outboundService.createWave(
                req.warehouseCode(), req.outboundNos(), req.type());
        return ApiResult.success(Map.of("waveNo", wave.getWaveNo()));
    }

    /**
     * 释放波次（生成拣货任务）。
     * POST /api/wms/v1/outbound/wave/{no}/release
     */
    @PostMapping("/wave/{no}/release")
    public ApiResult<List<WmsPickingTaskEntity>> releaseWave(
            @PathVariable("no") String waveNo,
            @RequestParam(defaultValue = "SYSTEM") String opBy) {
        List<WmsPickingTaskEntity> tasks = outboundService.releaseWave(waveNo, opBy);
        return ApiResult.success(tasks);
    }

    /**
     * 查询波次详情。
     * GET /api/wms/v1/outbound/wave/{no}
     */
    @GetMapping("/wave/{no}")
    public ApiResult<WmsWaveEntity> getWave(@PathVariable("no") String waveNo) {
        WmsWaveEntity wave = outboundService.getWave(waveNo);
        return wave != null ? ApiResult.success(wave) : ApiResult.error("NOT_FOUND", "波次不存在");
    }

    // ========================================================================
    // 拣货
    // ========================================================================

    /**
     * 拣货确认。
     * PUT /api/wms/v1/outbound/pick/{itemId}?quantity=1&picker=张三
     */
    @PutMapping("/pick/{itemId}")
    public ApiResult<Boolean> confirmPick(
            @PathVariable Long itemId,
            @RequestParam int quantity,
            @RequestParam String picker) {
        boolean ok = outboundService.confirmPick(itemId, quantity, picker);
        return ok ? ApiResult.success(true) : ApiResult.error("PICK_FAILED", "拣货确认失败");
    }

    /**
     * 查询拣货任务。
     * GET /api/wms/v1/outbound/task/{taskNo}
     */
    @GetMapping("/task/{taskNo}")
    public ApiResult<WmsPickingTaskEntity> getPickingTask(@PathVariable String taskNo) {
        WmsPickingTaskEntity task = outboundService.getPickingTask(taskNo);
        return task != null ? ApiResult.success(task) : ApiResult.error("NOT_FOUND", "拣货任务不存在");
    }

    /**
     * 查询拣货任务明细。
     * GET /api/wms/v1/outbound/task/{taskNo}/items
     */
    @GetMapping("/task/{taskNo}/items")
    public ApiResult<List<WmsPickingItemEntity>> getPickingItems(@PathVariable String taskNo) {
        return ApiResult.success(outboundService.getPickingItems(taskNo));
    }

    // ========================================================================
    // 打包
    // ========================================================================

    /**
     * 打包记录。
     * POST /api/wms/v1/outbound/pack
     */
    @PostMapping("/pack")
    public ApiResult<Map<String, Object>> pack(@RequestBody PackRequest req) {
        WmsPackingEntity packing = outboundService.pack(
                req.outboundNo(), req.taskNo(), req.packageNo(),
                req.skuId(), req.skuName(), req.quantity(),
                req.weight(), req.length(), req.width(), req.height(),
                req.operator());
        return ApiResult.success(Map.of(
                "id", packing.getId(),
                "packageNo", packing.getPackageNo()));
    }

    /**
     * 查询出库单的包裹列表。
     * GET /api/wms/v1/outbound/{no}/packages
     */
    @GetMapping("/{no}/packages")
    public ApiResult<List<WmsPackingEntity>> getPackages(@PathVariable("no") String outboundNo) {
        return ApiResult.success(outboundService.getPackings(outboundNo));
    }

    // ========================================================================
    // 发货
    // ========================================================================

    /**
     * 发货确认（扣减库存 + 同步聚合）。
     * POST /api/wms/v1/outbound/ship
     */
    @PostMapping("/ship")
    public ApiResult<Boolean> ship(@RequestBody ShipRequest req) {
        boolean ok = outboundService.ship(
                req.outboundNo(), req.logisticsCompany(), req.logisticsNo(), req.opBy());
        return ok ? ApiResult.success(true)
                : ApiResult.error("SHIP_FAILED", "发货确认失败，请检查库存锁定状态");
    }

    // ========================================================================
    // 取消
    // ========================================================================

    /**
     * 取消出库单（释放库存）。
     * POST /api/wms/v1/outbound/{no}/cancel
     */
    @PostMapping("/{no}/cancel")
    public ApiResult<Void> cancelOutbound(
            @PathVariable("no") String outboundNo,
            @RequestParam(defaultValue = "手动取消") String reason) {
        outboundService.cancelOutbound(outboundNo, reason);
        return ApiResult.success();
    }

    // ========================================================================
    // 内部 DTO
    // ========================================================================

    public record CreateOrderRequest(
            String orderNo,
            String warehouseCode,
            Integer priority,
            String deliveryMethod,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            java.time.LocalDateTime expectedAt,
            List<WmsOutboundItemEntity> items) {}

    public record AllocateOrdersRequest(
            List<String> outboundNos,
            String opBy) {}

    public record CreateWaveRequest(
            String warehouseCode,
            List<String> outboundNos,
            String type) {}

    public record PackRequest(
            String outboundNo,
            String taskNo,
            String packageNo,
            String skuId,
            String skuName,
            int quantity,
            BigDecimal weight,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String operator) {}

    public record ShipRequest(
            String outboundNo,
            String logisticsCompany,
            String logisticsNo,
            String opBy) {}
}
