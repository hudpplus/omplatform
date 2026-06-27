package com.omplatform.trade.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * ShardingSphere 自定义数据源分片算法 — 按 business_type 路由到正确的物理数据库。
 * <p>
 * ADR-017 Phase 4：与 {@link OrderShardingAlgorithm} 配合使用，后者负责表路由，
 * 本算法负责数据源路由（{@code database-strategy}）。
 * <p>
 * <h3>路由公式</h3>
 * <pre>
 *   电商 (8 DB):  dbIndex = (hash(buyer_id) / 8) % 8
 *   本地生活 (2 DB): dbIndex = (hash(buyer_id) / 8) % 2
 *   B2B (1 DB):    dbIndex = 0
 * </pre>
 * <p>
 * 路由逻辑委派给 {@link BusinessRouter}，支持多业务线的差异化分片策略。
 */
public final class BusinessDbShardingAlgorithm implements StandardShardingAlgorithm<String> {

    private static final BusinessRouter ROUTER = new BusinessRouter();

    private Properties props;

    @Override
    public String doSharding(final Collection<String> availableTargetNames,
                              final PreciseShardingValue<String> shardingValue) {
        String businessType = BusinessContext.getBusinessType();
        String columnValue = shardingValue.getValue();
        Long shardKey = columnValue != null ? BusinessContext.hashShardKey(columnValue) : 0L;

        // 计算目标数据源索引
        int dbCount = ROUTER.getDbCount(businessType);
        int shardValue = normalizeShardKey(shardKey);
        int dbIndex = dbCount > 1 ? (shardValue % dbCount) : 0;

        // 从 availableTargetNames 中匹配目标数据源
        String suffix = "_" + dbIndex;
        for (String target : availableTargetNames) {
            if (target.endsWith(suffix)) {
                return target;
            }
        }

        // 兜底：返回第一个可用数据源
        return availableTargetNames.iterator().next();
    }

    @Override
    public Collection<String> doSharding(final Collection<String> availableTargetNames,
                                          final RangeShardingValue<String> shardingValue) {
        // 范围查询暂不启用分片优化，返回所有目标
        return availableTargetNames;
    }

    @Override
    public String getType() {
        return "BUSINESS_DB";
    }

    @SuppressWarnings("unused")
    public Properties getProps() {
        return props;
    }

    @SuppressWarnings("unused")
    public void setProps(final Properties props) {
        this.props = props;
    }

    // ====== 辅助 ======

    private int normalizeShardKey(Long shardKey) {
        if (shardKey == null) return 0;
        long v = shardKey & 0x7FFFFFFFFFFFFFFFL;
        long h = v ^ (v >>> 32);
        return (int) (h & 0x7FFFFFFF);
    }
}
