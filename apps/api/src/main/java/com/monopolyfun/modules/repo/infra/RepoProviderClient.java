package com.monopolyfun.modules.repo.infra;

import java.time.Instant;
import java.util.Map;

public interface RepoProviderClient {
    ProvisionedRepository provisionPublicRepository(ProvisionRepositoryCommand command);

    RepositoryAccess issueRepositoryAccess(IssueRepositoryAccessCommand command);

    PullRequestInspection inspectPullRequest(InspectPullRequestCommand command);

    record ProvisionRepositoryCommand(
            String repoName,
            String description,
            String projectNo,
            String actorAccountId
    ) {
    }

    record ProvisionedRepository(
            String provider,
            String repoUrl,
            String cloneUrl,
            String repoOwner,
            String repoName,
            String defaultBranch,
            String visibility,
            Map<String, Object> metadata
    ) {
    }

    record IssueRepositoryAccessCommand(
            String repoOwner,
            String repoName,
            String orderNo,
            String headBranch,
            String issuedToAccountId
    ) {
    }

    record RepositoryAccess(
            String accessToken,
            Instant expiresAt,
            Map<String, Object> metadata
    ) {
    }

    record InspectPullRequestCommand(
            String repoOwner,
            String repoName,
            String prUrl,
            String expectedHeadCommit
    ) {
    }

    record PullRequestInspection(
            String repoUrl,
            String prUrl,
            String headCommit,
            String baseBranch,
            String headBranch,
            String state,
            boolean merged,
            boolean draft,
            String ciStatus,
            String statusCheckRollup,
            String diffSummary,
            Map<String, Object> metadata
    ) {
    }
}
