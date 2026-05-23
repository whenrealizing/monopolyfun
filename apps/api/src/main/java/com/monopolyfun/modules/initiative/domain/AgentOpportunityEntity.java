package com.monopolyfun.modules.initiative.domain;

import java.time.Instant;

public record AgentOpportunityEntity(
        String id,
        String opportunityNo,
        String mandateId,
        String type,
        String reason,
        String targetType,
        String targetId,
        String suggestedAction,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
