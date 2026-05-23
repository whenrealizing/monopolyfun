package com.monopolyfun.modules.work.domain;

import java.time.Instant;

public record WorkRunEntity(
        String id,
        String runNo,
        String workItemId,
        String actorAccountId,
        String status,
        String executionMode,
        Instant startedAt,
        Instant submittedAt,
        Instant acceptedAt,
        Instant updatedAt
) {
}
