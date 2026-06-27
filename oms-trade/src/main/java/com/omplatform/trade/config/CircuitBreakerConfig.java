package com.omplatform.trade.config;

import com.omplatform.common.circuitbreaker.BusinessCircuitBreaker.StateListener;
import com.omplatform.common.circuitbreaker.CircuitBreakerRegistry;
import com.omplatform.common.circuitbreaker.CircuitBreakerState;
import com.omplatform.common.circuitbreaker.CircuitBreakerTemplate;
import com.omplatform.common.circuitbreaker.degrade.DegradeConfigProvider;
import com.omplatform.common.circuitbreaker.degrade.DegradeLevel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * 断路器注册配置（ADR-040 Part B §3.4）。
 * <p>
 * 在应用启动时注册所有命名断路器实例。
 */
@Slf4j
@Configuration
public class CircuitBreakerConfig {

    @Autowired
    private CircuitBreakerRegistry registry;

    @Autowired(required = false)
    private DegradeConfigProvider degradeConfig;

    /** 通用状态变更监听器（日志 + 后续可扩展为指标上报） */
    private final StateListener logListener = (oldState, newState, name) -> {
        if (newState == CircuitBreakerState.OPEN) {
            log.warn("[断路器状态] {} → OPEN: name={}", oldState, name);
        } else if (newState == CircuitBreakerState.CLOSED && oldState != newState) {
            log.info("[断路器状态] {} → CLOSED: name={}（已恢复）", oldState, name);
        }
    };

    /**
     * 创建并注册一个断路器，绑定降级配置。
     *
     * @param name              断路器名称
     * @param threshold         连续失败阈值
     * @param recoveryMs        恢复超时(ms)
     * @param degradeThreshold  降级触发等级 — 当前 degrade ≥ 此等级时断路器无条件走 fallback
     */
    private CircuitBreakerTemplate createBreaker(String name, int threshold, long recoveryMs,
                                                  DegradeLevel degradeThreshold) {
        CircuitBreakerTemplate breaker = new CircuitBreakerTemplate(name, threshold, recoveryMs, 1);
        breaker.addStateListener(logListener);
        if (degradeConfig != null) {
            breaker.withDegradeConfig(degradeConfig, degradeThreshold);
        }
        registry.register(name, breaker);
        return breaker;
    }

    @PostConstruct
    public void init() {
        // 1. HotCache 断路器 — Redis 缓存（L1 性能降级时开始影响）
        createBreaker("hot-cache", 50, 30_000, DegradeLevel.L1);

        // 2. ES 查询断路器（L1 性能降级时开始影响）
        createBreaker("es-query", 10, 60_000, DegradeLevel.L1);

        // 3. 幂等存储断路器 — L2 功能受限时才降级（幂等重要）
        createBreaker("idempotent-store", 10, 5_000, DegradeLevel.L2);

        // 4. 支付网关断路器 — 外部支付 API（L3 核心仅存时才降）
        createBreaker("payment-gateway", 5, 30_000, DegradeLevel.L3);

        // 5. 库存 Dubbo 断路器（L2 功能受限时才降）
        createBreaker("inventory-dubbo", 20, 15_000, DegradeLevel.L2);

        // 6. 营销 Dubbo 断路器 — 成长值/积分等非核心（L1 即降级）
        createBreaker("marketing-dubbo", 20, 15_000, DegradeLevel.L1);

        log.info("[断路器配置] 已注册 {} 个断路器 (degradeConfig={})",
                registry.size(), degradeConfig != null ? "已连接" : "未连接(NOP)");
    }
}
