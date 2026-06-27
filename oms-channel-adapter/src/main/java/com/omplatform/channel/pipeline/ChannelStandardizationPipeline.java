package com.omplatform.channel.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 渠道订单标准化管线（ADR-036）。
 * <p>
 * 六步处理：mapping → enrich → validate → route → save → create
 * <p>
 * 将不同渠道的原始订单 JSON 标准化为中台统一订单格式。
 */
@Slf4j
@Component
public class ChannelStandardizationPipeline {

    private final List<StandardizationStage> stages = new ArrayList<>();

    public void registerStage(StandardizationStage stage) {
        stages.add(stage);
    }

    /**
     * 执行标准化管线。
     *
     * @param channel         渠道标识（TMALL / JD / DOUYIN）
     * @param rawOrderJson    渠道原始订单 JSON
     * @return 标准化后的中台订单
     */
    public StandardizedOrder process(String channel, String rawOrderJson) {
        StandardizedOrder order = new StandardizedOrder(channel, rawOrderJson);

        for (StandardizationStage stage : stages) {
            stage.process(order);
            log.debug("  标准化步骤 {}: 渠道={}", stage.name(), channel);
        }

        log.info("渠道订单标准化完成: channel={}, outOrderId={}", channel, order.getOutOrderId());
        return order;
    }

    // ========== 管线步骤接口 ==========

    public interface StandardizationStage {
        String name();
        void process(StandardizedOrder order);
    }

    // ========== 标准化订单 ==========

    public static class StandardizedOrder {
        private final String channel;
        private final String rawOrderJson;
        private String outOrderId;
        private String buyerId;
        private List<OrderLine> items;
        private Map<String, String> extensions;
        private boolean valid;

        public StandardizedOrder(String channel, String rawOrderJson) {
            this.channel = channel;
            this.rawOrderJson = rawOrderJson;
        }

        // getters & setters omitted for brevity (use Lombok in production)

        public String getChannel() { return channel; }
        public String getRawOrderJson() { return rawOrderJson; }
        public String getOutOrderId() { return outOrderId; }
        public void setOutOrderId(String outOrderId) { this.outOrderId = outOrderId; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
    }

    public record OrderLine(String skuId, int quantity, java.math.BigDecimal price) {}
}
