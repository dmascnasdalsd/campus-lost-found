package com.campus.lostfound.exception;

/**
 * 业务异常：由 GlobalExceptionHandler 翻译成统一响应体。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final int httpStatus;

    public BusinessException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BusinessException invalidParam(String message) {
        return new BusinessException(ErrorCodes.INVALID_PARAM, 400, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCodes.NOT_FOUND, 404, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCodes.UNAUTHORIZED, 401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCodes.FORBIDDEN, 403, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCodes.CONFLICT, 409, message);
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
