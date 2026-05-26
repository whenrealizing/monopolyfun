package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;

public record DistributionBatchEntity(
        String id,
        String projectId,
        String period,
        int totalRevenueMinor,
        int totalSnapshotShares,
        String merkleRoot,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
