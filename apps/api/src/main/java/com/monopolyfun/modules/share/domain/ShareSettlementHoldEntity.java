package com.monopolyfun.modules.share.domain;

import java.time.Instant;
import java.util.Map;

public record ShareSettlementHoldEntity(
        String id,
        String orderId,
        String proofId,
        String shareReleaseRequestId,
        String marketId,
        String projectId,
        String itemId,
        String accountId,
        int amount,
        int curveSlot,
        LedgerReason reason,
        String status,
        String lockReason,
        String releaseReason,
        Instant releasedAt,
        Instant cancelledAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_LOCKED = "locked";
    public static final String STATUS_RELEASED = "released";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String LOCK_REASON_ACCEPTANCE_WINDOW = "acceptance_window";
    public static final String LOCK_REASON_DISPUTED = "disputed";
    public static final String LOCK_REASON_APPROVAL_PENDING = "approval_pending";
    public static final String LOCK_REASON_MANUAL_FREEZE = "manual_freeze";

    public static final String RELEASE_REASON_WINDOW_EXPIRED = "window_expired";
    public static final String RELEASE_REASON_REVIEW_ACCEPTED = "review_accepted";
    public static final String RELEASE_REASON_OVERRIDE_ACCEPTED = "override_accepted";
    public static final String RELEASE_REASON_DIRECT_FINAL_ACCEPT = "direct_final_accept";

    public boolean isLocked() {
        return STATUS_LOCKED.equalsIgnoreCase(status);
    }

    public boolean isReleased() {
        return STATUS_RELEASED.equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return STATUS_CANCELLED.equalsIgnoreCase(status);
    }

    public ShareSettlementHoldEntity withLockReason(String nextLockReason, String requestId, Instant now) {
        return new ShareSettlementHoldEntity(
                id,
                orderId,
                proofId,
                requestId,
                marketId,
                projectId,
                itemId,
                accountId,
                amount,
                curveSlot,
                reason,
                STATUS_LOCKED,
                nextLockReason,
                releaseReason,
                releasedAt,
                cancelledAt,
                metadata,
                createdAt,
                now);
    }

    public ShareSettlementHoldEntity withReleased(String nextReleaseReason, String requestId, Instant now) {
        return new ShareSettlementHoldEntity(
                id,
                orderId,
                proofId,
                requestId,
                marketId,
                projectId,
                itemId,
                accountId,
                amount,
                curveSlot,
                reason,
                STATUS_RELEASED,
                lockReason,
                nextReleaseReason,
                now,
                null,
                metadata,
                createdAt,
                now);
    }

    public ShareSettlementHoldEntity withCancelled(String nextReleaseReason, Instant now) {
        return new ShareSettlementHoldEntity(
                id,
                orderId,
                proofId,
                shareReleaseRequestId,
                marketId,
                projectId,
                itemId,
                accountId,
                amount,
                curveSlot,
                reason,
                STATUS_CANCELLED,
                lockReason,
                nextReleaseReason,
                null,
                now,
                metadata,
                createdAt,
                now);
    }
}
