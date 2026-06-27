package com.omplatform.common.circuitbreaker;

/**
 * 断路器状态（ADR-040 Part B §3）。
 * <p>
 * 标准三态迁移：
 * <pre>
 *   CLOSED (正常)
 *      │ 连续失败 ≥ threshold
 *      ├─────────────────────→ OPEN (熔断)
 *      │                         │ 恢复窗口超时
 *      │                         ├────→ HALF_OPEN (半开探测)
 *      │                                │ 探测成功 → CLOSED
 *      │                                │ 探测失败 → OPEN
 * </pre>
 */
public enum CircuitBreakerState {

    /** 关闭 — 正常状态，请求通过 */
    CLOSED,

    /** 开启 — 熔断状态，直接走 fallback */
    OPEN,

    /** 半开 — 允许一个探测请求通过，判断是否恢复 */
    HALF_OPEN
}
