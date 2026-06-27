package com.omplatform.fulfillment.inventory.lua;

/**
 * 通用 Lua 脚本返回结果（confirm / release / undo / freeze）。
 *
 * @param code    返回码
 * @param message 返回消息
 */
public record LuaResult(int code, String message) {

    public boolean isSuccess() {
        return code == LuaResultCode.SUCCESS;
    }
}
