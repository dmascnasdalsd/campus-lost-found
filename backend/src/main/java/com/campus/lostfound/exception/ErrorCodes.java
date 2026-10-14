package com.campus.lostfound.exception;

/**
 * 业务错误码。HTTP 状态码用于表达错误大类，业务错误码用于前端做分支。
 */
public final class ErrorCodes {

    public static final int INVALID_PARAM = 1001;
    public static final int NOT_FOUND = 1002;
    public static final int UNAUTHORIZED = 1003;
    public static final int FORBIDDEN = 1004;
    public static final int CONFLICT = 1005;
    public static final int INTERNAL_ERROR = 1099;

    private ErrorCodes() {
    }
}
