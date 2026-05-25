package com.monopolyfun.modules.workthread.service.view;

import java.util.List;

public record WorkThreadView(
        String id,
        String threadNo,
        String projectId,
        String assigneeAccountId,
        String reviewerAccountId,
        String issueUrl,
        String repoRef,
        String title,
        String goal,
        List<String> deliverables,
        List<String> acceptanceCriteria,
        int taskValue,
        int bountyAmountMinor,
        String bountyToken,
        String status,
        String updatedAt
) {
}
