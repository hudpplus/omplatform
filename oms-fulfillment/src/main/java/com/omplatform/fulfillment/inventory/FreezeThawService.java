package com.omplatform.fulfillment.inventory;

import com.omplatform.fulfillment.inventory.lua.InventoryLuaEngine;
import com.omplatform.fulfillment.inventory.lua.LuaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 库存冻结/解冻服务（ADR-043 §10）。
 * <p>
 * 冻结后通过 Redis 冻结标记阻止新预占，已预占订单不受影响。
 * Redis 层冻结：所有预占操作前先检查冻结标记。
 */
@Slf4j
@Service
public class FreezeThawService {

    @Autowired
    private InventoryLuaEngine luaEngine;

    /**
     * 冻结库存（Redis 标记）。
     *
     * @param skuId       SKU ID
     * @param reason      冻结原因（仅日志）
     * @param durationMin 冻结时长（分钟），null 或 <=0 表示永久冻结
     * @return true=冻结成功
     */
    public boolean freeze(String skuId, String reason, Integer durationMin) {
        long ttlSeconds = (durationMin != null && durationMin > 0) ? durationMin * 60L : -1L;
        LuaResult r = luaEngine.freeze(skuId, ttlSeconds);

        if (!r.isSuccess()) {
            log.error("Redis 冻结失败: skuId={}, reason={}, result={}", skuId, reason, r);
            return false;
        }

        log.info("库存已冻结: skuId={}, reason={}, durationMin={}, {}",
                skuId, reason, durationMin,
                ttlSeconds > 0 ? "到期自动解冻" : "永久冻结（需手动解冻）");
        return true;
    }

    /**
     * 解冻库存（移除 Redis 冻结标记）。
     */
    public boolean unfreeze(String skuId) {
        LuaResult r = luaEngine.unfreeze(skuId);
        if (!r.isSuccess()) {
            log.error("Redis 解冻失败: skuId={}, result={}", skuId, r);
            return false;
        }
        log.info("库存已解冻: skuId={}", skuId);
        return true;
    }

    /**
     * 检查 SKU 是否冻结。
     */
    public boolean isFrozen(String skuId) {
        return luaEngine.isFrozen(skuId);
    }
}
