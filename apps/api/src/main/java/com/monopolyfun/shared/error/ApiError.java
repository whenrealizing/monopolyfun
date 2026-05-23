package com.monopolyfun.shared.error;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        Map<String, Object> context,
        Map<String, String> fields
) {
}
