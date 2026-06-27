package com.omplatform.trade.sharding;

/**
 * 业务线上下文 — ThreadLocal 传递当前请求的 businessType 和分片键。
 * <p>
 * 使用规范：
 * <ol>
 *   <li>Dubbo 入口处通过 {@link com.omplatform.trade.filter.DubboBusinessContextFilter} 自动设置</li>
 *   <li>Web 入口处通过 {@link com.omplatform.trade.interceptor.BusinessContextInterceptor} 自动设置</li>
 *   <li>内部调用手动 set/clear；务必在 finally 中 clear()</li>
 * </ol>
 */
public final class BusinessContext {

    private static final ThreadLocal<String> BUSINESS_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<Long> SHARD_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> ORDER_NO = new ThreadLocal<>();
    private static final ThreadLocal<String> BUYER_ID = new ThreadLocal<>();

    private BusinessContext() {}

    // ====== 设置 ======

    public static void set(String businessType, Long shardKey) {
        BUSINESS_TYPE.set(businessType);
        SHARD_KEY.set(shardKey);
    }

    public static void setOrderNo(String orderNo) {
        ORDER_NO.set(orderNo);
    }

    public static void setBuyerId(String buyerId) {
        BUYER_ID.set(buyerId);
        // buyerId 作为 shardKey（字符串 → 哈希）
        if (buyerId != null) {
            SHARD_KEY.set(hashShardKey(buyerId));
        }
    }

    /** 一次性设置所有上下文 */
    public static void setAll(String businessType, String buyerId, String orderNo) {
        BUSINESS_TYPE.set(businessType);
        BUYER_ID.set(buyerId);
        ORDER_NO.set(orderNo);
        if (buyerId != null) {
            SHARD_KEY.set(hashShardKey(buyerId));
        }
    }

    // ====== 获取 ======

    public static String getBusinessType() {
        String bt = BUSINESS_TYPE.get();
        return bt != null ? bt : "ecommerce";
    }

    public static Long getShardKey() {
        Long sk = SHARD_KEY.get();
        if (sk == null) {
            // 兜底：从 buyerId 推导
            String bid = BUYER_ID.get();
            if (bid != null) {
                sk = hashShardKey(bid);
                SHARD_KEY.set(sk);
            }
        }
        return sk;
    }

    public static String getOrderNo() {
        return ORDER_NO.get();
    }

    public static String getBuyerId() {
        return BUYER_ID.get();
    }

    // ====== 清理 ======

    public static void clear() {
        BUSINESS_TYPE.remove();
        SHARD_KEY.remove();
        ORDER_NO.remove();
        BUYER_ID.remove();
    }

    // ====== 辅助 ======

    /** 将 String buyerId 转为均匀分布的 long 哈希（用于分片计算） */
    public static long hashShardKey(String key) {
        if (key == null) return 0L;
        int h = key.hashCode();
        // 混合高位低位，使分布更均匀
        return (long) (h ^ (h >>> 16)) & 0x7FFFFFFFFFFFFFFFL;
    }
}
