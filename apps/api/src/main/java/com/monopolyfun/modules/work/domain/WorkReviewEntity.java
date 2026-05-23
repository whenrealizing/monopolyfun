package com.monopolyfun.modules.work.domain;

import java.time.Instant;

public record WorkReviewEntity(
        String id,
        String reviewNo,
        String workRunId,
        String reviewerAccountId,
        String status,
        String decision,
        String decisionReason,
        Instant createdAt,
        Instant resolvedAt
) {
}
