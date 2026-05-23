package com.monopolyfun.modules.repo.domain;

import java.time.Instant;
import java.util.Map;

public record RepoProvisionSessionEntity(
        String id,
        String projectNo,
        String provider,
        String repoUrl,
        String cloneUrl,
        String repoOwner,
        String repoName,
        String defaultBranch,
        String visibility,
        String status,
        String createdByAccountId,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public RepoProvisionSessionEntity bindProject(String nextProjectNo, Map<String, Object> nextMetadata, Instant now) {
        return new RepoProvisionSessionEntity(
                id,
                nextProjectNo,
                provider,
                repoUrl,
                cloneUrl,
                repoOwner,
                repoName,
                defaultBranch,
                visibility,
                "bound",
                createdByAccountId,
                nextMetadata,
                createdAt,
                now);
    }
}
