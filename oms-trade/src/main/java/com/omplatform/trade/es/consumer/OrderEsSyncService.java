package com.omplatform.trade.es.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omplatform.trade.es.document.OrderDocument;
import com.omplatform.trade.repository.OrderRepository;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.omplatform.trade.sharding.BusinessContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ES 写入服务 — 接收事件后从 MySQL 拉取全量订单数据构建 document 并写入 ES。
 * <p>
 * 采用"事件触发 → 读取 MySQL 最新完整数据 → 覆盖写入 ES"模式：
 * <ul>
 *   <li>简单可靠，document 始终是 MySQL 的快照</li>
 *   <li>天然幂等：同 orderNo 多次写入是幂等的</li>
 *   <li>事件丢失可以通过全量同步恢复</li>
 * </ul>
 * <p>
 * ADR-017 改造：写入前通过 {@link BusinessContext#setAll} 注入业务线上下文，
 * 使 ES 索引名 SpEL {@code #esIndexNameProvider.indexName} 能正确解析为
 * {@code orders-ecommerce-{yyyy-MM}} 等按 business_type 分片的索引。
 */
@Slf4j
@Service
public class OrderEsSyncService {

    @Autowired
    private ElasticsearchOperations esOperations;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /**
     * 同步单条订单到 ES。
     * <p>
     * RocketMQ 消费者线程中无 {@link BusinessContext}，因此从 entity 中读取
     * {@code businessType} 并主动注入上下文，确保 {@link com.omplatform.trade.es.router.EsIndexRouter}
     * 能解析到正确的索引名。
     */
    public void syncOrder(String orderNo) {
        if (orderNo == null) return;

        try {
            OrderEntity entity = orderRepository.getById(orderNo);
            if (entity == null || Boolean.TRUE.equals(entity.getDeleted()) || entity.getDeleted() == 1) {
                // 删除场景：优先从实体（软删除仍可查到）获取 businessType
                String businessType = entity != null ? entity.getBusinessType() : null;
                if (businessType != null) {
                    BusinessContext.setAll(businessType, entity.getBuyerId(), orderNo);
                }
                try {
                    esOperations.delete(orderNo, OrderDocument.class);
                    log.info("ES 删除文档: orderNo={}, businessType={}", orderNo, businessType);
                } finally {
                    if (businessType != null) BusinessContext.clear();
                }
                return;
            }

            // 从实体读取 businessType 并注入 ThreadLocal → EsIndexRouter 按业务线路由
            BusinessContext.setAll(entity.getBusinessType(), entity.getBuyerId(), orderNo);
            try {
                OrderDocument doc = buildDocument(entity);
                esOperations.save(doc);
                log.info("ES 同步成功: orderNo={}, businessType={}, status={}",
                        orderNo, entity.getBusinessType(), doc.getStatus());
            } finally {
                BusinessContext.clear();
            }

        } catch (Exception e) {
            log.error("ES 同步失败: orderNo={}, err={}", orderNo, e.getMessage(), e);
        }
    }

    /**
     * 批量同步（全量同步使用）。
     * <p>
     * 逐条写入，失败只记录日志不阻塞整体，避免单条脏数据导致整个批次回退。
     * 每条写入前注入对应实体的 {@link BusinessContext}，确保正确路由。
     *
     * @return 成功条数
     */
    public int syncOrdersBulk(List<OrderEntity> entities) {
        int success = 0;
        int failed = 0;
        for (OrderEntity entity : entities) {
            BusinessContext.setAll(entity.getBusinessType(), entity.getBuyerId(), entity.getOrderNo());
            try {
                OrderDocument doc = buildDocument(entity);
                esOperations.save(doc);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("ES 批量同步失败: orderNo={}, businessType={}, err={}",
                        entity.getOrderNo(), entity.getBusinessType(), e.getMessage());
            } finally {
                BusinessContext.clear();
            }
        }
        if (failed > 0) {
            log.warn("ES 批量同步完成: 成功 {} 条, 失败 {} 条", success, failed);
        }
        return success;
    }

    // ========== 内部 ==========

    private OrderDocument buildDocument(OrderEntity entity) {
        OrderDocument doc = new OrderDocument();
        doc.setOrderNo(entity.getOrderNo());
        doc.setParentOrderNo(entity.getParentOrderNo());
        doc.setBuyerId(entity.getBuyerId());
        doc.setShopId(entity.getShopId());
        doc.setBusinessType(entity.getBusinessType());
        doc.setStatus(entity.getStatus().name());
        doc.setPreviousStatus(entity.getPreviousStatus() != null ? entity.getPreviousStatus().name() : null);
        doc.setTotalAmount(entity.getTotalAmount());
        doc.setPayAmount(entity.getPayAmount());
        doc.setFreightAmount(entity.getFreightAmount());
        doc.setDiscountAmount(entity.getDiscountAmount());
        doc.setRemark(entity.getRemark());
        doc.setChannelSource(entity.getChannelSource());
        doc.setStatusChangedAt(entity.getStatusChangedAt());
        doc.setStatusExpiresAt(entity.getStatusExpiresAt());
        doc.setGmtCreate(entity.getGmtCreate());
        doc.setGmtModified(entity.getGmtModified());

        // 加载商品行
        LambdaQueryWrapper<OrderItemEntity> iw = new LambdaQueryWrapper<>();
        iw.eq(OrderItemEntity::getOrderNo, entity.getOrderNo());
        List<OrderItemEntity> items = orderItemMapper.selectList(iw);
        if (items != null && !items.isEmpty()) {
            doc.setItems(items.stream().map(this::buildItemDoc).collect(Collectors.toList()));

            // 构建 searchText：订单号 + 各商品名（用于模糊搜索）
            String searchText = entity.getOrderNo() + " " +
                    items.stream().map(OrderItemEntity::getSkuName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(" "));
            doc.setSearchText(searchText);
        }

        return doc;
    }

    private OrderDocument.OrderItemDoc buildItemDoc(OrderItemEntity item) {
        OrderDocument.OrderItemDoc doc = new OrderDocument.OrderItemDoc();
        doc.setItemId(String.valueOf(item.getItemId()));
        doc.setSkuId(item.getSkuId());
        doc.setSkuName(item.getSkuName());
        doc.setQuantity(item.getQuantity());
        doc.setUnitPrice(item.getUnitPrice());
        doc.setSubtotal(item.getTotalAmount());
        doc.setDiscountAmount(item.getDiscountAmount());
        return doc;
    }
}
