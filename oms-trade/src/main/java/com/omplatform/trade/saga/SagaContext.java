package com.omplatform.trade.saga;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Saga 执行上下文。
 */
public class SagaContext {

    private String sagaId;
    private String sagaName;
    private String businessKey;
    private Map<String, Object> stepArgs = new HashMap<>();
    private Map<String, Object> stepResults = new HashMap<>();
    private Map<String, Object> compensateArgs = new HashMap<>();
    private LocalDateTime startedAt;
    private boolean compensating;

    public SagaContext() {}

    // ========== 工厂方法 ==========

    public static SagaContext create(String sagaId, String sagaName, String businessKey) {
        SagaContext ctx = new SagaContext();
        ctx.sagaId = sagaId;
        ctx.sagaName = sagaName;
        ctx.businessKey = businessKey;
        ctx.startedAt = LocalDateTime.now();
        return ctx;
    }

    // ========== 便捷方法 ==========

    @SuppressWarnings("unchecked")
    public <T> T getStepArg(String stepName) {
        return (T) stepArgs.get(stepName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getStepResult(String stepName) {
        return (T) stepResults.get(stepName);
    }

    public void setStepArg(String stepName, Object arg) {
        stepArgs.put(stepName, arg);
    }

    public void setStepResult(String stepName, Object result) {
        stepResults.put(stepName, result);
    }

    public void setCompensateArg(String stepName, Object arg) {
        compensateArgs.put(stepName, arg);
    }

    // ========== getter/setter ==========

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public String getSagaName() { return sagaName; }
    public void setSagaName(String sagaName) { this.sagaName = sagaName; }

    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }

    public Map<String, Object> getStepArgs() { return stepArgs; }
    public void setStepArgs(Map<String, Object> stepArgs) { this.stepArgs = stepArgs; }

    public Map<String, Object> getStepResults() { return stepResults; }
    public void setStepResults(Map<String, Object> stepResults) { this.stepResults = stepResults; }

    public Map<String, Object> getCompensateArgs() { return compensateArgs; }
    public void setCompensateArgs(Map<String, Object> compensateArgs) { this.compensateArgs = compensateArgs; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public boolean isCompensating() { return compensating; }
    public void setCompensating(boolean compensating) { this.compensating = compensating; }
}
