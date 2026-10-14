package com.campus.lostfound.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 表单校验失败：把字段级错误拼成一句中文提示，前端仍可用 code=INVALID_PARAM 做统一处理。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCodes.INVALID_PARAM, message));
    }

    private static String describe(FieldError error) {
        String detail = error.getDefaultMessage() == null ? "取值不合法" : error.getDefaultMessage();
        return error.getField() + "：" + detail;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未预期的服务端异常", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCodes.INTERNAL_ERROR, "服务器内部错误"));
    }
}
