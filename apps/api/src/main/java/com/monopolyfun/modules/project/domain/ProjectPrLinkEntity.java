package com.monopolyfun.modules.project.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectPrLinkEntity(
        String id,
        String projectId,
        String validationTaskId,
        String repoUrl,
        Integer prNumber,
        String prUrl,
        String headSha,
        String baseBranch,
        String branchName,
        String state,
        Instant mergedAt,
        Instant lastSyncedAt,
        Map<String, Object> rawPayload,
        Instant createdAt,
        Instant updatedAt
) {
}
