package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.share.service.view.ProjectSharesView;

import java.util.List;

public record ProjectCommercializationView(
        String projectNo,
        String projectId,
        List<DirectionCardView> directions,
        DirectionCardView leadingDirection,
        List<DirectionCardView> validatedDirections,
        ProofStatsView proofStats,
        ProjectSharesView sharePool,
        RevenuePoolView revenuePool,
        DistributionEpochView currentDistribution
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
}
