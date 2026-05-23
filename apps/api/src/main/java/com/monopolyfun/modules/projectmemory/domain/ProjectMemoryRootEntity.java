package com.monopolyfun.modules.projectmemory.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectMemoryRootEntity(
        String id,
        String projectId,
        String repoBindingId,
        String provider,
        String repoOwner,
        String repoName,
        String branch,
        String commitSha,
        String rootHash,
        String latestPath,
        String syncStatus,
        String errorCode,
        String errorMessage,
        Map<String, Object> rawRoot,
        Instant createdAt,
        Instant syncedAt
) {
}
