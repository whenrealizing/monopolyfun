package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.share.service.view.ProjectSharesView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectCommercializationView(
        String projectNo,
        String projectId,
        List<DirectionCardView> directions,
        DirectionCardView leadingDirection,
        List<DirectionCardView> validatedDirections,
        ProofStatsView proofStats,
        ProjectSharesView sharePool,
        RevenuePoolView revenuePool,
        DistributionEpochView currentDistribution,
        List<ContributionLedgerEntryView> contributionLedger,
        List<ContributionMemberView> contributors
) {
    public record DirectionCardView(
            String directionId,
            String statement,
            String hypothesis,
            String audience,
            String successMetric,
            int score,
            String status,
            List<String> taskIds,
            int taskCount,
            int claimedCount,
            int proofCount,
            int acceptedCount
    ) {
    }

    public record ProofStatsView(
            int totalTasks,
            int claimedTasks,
            int submittedProofs,
            int acceptedProofs,
            int deploymentProofs,
            int releaseProofs,
            int opsIncidentProofs
    ) {
    }

    public record RevenuePoolView(
            String currency,
            int eventCount,
            int totalMinor
    ) {
    }

    public record DistributionEpochView(
            String epochId,
            String status,
            String currency,
            int totalRevenueMinor,
            int eligibleShareMinted,
            int acceptedTaskCount
    ) {
    }

    public record ContributionLedgerEntryView(
            String id,
            String projectId,
            String sourceType,
            String sourceId,
            String contributionRole,
            String accountId,
            int taskValue,
            int shares,
            int bountyAmountMinor,
            String bountyToken,
            String status,
            BigDecimal contributionWeight,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
    }

    public record ContributionMemberView(
            String accountId,
            int totalShares,
            int totalTaskValue,
            int settledCount,
            int bountyAmountMinor,
            String bountyToken,
            BigDecimal totalContributionWeight,
            Map<String, Integer> sourceCounts
    ) {
    }
}
