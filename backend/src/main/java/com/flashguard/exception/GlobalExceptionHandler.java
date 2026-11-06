package com.flashguard.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ", req, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Request sai định dạng (JSON, kiểu dữ liệu hoặc ngày giờ)", req, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MISSING_IDEMPOTENCY_KEY,
                "Thiếu header " + ex.getHeaderName(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Lỗi hệ thống", req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorCode code, String message,
                                                HttpServletRequest req, Map<String, String> fields) {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), status.value(), code, message,
                req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
