package com.omplatform.common.constant;

/**
 * 系统级错误码常量。
 * <p>
 * 格式：5 位数字，前 2 位标识模块，后 3 位标识具体错误。
 * <ul>
 *   <li>00000 = 成功</li>
 *   <li>1xxxx = 通用错误</li>
 *   <li>2xxxx = 订单域</li>
 *   <li>3xxxx = 资金域</li>
 *   <li>4xxxx = 营销域</li>
 *   <li>5xxxx = 履约域</li>
 *   <li>9xxxx = 系统异常</li>
 * </ul>
 */
public final class ErrorCode {

    private ErrorCode() {}

    /** 成功 */
    public static final String SUCCESS = "00000";

    // ========== 通用 1xxxx ==========
    public static final String PARAM_INVALID = "10001";
    public static final String RESOURCE_NOT_FOUND = "10004";
    public static final String METHOD_NOT_ALLOWED = "10005";
    public static final String TOO_MANY_REQUESTS = "10029";
    public static final String IDEMPOTENT_CONFLICT = "10090";
    public static final String IDEMPOTENT_EXPIRED = "10091";

    // ========== 订单域 2xxxx ==========
    public static final String ORDER_NOT_FOUND = "20001";
    public static final String ORDER_STATUS_INVALID = "20002";
    public static final String ORDER_STATE_GUARD_REJECTED = "20003";
    public static final String OPTIMISTIC_LOCK_CONFLICT = "20004";
    public static final String ORDER_HOLD = "20005";
    public static final String ORDER_FROZEN = "20006";
    public static final String ORDER_CANNOT_CANCEL = "20007";

    // ========== 资金域 3xxxx ==========
    public static final String PAYMENT_FAILED = "30001";
    public static final String PAYMENT_AMOUNT_MISMATCH = "30002";
    public static final String REFUND_FAILED = "30003";

    // ========== 营销域 4xxxx ==========
    public static final String PRICE_CALCULATION_FAILED = "40001";
    public static final String PROMOTION_EXPIRED = "40002";
    public static final String COUPON_UNAVAILABLE = "40003";
    public static final String MEMBER_TIER_INVALID = "40004";

    // ========== 履约域 5xxxx ==========
    public static final String INVENTORY_SHORTAGE = "50001";
    public static final String INVENTORY_LOCK_FAILED = "50002";
    public static final String LOGISTICS_NOT_FOUND = "50003";

    // ========== 系统异常 9xxxx ==========
    public static final String SYS_ERROR = "99999";
    public static final String SERVICE_UNAVAILABLE = "99503";
    public static final String GATEWAY_TIMEOUT = "99504";
}
