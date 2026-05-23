package com.monopolyfun.modules.project.domain;

import java.time.Instant;

public record ProjectSharePoolEntity(
        String projectId,
        String marketId,
        int shareTotal,
        int shareMinted,
        int shareReserved,
        int taskBudget,
        int taskMinted,
        int taskReserved,
        int reserveBudget,
        int nextCurveSlot,
        int initialBaseReward,
        double decay,
        int minBaseReward,
        Instant createdAt,
        Instant updatedAt
) {
    public int shareRemaining() {
        return Math.max(0, shareTotal - shareMinted - shareReserved);
    }

    public int taskRemaining() {
        return Math.max(0, taskBudget - taskMinted - taskReserved);
    }

    public int currentBaseReward() {
        return Math.max(minBaseReward, (int) Math.floor(initialBaseReward * Math.pow(decay, nextCurveSlot)));
    }

    public int rewardPreview(double difficultyScore) {
        return Math.max(1, (int) Math.floor(currentBaseReward() * difficultyScore));
    }

    public ProjectSharePoolEntity withTaskReserved(int delta, Instant now) {
        return new ProjectSharePoolEntity(
                projectId, marketId, shareTotal, shareMinted, Math.max(0, shareReserved + delta),
                taskBudget, taskMinted, Math.max(0, taskReserved + delta),
                reserveBudget, nextCurveSlot, initialBaseReward, decay, minBaseReward, createdAt, now);
    }

    public ProjectSharePoolEntity withTaskMinted(int mintedDelta, int reservedDelta, int nextSlot, Instant now) {
        return new ProjectSharePoolEntity(
                projectId, marketId, shareTotal, Math.max(0, shareMinted + mintedDelta), Math.max(0, shareReserved + reservedDelta),
                taskBudget, Math.max(0, taskMinted + mintedDelta), Math.max(0, taskReserved + reservedDelta),
                reserveBudget, Math.max(nextCurveSlot, nextSlot), initialBaseReward, decay, minBaseReward, createdAt, now);
    }
}
