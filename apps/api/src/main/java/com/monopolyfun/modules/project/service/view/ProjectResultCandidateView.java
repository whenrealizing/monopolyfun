package com.monopolyfun.modules.project.service.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectResultCandidateView(
        String candidateId,
        String projectId,
        String taskId,
        String launchId,
        String taskTitle,
        String taskStatus,
        boolean taskCompleted,
        String createdByAccountId,
        String claimedByAccountId,
        String resultType,
        String candidateStatus,
        String mergeabilityStatus,
        String mergeabilityReason,
        String repoUrl,
        Integer prNumber,
        String prUrl,
        String headSha,
        String baseBranch,
        String branchName,
        String prState,
        String ciStatus,
        boolean ciPassed,
        int supportCount,
        int weightedSupport,
        int supportThreshold,
        String consensusStatus,
        String finalReviewStatus,
        String reviewedCommitSha,
        List<Map<String, Object>> checks,
        Instant lastSyncedAt,
        Instant taskUpdatedAt
) {
}
