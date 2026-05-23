package com.monopolyfun.modules.share.service.mapper;

import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestEntity;
import com.monopolyfun.modules.share.domain.ShareSettlementHoldEntity;
import com.monopolyfun.modules.share.service.view.ShareReleaseRequestView;
import com.monopolyfun.modules.share.service.view.ShareSettlementHoldView;

import java.util.Locale;

public final class ShareViewMapper {
    private ShareViewMapper() {
    }

    public static ShareReleaseRequestView shareReleaseRequest(ShareReleaseRequestEntity request) {
        if (request == null) return null;
        return new ShareReleaseRequestView(
                request.id(),
                request.issuerType(),
                request.issuerId(),
                request.marketId(),
                request.projectId(),
                request.orderId(),
                request.proofId(),
                request.accountId(),
                request.amount(),
                request.curveSlot(),
                request.status(),
                request.requiredRoleCodes(),
                request.approvedRoleCodes(),
                request.skippedRoleCodes(),
                request.requestedByAccountId(),
                request.resolvedAt(),
                request.metadata(),
                request.createdAt(),
                request.updatedAt());
    }

    public static ShareSettlementHoldView shareSettlementHold(ShareSettlementHoldEntity hold, OrderSummary order) {
        if (hold == null) return null;
        return new ShareSettlementHoldView(
                hold.id(),
                hold.orderId(),
                order == null ? hold.orderId() : order.orderNo(),
                order == null ? null : order.status(),
                hold.marketId(),
                hold.projectId(),
                hold.itemId(),
                hold.accountId(),
                hold.amount(),
                hold.curveSlot(),
                hold.reason().name().toLowerCase(Locale.ROOT),
                hold.status(),
                hold.lockReason(),
                hold.releaseReason(),
                order == null ? null : order.disputeWindowExpiresAt(),
                hold.releasedAt(),
                hold.cancelledAt(),
                hold.createdAt(),
                hold.updatedAt());
    }
}
