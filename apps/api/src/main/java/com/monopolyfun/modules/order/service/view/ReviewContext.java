package com.monopolyfun.modules.order.service.view;

import java.util.List;

public record ReviewContext(
        String parentOrderId,
        String parentOrderNo,
        String reviewPostId,
        String reviewOrderId,
        String reviewOrderNo,
        String disputeReason,
        String disputeOpenedByAccountId,
        String disputeOpenedFromStatus,
        String disputeOpenedFromWindowStatus,
        java.time.Instant disputeOpenedFromWindowExpiresAt,
        java.time.Instant disputeOpenedAt,
        String disputeCancelledByAccountId,
        java.time.Instant disputeCancelledAt,
        String disputeCancelReason,
        String reviewerAccountId,
        java.time.Instant reviewDueAt,
        String backofficeOverrideDecision,
        String backofficeOverrideReason,
        List<String> disputeEvidenceRefs
) {
}
