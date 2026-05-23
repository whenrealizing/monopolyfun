package com.monopolyfun.modules.repo.api.response;

public record ProjectRepoProvisionResponse(
        String provisionSessionId,
        String repoUrl,
        String cloneUrl,
        String provider,
        String owner,
        String name,
        String defaultBranch,
        String visibility
) {
}
