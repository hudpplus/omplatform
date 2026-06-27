package com.omplatform.trade.saga;

import java.time.Duration;
import java.util.List;

/**
 * Saga 步骤（ADR-020 §2）。
 * <p>
 * 一个步骤 = 正向操作 + 补偿操作。
 * <p>
 * 步骤执行支持两种模式（按优先级）：
 * <ol>
 *   <li><b>Lambda 注册</b> — 通过 {@link SagaExecutor#registerForwardStep(String, SagaExecutor.StepInvoker)}
 *       注册执行器，优先级最高，适合组合逻辑</li>
 *   <li><b>Dubbo 泛化调用</b> — 当设置了 {@link #forwardInterface} 时，自动通过 Dubbo
 *       GenericService 反射调用，步骤定义可序列化、可跨进程</li>
 * </ol>
 */
public class SagaStep {

    private String stepName;
    private int order;

    // ====== Dubbo 泛化调用所需字段（forward） ======
    /** Dubbo 服务接口全限定名，例如 "com.omplatform.api.inventory.InventoryService" */
    private String forwardInterface;
    /** 正向方法名 */
    private String forwardMethod;
    /** 各参数在 SagaContext 中的 key（按方法参数顺序） */
    private List<String> forwardParameterKeys;
    /** 各参数的 Java 类全名（如 ["java.util.List", "java.lang.String"]） */
    private List<String> forwardParameterClassNames;

    // ====== Dubbo 泛化调用所需字段（compensate） ======
    private String compensateInterface;
    private String compensateMethod;
    private List<String> compensateParameterKeys;
    private List<String> compensateParameterClassNames;

    // ====== 原始字段（保留，当前仅用于日志和 metadata） ======
    private String forwardService;
    private String forwardMethodAlias;
    private Duration stepTimeout;
    private String compensateService;
    private String compensateMethodAlias;

    // ====== 执行策略 ======
    private boolean mandatory = true;
    private SagaDefinition.RetryPolicy retryPolicy;
    private SagaDefinition.RetryPolicy compensateRetry;
    private CompensatePolicy compensatePolicy = CompensatePolicy.FAIL_FAST;

    public SagaStep() {}

    // ========== getter/setter ==========

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    // --- Dubbo 泛化调用 ---

    public String getForwardInterface() { return forwardInterface; }
    public void setForwardInterface(String forwardInterface) { this.forwardInterface = forwardInterface; }

    public String getForwardMethod() { return forwardMethod; }
    public void setForwardMethod(String forwardMethod) { this.forwardMethod = forwardMethod; }

    public List<String> getForwardParameterKeys() { return forwardParameterKeys; }
    public void setForwardParameterKeys(List<String> forwardParameterKeys) { this.forwardParameterKeys = forwardParameterKeys; }

    public List<String> getForwardParameterClassNames() { return forwardParameterClassNames; }
    public void setForwardParameterClassNames(List<String> forwardParameterClassNames) { this.forwardParameterClassNames = forwardParameterClassNames; }

    public String getCompensateInterface() { return compensateInterface; }
    public void setCompensateInterface(String compensateInterface) { this.compensateInterface = compensateInterface; }

    public String getCompensateMethod() { return compensateMethod; }
    public void setCompensateMethod(String compensateMethod) { this.compensateMethod = compensateMethod; }

    public List<String> getCompensateParameterKeys() { return compensateParameterKeys; }
    public void setCompensateParameterKeys(List<String> compensateParameterKeys) { this.compensateParameterKeys = compensateParameterKeys; }

    public List<String> getCompensateParameterClassNames() { return compensateParameterClassNames; }
    public void setCompensateParameterClassNames(List<String> compensateParameterClassNames) { this.compensateParameterClassNames = compensateParameterClassNames; }

    // --- 原始字段（保留兼容） ---

    public String getForwardService() { return forwardService; }
    public void setForwardService(String forwardService) { this.forwardService = forwardService; }

    /** @deprecated 改用 {@link #getForwardMethod()} */
    @Deprecated
    public String getForwardMethodAlias() { return forwardMethodAlias; }
    public void setForwardMethodAlias(String forwardMethodAlias) { this.forwardMethodAlias = forwardMethodAlias; }

    public void setForwardMethodLegacy(String method) { this.forwardMethodAlias = method; }

    public Duration getStepTimeout() { return stepTimeout; }
    public void setStepTimeout(Duration stepTimeout) { this.stepTimeout = stepTimeout; }

    public String getCompensateService() { return compensateService; }
    public void setCompensateService(String compensateService) { this.compensateService = compensateService; }

    /** @deprecated 改用 {@link #getCompensateMethod()} */
    @Deprecated
    public String getCompensateMethodAlias() { return compensateMethodAlias; }
    public void setCompensateMethodAlias(String compensateMethodAlias) { this.compensateMethodAlias = compensateMethodAlias; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public SagaDefinition.RetryPolicy getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(SagaDefinition.RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; }

    public SagaDefinition.RetryPolicy getCompensateRetry() { return compensateRetry; }
    public void setCompensateRetry(SagaDefinition.RetryPolicy compensateRetry) { this.compensateRetry = compensateRetry; }

    public CompensatePolicy getCompensatePolicy() { return compensatePolicy; }
    public void setCompensatePolicy(CompensatePolicy compensatePolicy) { this.compensatePolicy = compensatePolicy; }

    /** 补偿策略枚举 */
    public enum CompensatePolicy {
        FAIL_FAST,
        SKIP_ON_FAILURE
    }
}
