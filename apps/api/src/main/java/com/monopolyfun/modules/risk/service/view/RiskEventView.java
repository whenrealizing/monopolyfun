package com.monopolyfun.modules.risk.service.view;

import java.time.Instant;
import java.util.Map;

public record RiskEventView(
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
