package com.omplatform.trade.saga;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga 定义（ADR-020 §2）。
 * <p>
 * 一个业务流程编排为一个 Saga，包含有序的正向步骤列表。
 */
public class SagaDefinition {

    private String sagaName;
    private Duration globalTimeout = Duration.ofMinutes(5);
    private RetryPolicy retryPolicy;
    private List<SagaStep> steps = new ArrayList<>();

    public SagaDefinition() {}

    // ========== getter/setter ==========

    public String getSagaName() { return sagaName; }
    public void setSagaName(String sagaName) { this.sagaName = sagaName; }

    public Duration getGlobalTimeout() { return globalTimeout; }
    public void setGlobalTimeout(Duration globalTimeout) { this.globalTimeout = globalTimeout; }

    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }

    public List<SagaStep> getSteps() { return steps; }
    public void setSteps(List<SagaStep> steps) { this.steps = steps; }

    // ========== RetryPolicy ==========

    public static class RetryPolicy {
        private int maxRetries = 3;
        private List<Duration> backoffIntervals;

        public RetryPolicy() {}

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public List<Duration> getBackoffIntervals() { return backoffIntervals; }
        public void setBackoffIntervals(List<Duration> backoffIntervals) { this.backoffIntervals = backoffIntervals; }

        public Duration getBackoffInterval(int attempt) {
            if (backoffIntervals != null && attempt < backoffIntervals.size()) {
                return backoffIntervals.get(attempt);
            }
            return Duration.ofSeconds(5);
        }
    }
}
