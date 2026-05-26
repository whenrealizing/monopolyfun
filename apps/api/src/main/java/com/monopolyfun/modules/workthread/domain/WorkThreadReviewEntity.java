package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;

public record WorkThreadReviewEntity(
        String id,
        String reviewNo,
        String workThreadId,
        String resultId,
        String reviewerAccountId,
        String decision,
        String reason,
        Instant createdAt
) {
}
