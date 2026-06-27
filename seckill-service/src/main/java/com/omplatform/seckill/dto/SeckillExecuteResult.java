package com.omplatform.seckill.dto;

import com.omplatform.api.order.dto.OrderDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillExecuteResult {

    /**
     * 执行状态:
     * <ul>
     *   <li>{@code SUCCESS} — 同步成功（下单完成）</li>
     *   <li>{@code PROCESSING} — 异步处理中（令牌桶通过 + 库存已抢，MQ 消费中）</li>
     *   <li>{@code SOLD_OUT} — 售罄</li>
     *   <li>{@code BUYER_LIMIT} — 已达购买上限</li>
     *   <li>{@code ACTIVITY_INVALID} — 活动无效</li>
     *   <li>{@code TOKEN_BUCKET_LIMITED} — 令牌桶限流</li>
     *   <li>{@code DUPLICATE_SUBMIT} — 重复提交</li>
     *   <li>{@code ORDER_FAILED} — 下单失败</li>
     * </ul>
     */
    private String status;

    /** 提示消息 */
    private String message;

    /** 订单号（成功时） */
    private String orderNo;

    /** 订单详情（成功时） */
    private OrderDTO order;
}
