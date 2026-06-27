package com.omplatform.trade.es.repository;

import com.omplatform.trade.es.document.OrderDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data ES Repository。
 * 提供基础 CRUD（save / findById / deleteById / findAll）。
 * 复杂查询通过 {@code ElasticsearchRestTemplate} 实现。
 */
@Repository
public interface OrderEsRepository extends ElasticsearchRepository<OrderDocument, String> {

}
