package com.omplatform.fulfillment.inventory.lua;

/**
 * Redis Lua 脚本返回码（ADR-043 §5）。
 * <p>
 * 对应 Lua 脚本返回值中 {@code result[0]} 的 code 字段。
 */
public final class LuaResultCode {

    private LuaResultCode() {}

    /** 成功（含幂等重复调用） */
    public static final int SUCCESS = 200;

    /** 库存不足 */
    public static final int INSUFFICIENT_STOCK = 4001;

    /** 商品已冻结 */
    public static final int FROZEN = 4003;

    /** 预占记录不存在或已过期 */
    public static final int HOLD_NOT_FOUND = 4004;

    /** 预占状态异常 */
    public static final int HOLD_STATUS_INVALID = 4005;

    /** 预占已确认，不可释放 */
    public static final int HOLD_CONFIRMED = 4006;
}
