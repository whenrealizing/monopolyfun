package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;

public record ProjectRevenueAddressEntity(
        String id,
        String projectId,
        String chainId,
        String contractAddress,
        String tokenAddress,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
