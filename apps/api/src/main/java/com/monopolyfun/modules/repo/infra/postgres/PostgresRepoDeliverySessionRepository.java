package com.monopolyfun.modules.repo.infra.postgres;

import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;
import com.monopolyfun.modules.repo.infra.RepoDeliverySessionRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresRepoDeliverySessionRepository implements RepoDeliverySessionRepository {
    // 中文注释：repo delivery 是 repo_jobs 的 delivery 类型，PR、CI、token 等交付上下文放在同一 job 行。
    private static final Table<Record> REPO_JOBS = DSL.table(DSL.name("repo_jobs"));
    private static final Field<String> ID = DSL.field(DSL.name("repo_jobs", "id"), String.class);
    private static final Field<String> JOB_TYPE = DSL.field(DSL.name("repo_jobs", "job_type"), String.class);
    private static final Field<String> PROJECT_NO = DSL.field(DSL.name("repo_jobs", "project_no"), String.class);
    private static final Field<String> ORDER_NO = DSL.field(DSL.name("repo_jobs", "order_no"), String.class);
    private static final Field<String> PROVIDER = DSL.field(DSL.name("repo_jobs", "provider"), String.class);
    private static final Field<String> REPO_URL = DSL.field(DSL.name("repo_jobs", "repo_url"), String.class);
    private static final Field<String> CLONE_URL = DSL.field(DSL.name("repo_jobs", "clone_url"), String.class);
    private static final Field<String> BASE_BRANCH = DSL.field(DSL.name("repo_jobs", "base_branch"), String.class);
    private static final Field<String> HEAD_BRANCH = DSL.field(DSL.name("repo_jobs", "head_branch"), String.class);
    private static final Field<String> PR_URL = DSL.field(DSL.name("repo_jobs", "pr_url"), String.class);
    private static final Field<String> HEAD_COMMIT = DSL.field(DSL.name("repo_jobs", "head_commit"), String.class);
    private static final Field<String> CI_STATUS = DSL.field(DSL.name("repo_jobs", "ci_status"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("repo_jobs", "status"), String.class);
    private static final Field<String> RUNTIME = DSL.field(DSL.name("repo_jobs", "runtime"), String.class);
    private static final Field<String> ISSUED_TO_ACCOUNT_ID = DSL.field(DSL.name("repo_jobs", "issued_to_account_id"), String.class);
    private static final Field<String> TOKEN_SECRET_REF = DSL.field(DSL.name("repo_jobs", "token_secret_ref"), String.class);
    private static final Field<OffsetDateTime> EXPIRES_AT = DSL.field(DSL.name("repo_jobs", "expires_at"), OffsetDateTime.class);
    private static final Field<JSONB> METADATA = DSL.field(DSL.name("repo_jobs", "metadata"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("repo_jobs", "created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("repo_jobs", "updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresRepoDeliverySessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<RepoDeliverySessionEntity> findById(String id) {
        return dsl.select(fields())
                .from(REPO_JOBS)
                .where(ID.eq(id))
                .and(JOB_TYPE.eq("delivery"))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<RepoDeliverySessionEntity> findActiveByOrderNo(String orderNo) {
        return dsl.select(fields())
                .from(REPO_JOBS)
                .where(ORDER_NO.eq(orderNo))
                .and(JOB_TYPE.eq("delivery"))
                // 中文注释：同一个订单只保留一个活跃仓库交付会话，后续提交继续复用同一 PR 分支。
                .and(STATUS.in("issued", "pr_reported", "progress_observed", "proof_submitted"))
                .orderBy(UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<RepoDeliverySessionEntity> findActiveByRepoUrlAndHeadBranch(String repoUrl, String headBranch) {
        return dsl.select(fields())
                .from(REPO_JOBS)
                .where(REPO_URL.eq(repoUrl))
                .and(JOB_TYPE.eq("delivery"))
                .and(HEAD_BRANCH.eq(headBranch))
                // 中文注释：GitHub webhook 只续期仍在交付中的 session，避免已提交证明的历史记录被重新激活。
                .and(STATUS.in("issued", "pr_reported", "progress_observed"))
                .orderBy(UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public int countCreatedByAccountSince(String accountId, Instant since) {
        return dsl.selectCount()
                .from(REPO_JOBS)
                .where(JOB_TYPE.eq("delivery"))
                .and(ISSUED_TO_ACCOUNT_ID.eq(accountId))
                .and(CREATED_AT.ge(PostgresJson.offsetDateTime(since)))
                .fetchOne(0, int.class);
    }

    @Override
    public int countCreatedByProjectSince(String projectNo, Instant since) {
        return dsl.selectCount()
                .from(REPO_JOBS)
                .where(JOB_TYPE.eq("delivery"))
                .and(PROJECT_NO.eq(projectNo))
                .and(CREATED_AT.ge(PostgresJson.offsetDateTime(since)))
                .fetchOne(0, int.class);
    }

    @Override
    public RepoDeliverySessionEntity save(RepoDeliverySessionEntity session) {
        dsl.insertInto(REPO_JOBS)
                .set(ID, session.id())
                .set(JOB_TYPE, "delivery")
                .set(PROJECT_NO, session.projectNo())
                .set(ORDER_NO, session.orderNo())
                .set(PROVIDER, session.provider())
                .set(REPO_URL, session.repoUrl())
                .set(CLONE_URL, session.cloneUrl())
                .set(BASE_BRANCH, session.baseBranch())
                .set(HEAD_BRANCH, session.headBranch())
                .set(PR_URL, session.prUrl())
                .set(HEAD_COMMIT, session.headCommit())
                .set(CI_STATUS, session.ciStatus())
                .set(STATUS, session.status())
                .set(RUNTIME, session.runtime())
                .set(ISSUED_TO_ACCOUNT_ID, session.issuedToAccountId())
                .set(TOKEN_SECRET_REF, session.tokenSecretRef())
                .set(EXPIRES_AT, PostgresJson.offsetDateTime(session.expiresAt()))
                .set(METADATA, PostgresJson.jsonb(session.metadata()))
                .set(CREATED_AT, PostgresJson.offsetDateTime(session.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(session.updatedAt()))
                .onConflict(ID)
                .doUpdate()
                .set(PR_URL, session.prUrl())
                .set(HEAD_COMMIT, session.headCommit())
                .set(CI_STATUS, session.ciStatus())
                .set(STATUS, session.status())
                .set(CLONE_URL, session.cloneUrl())
                .set(EXPIRES_AT, PostgresJson.offsetDateTime(session.expiresAt()))
                .set(METADATA, PostgresJson.jsonb(session.metadata()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(session.updatedAt()))
                .execute();
        return session;
    }

    private List<Field<?>> fields() {
        return List.of(ID, PROJECT_NO, ORDER_NO, PROVIDER, REPO_URL, CLONE_URL, BASE_BRANCH, HEAD_BRANCH, PR_URL, HEAD_COMMIT,
                CI_STATUS, STATUS, RUNTIME, ISSUED_TO_ACCOUNT_ID, TOKEN_SECRET_REF, EXPIRES_AT, METADATA, CREATED_AT, UPDATED_AT);
    }

    private RepoDeliverySessionEntity mapRecord(Record record) {
        return new RepoDeliverySessionEntity(
                record.get(ID),
                record.get(PROJECT_NO),
                record.get(ORDER_NO),
                record.get(PROVIDER),
                record.get(REPO_URL),
                record.get(CLONE_URL),
                record.get(BASE_BRANCH),
                record.get(HEAD_BRANCH),
                record.get(PR_URL),
                record.get(HEAD_COMMIT),
                record.get(CI_STATUS),
                record.get(STATUS),
                record.get(RUNTIME),
                record.get(ISSUED_TO_ACCOUNT_ID),
                record.get(TOKEN_SECRET_REF),
                PostgresJson.instant(record.get(EXPIRES_AT)),
                PostgresJson.map(record.get(METADATA)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }
}
