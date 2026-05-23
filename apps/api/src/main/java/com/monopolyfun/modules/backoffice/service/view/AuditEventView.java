package com.monopolyfun.modules.backoffice.service.view;

import java.time.Instant;
import java.util.Map;

public record AuditEventView(
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
}
