package com.omplatform.trade.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * ShardingSphere 自定义标准分片算法 — 按 buyer_id 路由到 order_ecommerce 分表。
 * <p>
 * 路由逻辑委派给 {@link BusinessRouter}，支持多业务线的差异化分片策略。
 * <br>
 * ShardingSphere 通过 SPI {@code META-INF/services/} 发现此实现。
 *
 * <h3>路由公式（电商，8 库 × 8 表 = 64 分区）</h3>
 * <pre>
 *   tableIndex = hash(buyer_id) % 8
 *   dbIndex    = (hash(buyer_id) / 8) % 8
 * </pre>
 */
public final class OrderShardingAlgorithm implements StandardShardingAlgorithm<String> {

    private static final BusinessRouter ROUTER = new BusinessRouter();

    private Properties props;

    @Override
    public String doSharding(final Collection<String> availableTargetNames,
                             final PreciseShardingValue<String> shardingValue) {
        String businessType = BusinessContext.getBusinessType();
        String columnValue = shardingValue.getValue();
        Long shardKey = columnValue != null ? BusinessContext.hashShardKey(columnValue) : 0L;

        // 计算目标表/数据源后缀
        int tableCount = ROUTER.getTableCount(businessType);
        int shardValue = normalizeShardKey(shardKey);
        int tableIndex = shardValue % tableCount;

        // 从 availableTargetNames 中匹配目标
        String suffix = "_" + tableIndex;
        for (String target : availableTargetNames) {
            if (target.endsWith(suffix)) {
                return target;
            }
        }

        // 兜底：返回第一个可用目标
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
        return "ORDER_BUSINESS";
    }

    /**
     * 获取配置属性（ShardingSphere SPI 自动注入）。
     */
    @SuppressWarnings("unused")
    public Properties getProps() {
        return props;
    }

    /**
     * 设置配置属性（ShardingSphere SPI 自动注入）。
     */
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
