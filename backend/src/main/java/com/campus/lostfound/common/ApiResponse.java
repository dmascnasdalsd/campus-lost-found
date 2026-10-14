package com.campus.lostfound.common;

/**
 * 统一响应体：所有接口都返回 { code, message, data }。
 * code 为 0 表示成功，非 0 为业务错误码。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
