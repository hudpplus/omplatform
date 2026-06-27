package com.omplatform.common.circuitbreaker;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 断路器注册表（ADR-040 Part B §3.4）。
 * <p>
 * 管理所有命名断路器的生命周期，提供统一注册/获取/监控能力。
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 在 @Configuration 中注册
 * registry.register("hot-cache", () -> new CircuitBreakerTemplate("hot-cache", 50, 30_000, 1));
 *
 * // 在业务代码中获取
 * BusinessCircuitBreaker breaker = registry.get("hot-cache");
 * String result = breaker.execute("cache.get", () -> redisGet(key), () -> esFallback(key));
 * }</pre>
 */
@Slf4j
@Component
public class CircuitBreakerRegistry {

    /** 断路器实例缓存 */
    private final Map<String, BusinessCircuitBreaker> breakers = new ConcurrentHashMap<>();

    /** 断路器工厂（延迟创建） */
    private final Map<String, Supplier<BusinessCircuitBreaker>> factories = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[断路器注册表] 已初始化");
    }

    /**
     * 注册一个断路器（立即创建）。
     *
     * @param name   断路器名称
     * @param breaker 断路器实例
     * @return 之前注册的同名断路器（可能为 null）
     */
    public BusinessCircuitBreaker register(String name, BusinessCircuitBreaker breaker) {
        BusinessCircuitBreaker prev = breakers.put(name, breaker);
        if (prev != null) {
            log.warn("[断路器注册表] 覆盖已有断路器: name={}", name);
        } else {
            log.info("[断路器注册表] 注册: name={}, threshold={}",
                    name, breaker instanceof CircuitBreakerTemplate t ? t.getThreshold() : "?");
        }
        return prev;
    }

    /**
     * 注册一个断路器工厂（延迟创建，首次 get 时初始化）。
     *
     * @param name    断路器名称
     * @param factory 断路器工厂
     */
    public void registerFactory(String name, Supplier<BusinessCircuitBreaker> factory) {
        factories.put(name, factory);
        log.info("[断路器注册表] 注册工厂: name={}", name);
    }

    /**
     * 获取已注册的断路器。
     *
     * @param name 断路器名称
     * @return 断路器实例，未注册返回 null
     */
    public BusinessCircuitBreaker get(String name) {
        BusinessCircuitBreaker breaker = breakers.get(name);
        if (breaker != null) return breaker;

        // 尝试从工厂创建
        Supplier<BusinessCircuitBreaker> factory = factories.get(name);
        if (factory != null) {
            breaker = factory.get();
            if (breaker != null) {
                breakers.put(name, breaker);
                log.info("[断路器注册表] 从工厂创建: name={}", name);
                return breaker;
            }
        }

        return null;
    }

    /**
     * 获取或创建一个断路器（如果尚未注册）。
     *
     * @param name      断路器名称
     * @param supplier  创建新断路器的工厂
     * @return 已有或新建的断路器
     */
    public BusinessCircuitBreaker getOrCreate(String name, Supplier<BusinessCircuitBreaker> supplier) {
        return breakers.computeIfAbsent(name, k -> {
            BusinessCircuitBreaker breaker = supplier.get();
            log.info("[断路器注册表] 自动创建: name={}", name);
            return breaker;
        });
    }

    /**
     * 获取所有已注册的断路器名称。
     */
    public Map<String, BusinessCircuitBreaker> getAll() {
        return Map.copyOf(breakers);
    }

    /**
     * 移除断路器。
     *
     * @param name 断路器名称
     * @return 被移除的断路器，不存在返回 null
     */
    public BusinessCircuitBreaker remove(String name) {
        BusinessCircuitBreaker removed = breakers.remove(name);
        factories.remove(name);
        if (removed != null) {
            log.info("[断路器注册表] 移除: name={}", name);
        }
        return removed;
    }

    /**
     * 重置所有断路器到 CLOSED 状态。
     */
    public void resetAll() {
        breakers.values().forEach(BusinessCircuitBreaker::reset);
        log.info("[断路器注册表] 已重置所有断路器");
    }

    /**
     * 获取断路器数量。
     */
    public int size() {
        return breakers.size();
    }

    /**
     * 是否包含指定断路器。
     */
    public boolean contains(String name) {
        return breakers.containsKey(name) || factories.containsKey(name);
    }
}
