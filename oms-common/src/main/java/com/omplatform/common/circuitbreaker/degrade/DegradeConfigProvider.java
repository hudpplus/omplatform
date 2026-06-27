package com.omplatform.common.circuitbreaker.degrade;

/**
 * 降级配置提供者接口 — 统一读取降级策略（ADR-040 Part B §3 / degradation-strategy.md §6）。
 * <p>
 * 各服务注入此接口，由底层实现决定配置来源（Apollo / 本地文件 / 默认值）。
 * 无 Apollo 时可回退 {@link #noop()} 实现（所有功能开启）。
 */
public interface DegradeConfigProvider {

    /** 获取当前系统降级等级（默认 L0）。 */
    DegradeLevel getCurrentLevel();

    /**
     * 指定组件在当前降级等级下是否可用。
     * <p>
     * 组件名称对应 degradation-strategy.md §4 的组件名：
     * {@code redis-cache}, {@code es-query}, {@code webhook}, {@code export}, {@code notification} ...
     */
    boolean isComponentEnabled(String componentName);

    /**
     * 指定功能在当前降级等级下是否可用。
     * <p>
     * 功能名称对应 {@code degrade-control} 命名空间的 feature.* 开关：
     * {@code webhook}, {@code export}, {@code report}, {@code notification} ...
     */
    boolean isFeatureEnabled(String featureName);

    /**
     * 获取断路器阈值（可被 Apollo 动态覆盖）。
     *
     * @param breakerName    断路器名称（如 "hot-cache", "es-query"）
     * @param defaultThreshold 默认阈值（当 Apollo 未配置时返回此值）
     */
    int getIntConfig(String key, int defaultValue);

    /**
     * 获取断路器恢复超时（可被 Apollo 动态覆盖）。
     */
    long getLongConfig(String key, long defaultValue);

    /**
     * 注册降级等级变更监听器（例如用于日志或指标更新）。
     */
    default void addLevelChangeListener(LevelChangeListener listener) {}

    /** 降级等级变更监听器 */
    @FunctionalInterface
    interface LevelChangeListener {
        void onLevelChange(DegradeLevel oldLevel, DegradeLevel newLevel);
    }

    /** 创建一个无 Apollo 时的默认实现（所有功能开启，L0 等级）。 */
    static DegradeConfigProvider noop() {
        return NoopDegradeConfig.INSTANCE;
    }
}
