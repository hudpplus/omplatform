package com.omplatform.common.circuitbreaker.degrade;

/**
 * 降级等级定义（alignment with degradation-strategy.md §1）。
 * <p>
 * 系统降级划分为 5 个等级，等级越高，可用功能越少。
 * <pre>
 *   L0: 正常运行 — 所有功能正常
 *   L1: 性能降级 — 非核心组件不可用，性能受损
 *   L2: 功能受限 — 非核心功能关闭，仅核心链路
 *   L3: 核心仅存 — 仅保留订单 + 支付核心链路
 *   L4: 保护模式 — 数据只读或写入受限
 * </pre>
 */
public enum DegradeLevel {

    /** L0: 正常运行 — 所有功能正常 */
    L0(0, "正常运行"),
    /** L1: 性能降级 — 非核心组件性能受损 */
    L1(1, "性能降级"),
    /** L2: 功能受限 — 非核心功能关闭 */
    L2(2, "功能受限"),
    /** L3: 核心仅存 — 仅订单+支付核心链路 */
    L3(3, "核心仅存"),
    /** L4: 保护模式 — 数据只读 */
    L4(4, "保护模式");

    private final int level;
    private final String displayName;

    DegradeLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }

    /**
     * 当前等级是否 ≥ 指定等级。
     * <p>
     * 用于判断是否应该触发某等级对应的降级动作。
     */
    public boolean ge(DegradeLevel other) {
        return this.level >= other.level;
    }

    /**
     * 从 Apollo 配置字符串解析（如 "L2" 或 "l2"）。
     */
    public static DegradeLevel fromString(String value) {
        if (value == null || value.isBlank()) return L0;
        String upper = value.trim().toUpperCase();
        for (DegradeLevel dl : values()) {
            if (dl.name().equals(upper)) return dl;
        }
        return L0;
    }
}
