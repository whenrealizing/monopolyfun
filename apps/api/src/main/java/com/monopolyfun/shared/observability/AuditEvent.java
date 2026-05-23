package com.monopolyfun.shared.observability;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String id,
        String type,
        String subjectType,
        String subjectId,
        String actorAccountId,
        String traceId,
        String outcome,
        Map<String, Object> payload,
        Instant createdAt
) {
    public AuditEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
