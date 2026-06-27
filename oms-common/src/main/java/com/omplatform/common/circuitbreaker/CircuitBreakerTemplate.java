package com.omplatform.common.circuitbreaker;

import com.omplatform.common.circuitbreaker.degrade.DegradeConfigProvider;
import com.omplatform.common.circuitbreaker.degrade.DegradeLevel;
import com.omplatform.common.circuitbreaker.degrade.NoopDegradeConfig;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 断路器实现模板（ADR-040 Part B §3.3）。
 * <p>
 * 状态机：CLOSED → OPEN → HALF_OPEN → CLOSED
 * <p>
 * <pre>
 *   CLOSED:
 *     请求正常通过，失败计数累加。
 *     连续失败达到 threshold → 迁移至 OPEN，记录熔断时间。
 *
 *   OPEN:
 *     所有请求直接走 fallback。
 *     经过 recoveryTimeoutMs 后 → 自动迁移至 HALF_OPEN。
 *
 *   HALF_OPEN:
 *     允许一个探测请求通过（其它仍走 fallback）。
 *     探测成功 → 重置计数，迁移至 CLOSED。
 *     探测失败 → 回退到 OPEN，重置恢复时间窗口。
 * </pre>
 * <p>
 * 线程安全：使用 {@link AtomicInteger} / {@link AtomicReference} + {@code volatile} 保证可见性。
 * 不依赖任何框架，可独立使用。
 */
@Slf4j
public class CircuitBreakerTemplate implements BusinessCircuitBreaker {

    /** 断路器名称 */
    private final String name;

    /** 熔断阈值 — 连续失败次数 */
    private final int threshold;

    /** 恢复超时（毫秒）— OPEN 状态持续多久后进入 HALF_OPEN */
    private final long recoveryTimeoutMs;

    /** 半开状态下允许的最大探测请求数（通常为 1） */
    private final int halfOpenMaxProbes;

    /** 当前状态 */
    private final AtomicReference<CircuitBreakerState> state;

    /** 连续失败计数 */
    private final AtomicInteger failureCount;

    /** 半开探测中已发出的请求数 */
    private final AtomicInteger halfOpenProbeCount;

    /** 上次失败时间 */
    private volatile Instant lastFailureTime;

    /** 状态变更监听器 */
    private final List<StateListener> listeners = new CopyOnWriteArrayList<>();

    /** 降级配置提供者（默认无降级） */
    private volatile DegradeConfigProvider degradeConfig = NoopDegradeConfig.INSTANCE;

    /** 降级等级阈值 — 当前等级 ≥ 此值时，断路器无条件走 fallback */
    private volatile DegradeLevel degradeThreshold = DegradeLevel.L4;

    // ========== 构造 ==========

    public CircuitBreakerTemplate(String name) {
        this(name, 50, 30_000L, 1);
    }

    /**
     * @param name              断路器名称（用于日志和监控）
     * @param threshold         连续失败次数阈值
     * @param recoveryTimeoutMs 恢复超时（毫秒）
     * @param halfOpenMaxProbes 半开最大探测请求数
     */
    public CircuitBreakerTemplate(String name, int threshold, long recoveryTimeoutMs, int halfOpenMaxProbes) {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        if (recoveryTimeoutMs <= 0) throw new IllegalArgumentException("recoveryTimeoutMs must be > 0");
        if (halfOpenMaxProbes <= 0) throw new IllegalArgumentException("halfOpenMaxProbes must be > 0");
        this.name = name;
        this.threshold = threshold;
        this.recoveryTimeoutMs = recoveryTimeoutMs;
        this.halfOpenMaxProbes = halfOpenMaxProbes;
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.halfOpenProbeCount = new AtomicInteger(0);
    }

    // ========== BusinessCircuitBreaker ==========

    @Override
    public boolean isOpen() {
        return state.get() != CircuitBreakerState.CLOSED;
    }

