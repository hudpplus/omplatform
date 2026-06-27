package com.omplatform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 API 响应体。
 * <p>
 * 所有 Controller 方法统一返回此类型，由 ResponseBodyAdvice 强制包装。
 * <p>
 * 遵循 ADR-038 ApiResult 规范：
 * <ul>
 *   <li>成功：{@link #success()} / {@link #success(Object)}</li>
 *   <li>失败：{@link #error(String, String)}</li>
 *   <li>系统异常：{@link #sysError(String)}</li>
 * </ul>
 *
 * @param <T> data 类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否业务成功 */
    private boolean success;

    /** 业务码：00000=成功，四位失败码 */
    private String code;

    /** 错误描述 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 请求追踪 ID（由 IGW 注入） */
    private String traceId;

    /** 幂等标记：重复请求时为 true */
    @Builder.Default
    private boolean idempotent = false;

    // ========== 成功 ==========

    public static <T> ApiResult<T> success() {
        return ApiResult.<T>builder().success(true).code("00000").message("OK").build();
    }

    public static <T> ApiResult<T> success(T data) {
        return ApiResult.<T>builder().success(true).code("00000").message("OK").data(data).build();
    }

    public static <T> ApiResult<T> success(T data, String message) {
        return ApiResult.<T>builder().success(true).code("00000").message(message).data(data).build();
    }

    // ========== 业务错误 ==========

    public static <T> ApiResult<T> error(String code, String message) {
        return ApiResult.<T>builder().success(false).code(code).message(message).build();
    }

    public static <T> ApiResult<T> error(String code, String message, T data) {
        return ApiResult.<T>builder().success(false).code(code).message(message).data(data).build();
    }

    // ========== 系统异常 ==========

    public static <T> ApiResult<T> sysError(String message) {
        return ApiResult.<T>builder().success(false).code("99999").message(message).build();
    }

    // ========== 快捷 ==========

    public boolean isSuccess() {
        return success;
    }
}
