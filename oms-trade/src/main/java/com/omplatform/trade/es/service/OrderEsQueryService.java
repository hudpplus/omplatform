package com.omplatform.trade.es.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.api.order.dto.OrderQueryRequest;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.api.PageResult;
import com.omplatform.common.circuitbreaker.BusinessCircuitBreaker;
import com.omplatform.common.circuitbreaker.CircuitBreakerRegistry;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.trade.es.document.OrderDocument;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ES 查询服务 — 封装所有 OrderReadService 需要的查询逻辑。
 * <p>
 * 优先查询 ES，ES 不可用时回退 MySQL。
 */
@Slf4j
@Service
public class OrderEsQueryService {

    @Autowired(required = false)
    private ElasticsearchTemplate esTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Value("${omplatform.es.fallback-to-mysql:true}")
    private boolean fallbackToMysql;

    @Autowired
    private CircuitBreakerRegistry breakerRegistry;

    /** ES 查询断路器（延迟初始化） */
    private BusinessCircuitBreaker esBreaker;

    private boolean esReady = false;

    @PostConstruct
    public void init() {
        if (esTemplate != null) {
            try {
                esReady = esTemplate.indexOps(OrderDocument.class).exists();
                log.info("ES 索引状态: ready={}", esReady);
            } catch (Exception e) {
                log.warn("ES 索引检测失败，将回退 MySQL: {}", e.getMessage());
            }
        }
        // 获取或创建 es-query 断路器（由 CircuitBreakerConfig 注册，此处兜底创建）
        esBreaker = breakerRegistry.getOrCreate("es-query",
                () -> new com.omplatform.common.circuitbreaker.CircuitBreakerTemplate("es-query", 10, 60_000, 1));
    }

    // ========== 公开 API（断路器保护） ==========

    public ApiResult<OrderDTO> getByOrderNo(String orderNo) {
        return esBreaker.execute("es.getById",
                // 主路径：查询 ES
                () -> {
                    OrderDocument doc = tryQueryEs(orderNo);
                    if (doc != null) return ApiResult.success(toDTO(doc));
                    if (fallbackToMysql) return queryFromMysql(orderNo);
                    return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
                },
                // 熔断 fallback：直接查 MySQL
                () -> {
                    log.info("[断路器] es-query 熔断, getByOrderNo 走 MySQL: orderNo={}", orderNo);
                    return queryFromMysql(orderNo);
                });
    }

    public ApiResult<PageResult<OrderDTO>> queryByBuyer(OrderQueryRequest request) {
        return esBreaker.execute("es.queryByBuyer",
                () -> doEsSearch(request, "buyerId", request.getBuyerId()),
                () -> {
                    log.info("[断路器] es-query 熔断, queryByBuyer 走 MySQL: buyerId={}", request.getBuyerId());
                    return fallbackToMysql ? queryByBuyerFromMysql(request) : ApiResult.success(PageResult.empty());
                });
    }

    public ApiResult<PageResult<OrderDTO>> queryByShop(OrderQueryRequest request) {
        return esBreaker.execute("es.queryByShop",
                () -> doEsSearch(request, "shopId", request.getShopId()),
                () -> {
                    log.info("[断路器] es-query 熔断, queryByShop 走 MySQL: shopId={}", request.getShopId());
                    return fallbackToMysql ? queryByShopFromMysql(request) : ApiResult.success(PageResult.empty());
                });
    }

    // ========== ES 查询（注意：异常向上抛给断路器计数） ==========

    private OrderDocument tryQueryEs(String orderNo) {
        if (!esReady) return null;
        // 异常不 catch — 由断路器捕获并计入失败计数
        return esTemplate.get(orderNo, OrderDocument.class);
    }

