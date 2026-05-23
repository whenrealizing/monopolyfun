package com.monopolyfun.modules.project.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectRoleEntity(
        String id,
        String projectId,
        ProjectRoleCode roleCode,
        String accountId,
        String assignedByAccountId,
        Instant assignedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
