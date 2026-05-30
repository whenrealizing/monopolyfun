package com.monopolyfun.modules.workthread.infra.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.monopolyfun.modules.workthread.domain.ContributionEntryEntity;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.DistributionEntitlementEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.domain.WorkResultEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadEntity;
import com.monopolyfun.modules.workthread.domain.WorkThreadReviewEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresWorkThreadRepository implements WorkThreadRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DSLContext dsl;

    public PostgresWorkThreadRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public WorkThreadEntity saveThread(WorkThreadEntity thread) {
        dsl.query("""
                        insert into work_threads (
                          id, thread_no, project_id, created_by_account_id, assignee_account_id, reviewer_account_id,
                          issue_url, repo_ref, title, goal, deliverables, acceptance_criteria,
                          task_value, bounty_amount_minor, bounty_token, status,
                          created_at, updated_at, submitted_at, accepted_at, settled_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                        on conflict (id) do update
                        set assignee_account_id = excluded.assignee_account_id,
                            reviewer_account_id = excluded.reviewer_account_id,
                            issue_url = excluded.issue_url,
                            repo_ref = excluded.repo_ref,
                            title = excluded.title,
                            goal = excluded.goal,
                            deliverables = excluded.deliverables,
                            acceptance_criteria = excluded.acceptance_criteria,
                            task_value = excluded.task_value,
                            bounty_amount_minor = excluded.bounty_amount_minor,
                            bounty_token = excluded.bounty_token,
                            status = excluded.status,
                            updated_at = excluded.updated_at,
                            submitted_at = excluded.submitted_at,
                            accepted_at = excluded.accepted_at,
                            settled_at = excluded.settled_at
                        """,
                thread.id(),
                thread.threadNo(),
                thread.projectId(),
                thread.createdByAccountId(),
                thread.assigneeAccountId(),
                thread.reviewerAccountId(),
                thread.issueUrl(),
                thread.repoRef(),
                thread.title(),
                thread.goal(),
                PostgresJson.jsonb(thread.deliverables()).data(),
                PostgresJson.jsonb(thread.acceptanceCriteria()).data(),
                thread.taskValue(),
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                thread.status(),
                PostgresJson.offsetDateTime(thread.createdAt()),
                PostgresJson.offsetDateTime(thread.updatedAt()),
                PostgresJson.offsetDateTime(thread.submittedAt()),
                PostgresJson.offsetDateTime(thread.acceptedAt()),
                PostgresJson.offsetDateTime(thread.settledAt()))
                .execute();
        return findThread(thread.id()).orElse(thread);
    }

    @Override
    public Optional<WorkThreadEntity> findThread(String idOrNo) {
        return dsl.resultQuery("""
                        select id, thread_no, project_id, created_by_account_id, assignee_account_id, reviewer_account_id,
                               issue_url, repo_ref, title, goal, deliverables, acceptance_criteria,
                               task_value, bounty_amount_minor, bounty_token, status,
                               created_at, updated_at, submitted_at, accepted_at, settled_at
                        from work_threads
                        where id = ? or thread_no = ?
                        """, idOrNo, idOrNo)
                .fetchOptional(this::mapThread);
    }

    @Override
    public List<WorkThreadEntity> listThreadsByProject(String projectId) {
        return dsl.resultQuery("""
                        select id, thread_no, project_id, created_by_account_id, assignee_account_id, reviewer_account_id,
                               issue_url, repo_ref, title, goal, deliverables, acceptance_criteria,
                               task_value, bounty_amount_minor, bounty_token, status,
                               created_at, updated_at, submitted_at, accepted_at, settled_at
                        from work_threads
                        where project_id = ?
                        order by created_at desc
                        """, projectId)
                .fetch(this::mapThread);
    }

    @Override
    public WorkThreadEntity updateThreadState(WorkThreadEntity thread, String expectedStatus) {
        int updated = dsl.query("""
                        update work_threads
                        set assignee_account_id = ?,
                            reviewer_account_id = ?,
                            issue_url = ?,
                            repo_ref = ?,
                            title = ?,
                            goal = ?,
                            deliverables = ?::jsonb,
                            acceptance_criteria = ?::jsonb,
                            task_value = ?,
                            bounty_amount_minor = ?,
                            bounty_token = ?,
                            status = ?,
                            updated_at = ?::timestamptz,
                            submitted_at = ?::timestamptz,
                            accepted_at = ?::timestamptz,
                            settled_at = ?::timestamptz
                        where id = ? and status = ?
                        """,
                thread.assigneeAccountId(),
                thread.reviewerAccountId(),
                thread.issueUrl(),
                thread.repoRef(),
                thread.title(),
                thread.goal(),
                PostgresJson.jsonb(thread.deliverables()).data(),
                PostgresJson.jsonb(thread.acceptanceCriteria()).data(),
                thread.taskValue(),
                thread.bountyAmountMinor(),
                thread.bountyToken(),
                thread.status(),
                PostgresJson.offsetDateTime(thread.updatedAt()),
                PostgresJson.offsetDateTime(thread.submittedAt()),
                PostgresJson.offsetDateTime(thread.acceptedAt()),
                PostgresJson.offsetDateTime(thread.settledAt()),
                thread.id(),
                expectedStatus)
                .execute();
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work thread status changed");
        }
        return findThread(thread.id()).orElse(thread);
    }

    @Override
    public WorkResultEntity saveResult(WorkResultEntity result) {
        dsl.query("""
                        insert into work_results (
                          id, result_no, work_thread_id, actor_account_id, result_markdown, summary, pr_url,
                          test_summary, changed_files, evidence_refs, runtime, status, created_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::timestamptz)
                        """,
                result.id(),
                result.resultNo(),
                result.workThreadId(),
                result.actorAccountId(),
                result.resultMarkdown(),
                result.summary(),
                result.prUrl(),
                result.testSummary(),
                PostgresJson.jsonb(result.changedFiles()).data(),
                PostgresJson.jsonb(result.evidenceRefs()).data(),
                result.runtime(),
                result.status(),
                PostgresJson.offsetDateTime(result.createdAt()))
                .execute();
        return result;
    }

    @Override
    public Optional<WorkResultEntity> findLatestResult(String workThreadId) {
        return dsl.resultQuery("""
                        select id, result_no, work_thread_id, actor_account_id, result_markdown, summary, pr_url,
                               test_summary, changed_files, evidence_refs, runtime, status, created_at
                        from work_results
                        where work_thread_id = ?
                        order by created_at desc
                        limit 1
                        """, workThreadId)
                .fetchOptional(this::mapResult);
    }

    @Override
    public WorkResultEntity updateResultStatus(String resultId, String status) {
        dsl.query("""
                        update work_results
                        set status = ?
                        where id = ?
                        """,
                status,
                resultId)
                .execute();
        return dsl.resultQuery("""
                        select id, result_no, work_thread_id, actor_account_id, result_markdown, summary, pr_url,
                               test_summary, changed_files, evidence_refs, runtime, status, created_at
                        from work_results
                        where id = ?
                        """, resultId)
                .fetchOptional(this::mapResult)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work result not found"));
    }

    @Override
    public WorkThreadReviewEntity saveReview(WorkThreadReviewEntity review) {
        dsl.query("""
                        insert into work_thread_reviews (
                          id, review_no, work_thread_id, result_id, reviewer_account_id, decision, reason, created_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?::timestamptz)
                        """,
                review.id(),
                review.reviewNo(),
                review.workThreadId(),
                review.resultId(),
                review.reviewerAccountId(),
                review.decision(),
                review.reason(),
                PostgresJson.offsetDateTime(review.createdAt()))
                .execute();
        return review;
    }

    @Override
    public List<ContributionEntryEntity> listContributionsByProject(String projectId) {
        return dsl.resultQuery("""
                        select id, project_id, work_thread_id, result_id, account_id, task_value, shares,
                               bounty_amount_minor, bounty_token, status, created_at
                        from contribution_ledger
                        where project_id = ?
                        order by created_at desc
                        """, projectId)
                .fetch(this::mapContribution);
    }

    @Override
    public int countSettledContributionsByProject(String projectId) {
        Integer value = dsl.resultQuery("select count(*)::int as total from contribution_ledger where project_id = ? and status = 'settled'", projectId)
                .fetchOne("total", Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public int sumSharesByProject(String projectId) {
        Integer value = dsl.resultQuery("select coalesce(sum(shares), 0)::int as total from contribution_ledger where project_id = ? and status = 'settled'", projectId)
                .fetchOne("total", Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public int sumSharesByProjectAndAccount(String projectId, String accountId) {
        Integer value = dsl.resultQuery("select coalesce(sum(shares), 0)::int as total from contribution_ledger where project_id = ? and account_id = ? and status = 'settled'", projectId, accountId)
                .fetchOne("total", Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public int sumBountyByProjectAndAccount(String projectId, String accountId) {
        Integer value = dsl.resultQuery("select coalesce(sum(bounty_amount_minor), 0)::int as total from contribution_ledger where project_id = ? and account_id = ? and status = 'settled'", projectId, accountId)
                .fetchOne("total", Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public ProjectRevenueAddressEntity saveRevenueAddress(ProjectRevenueAddressEntity address) {
        dsl.query("""
                        insert into project_revenue_addresses (
                          id, project_id, chain_id, contract_address, token_address, status, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                        on conflict (project_id, chain_id, contract_address) do update
                        set token_address = excluded.token_address,
                            status = excluded.status,
                            updated_at = excluded.updated_at
                        """,
                address.id(),
                address.projectId(),
                address.chainId(),
                address.contractAddress(),
                address.tokenAddress(),
                address.status(),
                PostgresJson.offsetDateTime(address.createdAt()),
                PostgresJson.offsetDateTime(address.updatedAt()))
                .execute();
        return findActiveRevenueAddress(address.projectId()).orElse(address);
    }

    @Override
    public Optional<ProjectRevenueAddressEntity> findActiveRevenueAddress(String projectId) {
        return dsl.resultQuery("""
                        select id, project_id, chain_id, contract_address, token_address, status, created_at, updated_at
                        from project_revenue_addresses
                        where project_id = ? and status = 'active'
                        order by updated_at desc
                        limit 1
                        """, projectId)
                .fetchOptional(this::mapRevenueAddress);
    }

    @Override
    public DistributionBatchEntity saveDistributionBatch(DistributionBatchEntity batch) {
        try {
            dsl.query("""
                            insert into distribution_batches (
                              id, project_id, period, total_revenue_minor, total_snapshot_shares, merkle_root, status, created_at, updated_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                            """,
                    batch.id(),
                    batch.projectId(),
                    batch.period(),
                    batch.totalRevenueMinor(),
                    batch.totalSnapshotShares(),
                    batch.merkleRoot(),
                    batch.status(),
                    PostgresJson.offsetDateTime(batch.createdAt()),
                    PostgresJson.offsetDateTime(batch.updatedAt()))
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution period already exists", exception);
        }
        return findDistributionBatch(batch.projectId(), batch.period()).orElse(batch);
    }

    @Override
    public void saveDistributionEntitlements(List<DistributionEntitlementEntity> entitlements) {
        for (DistributionEntitlementEntity entitlement : entitlements) {
            dsl.query("""
                            insert into distribution_entitlements (
                              id, batch_id, account_id, snapshot_shares, amount_minor, status, created_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?::timestamptz)
                            """,
                    entitlement.id(),
                    entitlement.batchId(),
                    entitlement.accountId(),
                    entitlement.snapshotShares(),
                    entitlement.amountMinor(),
                    entitlement.status(),
                    PostgresJson.offsetDateTime(entitlement.createdAt()))
                    .execute();
        }
    }

    @Override
    public Optional<DistributionBatchEntity> findDistributionBatch(String projectId, String period) {
        return dsl.resultQuery("""
                        select id, project_id, period, total_revenue_minor, total_snapshot_shares, merkle_root, status, created_at, updated_at
                        from distribution_batches
                        where project_id = ? and period = ?
                        """, projectId, period)
                .fetchOptional(this::mapDistributionBatch);
    }

    @Override
    public List<DistributionBatchEntity> listDistributionBatches(String projectId) {
        return dsl.resultQuery("""
                        select id, project_id, period, total_revenue_minor, total_snapshot_shares, merkle_root, status, created_at, updated_at
                        from distribution_batches
                        where project_id = ?
                        order by period desc, created_at desc
                        """, projectId)
                .fetch(this::mapDistributionBatch);
    }

    @Override
    public List<DistributionEntitlementEntity> listDistributionEntitlements(String batchId) {
        return dsl.resultQuery("""
                        select id, batch_id, account_id, snapshot_shares, amount_minor, status, created_at
                        from distribution_entitlements
                        where batch_id = ?
                        order by account_id asc
                        """, batchId)
                .fetch(this::mapDistributionEntitlement);
    }

    @Override
    public Optional<DistributionEntitlementEntity> findDistributionEntitlement(String batchId, String accountId) {
        return dsl.resultQuery("""
                        select id, batch_id, account_id, snapshot_shares, amount_minor, status, created_at
                        from distribution_entitlements
                        where batch_id = ? and account_id = ?
                        """, batchId, accountId)
                .fetchOptional(this::mapDistributionEntitlement);
    }

    @Override
    public DistributionClaimEntity saveDistributionClaim(DistributionClaimEntity claim) {
        try {
            dsl.query("""
                            insert into distribution_claims (
                              id, batch_id, account_id, wallet_address, amount_minor, proof, tx_hash, status, created_at, updated_at
                            )
                            values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::timestamptz, ?::timestamptz)
                            """,
                    claim.id(),
                    claim.batchId(),
                    claim.accountId(),
                    claim.walletAddress(),
                    claim.amountMinor(),
                    PostgresJson.jsonb(claim.proof()).data(),
                    claim.txHash(),
                    claim.status(),
                    PostgresJson.offsetDateTime(claim.createdAt()),
                    PostgresJson.offsetDateTime(claim.updatedAt()))
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution claim already exists", exception);
        }
        return findDistributionClaim(claim.batchId(), claim.accountId()).orElse(claim);
    }

    @Override
    public DistributionClaimEntity submitDistributionClaimTx(String batchId, String accountId, String txHash, Instant now) {
        int updated = dsl.query("""
                        update distribution_claims
                        set tx_hash = ?,
                            status = 'submitted',
                            updated_at = ?::timestamptz
                        where batch_id = ? and account_id = ? and tx_hash is null
                        """,
                txHash,
                PostgresJson.offsetDateTime(now),
                batchId,
                accountId)
                .execute();
        DistributionClaimEntity claim = findDistributionClaim(batchId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution claim not found"));
        if (updated == 0 && (claim.txHash() == null || !claim.txHash().equals(txHash))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution claim txHash already exists");
        }
        return claim;
    }

    @Override
    public DistributionClaimEntity confirmDistributionClaimTx(String batchId, String accountId, String txHash, Instant now) {
        int updated = dsl.query("""
                        update distribution_claims
                        set tx_hash = ?,
                            status = 'claimed',
                            updated_at = ?::timestamptz
                        where batch_id = ? and account_id = ? and (tx_hash is null or tx_hash = ?)
                        """,
                txHash,
                PostgresJson.offsetDateTime(now),
                batchId,
                accountId,
                txHash)
                .execute();
        DistributionClaimEntity claim = findDistributionClaim(batchId, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribution claim not found"));
        if (updated == 0 && (claim.txHash() == null || !claim.txHash().equals(txHash))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Distribution claim txHash already exists");
        }
        return claim;
    }

    @Override
    public Optional<DistributionClaimEntity> findDistributionClaim(String batchId, String accountId) {
        return dsl.resultQuery("""
                        select id, batch_id, account_id, wallet_address, amount_minor, proof, tx_hash, status, created_at, updated_at
                        from distribution_claims
                        where batch_id = ? and account_id = ?
                        """, batchId, accountId)
                .fetchOptional(this::mapDistributionClaim);
    }

    @Override
    public boolean hasDistributionClaim(String batchId, String accountId) {
        Integer value = dsl.resultQuery("""
                        select count(*)::int as total
                        from distribution_claims
                        where batch_id = ? and account_id = ?
                        """, batchId, accountId)
                .fetchOne("total", Integer.class);
        return value != null && value > 0;
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    private WorkThreadEntity mapThread(Record record) {
        return new WorkThreadEntity(
                record.get("id", String.class),
                record.get("thread_no", String.class),
                record.get("project_id", String.class),
                record.get("created_by_account_id", String.class),
                record.get("assignee_account_id", String.class),
                record.get("reviewer_account_id", String.class),
                record.get("issue_url", String.class),
                record.get("repo_ref", String.class),
                record.get("title", String.class),
                record.get("goal", String.class),
                PostgresJson.jsonbValue(record.get("deliverables", JSONB.class), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get("acceptance_criteria", JSONB.class), STRING_LIST, List.of()),
                record.get("task_value", Integer.class),
                record.get("bounty_amount_minor", Integer.class),
                record.get("bounty_token", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("submitted_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("accepted_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("settled_at", OffsetDateTime.class)));
    }

    private WorkResultEntity mapResult(Record record) {
        return new WorkResultEntity(
                record.get("id", String.class),
                record.get("result_no", String.class),
                record.get("work_thread_id", String.class),
                record.get("actor_account_id", String.class),
                record.get("result_markdown", String.class),
                record.get("summary", String.class),
                record.get("pr_url", String.class),
                record.get("test_summary", String.class),
                PostgresJson.jsonbValue(record.get("changed_files", JSONB.class), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get("evidence_refs", JSONB.class), STRING_LIST, List.of()),
                record.get("runtime", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)));
    }

    private ContributionEntryEntity mapContribution(Record record) {
        return new ContributionEntryEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("work_thread_id", String.class),
                record.get("result_id", String.class),
                record.get("account_id", String.class),
                record.get("task_value", Integer.class),
                record.get("shares", Integer.class),
                record.get("bounty_amount_minor", Integer.class),
                record.get("bounty_token", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)));
    }

    private ProjectRevenueAddressEntity mapRevenueAddress(Record record) {
        return new ProjectRevenueAddressEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("chain_id", String.class),
                record.get("contract_address", String.class),
                record.get("token_address", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private DistributionBatchEntity mapDistributionBatch(Record record) {
        return new DistributionBatchEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("period", String.class),
                record.get("total_revenue_minor", Integer.class),
                record.get("total_snapshot_shares", Integer.class),
                record.get("merkle_root", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private DistributionEntitlementEntity mapDistributionEntitlement(Record record) {
        return new DistributionEntitlementEntity(
                record.get("id", String.class),
                record.get("batch_id", String.class),
                record.get("account_id", String.class),
                record.get("snapshot_shares", Integer.class),
                record.get("amount_minor", Integer.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)));
    }

    private DistributionClaimEntity mapDistributionClaim(Record record) {
        return new DistributionClaimEntity(
                record.get("id", String.class),
                record.get("batch_id", String.class),
                record.get("account_id", String.class),
                record.get("wallet_address", String.class),
                record.get("amount_minor", Integer.class),
                PostgresJson.jsonbValue(record.get("proof", JSONB.class), STRING_LIST, List.of()),
                record.get("tx_hash", String.class),
                record.get("status", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }
}
