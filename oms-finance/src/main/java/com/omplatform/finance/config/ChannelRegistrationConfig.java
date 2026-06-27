package com.omplatform.finance.config;

import com.omplatform.finance.payment.PaymentChannelManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 支付渠道自动注册配置。
 * <p>
 * 自动发现所有 PaymentChannel 实现并注册到 PaymentChannelManager。
 */
@Slf4j
@Configuration
public class ChannelRegistrationConfig {

    @Autowired
    private PaymentChannelManager paymentChannelManager;

    @Autowired
    private List<PaymentChannelManager.PaymentChannel> channels;

    @PostConstruct
    public void registerChannels() {
        for (PaymentChannelManager.PaymentChannel channel : channels) {
            paymentChannelManager.registerChannel(channel);
        }
        log.info("支付渠道注册完成: 共 {} 个渠道", channels.size());
    }
}
