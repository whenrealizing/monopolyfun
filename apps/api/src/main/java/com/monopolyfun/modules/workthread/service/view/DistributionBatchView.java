package com.monopolyfun.modules.workthread.service.view;

public record DistributionBatchView(
        String id,
        String projectId,
        String period,
        int totalRevenueMinor,
        int totalSnapshotShares,
        String merkleRoot,
        int myClaimableAmountMinor,
        String token,
        String status,
        String createdAt,
        String updatedAt
) {
}
