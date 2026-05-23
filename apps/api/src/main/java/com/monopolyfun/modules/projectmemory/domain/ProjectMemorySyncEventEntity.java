package com.monopolyfun.modules.projectmemory.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectMemorySyncEventEntity(
        String id,
        String projectId,
        String rootId,
        String eventType,
        String status,
        String message,
        Map<String, Object> payload,
        Instant createdAt
) {
}
