package com.omplatform.trade.service.atomic;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.common.constant.OrderStatus;
import com.omplatform.common.exception.BizException;
import com.omplatform.trade.repository.OrderItemMapper;
import com.omplatform.trade.repository.entity.OrderEntity;
import com.omplatform.trade.repository.entity.OrderItemEntity;
import com.omplatform.trade.statemachine.TransitionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.omplatform.tcc.TccParticipantStateClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单创建原子服务（ADR-039 §2.2）。
 * <p>
 * 转换：null → PENDING_PAY
 * 触发：买家提交订单 / 渠道订单接入
 * Saga 步骤名：createOrder
 */
@Slf4j
@Service
public class
OrderCreateService extends AbstractAtomicOrderService {

    @Autowired
    private TccParticipantStateClient tccClient;

    @Autowired
    private OrderItemMapper orderItemMapper;

    /* public OrderCreateService(TccParticipantStateClient tccClient) {
        this.tccClient = tccClient;
    } */

    @Override
    protected void validate(String orderId, TransitionContext context) {
        log.debug("校验订单创建: orderId={}", orderId);
        if (context == null || context.getExtras() == null) {
            throw new com.omplatform.common.exception.BizException("INVALID_REQUEST", "缺失上下文或必要参数");
        }

        Object o = context.getExtras().get("createRequest");
        com.omplatform.api.order.dto.CreateOrderRequest req = null;
        if (o instanceof com.omplatform.api.order.dto.CreateOrderRequest) {
            req = (com.omplatform.api.order.dto.CreateOrderRequest) o;
        }

        // 校验买家/店铺/渠道
        if (req != null) {
            if (req.getBuyerId() == null || req.getBuyerId().isBlank()) {
                throw new com.omplatform.common.exception.BizException("INVALID_BUYER", "买家 ID 不能为空");
            }
            if (req.getShopId() == null || req.getShopId().isBlank()) {
                throw new com.omplatform.common.exception.BizException("INVALID_SHOP", "店铺 ID 不能为空");
            }
            if (req.getChannelSource() == null || req.getChannelSource().isBlank()) {
                throw new com.omplatform.common.exception.BizException("INVALID_CHANNEL", "渠道来源不能为空");
            }

            // 校验订单项
            if (req.getItems() == null || req.getItems().isEmpty()) {
                throw new com.omplatform.common.exception.BizException("EMPTY_ITEMS", "订单项不能为空");
            }

            java.math.BigDecimal computed = java.math.BigDecimal.ZERO;
            int idx = 0;
            for (com.omplatform.api.order.dto.CreateOrderRequest.OrderItemRequest it : req.getItems()) {
                idx++;
                if (it.getQuantity() == null || it.getQuantity() <= 0) {
                    throw new com.omplatform.common.exception.BizException("INVALID_QTY", "第" + idx + "项数量不合法");
                }
                if (it.getUnitPrice() == null || it.getUnitPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new com.omplatform.common.exception.BizException("INVALID_PRICE", "第" + idx + "项单价不合法");
                }
                computed = computed.add(it.getUnitPrice().multiply(java.math.BigDecimal.valueOf(it.getQuantity())));
            }

            context.getExtras().put("computedTotal", computed);
        } else {
            // 无完整 request 时仅做基本检验
            Object buyer = context.getExtras().get("buyerId");
            Object shop = context.getExtras().get("shopId");
            if (buyer == null || String.valueOf(buyer).isBlank()) {
                throw new com.omplatform.common.exception.BizException("INVALID_REQUEST", "缺失买家信息");
            }
            if (shop == null || String.valueOf(shop).isBlank()) {
                throw new com.omplatform.common.exception.BizException("INVALID_REQUEST", "缺失店铺信息");
            }
        }
    }

    @Override
    protected OrderStatus resolveTargetStatus() {
        return OrderStatus.PENDING_PAY;
    }

    @Override
    protected void doExecute(String orderId, TransitionContext context) {
        log.info("创建订单: orderId={}", orderId);
        // 如果该操作属于 TCC 事务，sagaId 作为 txId 传入 extras
        String txId = null;
        String participantId = null;
        if (context != null && context.getExtras() != null) {
            Object txObj = context.getExtras().get("sagaId");
            if (txObj instanceof String) txId = (String) txObj;
            Object partObj = context.getExtras().get("participantId");
            if (partObj instanceof String) participantId = (String) partObj;
        }

        if (txId != null) {
            if (participantId == null) participantId = "order-service";
            try {
                String status = tccClient.getStatus(txId, participantId);
                if ("TRIED".equals(status) || "CONFIRMED".equals(status)) {
                    log.info("OrderCreateService: TCC already TRIED/CONFIRMED txId={} participant={}", txId, participantId);
                    return; // idempotent: already processed
                }
            } catch (Exception ex) {
                log.warn("TCC client unavailable when creating order, proceeding without pre-check: {}", ex.getMessage());
            }
        }

        // 实际实现：写入 order 表主记录
        // Read inputs from TransitionContext extras (controller should place the CreateOrderRequest or fields there)
        com.omplatform.api.order.dto.CreateOrderRequest req = null;
        if (context != null && context.getExtras() != null) {
            Object o = context.getExtras().get("createRequest");
            if (o instanceof com.omplatform.api.order.dto.CreateOrderRequest) {
                req = (com.omplatform.api.order.dto.CreateOrderRequest) o;
            } else {
                // try individual fields
                Object b = context.getExtras().get("buyerId");
                Object s = context.getExtras().get("shopId");
                Object addr = context.getExtras().get("addressId");
                Object ch = context.getExtras().get("channelSource");
                if (b != null || s != null) {
                    req = com.omplatform.api.order.dto.CreateOrderRequest.builder()
                            .buyerId(b == null ? "" : String.valueOf(b))
                            .shopId(s == null ? "" : String.valueOf(s))
                            .addressId(addr == null ? "" : String.valueOf(addr))
                            .channelSource(ch == null ? "" : String.valueOf(ch))
                            .items(new java.util.ArrayList<>())
                            .build();
                }
            }
        }

        // Build order entity and persist
        com.omplatform.trade.repository.entity.OrderEntity order = new com.omplatform.trade.repository.entity.OrderEntity();
        order.setOrderNo(orderId);
        if (req != null) {
            order.setBuyerId(req.getBuyerId());
            order.setShopId(req.getShopId());
            order.setAddressId(req.getAddressId());
            order.setRemark(req.getRemark());
            order.setChannelSource(req.getChannelSource());
            order.setCouponInstanceId(req.getCouponInstanceId());
            order.setSeckillActivityId(req.getSeckillActivityId());
            order.setSeckillPipeline(req.getSeckillPipeline());
            order.setBusinessType(req.getBusinessType() != null ? req.getBusinessType() : "ecommerce");
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            if (req.getItems() != null) {
                for (com.omplatform.api.order.dto.CreateOrderRequest.OrderItemRequest it : req.getItems()) {
                    if (it.getUnitPrice() != null && it.getQuantity() != null) {
                        total = total.add(it.getUnitPrice().multiply(java.math.BigDecimal.valueOf(it.getQuantity())));
                    }
                }
            }
            order.setTotalAmount(total);
            order.setPayAmount(total);
            order.setFreightAmount(java.math.BigDecimal.ZERO);
            order.setDiscountAmount(java.math.BigDecimal.ZERO);
        } else {
            order.setBuyerId("UNKNOWN");
            order.setShopId("UNKNOWN");
            order.setTotalAmount(java.math.BigDecimal.ZERO);
            order.setPayAmount(java.math.BigDecimal.ZERO);
            order.setFreightAmount(java.math.BigDecimal.ZERO);
            order.setDiscountAmount(java.math.BigDecimal.ZERO);
        }
        order.setStatus(resolveTargetStatus());
        order.setStatusChangedAt(java.time.LocalDateTime.now());

        // persist
        try {
            orderRepository.save(order);
        } catch (Exception ex) {
            log.error("Failed to persist order {}: {}", orderId, ex.getMessage());
            throw ex;
        }

        // 写入订单商品行（order_items）
        if (req != null && req.getItems() != null && !req.getItems().isEmpty()) {
            try {
                List<OrderItemEntity> items = new ArrayList<>(req.getItems().size());
                for (CreateOrderRequest.OrderItemRequest it : req.getItems()) {
                    BigDecimal rowTotal = it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
                    OrderItemEntity item = new OrderItemEntity();
                    item.setOrderNo(orderId);
                    item.setSkuId(it.getSkuId());
                    item.setQuantity(it.getQuantity());
                    item.setBusinessType(req.getBusinessType() != null ? req.getBusinessType() : "ecommerce");
                    item.setUnitPrice(it.getUnitPrice());
                    item.setTotalAmount(rowTotal);
                    item.setPayAmount(rowTotal);
                    item.setDiscountAmount(BigDecimal.ZERO);
                    item.setLineType("NORMAL");
                    item.setStatus("PENDING");
                    items.add(item);
                }
                items.forEach(orderItemMapper::insert);
            } catch (Exception ex) {
                log.error("写入订单商品行失败, 回滚订单 {}: {}", orderId, ex.getMessage());
                throw new BizException("ORDER_ITEM_SAVE_FAILED", "订单商品行保存失败");
            }
        }

        // If part of TCC, write TRIED state after successful local actions
        if (txId != null) {
            try {
                tccClient.upsertStatus(txId, participantId, "TRIED", orderId, LocalDateTime.now());
            } catch (Exception ex) {
                log.warn("Failed to write TCC participant state after creating order txId={} participant={}, err={}", txId, participantId, ex.getMessage());
            }
        }
    }

    @Override
    protected void publishEvent(String orderId, OrderStatus newStatus,
                                 TransitionContext context) {
        log.info("发布订单创建事件: orderId={}, status={}", orderId, newStatus);
        // Publish OrderCreatedEvent using transactional outbox so message sending
        // is decoupled from the business transaction commit.
        String payload = null;
        try {
            if (objectMapper != null) {
                var map = new java.util.HashMap<String, Object>();
                map.put("orderId", orderId);
                map.put("status", newStatus == null ? null : newStatus.name());
                map.put("createdAt", java.time.LocalDateTime.now().toString());
                payload = objectMapper.writeValueAsString(map);
            }
        } catch (Exception ex) {
            log.warn("Failed to serialize outbox payload: {}", ex.getMessage());
        }

        if (payload == null) {
            payload = "{\"orderId\":\"" + orderId + "\",\"status\":\"" + (newStatus==null?"":newStatus.name()) + "\"}";
        }

        // topic name follows platform convention
        writeOutbox("ORDER_CREATED", payload);
    }

    @Override
    protected void doCompensate(String orderId, TransitionContext context) {
        log.info("补偿订单创建: orderId={}", orderId);
        // 1. 将订单状态改为已取消（如果还是 PENDING_PAY）
        OrderEntity entity = orderRepository.getById(orderId);
        if (entity != null && entity.getStatus() == OrderStatus.PENDING_PAY) {
            entity.setStatus(OrderStatus.CANCELLED);
            entity.setStatusChangedAt(java.time.LocalDateTime.now());
            orderRepository.updateById(entity);
        }

        // 2. 投放取消事件（由 OrderEventListener 处理库存释放和优惠券回退）
        try {
            if (objectMapper != null) {
                var map = new java.util.HashMap<String, Object>();
                map.put("orderId", orderId);
                map.put("reason", "Saga补偿");
                String payload = objectMapper.writeValueAsString(map);
                writeOutbox("ORDER_CANCELLED", payload);
            }
        } catch (Exception e) {
            log.warn("发送补偿事件失败: {}", e.getMessage());
        }

        log.info("订单创建补偿完成: orderId={}", orderId);
    }
}
