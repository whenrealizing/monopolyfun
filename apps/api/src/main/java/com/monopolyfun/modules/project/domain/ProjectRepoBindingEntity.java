package com.monopolyfun.modules.project.domain;

import java.time.Instant;

public record ProjectRepoBindingEntity(
        String id,
        String projectId,
        String provider,
        String repoUrl,
        String repoOwner,
        String repoName,
        String defaultBranch,
        String installationId,
        String createdByAccountId,
        Instant createdAt,
        Instant updatedAt
) {
}
