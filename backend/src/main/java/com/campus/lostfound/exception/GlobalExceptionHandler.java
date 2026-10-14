package com.campus.lostfound.exception;

import com.campus.lostfound.dto.ApiResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * 上传文件超过 multipart 大小上限：返回 400 与可辨识业务码，而非 500。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCodes.INVALID_PARAM, "上传文件超过大小上限（单张图片最多 5MB）"));
    }

    /**
     * 请求体无法解析（如枚举取值非法、JSON 格式错误）：返回 400 而非 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCodes.INVALID_PARAM, "请求体格式不正确"));
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
