package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.service.ProjectContributionSettlementService;
import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ContributionSettlementService {
    private static final int INITIAL_BASE_REWARD = 1000;
    private static final double CURVE_DECAY = 0.96;
    private static final int MIN_BASE_REWARD = 50;

    private final WorkThreadRepository repository;
    private final ProjectContributionSettlementService contributionSettlementService;

    public ContributionSettlementService(WorkThreadRepository repository, ProjectContributionSettlementService contributionSettlementService) {
        this.repository = repository;
        this.contributionSettlementService = contributionSettlementService;
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
        // 中文注释：Work Thread 验收只通过统一贡献结算服务写 contribution_ledger 和 shares_ledger，避免同一事实双写。
        contributionSettlementService.settle(new ProjectContributionSettlementService.ContributionCommand(
                contribution.id(),
                thread.projectId(),
                "work_thread",
                thread.id(),
                result.id(),
                result.actorAccountId(),
                "assignee",
                thread.taskValue(),
                contribution.shares(),
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                LedgerReason.WORK_THREAD,
                SettlementType.SHARES,
                ShareIssuerType.PROJECT,
                thread.projectId(),
                null,
                null,
                null,
                null,
                null,
                curveSlot,
                BigDecimal.valueOf(thread.taskValue()),
                Map.of("threadNo", thread.threadNo(), "resultNo", result.resultNo()),
                false,
                now));
        return contribution;
    }

    private int mintedShares(int taskValue, int curveSlot) {
        double baseReward = Math.max(MIN_BASE_REWARD, Math.floor(INITIAL_BASE_REWARD * Math.pow(CURVE_DECAY, curveSlot)));
        // 中文注释：Task Value 只描述本次贡献权重，Bonding Curve 用 curveSlot 控制项目越成熟增发越克制。
        return Math.max(1, (int) Math.floor(baseReward * Math.max(1, taskValue) / 1000.0));
    }
}
