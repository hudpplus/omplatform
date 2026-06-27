package com.omplatform.trade.statemachine;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态转换上下文（状态机内部使用）。
 * <p>
 * 与 {@link com.omplatform.api.order.dto.TransitionContextDTO} 对应，
 * DTO 用于跨 Dubbo 传输，本类用于状态机内部处理。
 */
public class TransitionContext {

    private String operatorId;
    private String operatorType;
    private String source;
    private String reason;
    private Map<String, Object> extras;
    private boolean skipGuards;

    public TransitionContext() {}

    public static TransitionContext systemContext(String reason) {
        TransitionContext ctx = new TransitionContext();
        ctx.operatorId = "SYSTEM";
        ctx.operatorType = "SYSTEM";
        ctx.source = "JOB";
        ctx.reason = reason;
        ctx.extras = new HashMap<>();
        return ctx;
    }

    public static TransitionContext fromDTO(com.omplatform.api.order.dto.TransitionContextDTO dto) {
        TransitionContext ctx = new TransitionContext();
        ctx.operatorId = dto.getOperatorId();
        ctx.operatorType = dto.getOperatorType();
        ctx.source = dto.getSource();
        ctx.reason = dto.getReason();
        ctx.extras = dto.getExtras() != null ? dto.getExtras() : new HashMap<>();
        return ctx;
    }

    // ====== Getters ======

    public String getOperatorId() { return operatorId; }
    public String getOperatorType() { return operatorType; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
    public Map<String, Object> getExtras() { return extras; }
    public boolean isSkipGuards() { return skipGuards; }

    // ====== Setters ======

    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public void setOperatorType(String operatorType) { this.operatorType = operatorType; }
    public void setSource(String source) { this.source = source; }
    public void setReason(String reason) { this.reason = reason; }
    public void setExtras(Map<String, Object> extras) { this.extras = extras; }
    public void setSkipGuards(boolean skipGuards) { this.skipGuards = skipGuards; }
}
