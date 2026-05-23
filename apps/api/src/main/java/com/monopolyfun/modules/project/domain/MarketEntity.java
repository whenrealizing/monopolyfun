package com.monopolyfun.modules.project.domain;

import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import com.monopolyfun.platform.lifecycle.LifecycleEntity;
import com.monopolyfun.platform.lifecycle.LifecycleTransition;
import com.monopolyfun.platform.lifecycle.LifecycleTransitionResult;

import java.time.Instant;
import java.util.Map;

public record MarketEntity(
        String id,
        String name,
        String summary,
        String listingGoal,
        String leadAccountId,
        String sourceRef,
        String surfaceUrl,
        SettlementType settlementType,
        int nextCurveSlot,
        MarketStatus status,
        Instant leadLastActiveAt,
        String leadSeatStatus,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) implements LifecycleEntity<MarketStatus> {
    public static LifecycleTransitionResult<MarketEntity, MarketStatus, MarketAction> create(
            String id,
            String name,
            String summary,
            String listingGoal,
            String leadAccountId,
            String sourceRef,
            String surfaceUrl,
            SettlementType settlementType,
            LifecycleContext context) {
        MarketEntity market = new MarketEntity(
                id,
                name,
                summary,
                listingGoal,
                leadAccountId,
                sourceRef,
                surfaceUrl,
                settlementType,
                0,
                MarketStatus.ACTIVE,
                context.occurredAt(),
                "occupied",
                Map.of(),
                context.occurredAt(),
                context.occurredAt());
        LifecycleTransition<MarketStatus, MarketAction> transition = new LifecycleTransition<>(
                market.id(),
                market.lifecycleType(),
                null,
                market.status(),
                null,
                market.lifecycleDisplayPhase(),
                MarketAction.CREATE,
                context.actorAccountId(),
                context.traceId(),
                context.occurredAt(),
                context.metadata());
        return new LifecycleTransitionResult<>(market, transition);
    }

    @Override
    public String lifecycleId() {
        return id;
    }

    @Override
    public String lifecycleType() {
        return "market";
    }

    @Override
    public MarketStatus lifecycleStatus() {
        return status;
    }

    @Override
    public String lifecycleDisplayPhase() {
        return switch (status) {
            case ACTIVE -> "market_open";
            case STALLED -> "market_stalled";
        };
    }

    public MarketEntity withNextCurveSlot(int slot) {
        return new MarketEntity(id, name, summary, listingGoal, leadAccountId, sourceRef, surfaceUrl, settlementType,
                slot, status, leadLastActiveAt, leadSeatStatus, metadata, createdAt, Instant.now());
    }

    public MarketEntity withMetadata(Map<String, Object> nextMetadata) {
        return new MarketEntity(id, name, summary, listingGoal, leadAccountId, sourceRef, surfaceUrl, settlementType,
                nextCurveSlot, status, leadLastActiveAt, leadSeatStatus, nextMetadata, createdAt, Instant.now());
    }

    public MarketEntity withLeadAccountId(String nextLeadAccountId, Instant occurredAt) {
        return new MarketEntity(id, name, summary, listingGoal, nextLeadAccountId, sourceRef, surfaceUrl, settlementType,
                nextCurveSlot, status, occurredAt, "occupied", metadata, createdAt, occurredAt);
    }

    public LifecycleTransitionResult<MarketEntity, MarketStatus, MarketAction> updateDetails(
            String nextName,
            String nextSummary,
            String nextListingGoal,
            String nextSourceRef,
            String nextSurfaceUrl,
            SettlementType nextSettlementType,
            LifecycleContext context) {
        MarketEntity next = new MarketEntity(
                id,
                nextName,
                nextSummary,
                nextListingGoal,
                leadAccountId,
                nextSourceRef,
                nextSurfaceUrl,
                nextSettlementType,
                nextCurveSlot,
                status,
                leadLastActiveAt,
                leadSeatStatus,
                metadata,
                createdAt,
                context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(MarketAction.UPDATE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<MarketEntity, MarketStatus, MarketAction> stall(LifecycleContext context) {
        MarketEntity next = new MarketEntity(id, name, summary, listingGoal, leadAccountId, sourceRef, surfaceUrl, settlementType,
                nextCurveSlot, MarketStatus.STALLED, leadLastActiveAt, leadSeatStatus, metadata, createdAt, context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(MarketAction.STALL, next.status(), next.lifecycleDisplayPhase(), context));
    }

    public LifecycleTransitionResult<MarketEntity, MarketStatus, MarketAction> activate(LifecycleContext context) {
        MarketEntity next = new MarketEntity(id, name, summary, listingGoal, leadAccountId, sourceRef, surfaceUrl, settlementType,
                nextCurveSlot, MarketStatus.ACTIVE, context.occurredAt(), leadSeatStatus, metadata, createdAt, context.occurredAt());
        return new LifecycleTransitionResult<>(
                next,
                transition(MarketAction.ACTIVATE, next.status(), next.lifecycleDisplayPhase(), context));
    }

    private LifecycleTransition<MarketStatus, MarketAction> transition(
            MarketAction action,
            MarketStatus nextStatus,
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
