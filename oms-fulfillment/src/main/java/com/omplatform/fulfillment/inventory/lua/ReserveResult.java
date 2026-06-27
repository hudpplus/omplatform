package com.omplatform.fulfillment.inventory.lua;

/**
 * 预占库存 Lua 脚本返回结果（ADR-043 §5.1）。
 *
 * @param code    返回码（200=成功，4001=库存不足）
 * @param message 返回消息
 * @param holdId  预占 ID（成功时非空）
 * @param quantity 实际预占数量
 */
public record ReserveResult(int code, String message, String holdId, int quantity) {

    public boolean isSuccess() {
        return code == LuaResultCode.SUCCESS;
    }

    public boolean isIdempotent() {
        return code == LuaResultCode.SUCCESS && "已预占（幂等）".equals(message);
    }
}
