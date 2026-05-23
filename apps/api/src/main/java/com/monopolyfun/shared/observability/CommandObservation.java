package com.monopolyfun.shared.observability;

import java.time.Instant;

public record CommandObservation(
        String commandType,
        String subjectType,
        String subjectId,
        String actorAccountId,
        String traceId,
        String outcome,
        long durationMs,
        Instant createdAt
) {
}
