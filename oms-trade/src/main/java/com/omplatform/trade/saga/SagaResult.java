package com.omplatform.trade.saga;

import java.io.Serial;
import java.io.Serializable;

/**
 * Saga 执行结果。
 */
public class SagaResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sagaId;
    private boolean success;
    private String failedStep;
    private String errorMessage;

    public SagaResult() {}

    // ========== 工厂方法 ==========

    public static SagaResult success(String sagaId) {
        SagaResult r = new SagaResult();
        r.sagaId = sagaId;
        r.success = true;
        return r;
    }

    public static SagaResult failed(String sagaId, String failedStep, String errorMessage) {
        SagaResult r = new SagaResult();
        r.sagaId = sagaId;
        r.success = false;
        r.failedStep = failedStep;
        r.errorMessage = errorMessage;
        return r;
    }

    // ========== getter/setter ==========

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getFailedStep() { return failedStep; }
    public void setFailedStep(String failedStep) { this.failedStep = failedStep; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
