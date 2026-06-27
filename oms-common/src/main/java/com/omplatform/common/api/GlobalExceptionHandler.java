package com.omplatform.common.api;

import com.omplatform.common.constant.ErrorCode;
import com.omplatform.common.api.ApiResult;
import com.omplatform.common.exception.BizException;
import com.omplatform.common.exception.OptimisticLockException;
import com.omplatform.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 统一将异常转换为 {@link ApiResult} 返回。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========== 业务异常 ==========

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK) // 业务异常仍是 200，错误体现在 code 字段
    public ApiResult<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidation(ValidationException e) {
        return ApiResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResult<Void> handleOptimisticLock(OptimisticLockException e) {
        log.warn("乐观锁冲突: {}", e.getMessage());
        return ApiResult.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, e.getMessage());
    }

    // ========== 参数校验 ==========

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidationErrors(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResult.error(ErrorCode.PARAM_INVALID, msg);
    }

    // ========== 系统异常兜底 ==========

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleUnknown(Exception e) {
        log.error("未捕获异常", e);
        return ApiResult.sysError("服务器内部错误，请稍后重试");
    }
}
