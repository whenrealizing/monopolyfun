package com.monopolyfun.modules.project.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectCiCheckEntity(
        String id,
        String projectId,
        String validationTaskId,
        Integer prNumber,
        String checkName,
        String status,
        String conclusion,
        String detailsUrl,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> rawPayload,
        Instant createdAt,
        Instant updatedAt
) {
}
