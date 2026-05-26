package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;

public record DistributionEntitlementEntity(
        String id,
        String batchId,
        String accountId,
        int snapshotShares,
        int amountMinor,
        String status,
        Instant createdAt
) {
}
