package com.monopolyfun.platform.lifecycle;

import java.time.Instant;
import java.util.Map;

public record LifecycleTransition<S extends Enum<S>, A extends Enum<A>>(
        String entityId,
        String entityType,
        S fromStatus,
        S toStatus,
        String fromDisplayPhase,
        String toDisplayPhase,
        A action,
        String actorAccountId,
        String traceId,
        Instant occurredAt,
        Map<String, Object> metadata
) {
    public LifecycleTransition {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
