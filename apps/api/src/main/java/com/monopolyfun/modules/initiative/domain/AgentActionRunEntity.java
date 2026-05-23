package com.monopolyfun.modules.initiative.domain;

import java.time.Instant;
import java.util.Map;

public record AgentActionRunEntity(
        String id,
        String actionRunNo,
        String proposalId,
        String status,
        String workItemId,
        Map<String, Object> output,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {
    public AgentActionRunEntity {
        output = output == null ? Map.of() : Map.copyOf(output);
    }
}
