package com.monopolyfun.modules.share.service.view;

import java.time.Instant;

public record ShareSettlementHoldView(
        String id,
        String orderId,
        String orderNo,
        String orderStatus,
        String marketId,
        String projectId,
        String itemId,
        String accountId,
        int amount,
        int curveSlot,
        String reason,
        String status,
        String lockReason,
        String releaseReason,
        Instant disputeWindowExpiresAt,
        Instant releasedAt,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
