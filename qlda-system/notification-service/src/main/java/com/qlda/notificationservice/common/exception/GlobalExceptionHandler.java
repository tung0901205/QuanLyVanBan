package com.qlda.notificationservice.common.exception;

import com.qlda.notificationservice.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.error("Application error: code={} message={}", ex.getErrorCode(), ex.getMessage(), ex);
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.failure(errorCode.getMessage(), errorCode.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? ErrorCode.INVALID_REQUEST.getMessage() : fieldError.getDefaultMessage();
        return ResponseEntity
            .status(ErrorCode.INVALID_REQUEST.getStatus())
            .body(ApiResponse.failure(message, ErrorCode.INVALID_REQUEST.getCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ignored) {
        return ResponseEntity
            .status(403)
            .body(ApiResponse.failure("Khong du quyen", "FORBIDDEN"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unexpected notification/report service error", ex);
        return ResponseEntity
            .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
            .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }
}

