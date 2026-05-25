package com.monopolyfun.modules.workthread.service.view;

import java.util.List;

public record WorkThreadOverviewView(
        String projectId,
        String projectNo,
        boolean owner,
        ProjectRevenueAddressView revenueAddress,
        ContributionRewardView myRewards,
        List<WorkThreadView> workThreads,
        List<ContributionLedgerEntryView> ledger,
        List<ContributionMemberView> contributors,
        List<DistributionBatchView> distributions
) {
}
