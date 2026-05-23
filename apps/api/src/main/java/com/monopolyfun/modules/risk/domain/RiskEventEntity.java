package com.monopolyfun.modules.risk.domain;

import java.time.Instant;
import java.util.Map;

public record RiskEventEntity(
        String id,
        String kind,
        String subjectType,
        String subjectId,
        String actorRef,
        String severity,
        String reason,
        Map<String, Object> payload,
        Instant createdAt
) {
}
