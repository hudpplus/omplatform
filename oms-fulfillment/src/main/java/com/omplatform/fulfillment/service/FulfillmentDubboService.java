package com.omplatform.fulfillment.service;

import com.omplatform.api.inventory.InventoryService;
import com.omplatform.api.order.dto.TransitionContextDTO;
import com.omplatform.common.api.ApiResult;
import com.omplatform.fulfillment.inventory.FreezeThawService;
import com.omplatform.fulfillment.inventory.StockQueryService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * 履约 Dubbo 服务实现（ADR-043）。
 * <p>
 * 委托 InventoryService（Redis Lua 引擎）执行原子库存操作。
 * 所有操作通过幂等键保证重复调用安全。
 */
@Slf4j
@DubboService
public class FulfillmentDubboService implements InventoryService {

    @Autowired
    private com.omplatform.fulfillment.inventory.InventoryService inventoryService;
    @Autowired
    private FreezeThawService freezeThawService;

    @Autowired
    private StockQueryService stockQueryService;

    @Override
    @SentinelResource(value = "fulfillment.hold",
            blockHandler = "holdBlock",
            blockHandlerClass = com.omplatform.fulfillment.sentinel.FulfillmentDubboBlockHandler.class)
    public ApiResult<Boolean> hold(List<SkuHoldRequest> skuQuantities, String orderNo) {
        log.info("[Dubbo] 预占库存: orderNo={}, skus={}", orderNo, skuQuantities);
        for (SkuHoldRequest req : skuQuantities) {
            // 冻结检查（前置）
            if (freezeThawService.isFrozen(req.skuId())) {
                log.warn("[Dubbo] 商品已冻结，预占拒绝: skuId={}, orderNo={}", req.skuId(), orderNo);
                return ApiResult.success(false);
            }
            boolean ok = inventoryService.hold(req.skuId(), req.quantity(), orderNo);
            if (!ok) {
                log.warn("[Dubbo] 预占失败: skuId={}, qty={}, orderNo={}", req.skuId(), req.quantity(), orderNo);
                return ApiResult.success(false);
            }
        }
        log.info("[Dubbo] 预占全部成功: orderNo={}", orderNo);
        return ApiResult.success(true);
    }

    @Override
    @SentinelResource(value = "fulfillment.deduct",
            blockHandler = "deductBlock",
            blockHandlerClass = com.omplatform.fulfillment.sentinel.FulfillmentDubboBlockHandler.class)
    public ApiResult<Boolean> deduct(String orderNo) {
        log.info("[Dubbo] 确认扣减库存: orderNo={}", orderNo);
        boolean ok = inventoryService.deduct(orderNo);
        if (ok) {
            log.info("[Dubbo] 确认扣减完成: orderNo={}", orderNo);
        } else {
            log.warn("[Dubbo] 确认扣减失败: orderNo={}", orderNo);
        }
        return ApiResult.success(ok);
    }

    @Override
    @SentinelResource(value = "fulfillment.releaseHold",
            blockHandler = "releaseHoldBlock",
            blockHandlerClass = com.omplatform.fulfillment.sentinel.FulfillmentDubboBlockHandler.class)
    public ApiResult<Boolean> releaseHold(String orderNo) {
        log.info("[Dubbo] 释放库存预占: orderNo={}", orderNo);
        boolean ok = inventoryService.releaseOrderHolds(orderNo);
        return ApiResult.success(ok);
    }

    @Override
    @SentinelResource(value = "fulfillment.restore",
            blockHandler = "restoreBlock",
            blockHandlerClass = com.omplatform.fulfillment.sentinel.FulfillmentDubboBlockHandler.class)
    public ApiResult<Boolean> restore(String orderNo, TransitionContextDTO context) {
        log.info("[Dubbo] 回滚库存（退款）: orderNo={}", orderNo);
        boolean ok = inventoryService.restore(orderNo);
        return ApiResult.success(ok);
    }

    @Override
    @SentinelResource(value = "fulfillment.batchCheckAvailability",
            blockHandler = "batchCheckAvailabilityBlock",
            blockHandlerClass = com.omplatform.fulfillment.sentinel.FulfillmentDubboBlockHandler.class)
    public ApiResult<List<SkuStockDTO>> batchCheckAvailability(List<String> skuIds) {
        // 走多级缓存查询（L1 Caffeine + L2 Redis）
        Map<String, Integer> stockMap = stockQueryService.batchGetAvailable(skuIds);
        List<SkuStockDTO> results = skuIds.stream()
                .map(skuId -> new SkuStockDTO(skuId,
                        stockMap.getOrDefault(skuId, 0), 0))
                .toList();
        return ApiResult.success(results);
    }
}
