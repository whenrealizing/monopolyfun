package com.monopolyfun.modules.share.service;

import com.monopolyfun.modules.project.domain.ProjectSharePoolEntity;
import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.modules.share.infra.SharesLedgerRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ProjectContributionSettlementService {
    private final DSLContext dsl;
    private final SharesLedgerRepository sharesLedgerRepository;
    private final ProjectSharePoolService projectSharePoolService;

    public ProjectContributionSettlementService(
            DSLContext dsl,
            SharesLedgerRepository sharesLedgerRepository,
            ProjectSharePoolService projectSharePoolService) {
        this.dsl = dsl;
        this.sharesLedgerRepository = sharesLedgerRepository;
        this.projectSharePoolService = projectSharePoolService;
    }

    public ContributionSettlementResult settle(ContributionCommand command) {
        validate(command);
        Instant now = command.createdAt() == null ? Instant.now() : command.createdAt();
        ProjectSharePoolEntity pool = command.advanceProjectSharePool()
                ? projectSharePoolService.requireByProjectId(command.projectId())
                : null;
        int curveSlot = command.curveSlot() == null ? pool == null ? 0 : pool.nextCurveSlot() : command.curveSlot();
        String marketId = blank(command.marketId()) && pool != null ? pool.marketId() : command.marketId();

        // 中文注释：贡献事实统一写入 contribution_ledger，后续收益、活跃度和治理视图共用同一来源。
        dsl.query("""
                        insert into contribution_ledger (
                          id, project_id, work_thread_id, result_id, account_id, task_value, shares,
                          bounty_amount_minor, bounty_token, status, created_at,
                          source_type, source_id, contribution_role, contribution_weight, metadata
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'settled', ?::timestamptz, ?, ?, ?, ?, ?::jsonb)
                        on conflict (project_id, source_type, source_id, account_id, contribution_role) do update
                        set result_id = excluded.result_id,
                            task_value = excluded.task_value,
                            shares = excluded.shares,
                            bounty_amount_minor = excluded.bounty_amount_minor,
                            bounty_token = excluded.bounty_token,
                            status = excluded.status,
                            contribution_weight = excluded.contribution_weight,
                            metadata = excluded.metadata
                        """,
                "ctr-" + UUID.randomUUID(),
                command.projectId(),
                "work_thread".equals(command.sourceType()) ? command.sourceId() : null,
                // 中文注释：contribution_ledger.result_id 绑定 Work Thread 结果表；其它协议的 proofId 保留在 shares_ledger 和 metadata。
                "work_thread".equals(command.sourceType()) ? command.resultId() : null,
                command.accountId(),
                command.taskValue(),
                command.shares(),
                command.bountyAmountMinor(),
                blank(command.bountyToken()) ? "USDC" : command.bountyToken(),
                PostgresJson.offsetDateTime(now),
                command.sourceType(),
                command.sourceId(),
                command.contributionRole(),
                command.contributionWeight() == null ? BigDecimal.valueOf(command.taskValue()) : command.contributionWeight(),
                PostgresJson.jsonb(command.metadata() == null ? Map.of() : command.metadata()).data())
                .execute();

        // 中文注释：shares_ledger 仍是不可变份额账本，幂等键保证重复审批或重复结算只产生一次 mint。
        SharesLedgerEntryEntity entry = new SharesLedgerEntryEntity(
                "share-" + UUID.randomUUID(),
                command.sourceType(),
                command.sourceId(),
                command.issuerType(),
                command.issuerId(),
                marketId,
                command.orderId(),
                command.proofId(),
                command.shareReleaseRequestId(),
                command.projectId(),
                command.itemId(),
                command.accountId(),
                command.shares(),
                curveSlot,
                command.reason(),
                command.settlementTypeSnapshot(),
                now);
        boolean inserted = sharesLedgerRepository.saveIfAbsent(entry);
        if (inserted && command.advanceProjectSharePool()) {
            projectSharePoolService.mintTask(marketId, command.shares(), curveSlot);
        }
        return new ContributionSettlementResult(entry, inserted);
    }

    private void validate(ContributionCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution settlement command is required");
        }
        require(command.projectId(), "projectId");
        require(command.sourceType(), "sourceType");
        require(command.sourceId(), "sourceId");
        require(command.accountId(), "accountId");
        require(command.contributionRole(), "contributionRole");
        if (command.shares() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution shares must be positive");
        }
        if (command.taskValue() < 0 || command.taskValue() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution taskValue must be between 0 and 10000");
        }
        if (command.reason() == null || command.settlementTypeSnapshot() == null || command.issuerType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contribution ledger classification is required");
        }
        require(command.issuerId(), "issuerId");
    }

    private void require(String value, String field) {
        if (blank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ContributionCommand(
            String projectId,
            String sourceType,
            String sourceId,
            String resultId,
            String accountId,
            String contributionRole,
            int taskValue,
            int shares,
            int bountyAmountMinor,
            String bountyToken,
            LedgerReason reason,
            SettlementType settlementTypeSnapshot,
            ShareIssuerType issuerType,
            String issuerId,
            String marketId,
            String orderId,
            String proofId,
            String shareReleaseRequestId,
            String itemId,
            Integer curveSlot,
            BigDecimal contributionWeight,
            Map<String, Object> metadata,
            boolean advanceProjectSharePool,
            Instant createdAt
    ) {
    }

    public record ContributionSettlementResult(SharesLedgerEntryEntity shareEntry, boolean shareInserted) {
    }
}
