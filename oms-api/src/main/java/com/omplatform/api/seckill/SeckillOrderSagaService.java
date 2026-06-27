package com.omplatform.api.seckill;

import com.omplatform.api.order.dto.CreateOrderRequest;
import com.omplatform.api.order.dto.OrderDTO;
import com.omplatform.common.api.ApiResult;

/**
 * 秒杀订单 Saga 服务（Dubbo 接口）。
 * <p>
 * 秒杀服务（seckill-service）通过此接口调用 oms-trade 的订单创建能力，
 * 包括：执行订单创建 Saga、查询订单（幂等检查）、统计买家购买数量（限购检查）、发布订单事件。
 * <p>
 * 由 oms-trade 模块实现。
 */
public interface SeckillOrderSagaService {

    /**
     * 执行秒杀订单创建 Saga。
     * <p>
     * 内部调用 CreateOrderSagaDefinition 的四个步骤：
     * <ol>
     *   <li>createOrder — 使用秒杀价，跳过营销计价和优惠券锁定</li>
     *   <li>deductInventory — 跳过（秒杀已在入口预占库存）</li>
     *   <li>chargePayment — 正常发起支付</li>
     *   <li>confirmOrder — 正常确认</li>
     * </ol>
     *
     * @param request 订单创建请求（含秒杀价、秒杀活动 ID 等）
     * @return true 表示 Saga 执行成功，false 表示失败
     */
    ApiResult<Boolean> createSeckillOrder(CreateOrderRequest request);

    /**
     * 根据订单号查询订单。
     * <p>
     * 用于消费端幂等检查：订单已存在说明已处理过，跳过重复处理。
     *
     * @param orderNo 订单号
     * @return 订单 DTO，不存在时返回 null
     */
    ApiResult<OrderDTO> getByOrderNo(String orderNo);

    /**
     * 统计买家在指定秒杀活动中的已购数量。
     * <p>
     * 用于秒杀入口的限购检查，防止超买。
     *
     * @param buyerId    买家 ID
     * @param activityId 秒杀活动 ID
     * @return 已购订单数量
     */
    ApiResult<Long> countBuyerOrders(String buyerId, Long activityId);

    /**
     * 发布订单创建事件。
     * <p>
     * Saga 执行成功后，通知其他服务（如履约、营销）订单已创建。
     *
     * @param orderNo  订单号
     * @param buyerId  买家 ID
     * @param status   订单状态
     * @return void
     */
    ApiResult<Void> publishOrderCreated(String orderNo, String buyerId, String status);
}
