package com.monopolyfun.modules.workthread.service.view;

public record ContributionLedgerEntryView(
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
        String createdAt
) {
}
