package com.omplatform.channel.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 渠道 Dubbo 服务实现。
 * <p>
 * 接收来自 Ext GW 的渠道订单，执行标准化后调用 oms-trade 创建订单。
 */
@Slf4j
@DubboService
public class ChannelDubboService {

    // 完整实现中 inject:
    // private final ChannelStandardizationPipeline pipeline;
    // private final OrderService orderService;

    /**
     * 处理渠道订单 Webhook。
     *
     * @param channel      渠道标识
     * @param rawOrderJson 原始订单 JSON
     * @return 中台订单号
     */
    public String handleChannelOrder(String channel, String rawOrderJson) {
        log.info("收到渠道订单: channel={}", channel);
        // 1. 执行标准化管线
        // StandardizedOrder order = pipeline.process(channel, rawOrderJson);
        // 2. 调用 oms-trade 创建订单
        // CreateOrderRequest request = convertToCreateRequest(order);
        // orderService.createOrder(request, ...);
        return "ORDER" + System.currentTimeMillis();
    }
}
