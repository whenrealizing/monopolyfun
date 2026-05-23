package com.monopolyfun.modules.initiative.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectInitiativeRecommendationEntity(
        String id,
        String recommendationNo,
        String accountId,
        String projectId,
        String projectNo,
        String recommendationType,
        String targetKey,
        String targetRoleCode,
        String title,
        String reason,
        String suggestedAction,
        Map<String, Object> input,
        String status,
        String workItemId,
        Instant createdAt,
        Instant updatedAt
) {
    public ProjectInitiativeRecommendationEntity {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
