package com.omplatform.trade.es.provider;

import com.omplatform.trade.es.router.EsIndexRouter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ES 索引名提供者。
 * <p>
 * ADR-017 改造后：默认按 business_type 动态解析索引名，兼容旧配置兜底。
 * <br>
 * 索引格式: {@code orders-{business_type}-{yyyy-MM}}
 * <br>
 * 用于 {@code @Document(indexName = "#{@esIndexNameProvider.indexName}")} SpEL 表达式。
 */
@Component("esIndexNameProvider")
public class EsIndexNameProvider {

    @Value("${omplatform.es.index-name:}")
    private String legacyIndexName;

    @Autowired
    private EsIndexRouter esIndexRouter;

    /**
     * 从 {@link com.omplatform.trade.sharding.BusinessContext ThreadLocal} 获取
     * current business_type 并解析 ES 索引名。
     * <p>
     * 用于 {@code @Document} SpEL、以及 Web/Dubbo 请求上下文中自然继承 ThreadLocal 的场景。
     */
    public String getIndexName() {
        // 尝试从 BusinessContext 获取业务线
        String businessType = com.omplatform.trade.sharding.BusinessContext.getBusinessType();
        if (businessType != null && !businessType.isEmpty()) {
            return esIndexRouter.resolveIndexName(businessType, java.time.LocalDate.now());
        }
        // 兜底：旧配置或默认值
        return legacyIndexName != null && !legacyIndexName.isEmpty()
                ? legacyIndexName : "orders";
    }

    /**
     * 显式指定 businessType 解析 ES 索引名。
     * <p>
     * 适用于以下非 ThreadLocal 场景的调用者：
     * <ul>
     *   <li>{@link com.omplatform.trade.es.consumer.OrderEsSyncService} — RocketMQ 消费者</li>
     *   <li>{@link com.omplatform.trade.es.job.EsReindexJob} — XXL-Job 批量重建索引</li>
     * </ul>
     */
    public String getIndexName(String businessType) {
        if (businessType != null && !businessType.isEmpty()) {
            return esIndexRouter.resolveIndexName(businessType, java.time.LocalDate.now());
        }
        return legacyIndexName != null && !legacyIndexName.isEmpty()
                ? legacyIndexName : "orders";
    }
}
