package com.monopolyfun.modules.repo.infra.postgres;

import com.monopolyfun.modules.repo.domain.RepoProvisionSessionEntity;
import com.monopolyfun.modules.repo.infra.RepoProvisionSessionRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresRepoProvisionSessionRepository implements RepoProvisionSessionRepository {
    // 中文注释：repo provision 是 repo_jobs 的一种 job_type，避免 provision/delivery 双表表达同一外部仓库任务。
    private static final Table<Record> REPO_JOBS = DSL.table(DSL.name("repo_jobs"));
    private static final Field<String> ID = DSL.field(DSL.name("repo_jobs", "id"), String.class);
    private static final Field<String> JOB_TYPE = DSL.field(DSL.name("repo_jobs", "job_type"), String.class);
    private static final Field<String> PROJECT_NO = DSL.field(DSL.name("repo_jobs", "project_no"), String.class);
    private static final Field<String> PROVIDER = DSL.field(DSL.name("repo_jobs", "provider"), String.class);
    private static final Field<String> REPO_URL = DSL.field(DSL.name("repo_jobs", "repo_url"), String.class);
    private static final Field<String> CLONE_URL = DSL.field(DSL.name("repo_jobs", "clone_url"), String.class);
    private static final Field<String> REPO_OWNER = DSL.field(DSL.name("repo_jobs", "repo_owner"), String.class);
    private static final Field<String> REPO_NAME = DSL.field(DSL.name("repo_jobs", "repo_name"), String.class);
    private static final Field<String> DEFAULT_BRANCH = DSL.field(DSL.name("repo_jobs", "default_branch"), String.class);
    private static final Field<String> VISIBILITY = DSL.field(DSL.name("repo_jobs", "visibility"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("repo_jobs", "status"), String.class);
    private static final Field<String> CREATED_BY_ACCOUNT_ID = DSL.field(DSL.name("repo_jobs", "created_by_account_id"), String.class);
    private static final Field<JSONB> METADATA = DSL.field(DSL.name("repo_jobs", "metadata"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("repo_jobs", "created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("repo_jobs", "updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresRepoProvisionSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<RepoProvisionSessionEntity> findById(String id) {
        return dsl.select(fields())
                .from(REPO_JOBS)
                .where(ID.eq(id))
                .and(JOB_TYPE.eq("provision"))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public RepoProvisionSessionEntity save(RepoProvisionSessionEntity session) {
        dsl.insertInto(REPO_JOBS)
                .set(ID, session.id())
                .set(JOB_TYPE, "provision")
                .set(PROJECT_NO, session.projectNo())
                .set(PROVIDER, session.provider())
                .set(REPO_URL, session.repoUrl())
                .set(CLONE_URL, session.cloneUrl())
                .set(REPO_OWNER, session.repoOwner())
                .set(REPO_NAME, session.repoName())
                .set(DEFAULT_BRANCH, session.defaultBranch())
                .set(VISIBILITY, session.visibility())
                .set(STATUS, session.status())
                .set(CREATED_BY_ACCOUNT_ID, session.createdByAccountId())
                .set(METADATA, PostgresJson.jsonb(session.metadata()))
                .set(CREATED_AT, PostgresJson.offsetDateTime(session.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(session.updatedAt()))
                .onConflict(JOB_TYPE, PROVIDER, REPO_OWNER, REPO_NAME)
                .doUpdate()
                .set(PROJECT_NO, session.projectNo())
                .set(STATUS, session.status())
                .set(METADATA, PostgresJson.jsonb(session.metadata()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(session.updatedAt()))
                .execute();
        return dsl.select(fields())
                .from(REPO_JOBS)
                .where(PROVIDER.eq(session.provider()))
                .and(JOB_TYPE.eq("provision"))
                .and(REPO_OWNER.eq(session.repoOwner()))
                .and(REPO_NAME.eq(session.repoName()))
                .fetchOptional(this::mapRecord)
                .orElse(session);
    }

    private List<Field<?>> fields() {
        return List.of(ID, PROJECT_NO, PROVIDER, REPO_URL, CLONE_URL, REPO_OWNER, REPO_NAME, DEFAULT_BRANCH, VISIBILITY,
                STATUS, CREATED_BY_ACCOUNT_ID, METADATA, CREATED_AT, UPDATED_AT);
    }

    private RepoProvisionSessionEntity mapRecord(Record record) {
        return new RepoProvisionSessionEntity(
                record.get(ID),
                record.get(PROJECT_NO),
                record.get(PROVIDER),
                record.get(REPO_URL),
                record.get(CLONE_URL),
                record.get(REPO_OWNER),
                record.get(REPO_NAME),
                record.get(DEFAULT_BRANCH),
                record.get(VISIBILITY),
                record.get(STATUS),
                record.get(CREATED_BY_ACCOUNT_ID),
                PostgresJson.map(record.get(METADATA)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }
}
