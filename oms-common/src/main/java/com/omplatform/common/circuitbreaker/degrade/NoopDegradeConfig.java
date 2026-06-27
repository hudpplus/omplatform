package com.omplatform.common.circuitbreaker.degrade;

/**
 * 无 Apollo 时的默认降级配置 — 所有功能开启，等级 L0。
 */
public final class NoopDegradeConfig implements DegradeConfigProvider {

    public static final NoopDegradeConfig INSTANCE = new NoopDegradeConfig();

    @Override
    public DegradeLevel getCurrentLevel() {
        return DegradeLevel.L0;
    }

    @Override
    public boolean isComponentEnabled(String componentName) {
        return true;
    }

    @Override
    public boolean isFeatureEnabled(String featureName) {
        return true;
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        return defaultValue;
    }

    @Override
    public long getLongConfig(String key, long defaultValue) {
        return defaultValue;
    }
}
