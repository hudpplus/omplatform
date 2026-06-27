package com.omplatform.trade.es.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * ES 索引模板初始化器 — 在应用启动时创建按 business_type 隔离的索引模板和别名。
 * <p>
 * ADR-017 Phase 2：为每条业务线创建独立的 index template，
 * 确保 {@code orders-{business_type}-*} 索引具有一致的 mapping 和 setting。
 * <p>
 * 模板格式（ES 8.x composable index template）：
 * <pre>
 *   PUT _index_template/template-orders-ecommerce
 *   {
 *     "index_patterns": ["orders-ecommerce-*"],
 *     "template": {
 *       "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
 *       "mappings": { "properties": { ... } }
 *     },
 *     "priority": 100
 *   }
 * </pre>
 * <p>
 * 同时为每条业务线创建 search alias，查询时通过别名访问：
 * <pre>
 *   orders-ecommerce-search → orders-ecommerce-*
 * </pre>
 */
@Slf4j
@Component
public class EsIndexTemplateInitializer {

    private static final List<String> BUSINESS_TYPES = List.of("ecommerce", "locallife", "b2b");

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String[] esUris;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public EsIndexTemplateInitializer(RestTemplateBuilder restTemplateBuilder,
                                      ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        String esBaseUrl = (esUris != null && esUris.length > 0) ? esUris[0] : "http://localhost:9200";
        log.info("初始化 ES 索引模板, ES 地址: {}", esBaseUrl);

        for (String businessType : BUSINESS_TYPES) {
            try {
                createIndexTemplate(esBaseUrl, businessType);
                createSearchAlias(esBaseUrl, businessType);
            } catch (Exception e) {
                log.warn("ES 索引模板初始化失败 (businessType={}, 可手动创建): {}",
                        businessType, e.getMessage());
            }
        }
        log.info("ES 索引模板初始化完成");
    }

    // ========== 索引模板 ==========

    private void createIndexTemplate(String esBaseUrl, String businessType) {
        String templateName = "template-orders-" + businessType;
        String indexPattern = "orders-" + businessType + "-*";

        ObjectNode templateJson = buildTemplateJson(businessType, indexPattern);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(
                templateJson.toString(), headers);

        try {
            restTemplate.put(esBaseUrl + "/_index_template/" + templateName, entity);
            log.info("ES 索引模板已创建: {} → {}", templateName, indexPattern);
        } catch (Exception e) {
            // 如果已经存在，PUT 是幂等的 — 但可能返回 400/200 取决于 ES 版本
            log.debug("ES 索引模板 {} 已存在或创建失败: {}", templateName, e.getMessage());
            // 尝试使用 json body 重试（某些 ES 版本 PUT 需要请求体）
            try {
                restTemplate.put(esBaseUrl + "/_index_template/" + templateName, entity);
            } catch (Exception ignored) {
                // 非致命 — 可以手动创建
            }
        }
    }

    private ObjectNode buildTemplateJson(String businessType, String indexPattern) {
        ObjectNode root = objectMapper.createObjectNode();

        // index_patterns
        ArrayNode patterns = root.putArray("index_patterns");
        patterns.add(indexPattern);

        // template
        ObjectNode template = root.putObject("template");

        // settings
        ObjectNode settings = template.putObject("settings");
        settings.put("number_of_shards", 1);
        settings.put("number_of_replicas", 0);

        // mappings
        ObjectNode mappings = template.putObject("mappings");
        ObjectNode properties = mappings.putObject("properties");

        // 以下字段映射与 OrderDocument.java 保持一致
        addKeyword(properties, "orderNo");
        addKeyword(properties, "parentOrderNo");
        addKeyword(properties, "buyerId");
        addKeyword(properties, "shopId");
        addKeyword(properties, "businessType");     // ADR-017 业务线标识
        addKeyword(properties, "status");
        addKeyword(properties, "previousStatus");
        addDouble(properties, "totalAmount");
        addDouble(properties, "payAmount");
        addDouble(properties, "freightAmount");
        addDouble(properties, "discountAmount");
        addText(properties, "remark");
        addKeyword(properties, "channelSource");
        addDate(properties, "statusChangedAt");
        addDate(properties, "statusExpiresAt");
        addDate(properties, "gmtCreate");
        addDate(properties, "gmtModified");
        addText(properties, "searchText");          // 复合搜索字段

        // Nested: items
        ObjectNode items = properties.putObject("items");
        items.put("type", "nested");
        ObjectNode itemProps = items.putObject("properties");
        addKeyword(itemProps, "itemId");
        addKeyword(itemProps, "skuId");
        addText(itemProps, "skuName");
        addInteger(itemProps, "quantity");
        addDouble(itemProps, "unitPrice");
        addDouble(itemProps, "subtotal");
        addDouble(itemProps, "discountAmount");

        // priority
        root.put("priority", 100);

        return root;
    }

    // ========== Search Alias ==========

    private void createSearchAlias(String esBaseUrl, String businessType) {
        String aliasName = "orders-" + businessType + "-search";
        String indexPattern = "orders-" + businessType + "-*";

        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("actions")
                .putObject("add")
                .put("index", indexPattern)
                .put("alias", aliasName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            restTemplate.postForEntity(esBaseUrl + "/_aliases", entity, String.class);
            log.info("ES search alias 已创建: {} → {}", aliasName, indexPattern);
        } catch (Exception e) {
            log.debug("ES search alias {} 已存在或创建失败: {}", aliasName, e.getMessage());
        }
    }

    // ========== Mapping Helpers ==========

    private void addKeyword(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "keyword");
    }

    private void addText(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "text");
    }

    private void addDouble(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "double");
    }

    private void addInteger(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "integer");
    }

    private void addDate(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "date");
    }
}
