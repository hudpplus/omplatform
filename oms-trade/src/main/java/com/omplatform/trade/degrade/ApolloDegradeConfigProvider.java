package com.omplatform.trade.degrade;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigChangeListener;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.omplatform.common.circuitbreaker.degrade.DegradeConfigProvider;
import com.omplatform.common.circuitbreaker.degrade.DegradeLevel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Apollo 降级配置提供者（degradation-strategy.md §6）。
 * <p>
 * 从 Apollo {@code degrade-control} 命名空间读取全局降级等级和组件开关。
 * 本地开发无 Apollo 时回退 {@link DegradeConfigProvider#noop()}。
 * <p>
 * Apollo 配置示例（Namespace: degrade-control）：
 * <pre>
 * degrade.global.level = L0         # 全局降级等级 L0/L1/L2/L3/L4
 * component.redis-cache = true      # Redis 缓存组件是否可用
 * component.es-query = true         # ES 查询组件是否可用
 * feature.webhook = true            # Webhook 功能是否可用
 * circuit-breaker.hot-cache.threshold = 50
 * circuit-breaker.hot-cache.recovery-ms = 30000
 * </pre>
 */
@Slf4j
@Component
public class ApolloDegradeConfigProvider implements DegradeConfigProvider {

    /** Apollo namespace 名称 */
    private static final String NAMESPACE = "degrade-control";

    private final AtomicReference<DegradeLevel> currentLevel = new AtomicReference<>(DegradeLevel.L0);

    private final List<LevelChangeListener> levelListeners = new CopyOnWriteArrayList<>();

    private Config config;

    @PostConstruct
    public void init() {
        try {
            config = ConfigService.getConfig(NAMESPACE);

            // 读取初始降级等级
            String levelStr = config.getProperty("degrade.global.level", "L0");
            currentLevel.set(DegradeLevel.fromString(levelStr));
            log.info("[降级配置] 已连接 Apollo, namespace={}, 初始等级={}", NAMESPACE, currentLevel.get());

            // 监听配置变化
            config.addChangeListener(new ConfigChangeListener() {
                @Override
                public void onChange(ConfigChangeEvent changeEvent) {
                    for (String key : changeEvent.changedKeys()) {
                        ConfigChange change = changeEvent.getChange(key);
                        log.info("[降级配置] 变更: key={}, oldValue={}, newValue={}, changeType={}",
                                key, change.getOldValue(), change.getNewValue(), change.getChangeType());

                        if ("degrade.global.level".equals(key)) {
                            DegradeLevel oldLevel = currentLevel.get();
                            DegradeLevel newLevel = DegradeLevel.fromString(change.getNewValue());
                            currentLevel.set(newLevel);
                            log.warn("[降级配置] 降级等级变更: {} → {} (λ{})",
                                    oldLevel.getDisplayName(), newLevel.getDisplayName(), newLevel.getLevel());
                            notifyLevelChange(oldLevel, newLevel);
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.warn("[降级配置] Apollo 连接失败, 使用默认配置(L0): {}", e.getMessage());
            currentLevel.set(DegradeLevel.L0);
        }
    }

    @Override
    public DegradeLevel getCurrentLevel() {
        return currentLevel.get();
    }

    @Override
    public boolean isComponentEnabled(String componentName) {
        if (config == null) return true;
        return config.getBooleanProperty("component." + componentName, true);
    }

    @Override
    public boolean isFeatureEnabled(String featureName) {
        if (config == null) return true;
        return config.getBooleanProperty("feature." + featureName, true);
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        if (config == null) return defaultValue;
        return config.getIntProperty(key, defaultValue);
    }

    @Override
    public long getLongConfig(String key, long defaultValue) {
        if (config == null) return defaultValue;
        return config.getLongProperty(key, defaultValue);
    }

    @Override
    public void addLevelChangeListener(LevelChangeListener listener) {
        if (listener != null) {
            levelListeners.add(listener);
        }
    }

    private void notifyLevelChange(DegradeLevel oldLevel, DegradeLevel newLevel) {
        for (LevelChangeListener listener : levelListeners) {
            try {
                listener.onLevelChange(oldLevel, newLevel);
            } catch (Exception e) {
                log.warn("[降级配置] 通知监听器异常: {}", e.getMessage());
            }
        }
    }
}