    private ApiResult<PageResult<OrderDTO>> doEsSearch(OrderQueryRequest request,
                                                        String ownerField, String ownerValue) {
        // 异常不 catch — 由断路器捕获并计入失败计数
        List<Criteria> criteriaList = new ArrayList<>();

        if (ownerValue != null) {
            criteriaList.add(Criteria.where(ownerField).is(ownerValue));
        }
        if (request.getStatusList() != null && !request.getStatusList().isEmpty()) {
            List<String> statusValues = request.getStatusList().stream()
                    .map(Enum::name)
                    .collect(Collectors.toList());
            criteriaList.add(Criteria.where("status").in(statusValues));
        }
        if (request.getCreateTimeFrom() != null) {
            criteriaList.add(Criteria.where("gmtCreate").greaterThanEqual(request.getCreateTimeFrom()));
        }
        if (request.getCreateTimeTo() != null) {
            criteriaList.add(Criteria.where("gmtCreate").lessThanEqual(request.getCreateTimeTo()));
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            criteriaList.add(Criteria.where("searchText").matches(request.getKeyword()));
        }

        // 合并多个 Criteria
        Criteria criteria;
        if (criteriaList.isEmpty()) {
            criteria = new Criteria();
        } else {
            criteria = criteriaList.get(0);
            for (int i = 1; i < criteriaList.size(); i++) {
                criteria = criteria.and(criteriaList.get(i));
            }
        }

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        // 默认按创建时间倒序
        query.addSort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("gmtCreate")));

        SearchHits<OrderDocument> searchHits = esTemplate.search(query, OrderDocument.class);

        List<OrderDTO> dtos = searchHits.getSearchHits().stream()
                .map(hit -> toDTO(hit.getContent()))
                .collect(Collectors.toList());

        PageResult<OrderDTO> pageResult = new PageResult<>();
        pageResult.setRecords(dtos);
        pageResult.setTotal(searchHits.getTotalHits());
        pageResult.setPageNo(request.getPageNo());
        pageResult.setPageSize(request.getPageSize());
        return ApiResult.success(pageResult);
    }

    // ========== MySQL 回退 ==========

    private ApiResult<OrderDTO> queryFromMysql(String orderNo) {
        OrderEntity entity = orderRepository.getById(orderNo);
        if (entity == null) return ApiResult.error("ORDER_NOT_FOUND", "订单不存在");
        return ApiResult.success(toDTO(entity));
    }

    private ApiResult<PageResult<OrderDTO>> queryByBuyerFromMysql(OrderQueryRequest request) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getBuyerId, request.getBuyerId());
        applyFilters(wrapper, request);
        return doMysqlPageQuery(wrapper, request);
    }

    private ApiResult<PageResult<OrderDTO>> queryByShopFromMysql(OrderQueryRequest request) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderEntity::getShopId, request.getShopId());
        applyFilters(wrapper, request);
        return doMysqlPageQuery(wrapper, request);
    }

    private void applyFilters(LambdaQueryWrapper<OrderEntity> wrapper, OrderQueryRequest request) {
        if (request.getStatusList() != null && !request.getStatusList().isEmpty()) {
            wrapper.in(OrderEntity::getStatus, request.getStatusList());
        }
        if (request.getCreateTimeFrom() != null) {
            wrapper.ge(OrderEntity::getGmtCreate, request.getCreateTimeFrom());
        }
        if (request.getCreateTimeTo() != null) {
            wrapper.le(OrderEntity::getGmtCreate, request.getCreateTimeTo());
        }
        wrapper.orderByDesc(OrderEntity::getGmtCreate);
    }

    private ApiResult<PageResult<OrderDTO>> doMysqlPageQuery(LambdaQueryWrapper<OrderEntity> wrapper,
                                                              OrderQueryRequest request) {
        Page<OrderEntity> page = new Page<>(request.getPageNo(), request.getPageSize());
        page.addOrder(OrderItem.desc("gmt_create"));
        Page<OrderEntity> result = orderRepository.page(page, wrapper);

        List<OrderDTO> dtos = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        PageResult<OrderDTO> pageResult = new PageResult<>();
        pageResult.setRecords(dtos);
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNo((int) result.getCurrent());
        pageResult.setPageSize((int) result.getSize());
        return ApiResult.success(pageResult);
    }

    // ========== DTO 转换 ==========

    private OrderDTO toDTO(OrderDocument doc) {
        if (doc == null) return null;
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(doc.getOrderNo());
        dto.setParentOrderNo(doc.getParentOrderNo());
        dto.setBuyerId(doc.getBuyerId());
        dto.setShopId(doc.getShopId());
        if (doc.getStatus() != null) {
            dto.setStatus(OrderStatus.valueOf(doc.getStatus()));
        }
        dto.setTotalAmount(doc.getTotalAmount());
        dto.setPayAmount(doc.getPayAmount());
        dto.setFreightAmount(doc.getFreightAmount());
        dto.setDiscountAmount(doc.getDiscountAmount());
        dto.setRemark(doc.getRemark());
        dto.setStatusChangedAt(doc.getStatusChangedAt());
        dto.setStatusExpiresAt(doc.getStatusExpiresAt());
        return dto;
    }

    private OrderDTO toDTO(OrderEntity entity) {
        if (entity == null) return null;
        OrderDTO dto = new OrderDTO();
        dto.setOrderNo(entity.getOrderNo());
        dto.setParentOrderNo(entity.getParentOrderNo());
        dto.setBuyerId(entity.getBuyerId());
        dto.setShopId(entity.getShopId());
        dto.setStatus(entity.getStatus());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setPayAmount(entity.getPayAmount());
        dto.setFreightAmount(entity.getFreightAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setRemark(entity.getRemark());
        dto.setStatusChangedAt(entity.getStatusChangedAt());
        dto.setStatusExpiresAt(entity.getStatusExpiresAt());

        try {
            LambdaQueryWrapper<OrderItemEntity> iw = new LambdaQueryWrapper<>();
            iw.eq(OrderItemEntity::getOrderNo, entity.getOrderNo());
            List<OrderItemEntity> items = orderItemMapper.selectList(iw);
            if (items != null && !items.isEmpty()) {
                dto.setItems(items.stream().map(this::toItemDTO).collect(Collectors.toList()));
            }
        } catch (Exception ignored) {}
        return dto;
    }

    private OrderDTO.OrderItemDTO toItemDTO(OrderItemEntity item) {
        OrderDTO.OrderItemDTO dto = new OrderDTO.OrderItemDTO();
        dto.setItemId(String.valueOf(item.getItemId()));
        dto.setSkuId(item.getSkuId());
        dto.setSkuName(item.getSkuName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getTotalAmount());
        dto.setDiscountAmount(item.getDiscountAmount());
        return dto;
    }
}
