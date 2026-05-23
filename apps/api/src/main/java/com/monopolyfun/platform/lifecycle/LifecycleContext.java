package com.monopolyfun.platform.lifecycle;

import java.time.Instant;
import java.util.Map;

public record LifecycleContext(
        String actorAccountId,
        String traceId,
        Instant occurredAt,
        Map<String, Object> metadata
) {
    public LifecycleContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
