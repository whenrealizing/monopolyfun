package com.monopolyfun.modules.workthread.service.view;

public record ContributionRewardView(
        int totalShares,
        int bountyAmountMinor,
        String bountyToken,
        int claimableAmountMinor,
        String claimableToken
) {
}
