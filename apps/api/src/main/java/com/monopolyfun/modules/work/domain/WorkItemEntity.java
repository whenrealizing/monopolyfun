package com.monopolyfun.modules.work.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkItemEntity(
        String id,
        String itemNo,
        String sourceType,
        String sourceId,
        String accountId,
        String title,
        String goal,
        List<String> acceptanceCriteria,
        List<String> inputRefs,
        Map<String, Object> outputSchema,
        String requiredRole,
        String requiredCapability,
        String urgency,
        String status,
        Instant claimExpiresAt,
        Instant readyAt,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkItemEntity {
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
    }
}
