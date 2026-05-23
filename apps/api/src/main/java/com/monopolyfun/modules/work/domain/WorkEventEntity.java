package com.monopolyfun.modules.work.domain;

import java.time.Instant;
import java.util.Map;

public record WorkEventEntity(
        String id,
        String subjectType,
        String subjectId,
        String actorAccountId,
        String eventType,
        String actionId,
        Map<String, Object> inputSnapshot,
        Map<String, Object> outputSnapshot,
        String receiptId,
        Instant createdAt
) {
    public WorkEventEntity {
        inputSnapshot = inputSnapshot == null ? Map.of() : Map.copyOf(inputSnapshot);
        outputSnapshot = outputSnapshot == null ? Map.of() : Map.copyOf(outputSnapshot);
    }
}
