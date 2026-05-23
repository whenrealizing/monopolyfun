package com.monopolyfun.shared.error;

import com.monopolyfun.shared.observability.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final TraceContextHolder traceContextHolder;

    public ApiExceptionHandler(TraceContextHolder traceContextHolder) {
        this.traceContextHolder = traceContextHolder;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null || exception.getReason().isBlank() ? status.getReasonPhrase() : exception.getReason();
        String code = exception instanceof ApiStatusException apiException
                ? apiException.code()
                : ApiErrorCodes.fromReason(exception.getReason());
        Map<String, Object> context = exception instanceof ApiStatusException apiException
                ? apiException.context()
                : Map.of();
        return ResponseEntity.status(status).body(error(status, code, message, request.getRequestURI(), context, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), ApiErrorCodes.fromFieldError(fieldError));
        }
        String message = "Validation failed";
        return ResponseEntity.badRequest()
                .body(error(HttpStatus.BAD_REQUEST, ApiErrorCodes.VALIDATION_FAILED, message, request.getRequestURI(), Map.of(), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Throwable root = exception.getMostSpecificCause();
        String message = root == null || root.getMessage() == null || root.getMessage().isBlank()
                ? "Malformed request body"
                : root.getMessage();
        // 中文注释：JSON 级合同校验失败需要直接返回 400，把项目专用字段错误暴露给前端和 agent，而不是落到 500。
        return ResponseEntity.status(status)
                .body(error(status, ApiErrorCodes.VALIDATION_FAILED, message, request.getRequestURI(), Map.of(), Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(error(status, ApiErrorCodes.RESOURCE_NOT_FOUND, "Resource not found", request.getRequestURI(), Map.of(), Map.of()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiError> authorizationDenied(AuthorizationDeniedException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(error(status, ApiErrorCodes.fromReason("System capability required"), "Access denied", request.getRequestURI(), Map.of(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        // 未预期异常需要保留 traceId 和路径，方便从 API 500 直接定位底层 SQL 或业务错误。
        log.warn("api unexpected path={} traceId={}", request.getRequestURI(), traceContextHolder.currentTraceId().orElse(null), exception);
        return ResponseEntity.status(status).body(error(status, ApiErrorCodes.SERVER_INTERNAL, "Internal server error", request.getRequestURI(), Map.of(), Map.of()));
    }

    private ApiError error(
            HttpStatus status,
            String code,
            String message,
            String path,
            Map<String, Object> context,
            Map<String, String> fields
    ) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                traceContextHolder.currentTraceId().orElse(null),
                context,
                fields);
    }
}
