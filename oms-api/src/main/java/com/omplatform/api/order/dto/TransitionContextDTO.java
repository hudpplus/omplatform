package com.omplatform.api.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 状态转换上下文（跨服务传递）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionContextDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String operatorId;
    private String operatorType;   // BUYER / SELLER / ADMIN / SYSTEM
    private String source;         // API / JOB / CALLBACK / MQ
    private String reason;
    private Map<String, Object> extras;

    public static TransitionContextDTO systemContext(String reason) {
        return TransitionContextDTO.builder()
                .operatorId("SYSTEM")
                .operatorType("SYSTEM")
                .source("JOB")
                .reason(reason)
                .build();
    }
}
