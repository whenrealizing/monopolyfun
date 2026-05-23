package com.monopolyfun.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

public class ApiStatusException extends ResponseStatusException {
    private final String code;
    private final Map<String, Object> context;

    public ApiStatusException(HttpStatus status, String code, String reason) {
        this(status, code, reason, Map.of());
    }

    public ApiStatusException(HttpStatus status, String code, String reason, Map<String, Object> context) {
        super(status, reason);
        this.code = code;
        this.context = context == null ? Map.of() : Map.copyOf(context);
    }

    public String code() {
        return code;
    }

    public Map<String, Object> context() {
        return context;
    }
}
