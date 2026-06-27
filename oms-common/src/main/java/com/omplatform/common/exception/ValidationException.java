package com.omplatform.common.exception;

import java.io.Serial;

/**
 * 参数校验异常 —— 请求参数不合法。
 */
public class ValidationException extends BizException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", String.format("[%s] %s", field, message));
    }
}
