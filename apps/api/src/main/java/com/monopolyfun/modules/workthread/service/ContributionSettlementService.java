package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ContributionSettlementService {
    private static final int INITIAL_BASE_REWARD = 1000;
    private static final double CURVE_DECAY = 0.96;
    private static final int MIN_BASE_REWARD = 50;

    private final WorkThreadRepository repository;

    public ContributionSettlementService(WorkThreadRepository repository) {
        this.repository = repository;
    }

    public ContributionEntryEntity settle(WorkThreadEntity thread, WorkResultEntity result, Instant now) {
        int curveSlot = repository.countSettledContributionsByProject(thread.projectId());
        int mintedShares = mintedShares(thread.taskValue(), curveSlot);
        ContributionEntryEntity contribution = new ContributionEntryEntity(
                "ctr-" + UUID.randomUUID(),
                thread.projectId(),
                thread.id(),
                result.id(),
                result.actorAccountId(),
                thread.taskValue(),
                mintedShares,
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                "settled",
                now);
        ContributionEntryEntity saved = repository.saveContribution(contribution);
        repository.saveSharesLedgerEntry(saved, curveSlot);
        return saved;
    }

    private int mintedShares(int taskValue, int curveSlot) {
        double baseReward = Math.max(MIN_BASE_REWARD, Math.floor(INITIAL_BASE_REWARD * Math.pow(CURVE_DECAY, curveSlot)));
        // 中文注释：Task Value 只描述本次贡献权重，Bonding Curve 用 curveSlot 控制项目越成熟增发越克制。
        return Math.max(1, (int) Math.floor(baseReward * Math.max(1, taskValue) / 1000.0));
    }
}
