package com.omplatform.trade.sharding;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *  业务线路由器 — 根据 business_type + shardKey 解析目标表名和数据源。
 *  <p>
 *  ADR-017 多业务线物理隔离核心路由组件：
 *  <ul>
 *    <li>电商：8 库 × 8 表 = 64 分区，分片键 buyer_id</li>
 *    <li>本地生活：2 库 × 8 表 = 16 分区，分片键 buyer_id</li>
 *    <li>B2B：1 库 × 4 表 = 4 分区，分片键 company_id</li>
 *  </ul>
 */
@Component
public class BusinessRouter {

    /** 业务线 → 路由规则 */
    private static final Map<String, RouteRule> ROUTE_RULES = new HashMap<>();

    static {
        // 电商：64 分区 / 8 库 × 8 表
        ROUTE_RULES.put("ecommerce", new RouteRule(8, 8,
                shardValue -> (int) ((shardValue / 8) % 8),   // dbIndex = (hash / 8) % 8
                shardValue -> (int) (shardValue % 8)));       // tableIndex = hash % 8

        // 本地生活：16 分区 / 2 库 × 8 表
        ROUTE_RULES.put("locallife", new RouteRule(2, 8,
                shardValue -> (int) ((shardValue / 8) % 2),
                shardValue -> (int) (shardValue % 8)));

        // B2B：4 分区 / 1 库 × 4 表
        ROUTE_RULES.put("b2b", new RouteRule(1, 4,
                shardValue -> 0,
                shardValue -> (int) (shardValue % 4)));
    }

    /** 基础表名前缀 */
    private static final Map<String, String> BASE_TABLE_PREFIX = Map.of(
            "ecommerce", "order_ecommerce",
            "locallife", "order_locallife",
            "b2b", "order_b2b");

    /** 数据源名前缀 */
    private static final Map<String, String> DS_PREFIX = Map.of(
            "ecommerce", "ecommerce_ds_",
            "locallife", "locallife_ds_",
            "b2b", "b2b_ds_");

    /**
     *  解析目标表名。
     *
     * @param businessType 业务线
     * @param baseTable    基础逻辑表名（如 "order_ecommerce"）
     * @param shardKey     分片键（buyer_id / company_id 的哈希值）
     * @return 实际物理表名（如 "order_ecommerce_3"）
     */
    public String resolveTable(String businessType, String baseTable, Long shardKey) {
        RouteRule rule = getRule(businessType);
        int shardValue = normalizeShardKey(shardKey);
        int tableIndex = rule.tableIndexExtractor.apply(shardValue);
        return baseTable + "_" + tableIndex;
    }

    /**
     *  解析目标数据源名。
     *
     * @param businessType 业务线
     * @param shardKey     分片键哈希值
     * @return 数据源 bean 名称（如 "ecommerce_ds_3"）
     */
    public String resolveDataSource(String businessType, Long shardKey) {
        RouteRule rule = getRule(businessType);
        int shardValue = normalizeShardKey(shardKey);
        int dbIndex = rule.dbIndexExtractor.apply(shardValue);
        return DS_PREFIX.get(businessType) + dbIndex;
    }

    /**
     *  获取业务线的分库数和分表数（ShardingSphere 算法使用）。
     */
    public int getDbCount(String businessType) {
        return getRule(businessType).dbCount;
    }

    public int getTableCount(String businessType) {
        return getRule(businessType).tableCount;
    }

    /**
     *  判断业务线是否存在。
     */
    public boolean supports(String businessType) {
        return ROUTE_RULES.containsKey(businessType);
    }

    // ====== 内部 ======

    private RouteRule getRule(String businessType) {
        RouteRule rule = ROUTE_RULES.get(businessType);
        if (rule == null) {
            throw new IllegalArgumentException("Unknown business type: " + businessType
                    + ", supported: " + ROUTE_RULES.keySet());
        }
        return rule;
    }

    /** 将分片键哈希值归一化到 [0, Integer.MAX_VALUE) 区间 */
    private int normalizeShardKey(Long shardKey) {
        if (shardKey == null) return 0;
        long v = shardKey.longValue();
        // 先取绝对值，再截断为 int 范围
        v = v & 0x7FFFFFFFFFFFFFFFL;
        // 混合高位低位使分布更均匀
        long h = v ^ (v >>> 32);
        return (int) (h & 0x7FFFFFFF);
    }

    // ====== 路由规则 ======

    public static class RouteRule {
        final int dbCount;
        final int tableCount;
        final Function<Integer, Integer> dbIndexExtractor;
        final Function<Integer, Integer> tableIndexExtractor;

        RouteRule(int dbCount, int tableCount,
                  Function<Integer, Integer> dbIndexExtractor,
                  Function<Integer, Integer> tableIndexExtractor) {
            this.dbCount = dbCount;
            this.tableCount = tableCount;
            this.dbIndexExtractor = dbIndexExtractor;
            this.tableIndexExtractor = tableIndexExtractor;
        }

        public int getDbCount() { return dbCount; }
        public int getTableCount() { return tableCount; }
    }
}
