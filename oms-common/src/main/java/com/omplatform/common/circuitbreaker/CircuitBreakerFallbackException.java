package com.omplatform.common.circuitbreaker;

/**
 * 断路器 fallback 执行失败异常。
 * <p>
 * 熔断本身已触发，但 fallback 也失败了，由调用方决定如何处理。
 */
public class CircuitBreakerFallbackException extends RuntimeException {

    private final String breakerName;
    private final String operation;

    public CircuitBreakerFallbackException(String breakerName, String operation, Throwable cause) {
        super(String.format("[断路器] fallback 失败: name=%s, operation=%s", breakerName, operation), cause);
        this.breakerName = breakerName;
        this.operation = operation;
    }

    public String getBreakerName() {
        return breakerName;
    }

    public String getOperation() {
        return operation;
    }
}
