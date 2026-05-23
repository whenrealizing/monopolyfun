package com.monopolyfun.modules.project.service.view;

public record ProjectRepoBindingView(
        String id,
        String projectId,
        String provider,
        String repoUrl,
        String repoOwner,
        String repoName,
        String defaultBranch,
        String installationId
) {
}