    @Override
    public CircuitBreakerState getState() {
        return state.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <T> T execute(String operation, Callable<T> action, Callable<T> fallback) {
        // 降级等级检查：若当前等级 ≥ degradeThreshold，直接走 fallback
        DegradeLevel currentLevel = degradeConfig.getCurrentLevel();
        if (currentLevel.ge(degradeThreshold)) {
            log.debug("[断路器] 降级中(L{}≥L{}), 走 fallback: name={}, operation={}",
                    currentLevel.getLevel(), degradeThreshold.getLevel(), name, operation);
            return invokeFallback(operation, fallback);
        }

        CircuitBreakerState current = state.get();

        switch (current) {
            case OPEN:
                // 检查是否到达恢复时间窗口
                if (Duration.between(lastFailureTime, Instant.now()).toMillis() >= recoveryTimeoutMs) {
                    if (tryTransition(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                        log.info("[断路器] {} → HALF_OPEN: name={}, operation={}", current, name, operation);
                        halfOpenProbeCount.set(0);
                    } else {
                        // 竞争失败的线程仍走 fallback
                        return invokeFallback(operation, fallback);
                    }
                } else {
                    return invokeFallback(operation, fallback);
                }
                // fall through to HALF_OPEN path
                // (intentional — no break here when successfully transitioned to HALF_OPEN)

            case HALF_OPEN: {
                int probes = halfOpenProbeCount.incrementAndGet();
                if (probes > halfOpenMaxProbes) {
                    halfOpenProbeCount.decrementAndGet();
                    // 超过探测配额，走 fallback
                    return invokeFallback(operation, fallback);
                }
                // 允许探测
                try {
                    T result = action.call();
                    onSuccess();
                    return result;
                } catch (Exception e) {
                    onFailure(e);
                    return invokeFallback(operation, fallback);
                }
            }

            case CLOSED:
            default:
                try {
                    T result = action.call();
                    onSuccess();
                    return result;
                } catch (Exception e) {
                    onFailure(e);
                    return invokeFallback(operation, fallback);
                }
        }
    }

    @Override
    public void onSuccess() {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.HALF_OPEN) {
            // 探测成功 → 恢复 CLOSED
            if (tryTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.CLOSED)) {
                log.info("[断路器] {} → CLOSED: name={}（探测成功，已恢复）", current, name);
            }
        }
        // 不论当前状态，成功时都可降低失败计数（但不能小于 0）
        int currentFailure = failureCount.get();
        if (currentFailure > 0) {
            failureCount.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    @Override
    public void onFailure(Throwable t) {
        int currentFailures = failureCount.incrementAndGet();
        lastFailureTime = Instant.now();
        CircuitBreakerState current = state.get();

        switch (current) {
            case CLOSED:
                if (currentFailures >= threshold) {
                    if (tryTransition(CircuitBreakerState.CLOSED, CircuitBreakerState.OPEN)) {
                        log.warn("[断路器] CLOSED → OPEN: name={}, failureCount={}（达阈值 {}）",
                                name, currentFailures, threshold);
                    }
                }
                break;

            case HALF_OPEN:
                // 探测失败 → 回到 OPEN，重新计时
                if (tryTransition(CircuitBreakerState.HALF_OPEN, CircuitBreakerState.OPEN)) {
                    log.warn("[断路器] HALF_OPEN → OPEN: name={}（探测失败）, failureCount={}", name, currentFailures);
                }
                break;

            case OPEN:
                // 更新 lastFailureTime 延长恢复窗口
                break;
        }
    }

    @Override
    public void reset() {
        CircuitBreakerState old = state.getAndSet(CircuitBreakerState.CLOSED);
        failureCount.set(0);
        halfOpenProbeCount.set(0);
        lastFailureTime = null;
        notifyListeners(old, CircuitBreakerState.CLOSED);
        log.info("[断路器] 手动重置 → CLOSED: name={}", name);
    }

    @Override
    public int getFailureCount() {
        return failureCount.get();
    }

    @Override
    public String getBreakerName() {
        return name;
    }

    @Override
    public void addStateListener(StateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 绑定降级配置提供者，使断路器感知全局降级等级。
     * <p>
     * 当 {@code degradeConfig.getCurrentLevel() >= degradeThreshold} 时，
     * 断路器无条件走 fallback。
     *
     * @param degradeConfig 降级配置提供者
     * @param threshold     降级触发阈值（如 L2 表示 degrade≥L2 时熔断）
     */
    public CircuitBreakerTemplate withDegradeConfig(DegradeConfigProvider degradeConfig, DegradeLevel threshold) {
        if (degradeConfig != null) this.degradeConfig = degradeConfig;
        if (threshold != null) this.degradeThreshold = threshold;
        return this;
    }

    // ========== 内部 ==========

    private <T> T invokeFallback(String operation, Callable<T> fallback) {
        log.debug("[断路器] 熔断中, 走 fallback: name={}, operation={}, state={}", name, operation, state.get());
        try {
            return fallback.call();
        } catch (Exception e) {
            // fallback 本身失败 — 抛出运行时异常（熔断已尽力）
            throw new CircuitBreakerFallbackException(name, operation, e);
        }
    }

    /**
     * CAS 状态迁移 + 通知监听器。
     */
    private boolean tryTransition(CircuitBreakerState from, CircuitBreakerState to) {
        if (state.compareAndSet(from, to)) {
            notifyListeners(from, to);
            return true;
        }
        return false;
    }

    private void notifyListeners(CircuitBreakerState oldState, CircuitBreakerState newState) {
        for (StateListener listener : listeners) {
            try {
                listener.onStateChange(oldState, newState, name);
            } catch (Exception e) {
                log.warn("[断路器] 监听器异常: name={}, err={}", name, e.getMessage());
            }
        }
    }

    // ========== 配置 ==========

    public int getThreshold() {
        return threshold;
    }

    public long getRecoveryTimeoutMs() {
        return recoveryTimeoutMs;
    }

    public int getHalfOpenMaxProbes() {
        return halfOpenMaxProbes;
    }

    public Instant getLastFailureTime() {
        return lastFailureTime;
    }

    @Override
    public String toString() {
        return String.format("CircuitBreaker{name='%s', state=%s, failures=%d/%d}",
                name, state.get(), failureCount.get(), threshold);
    }
}
