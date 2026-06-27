package com.omplatform.trade.es.service;

import com.omplatform.trade.es.router.EsIndexRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * ES 索引管理服务 — 提供业务线索引的创建、删除、校验等管理操作。
 * <p>
 * ADR-017 Phase 2：支持按 business_type 独立管理 ES 索引生命周期。
 */
@Slf4j
@Service
public class EsAdminService {

    @Autowired
    private ElasticsearchTemplate esTemplate;

    @Autowired
    private EsIndexRouter esIndexRouter;

    /**
     * 检查指定业务线的当前月索引是否存在。
     */
    public boolean indexExists(String businessType) {
        try {
            String indexName = esIndexRouter.resolveIndexName(businessType, LocalDate.now());
            return esTemplate.indexOps(
                    org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of(indexName)).exists();
        } catch (Exception e) {
            log.warn("检查 ES 索引失败: businessType={}, err={}", businessType, e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定业务线的 ES 索引（谨慎使用！）。
     */
    public void deleteBusinessIndex(String businessType) {
        try {
            String indexName = esIndexRouter.resolveIndexName(businessType, LocalDate.now());
            boolean deleted = esTemplate.indexOps(
                    org.springframework.data.elasticsearch.core.mapping.IndexCoordinates.of(indexName)).delete();
            log.warn("删除 ES 索引: name={}, result={}", indexName, deleted);
        } catch (Exception e) {
            log.warn("删除 ES 索引失败: businessType={}, err={}", businessType, e.getMessage());
        }
    }

    /**
     * 获取所有业务线列表。
     */
    public List<String> getBusinessTypes() {
        return List.copyOf(esIndexRouter.getAllIndexPrefixes().keySet());
    }
}
