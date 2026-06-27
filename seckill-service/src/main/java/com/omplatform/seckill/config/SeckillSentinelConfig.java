package com.omplatform.seckill.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 秒杀 Sentinel 流控规则配置。
 * <p>
 * 注册排队模式 flow rule 实现削峰：
 * - 请求进入 Sentinel 队列按 QPS 匀速放行
 * - 排队超时（默认 500ms）的请求走 blockHandler 降级
 * <p>
 * 规则会被 Sentinel Dashboard 推送的规则覆盖，此为程序级兜底。
 */
@Slf4j
@Configuration
public class SeckillSentinelConfig {

    /**
     * 秒杀资源名称（与 {@code @SentinelResource(value = "seckill:execute")} 一致）。
     */
    public static final String RESOURCE_NAME = "seckill:execute";

    @PostConstruct
    public void init() {
        // 从环境变量 / Apollo 读取秒杀限流配置，无配置时使用兜底值
        int qps = getEnvInt("SECKILL_SENTINEL_QPS", 500);
        int queueTimeoutMs = getEnvInt("SECKILL_SENTINEL_QUEUE_TIMEOUT_MS", 500);

        List<FlowRule> rules = new ArrayList<>();

        // 规则 1：排队模式 — 削峰
        FlowRule queueRule = new FlowRule();
        queueRule.setResource(RESOURCE_NAME);
        queueRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        queueRule.setCount(qps);
        queueRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        queueRule.setMaxQueueingTimeMs(queueTimeoutMs);
        rules.add(queueRule);

        // 规则 2：直接拒绝模式 — 超出排队容量时快速失败（作为 queueRule 的补充）
        FlowRule fastFailRule = new FlowRule();
        fastFailRule.setResource(RESOURCE_NAME);
        fastFailRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        fastFailRule.setCount(qps * 10); // 10 倍排队 QPS 后直接拒绝
        fastFailRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(fastFailRule);

        FlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 秒杀流控规则已加载: resource={}, queueQPS={}, queueTimeout={}ms, fastFailQPS={}",
                RESOURCE_NAME, qps, queueTimeoutMs, qps * 10);
    }

    private static int getEnvInt(String key, int defaultValue) {
        try {
            String val = System.getenv(key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
