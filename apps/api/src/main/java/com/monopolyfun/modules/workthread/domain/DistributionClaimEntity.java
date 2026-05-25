package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;
import java.util.List;

public record DistributionClaimEntity(
        String id,
        String batchId,
        String accountId,
        String walletAddress,
        int amountMinor,
        List<String> proof,
        String txHash,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public DistributionClaimEntity {
        proof = proof == null ? List.of() : List.copyOf(proof);
    }
}
