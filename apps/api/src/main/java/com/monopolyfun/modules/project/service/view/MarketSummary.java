package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.share.domain.SettlementType;

public record MarketSummary(
        String id,
        String name,
        String summary,
        String listingGoal,
        String leadAccountId,
        SettlementType settlementType,
        int nextCurveSlot,
        MarketStatus status,
        String sourceRef,
        String surfaceUrl
) {
}
