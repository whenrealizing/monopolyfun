package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;

public record ContributionEntryEntity(
        String id,
        String projectId,
        String workThreadId,
        String resultId,
        String accountId,
        int taskValue,
        int shares,
        int bountyAmountMinor,
        String bountyToken,
        String status,
        Instant createdAt
) {
}
