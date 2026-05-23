package com.monopolyfun.modules.project.domain;

import java.time.Instant;
import java.util.Map;

public record OrganizationEventEntity(
        String id,
        String projectId,
        String actorAccountId,
        String eventType,
        Map<String, Object> payload,
        Instant createdAt
) {
}
