package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;
import java.util.List;

public record WorkThreadEntity(
        String id,
        String threadNo,
        String projectId,
        String createdByAccountId,
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
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant acceptedAt,
        Instant settledAt
) {
    public WorkThreadEntity {
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }
}
