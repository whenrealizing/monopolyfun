package com.monopolyfun.modules.share.domain;

import java.time.Instant;

public record SharesLedgerEntryEntity(
        String id,
        String sourceType,
        String sourceId,
        ShareIssuerType issuerType,
        String issuerId,
        String marketId,
        String orderId,
        String proofId,
        String shareReleaseRequestId,
        String projectId,
        String itemId,
        String accountId,
        int amount,
        int curveSlot,
        LedgerReason reason,
        SettlementType settlementTypeSnapshot,
        Instant createdAt
) {
}
