package com.omplatform.trade.es.router;

import com.omplatform.trade.sharding.BusinessContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * ES 索引路由器 — 根据 business_type 和日期解析目标索引名。
 * <p>
 * ADR-017 索引命名规则：
 * <pre>
 *   orders-{business_type}-{yyyy-MM}
 *   示例: orders-ecommerce-2026-06
 * </pre>
 */
@Component
public class EsIndexRouter {

    private static final Map<String, String> INDEX_PREFIX = Map.of(
            "ecommerce", "orders-ecommerce",
            "locallife", "orders-locallife",
            "b2b", "orders-b2b");

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 获取当前请求上下文对应的 ES 索引名。
     *
     * @return 格式 {@code orders-ecommerce-2026-06}
     */
    public String resolveIndexName() {
        String businessType = BusinessContext.getBusinessType();
        return resolveIndexName(businessType, LocalDate.now());
    }

    /**
     * 根据业务线和日期解析 ES 索引名。
     *
     * @param businessType 业务线（ecommerce / locallife / b2b）
     * @param date         订单日期
     * @return 完整索引名
     */
    public String resolveIndexName(String businessType, LocalDate date) {
        String prefix = INDEX_PREFIX.get(businessType);
        if (prefix == null) {
            // 未知业务线，使用默认
            prefix = INDEX_PREFIX.get("ecommerce");
        }
        return prefix + "-" + date.format(MONTH_FMT);
    }

    /**
     * 获取业务线对应的索引别名（查询时使用）。
     */
    public String getSearchAlias(String businessType) {
        String prefix = INDEX_PREFIX.get(businessType);
        if (prefix == null) {
            prefix = INDEX_PREFIX.get("ecommerce");
        }
        return prefix + "-search";
    }

    /**
     * 获取所有支持的索引前缀。
     */
    public Map<String, String> getAllIndexPrefixes() {
        return INDEX_PREFIX;
    }
}
