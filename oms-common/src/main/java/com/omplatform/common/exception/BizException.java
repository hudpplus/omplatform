package com.omplatform.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常基类。
 * <p>
 * 由 {@link com.omplatform.common.api.GlobalExceptionHandler} 统一拦截并转换为 {@link com.omplatform.common.api.ApiResult}。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final String code;

    /** 错误参数（用于前端 i18n 替换） */
    private final transient Object[] args;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.args = null;
    }

    public BizException(String code, String message, Object... args) {
        super(message);
        this.code = code;
        this.args = args;
    }

    public BizException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.args = null;
    }
}
