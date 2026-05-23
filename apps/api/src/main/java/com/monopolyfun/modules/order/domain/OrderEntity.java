package com.monopolyfun.modules.order.domain;

import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.platform.lifecycle.LifecycleEntity;
import com.monopolyfun.platform.lifecycle.LifecycleTransition;
import com.monopolyfun.platform.lifecycle.LifecycleTransitionResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OrderEntity(
        String id,
        String orderNo,
        String marketId,
        String listingId,
        ListingKind kind,
        PostKind postKind,
        String postId,
        String parentOrderId,
        OrderStatus status,
        String displayPhase,
        String claimedByAccountId,
        String submittedByAccountId,
        String acceptedByAccountId,
        String reviewerAccountId,
        Instant reviewDueAt,
        String proofId,
        SettlementType settlementType,
        BigDecimal settlementAmount,
        String closedReason,
        String disputeReason,
        String reviewPostId,
        String disputeOpenedByAccountId,
        OrderStatus disputeOpenedFromStatus,
        String disputeOpenedFromWindowStatus,
        Instant disputeOpenedFromWindowExpiresAt,
        Instant disputeOpenedAt,
        String disputeCancelledByAccountId,
        Instant disputeCancelledAt,
        String disputeCancelReason,
        ReviewDecision backofficeOverrideDecision,
        String backofficeOverrideReason,
        String challengeNonce,
        boolean settlementFrozen,
        List<String> acceptanceCriteriaSnapshot,
        String proofSpecSnapshot,
        String settlementSpecSnapshot,
        String reviewStatus,
        String disputeWindowStatus,
        Instant disputeWindowExpiresAt,
        Instant finalizedAt,
        String riskLevel,
        boolean manualReviewRequired,
        Map<String, Object> deliverySnapshot,
        Map<String, Object> settlementSnapshot,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) implements LifecycleEntity<OrderStatus> {
    public static final String ROLE_MODEL_VERSION = "order-actor-roles-v1";
    public static final String ROLE_PAYER = "payer";
    public static final String ROLE_FULFILLER = "fulfiller";
    public static final String ROLE_REVIEWER = "reviewer";
    public static final String ROLE_AUTHORITY = "authority";

    public static final String REVIEW_STATUS_NONE = "none";
    public static final String REVIEW_STATUS_OPEN = "open";
    public static final String REVIEW_STATUS_REVIEWER_ASSIGNED = "reviewer_assigned";
    public static final String REVIEW_STATUS_REVIEW_SUBMITTED = "review_submitted";
    public static final String REVIEW_STATUS_APPEAL_OPEN = "appeal_open";
    public static final String REVIEW_STATUS_RESOLVED = "resolved";

    public static final String DISPUTE_WINDOW_CLOSED = "closed";
    public static final String DISPUTE_WINDOW_OPEN = "open";
    public static final String DISPUTE_WINDOW_EXPIRED = "expired";

    public static LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> claim(
            String id,
            String orderNo,
            String marketId,
            String listingId,
            ListingKind kind,
            PostKind postKind,
            String postId,
            String parentOrderId,
            String claimedByAccountId,
            SettlementType settlementType,
            BigDecimal settlementAmount,
            String challengeNonce,
            List<String> acceptanceCriteriaSnapshot,
            String proofSpecSnapshot,
            String settlementSpecSnapshot,
            Map<String, Object> deliverySnapshot,
            Map<String, Object> settlementSnapshot,
            Map<String, Object> metadata,
            LifecycleContext context) {
        String deliveryMode = metadata == null ? "" : String.valueOf(metadata.get("deliveryMode"));
        String displayPhase = switch (deliveryMode.toLowerCase(java.util.Locale.ROOT)) {
            case "instant_fulfillment" -> "instant_fulfillment_pending";
            case "stock_fulfillment" -> "stock_fulfillment_pending";
            default -> "delivery_result_due";
        };
        OrderEntity order = new OrderEntity(
                id,
                orderNo,
                marketId,
                listingId,
                kind,
                postKind,
                postId,
                parentOrderId,
                OrderStatus.CLAIMED,
                displayPhase,
                claimedByAccountId,
                null,
                null,
                null,
                null,
                null,
                settlementType,
                settlementAmount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                challengeNonce,
                false,
                acceptanceCriteriaSnapshot == null ? List.of() : List.copyOf(acceptanceCriteriaSnapshot),
                proofSpecSnapshot == null ? "" : proofSpecSnapshot,
                settlementSpecSnapshot == null ? "" : settlementSpecSnapshot,
                REVIEW_STATUS_NONE,
                DISPUTE_WINDOW_CLOSED,
                null,
                null,
                "normal",
                false,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt(),
                context.occurredAt());
        LifecycleTransition<OrderStatus, OrderAction> transition = new LifecycleTransition<>(
                order.id(),
                order.lifecycleType(),
                null,
                order.status(),
                null,
                order.displayPhase(),
                OrderAction.CLAIM,
                context.actorAccountId(),
                context.traceId(),
                context.occurredAt(),
                context.metadata());
        return new LifecycleTransitionResult<>(order, transition);
    }

    private static BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public BigDecimal effectiveSettlementAmount() {
        if (settlementAmount != null) {
            return settlementAmount;
        }
        BigDecimal priceAmount = decimalValue(settlementSnapshot == null ? null : settlementSnapshot.get("priceAmount"));
        if (priceAmount != null) {
            return priceAmount;
        }
        return decimalValue(settlementSnapshot == null ? null : settlementSnapshot.get("budgetAmount"));
    }

    @Override
    public String lifecycleId() {
        return id;
    }

    @Override
    public String lifecycleType() {
        return "order";
    }

    @Override
    public OrderStatus lifecycleStatus() {
        return status;
    }

    @Override
    public String lifecycleDisplayPhase() {
        return displayPhase;
    }

    public String buyerAccountId() {
        return participant("buyerAccountId", claimedByAccountId);
    }

    public String sellerAccountId() {
        return participant("sellerAccountId", claimedByAccountId);
    }

    public String fulfillerAccountId() {
        return participant("fulfillerAccountId", claimedByAccountId);
    }

    public String acceptorAccountId() {
        return participant("acceptorAccountId", acceptedByAccountId == null ? claimedByAccountId : acceptedByAccountId);
    }

    public String roleModelVersion() {
        return ROLE_MODEL_VERSION;
    }

    public boolean isReviewOrder() {
        return kind == ListingKind.REVIEW;
    }

    public boolean isFinalStatus() {
        return status == OrderStatus.FINAL_ACCEPTED || status == OrderStatus.FINAL_CLOSED;
    }

    public OrderEntity withMetadata(Map<String, Object> nextMetadata, Instant nextUpdatedAt) {
        return copy(
                status,
                displayPhase,
                submittedByAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                reviewStatus,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                riskLevel,
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                nextMetadata == null ? Map.of() : Map.copyOf(nextMetadata),
                nextUpdatedAt);
    }

    public boolean hasOpenDisputeWindow(Instant now) {
        return status == OrderStatus.ACCEPTED_OPEN
                && DISPUTE_WINDOW_OPEN.equalsIgnoreCase(disputeWindowStatus)
                && disputeWindowExpiresAt != null
                && disputeWindowExpiresAt.isAfter(now);
    }

    // 中文注释：订单读写权限仍使用完整参与方快照，展示层只消费统一 actor role。
    public boolean hasParticipant(String accountId) {
        if (accountId == null || accountId.isBlank()) return false;
        return accountId.equals(buyerAccountId())
                || accountId.equals(sellerAccountId())
                || accountId.equals(fulfillerAccountId())
                || accountId.equals(acceptorAccountId())
                || accountId.equals(reviewerAccountId);
    }

    public String roleFor(String accountId) {
        if (accountId == null || accountId.isBlank()) return null;
        if (accountId.equals(reviewerAccountId)) return ROLE_REVIEWER;
        if (accountId.equals(acceptorAccountId()) || accountId.equals(buyerAccountId())) return ROLE_PAYER;
        if (accountId.equals(fulfillerAccountId()) || accountId.equals(sellerAccountId())) return ROLE_FULFILLER;
        return null;
    }

    private String participant(String key, String fallback) {
        return text(metadata.get(key), fallback);
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String normalizedRiskLevel() {
        return "review_required".equalsIgnoreCase(text(riskLevel, "")) ? "normal" : riskLevel;
    }

    private OrderEntity copy(
            OrderStatus nextStatus,
            String nextDisplayPhase,
            String nextSubmittedByAccountId,
            String nextAcceptedByAccountId,
            String nextReviewerAccountId,
            Instant nextReviewDueAt,
            String nextProofId,
            String nextClosedReason,
            String nextDisputeReason,
            String nextReviewPostId,
            ReviewDecision nextBackofficeOverrideDecision,
            String nextBackofficeOverrideReason,
            boolean nextSettlementFrozen,
            String nextReviewStatus,
            String nextDisputeWindowStatus,
            Instant nextDisputeWindowExpiresAt,
            Instant nextFinalizedAt,
            String nextRiskLevel,
            boolean nextManualReviewRequired,
            Map<String, Object> nextDeliverySnapshot,
            Map<String, Object> nextSettlementSnapshot,
            Map<String, Object> nextMetadata,
            Instant nextUpdatedAt) {
        return copyTracked(
                nextStatus,
                nextDisplayPhase,
                nextSubmittedByAccountId,
                nextAcceptedByAccountId,
                nextReviewerAccountId,
                nextReviewDueAt,
                nextProofId,
                nextClosedReason,
                nextDisputeReason,
                nextReviewPostId,
                disputeOpenedByAccountId,
                disputeOpenedFromStatus,
                disputeOpenedFromWindowStatus,
                disputeOpenedFromWindowExpiresAt,
                disputeOpenedAt,
                disputeCancelledByAccountId,
                disputeCancelledAt,
                disputeCancelReason,
                nextBackofficeOverrideDecision,
                nextBackofficeOverrideReason,
                nextSettlementFrozen,
                nextReviewStatus,
                nextDisputeWindowStatus,
                nextDisputeWindowExpiresAt,
                nextFinalizedAt,
                nextRiskLevel,
                nextManualReviewRequired,
                nextDeliverySnapshot,
                nextSettlementSnapshot,
                nextMetadata,
                nextUpdatedAt);
    }

    private OrderEntity copyTracked(
            OrderStatus nextStatus,
            String nextDisplayPhase,
            String nextSubmittedByAccountId,
            String nextAcceptedByAccountId,
            String nextReviewerAccountId,
            Instant nextReviewDueAt,
            String nextProofId,
            String nextClosedReason,
            String nextDisputeReason,
            String nextReviewPostId,
            String nextDisputeOpenedByAccountId,
            OrderStatus nextDisputeOpenedFromStatus,
            String nextDisputeOpenedFromWindowStatus,
            Instant nextDisputeOpenedFromWindowExpiresAt,
            Instant nextDisputeOpenedAt,
            String nextDisputeCancelledByAccountId,
            Instant nextDisputeCancelledAt,
            String nextDisputeCancelReason,
            ReviewDecision nextBackofficeOverrideDecision,
            String nextBackofficeOverrideReason,
            boolean nextSettlementFrozen,
            String nextReviewStatus,
            String nextDisputeWindowStatus,
            Instant nextDisputeWindowExpiresAt,
            Instant nextFinalizedAt,
            String nextRiskLevel,
            boolean nextManualReviewRequired,
            Map<String, Object> nextDeliverySnapshot,
            Map<String, Object> nextSettlementSnapshot,
            Map<String, Object> nextMetadata,
            Instant nextUpdatedAt) {
        return new OrderEntity(
                id,
                orderNo,
                marketId,
                listingId,
                kind,
                postKind,
                postId,
                parentOrderId,
                nextStatus,
                nextDisplayPhase,
                claimedByAccountId,
                nextSubmittedByAccountId,
                nextAcceptedByAccountId,
                nextReviewerAccountId,
                nextReviewDueAt,
                nextProofId,
                settlementType,
                settlementAmount,
                nextClosedReason,
                nextDisputeReason,
                nextReviewPostId,
                nextDisputeOpenedByAccountId,
                nextDisputeOpenedFromStatus,
                nextDisputeOpenedFromWindowStatus,
                nextDisputeOpenedFromWindowExpiresAt,
                nextDisputeOpenedAt,
                nextDisputeCancelledByAccountId,
                nextDisputeCancelledAt,
                nextDisputeCancelReason,
                nextBackofficeOverrideDecision,
                nextBackofficeOverrideReason,
                challengeNonce,
                nextSettlementFrozen,
                acceptanceCriteriaSnapshot,
                proofSpecSnapshot,
                settlementSpecSnapshot,
                nextReviewStatus,
                nextDisputeWindowStatus,
                nextDisputeWindowExpiresAt,
                nextFinalizedAt,
                nextRiskLevel,
                nextManualReviewRequired,
                nextDeliverySnapshot,
                nextSettlementSnapshot,
                nextMetadata,
                createdAt,
                nextUpdatedAt);
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> submitProof(
            String nextProofId,
            String submittedAccountId,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.DELIVERED, OrderAction.SUBMIT_PROOF);
        OrderEntity next = copy(
                OrderStatus.DELIVERED,
                "waiting_lead_acceptance",
                submittedAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                nextProofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                reviewStatus,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.SUBMIT_PROOF, next.status(), next.displayPhase(), context, Map.of("proofId", nextProofId)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> submitDeliveryResult(
            String submittedAccountId,
            Map<String, Object> nextDeliverySnapshot,
            Map<String, Object> nextMetadata,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.DELIVERED, OrderAction.SUBMIT_DELIVERY_RESULT);
        OrderEntity next = copy(
                OrderStatus.DELIVERED,
                "waiting_lead_acceptance",
                submittedAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                reviewStatus,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                nextDeliverySnapshot,
                settlementSnapshot,
                nextMetadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.SUBMIT_DELIVERY_RESULT, next.status(), next.displayPhase(), context, Map.of("deliveryResult", true)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> requestRevision(
            String actorAccountId,
            String reason,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.CLAIMED, OrderAction.REQUEST_REVISION);
        LinkedHashMap<String, Object> nextMetadata = new LinkedHashMap<>(metadata);
        nextMetadata.put("revisionRequestedByAccountId", actorAccountId);
        nextMetadata.put("revisionReason", reason == null ? "" : reason);
        nextMetadata.put("revisionRequestedAt", context.occurredAt().toString());
        // 中文注释：负责人要求返工时订单回到交付阶段，执行方可沿同一订单和仓库会话再次提交 PR proof。
        OrderEntity next = copy(
                OrderStatus.CLAIMED,
                "delivery_result_due",
                null,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                null,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                false,
                REVIEW_STATUS_NONE,
                DISPUTE_WINDOW_CLOSED,
                null,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                Map.of(),
                settlementSnapshot,
                nextMetadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.REQUEST_REVISION, next.status(), next.displayPhase(), context, Map.of("reason", reason == null ? "" : reason)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> recordProgress(
            Map<String, Object> nextMetadata,
            LifecycleContext context) {
        OrderEntity next = copy(
                OrderStatus.CLAIMED,
                "in_progress",
                submittedByAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                reviewStatus,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                nextMetadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.SUBMIT_PROGRESS, next.status(), next.displayPhase(), context, Map.of()));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> openDispute(
            String reason,
            String nextReviewListingId,
            LifecycleContext context) {
        return openDispute(reason, nextReviewListingId, reviewerAccountId, context);
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> openDispute(
            String reason,
            String nextReviewListingId,
            String nextReviewerAccountId,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.DISPUTED, OrderAction.OPEN_DISPUTE);
        OrderEntity next = copyTracked(
                OrderStatus.DISPUTED,
                "review_listing_open",
                submittedByAccountId,
                acceptedByAccountId,
                nextReviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                reason,
                nextReviewListingId,
                context.actorAccountId(),
                status,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                context.occurredAt(),
                null,
                null,
                null,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                true,
                REVIEW_STATUS_OPEN,
                DISPUTE_WINDOW_CLOSED,
                null,
                null,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.OPEN_DISPUTE, next.status(), next.displayPhase(), context, Map.of(
                        "reason", reason,
                        "reviewPostId", nextReviewListingId,
                        "reviewerAccountId", nextReviewerAccountId == null ? "" : nextReviewerAccountId)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> cancelDispute(
            String actorAccountId,
            String reason,
            LifecycleContext context) {
        OrderStatus restoredStatus = disputeOpenedFromStatus;
        if (restoredStatus != OrderStatus.DELIVERED && restoredStatus != OrderStatus.ACCEPTED_OPEN) {
            throw new IllegalStateException("Dispute restore status is missing");
        }
        String restoredDisplayPhase = restoredStatus == OrderStatus.ACCEPTED_OPEN ? "accepted_window_open" : "waiting_lead_acceptance";
        String restoredWindowStatus = restoredStatus == OrderStatus.ACCEPTED_OPEN
                ? text(disputeOpenedFromWindowStatus, DISPUTE_WINDOW_OPEN)
                : DISPUTE_WINDOW_CLOSED;
        Instant restoredWindowExpiresAt = restoredStatus == OrderStatus.ACCEPTED_OPEN ? disputeOpenedFromWindowExpiresAt : null;
        OrderTransitionPolicy.requireAllowed(status, restoredStatus, OrderAction.CANCEL_DISPUTE);

        // 中文注释：撤回争议恢复到发起前状态，让用户再显式验收，避免把争议撤回和订单接受混成一步。
        OrderEntity next = copyTracked(
                restoredStatus,
                restoredDisplayPhase,
                submittedByAccountId,
                acceptedByAccountId,
                null,
                null,
                proofId,
                closedReason,
                disputeReason,
                null,
                disputeOpenedByAccountId,
                disputeOpenedFromStatus,
                disputeOpenedFromWindowStatus,
                disputeOpenedFromWindowExpiresAt,
                disputeOpenedAt,
                actorAccountId,
                context.occurredAt(),
                reason,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                false,
                REVIEW_STATUS_NONE,
                restoredWindowStatus,
                restoredWindowExpiresAt,
                null,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.CANCEL_DISPUTE, next.status(), next.displayPhase(), context, Map.of(
                        "reason", reason == null ? "" : reason,
                        "restoredStatus", restoredStatus.name())));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> openAcceptanceWindow(
            String acceptedAccountId,
            String reason,
            Instant nextDisputeWindowExpiresAt,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.ACCEPTED_OPEN, OrderAction.ACCEPT);
        OrderEntity next = copy(
                OrderStatus.ACCEPTED_OPEN,
                "accepted_window_open",
                submittedByAccountId,
                acceptedAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                reason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                false,
                REVIEW_STATUS_NONE,
                DISPUTE_WINDOW_OPEN,
                nextDisputeWindowExpiresAt,
                null,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.ACCEPT, next.status(), next.displayPhase(), context, Map.of(
                        "reason", reason,
                        "disputeWindowExpiresAt", nextDisputeWindowExpiresAt == null ? "" : nextDisputeWindowExpiresAt.toString())));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> finalizeAccepted(
            String acceptedAccountId,
            String reason,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.FINAL_ACCEPTED, OrderAction.ACCEPT);
        String nextReviewStatus = status == OrderStatus.DISPUTED ? REVIEW_STATUS_RESOLVED : reviewStatus;
        String nextWindowStatus = status == OrderStatus.ACCEPTED_OPEN ? DISPUTE_WINDOW_EXPIRED : DISPUTE_WINDOW_CLOSED;
        OrderEntity next = copy(
                OrderStatus.FINAL_ACCEPTED,
                "final_accepted",
                submittedByAccountId,
                acceptedAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                reason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                false,
                nextReviewStatus,
                nextWindowStatus,
                null,
                context.occurredAt(),
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.ACCEPT, next.status(), next.displayPhase(), context, Map.of("reason", reason)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> finalizeClosed(
            String actorAccountId,
            String reason,
            LifecycleContext context) {
        OrderTransitionPolicy.requireAllowed(status, OrderStatus.FINAL_CLOSED, OrderAction.CLOSE);
        String nextReviewStatus = status == OrderStatus.DISPUTED ? REVIEW_STATUS_RESOLVED : reviewStatus;
        OrderEntity next = copy(
                OrderStatus.FINAL_CLOSED,
                "final_closed",
                submittedByAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                reason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                false,
                nextReviewStatus,
                DISPUTE_WINDOW_CLOSED,
                null,
                context.occurredAt(),
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.CLOSE, next.status(), next.displayPhase(), context, Map.of("reason", reason)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> markReviewSubmitted(
            LifecycleContext context) {
        OrderEntity next = copy(
                status,
                displayPhase,
                submittedByAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                REVIEW_STATUS_REVIEW_SUBMITTED,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.SUBMIT_PROOF, next.status(), next.displayPhase(), context, Map.of("reviewStatus", REVIEW_STATUS_REVIEW_SUBMITTED)));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> assignReviewer(
            String nextReviewerAccountId,
            Instant nextReviewDueAt,
            LifecycleContext context) {
        OrderEntity next = copy(
                status,
                displayPhase,
                submittedByAccountId,
                acceptedByAccountId,
                nextReviewerAccountId,
                nextReviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                settlementFrozen,
                REVIEW_STATUS_REVIEWER_ASSIGNED,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.ASSIGN_REVIEWER, next.status(), next.displayPhase(), context, Map.of(
                        "reviewerAccountId", nextReviewerAccountId,
                        "reviewDueAt", nextReviewDueAt == null ? "" : nextReviewDueAt.toString())));
    }

    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> replaceReviewer(
            String nextReviewPostId,
            String nextReviewerAccountId,
            Instant nextReviewDueAt,
            String nextReviewStatus,
            LifecycleContext context) {
        OrderEntity next = copy(
                OrderStatus.DISPUTED,
                "review_listing_open",
                submittedByAccountId,
                acceptedByAccountId,
                nextReviewerAccountId,
                nextReviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                nextReviewPostId,
                backofficeOverrideDecision,
                backofficeOverrideReason,
                true,
                nextReviewStatus,
                DISPUTE_WINDOW_CLOSED,
                null,
                null,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        Map<String, Object> transitionMetadata = new LinkedHashMap<>();
        transitionMetadata.put("reviewPostId", nextReviewPostId);
        transitionMetadata.put("reviewStatus", nextReviewStatus);
        if (nextReviewerAccountId != null && !nextReviewerAccountId.isBlank()) {
            transitionMetadata.put("reviewerAccountId", nextReviewerAccountId);
        }
        transitionMetadata.put("reviewDueAt", nextReviewDueAt == null ? "" : nextReviewDueAt.toString());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.OPEN_APPEAL, next.status(), next.displayPhase(), context, transitionMetadata));
    }

    // 中文注释：后台覆盖裁决只记录最终业务裁决，后续订单终态由命令服务按裁决继续推进。
    public LifecycleTransitionResult<OrderEntity, OrderStatus, OrderAction> backofficeOverride(
            ReviewDecision decision,
            String reason,
            String actorAccountId,
            LifecycleContext context) {
        OrderEntity next = copy(
                status,
                displayPhase,
                submittedByAccountId,
                acceptedByAccountId,
                reviewerAccountId,
                reviewDueAt,
                proofId,
                closedReason,
                disputeReason,
                reviewPostId,
                decision,
                reason,
                settlementFrozen,
                reviewStatus,
                disputeWindowStatus,
                disputeWindowExpiresAt,
                finalizedAt,
                normalizedRiskLevel(),
                manualReviewRequired,
                deliverySnapshot,
                settlementSnapshot,
                metadata,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(OrderAction.OVERRIDE_REVIEW, next.status(), next.displayPhase(), context, Map.of(
                        "decision", decision == null ? "" : decision.name(),
                        "reason", reason == null ? "" : reason,
                        "actorAccountId", actorAccountId)));
    }

    private LifecycleTransition<OrderStatus, OrderAction> transition(
            OrderAction action,
            OrderStatus nextStatus,
            String nextDisplayPhase,
            LifecycleContext context,
            Map<String, Object> transitionMetadata) {
        return new LifecycleTransition<>(
                id,
                lifecycleType(),
                status,
                nextStatus,
                displayPhase,
                nextDisplayPhase,
                action,
                context.actorAccountId(),
                context.traceId(),
                context.occurredAt(),
                transitionMetadata);
    }
}
