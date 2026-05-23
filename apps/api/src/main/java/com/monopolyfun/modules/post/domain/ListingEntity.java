package com.monopolyfun.modules.post.domain;

import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.platform.lifecycle.LifecycleEntity;
import com.monopolyfun.platform.lifecycle.LifecycleTransition;
import com.monopolyfun.platform.lifecycle.LifecycleTransitionResult;

import java.time.Instant;
import java.util.Map;

public record ListingEntity(
        String id,
        String marketId,
        ListingKind kind,
        String parentOrderId,
        String title,
        String subjectType,
        String subjectRef,
        String deliverableSpec,
        String proofSpec,
        String settlementSpec,
        int inventoryLimit,
        int activeOrdersCount,
        int stockTotal,
        SettlementType settlementType,
        ListingStatus status,
        String openedByAccountId,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) implements LifecycleEntity<ListingStatus> {
    public static LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> create(
            String id,
            String marketId,
            ListingKind kind,
            String parentOrderId,
            String title,
            String subjectType,
            String subjectRef,
            String deliverableSpec,
            String proofSpec,
            String settlementSpec,
            int inventoryLimit,
            SettlementType settlementType,
            String openedByAccountId,
            ListingStatus initialStatus,
            LifecycleContext context) {
        ListingEntity listing = new ListingEntity(
                id,
                marketId,
                kind,
                parentOrderId,
                title,
                subjectType,
                subjectRef,
                deliverableSpec,
                proofSpec,
                settlementSpec,
                inventoryLimit,
                0,
                inventoryLimit,
                settlementType,
                initialStatus,
                openedByAccountId,
                Map.of(),
                context.occurredAt(),
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                listing,
                new LifecycleTransition<>(
                        listing.id(),
                        listing.lifecycleType(),
                        null,
                        listing.status(),
                        null,
                        listing.lifecycleDisplayPhase(),
                        ListingAction.CREATE,
                        context.actorAccountId(),
                        context.traceId(),
                        context.occurredAt(),
                        context.metadata()));
    }

    @Override
    public String lifecycleId() {
        return id;
    }

    @Override
    public String lifecycleType() {
        return "listing";
    }

    @Override
    public ListingStatus lifecycleStatus() {
        return status;
    }

    @Override
    public String lifecycleDisplayPhase() {
        if (status == ListingStatus.DRAFT) return "draft";
        if (status == ListingStatus.OPEN) return "accepting_claims";
        if (status == ListingStatus.PAUSED) return "capacity_full";
        if (status == ListingStatus.CLOSED) return "closed";
        if (status == ListingStatus.ARCHIVED) return "archived";
        return status.name().toLowerCase();
    }

    public boolean hasAvailableCapacity() {
        return activeOrdersCount < inventoryLimit;
    }

    public ListingEntity withActiveOrdersCount(int count) {
        return new ListingEntity(id, marketId, kind, parentOrderId, title, subjectType, subjectRef, deliverableSpec,
                proofSpec, settlementSpec, inventoryLimit, count, stockTotal, settlementType, status, openedByAccountId,
                metadata, createdAt, Instant.now());
    }

    public ListingEntity withStatus(ListingStatus nextStatus) {
        return new ListingEntity(id, marketId, kind, parentOrderId, title, subjectType, subjectRef, deliverableSpec,
                proofSpec, settlementSpec, inventoryLimit, activeOrdersCount, stockTotal, settlementType, nextStatus,
                openedByAccountId, metadata, createdAt, Instant.now());
    }

    public ListingEntity withMetadata(Map<String, Object> nextMetadata) {
        // 中文注释：item 的交付配置和库存消耗放在 listing metadata，避免为不同 item 类型拆出多套状态表。
        return new ListingEntity(id, marketId, kind, parentOrderId, title, subjectType, subjectRef, deliverableSpec,
                proofSpec, settlementSpec, inventoryLimit, activeOrdersCount, stockTotal, settlementType, status,
                openedByAccountId, nextMetadata, createdAt, Instant.now());
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> pause(LifecycleContext context) {
        ListingEntity next = withStatus(ListingStatus.PAUSED);
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.PAUSE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> reopen(LifecycleContext context) {
        ListingEntity next = withStatus(ListingStatus.OPEN);
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.REOPEN, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> close(LifecycleContext context) {
        ListingEntity next = withStatus(ListingStatus.CLOSED);
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.CLOSE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> archive(LifecycleContext context) {
        ListingEntity next = withStatus(ListingStatus.ARCHIVED);
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.ARCHIVE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> publish(LifecycleContext context) {
        ListingEntity next = withStatus(ListingStatus.OPEN);
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.PUBLISH, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<ListingEntity, ListingStatus, ListingAction> updateDetails(
            String nextTitle,
            String nextSubjectType,
            String nextSubjectRef,
            String nextDeliverableSpec,
            String nextProofSpec,
            String nextSettlementSpec,
            int nextInventoryLimit,
            SettlementType nextSettlementType,
            LifecycleContext context) {
        ListingEntity next = new ListingEntity(
                id,
                marketId,
                kind,
                parentOrderId,
                nextTitle,
                nextSubjectType,
                nextSubjectRef,
                nextDeliverableSpec,
                nextProofSpec,
                nextSettlementSpec,
                nextInventoryLimit,
                Math.min(activeOrdersCount, nextInventoryLimit),
                Math.max(stockTotal, nextInventoryLimit),
                nextSettlementType,
                status,
                openedByAccountId,
                metadata,
                createdAt,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(ListingAction.UPDATE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    private LifecycleTransition<ListingStatus, ListingAction> transition(
            ListingAction action,
            ListingStatus nextStatus,
            String nextDisplayPhase,
            LifecycleContext context) {
        return new LifecycleTransition<>(
                id,
                lifecycleType(),
                status,
                nextStatus,
                lifecycleDisplayPhase(),
                nextDisplayPhase,
                action,
                context.actorAccountId(),
                context.traceId(),
                context.occurredAt(),
                context.metadata());
    }
}
