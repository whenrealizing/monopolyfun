package com.monopolyfun.modules.projectmemory.service.view;

import java.util.Map;

public record ProjectMemoryRootView(
        String id,
        String provider,
        String repoOwner,
        String repoName,
        String branch,
        String commitSha,
        String rootHash,
        String syncStatus,
        String errorCode,
        String errorMessage,
        Map<String, Object> rawRoot,
        String syncedAt
) {
}
