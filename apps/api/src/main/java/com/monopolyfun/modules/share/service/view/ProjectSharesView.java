package com.monopolyfun.modules.share.service.view;

public record ProjectSharesView(
        String marketId,
        int shareTotal,
        int shareMinted,
        int shareReserved,
        int shareRemaining,
        int taskBudget,
        int taskMinted,
        int taskReserved,
        int taskRemaining,
        int reserveBudget,
        int nextCurveSlot,
        int currentBaseReward
) {
}
