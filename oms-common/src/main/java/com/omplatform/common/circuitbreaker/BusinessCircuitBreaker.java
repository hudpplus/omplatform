package com.omplatform.common.circuitbreaker;

import java.util.concurrent.Callable;

/**
 * 业务断路器接口（ADR-040 Part B §3.2）。
 * <p>
 * 三层断路器体系的 L2 层：服务级熔断，适用于 Dubbo / Redis / ES 等关键依赖。
 * L1 = Sentinel（资源级），L3 = Apollo 全局降级。
 * <p>
 * 线程安全：实现类必须保证所有方法的并发可见性。
 *
 * @param <T> 业务操作返回类型
 */
public interface BusinessCircuitBreaker {

    /**
     * 当前是否处于熔断状态。
     * <p>
     * {@code true} = OPEN 或 HALF_OPEN（探测请求未发出时也算熔断）。
     */
    boolean isOpen();

    /**
     * 获取当前状态。
     */
    CircuitBreakerState getState();

    /**
     * 获取断路器名称（用于监控和日志）。
     */
    String getName();

    /**
     * 在断路器保护下执行业务操作。
     * <p>
     * <ul>
     *   <li>CLOSED → 执行业务操作，失败计入计数器</li>
     *   <li>OPEN → 直接走 fallback（若恢复窗口超时，自动进入 HALF_OPEN）</li>
     *   <li>HALF_OPEN → 允许一个探测请求通过，成功则重置，失败则回到 OPEN</li>
     * </ul>
     *
     * @param operation 操作标识（用于日志和指标）
     * @param action    业务操作
     * @param fallback  降级操作（熔断时执行，不可为 null）
     * @return 业务结果或降级结果
     */
    <T> T execute(String operation, Callable<T> action, Callable<T> fallback);

    /**
     * 成功回调 — 减少失败计数 / 重置状态。
     */
    void onSuccess();

    /**
     * 失败回调 — 递增失败计数，可能触发 OPEN 迁移。
     */
    void onFailure(Throwable t);

    /**
     * 手动重置断路器到 CLOSED 状态。
     */
    void reset();

    /**
     * 获取当前失败计数。
     */
    int getFailureCount();

    /**
     * 获取断路器名称。
     */
    String getBreakerName();

    /**
     * 注册状态变更监听器。
     */
    void addStateListener(StateListener listener);

    /** 状态变更监听器 */
    @FunctionalInterface
    interface StateListener {
        void onStateChange(CircuitBreakerState oldState, CircuitBreakerState newState, String breakerName);
    }
}
