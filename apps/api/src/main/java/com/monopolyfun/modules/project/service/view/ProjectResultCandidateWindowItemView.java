package com.monopolyfun.modules.project.service.view;

import java.time.Instant;

public record ProjectResultCandidateWindowItemView(
        String candidateId,
        String taskId,
        String taskTitle,
        String resultType,
        String candidateStatus,
        String consensusStatus,
        int supportCount,
        int supportThreshold,
        String reasonToAct,
        String nextAction,
        int actionScore,
        Integer prNumber,
        String headSha,
        Instant taskUpdatedAt
) {
}
