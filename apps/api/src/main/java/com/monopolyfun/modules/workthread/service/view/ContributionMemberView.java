package com.monopolyfun.modules.workthread.service.view;

public record ContributionMemberView(
        String accountId,
        int totalShares,
        int totalTaskValue,
        int settledCount,
        int bountyAmountMinor,
        String bountyToken
) {
}
