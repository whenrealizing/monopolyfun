package com.monopolyfun.shared.observability;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TraceContextHolder {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public Optional<String> currentTraceId() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    public String requireTraceId() {
        return currentTraceId().orElseGet(this::createFallbackTraceId);
    }

    public void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public void clear() {
        TRACE_ID.remove();
    }

    private String createFallbackTraceId() {
        String traceId = "trace-" + UUID.randomUUID();
        setTraceId(traceId);
        return traceId;
    }
}
