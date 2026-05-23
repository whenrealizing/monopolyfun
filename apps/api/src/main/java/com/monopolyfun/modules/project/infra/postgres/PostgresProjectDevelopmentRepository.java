package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.ProjectCiCheckEntity;
import com.monopolyfun.modules.project.domain.ProjectPrLinkEntity;
import com.monopolyfun.modules.project.domain.ProjectRepoBindingEntity;
import com.monopolyfun.modules.project.infra.ProjectDevelopmentRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
public class PostgresProjectDevelopmentRepository implements ProjectDevelopmentRepository {
    private final DSLContext dsl;

    public PostgresProjectDevelopmentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ProjectRepoBindingEntity saveRepoBinding(
            String projectId,
            String provider,
            String repoUrl,
            String repoOwner,
            String repoName,
            String defaultBranch,
            String installationId,
            String createdByAccountId) {
        dsl.query("""
                        insert into project_repo_bindings (
                          id, project_id, provider, repo_url, repo_owner, repo_name, default_branch, installation_id, created_by_account_id
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (project_id, provider, repo_owner, repo_name) do update
                        set repo_url = excluded.repo_url,
                            default_branch = excluded.default_branch,
                            installation_id = excluded.installation_id,
                            updated_at = now()
                        """, "prb-" + UUID.randomUUID(), projectId, provider, repoUrl, repoOwner, repoName, defaultBranch, installationId, createdByAccountId)
                .execute();
        return findRepoBindings(projectId).stream()
                .filter(binding -> binding.provider().equals(provider) && binding.repoOwner().equals(repoOwner) && binding.repoName().equals(repoName))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<ProjectRepoBindingEntity> findRepoBindings(String projectId) {
        return dsl.resultQuery("""
                        select id, project_id, provider, repo_url, repo_owner, repo_name, default_branch, installation_id,
                               created_by_account_id, created_at, updated_at
                        from project_repo_bindings
                        where project_id = ?
                        order by created_at asc
                        """, projectId)
                .fetch(this::mapRepoBinding);
    }

    @Override
    public ProjectPrLinkEntity savePrLink(
            String projectId,
            String validationTaskId,
            String repoUrl,
            int prNumber,
            String prUrl,
            String headSha,
            String baseBranch,
            String branchName,
            String state,
            Map<String, Object> rawPayload) {
        dsl.query("""
                        insert into project_external_refs (
                          id, ref_type, project_id, validation_task_id, repo_url, pr_number, pr_url, head_sha, base_branch, branch_name, state,
                          last_synced_at, raw_payload
                        )
                        values (?, 'pull_request', ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?::jsonb)
                        on conflict (project_id, repo_url, pr_number, ref_type) where ref_type = 'pull_request' do update
                        set validation_task_id = coalesce(excluded.validation_task_id, project_external_refs.validation_task_id),
                            pr_url = excluded.pr_url,
                            head_sha = excluded.head_sha,
                            base_branch = excluded.base_branch,
                            branch_name = excluded.branch_name,
                            state = excluded.state,
                            merged_at = case when excluded.state = 'merged' then now() else project_external_refs.merged_at end,
                            last_synced_at = now(),
                            raw_payload = excluded.raw_payload,
                            updated_at = now()
                        """, "ppr-" + UUID.randomUUID(), projectId, validationTaskId, repoUrl, prNumber, prUrl, headSha, baseBranch, branchName, state, PostgresJson.jsonb(rawPayload).data())
                .execute();
        if (mergedPullRequest(state, rawPayload)) {
            invalidateSiblingPullRequests(projectId, repoUrl, prNumber, headSha);
        }
        return findPrLinks(projectId).stream()
                .filter(link -> link.repoUrl().equals(repoUrl) && link.prNumber() == prNumber)
                .findFirst()
                .orElseThrow();
    }

    @Override
    public ProjectCiCheckEntity saveCiCheck(
            String projectId,
            String validationTaskId,
            Integer prNumber,
            String checkName,
            String status,
            String conclusion,
            String detailsUrl,
            Map<String, Object> rawPayload) {
        dsl.query("""
                        insert into project_external_refs (
                          id, ref_type, project_id, validation_task_id, pr_number, check_name, status, conclusion, details_url, completed_at, raw_payload
                        )
                        values (?, 'ci_check', ?, ?, ?, ?, ?, ?, ?, case when ? is not null then now() else null end, ?::jsonb)
                        """, "pci-" + UUID.randomUUID(), projectId, validationTaskId, prNumber, checkName, status, conclusion, detailsUrl, conclusion, PostgresJson.jsonb(rawPayload).data())
                .execute();
        return findCiChecks(projectId).stream()
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<ProjectPrLinkEntity> findPrLinks(String projectId) {
        return dsl.resultQuery(selectPrSql() + " where ref_type = 'pull_request' and project_id = ? order by updated_at desc", projectId)
                .fetch(this::mapPrLink);
    }

    @Override
    public List<ProjectCiCheckEntity> findCiChecks(String projectId) {
        return dsl.resultQuery(selectCiSql() + " where ref_type = 'ci_check' and project_id = ? order by updated_at desc", projectId)
                .fetch(this::mapCiCheck);
    }

    @Override
    public List<ProjectCiCheckEntity> findActionableCiChecks(String accountId) {
        return dsl.resultQuery(selectCiSql() + """
                        where ref_type = 'ci_check'
                          and project_id in (
                          select project_id from project_roles where account_id = ? and role_code in ('system_cto', 'system_ceo')
                        )
                          and lower(coalesce(conclusion, status)) in ('failure', 'failed', 'cancelled', 'timed_out')
                        order by updated_at desc
                        limit 50
                        """, accountId)
                .fetch(this::mapCiCheck);
    }

    @Override
    public List<ProjectPrLinkEntity> findActionablePrLinks(String accountId) {
        return dsl.resultQuery(selectPrSql() + """
                        where ref_type = 'pull_request'
                          and project_id in (
                          select project_id from project_roles where account_id = ? and role_code in ('system_cto', 'system_ceo')
                        )
                          and state in ('open', 'ready', 'checks_passed')
                        order by updated_at desc
                        limit 50
                        """, accountId)
                .fetch(this::mapPrLink);
    }

    @Override
    public List<Map<String, Object>> findCandidateSupports(String projectId) {
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = 'project_result_candidate_support'
                          and input_snapshot->>'projectId' = ?
                        order by created_at asc
                        """, projectId)
                .fetch(record -> PostgresJson.map(record.get("output_snapshot", JSONB.class)));
    }

    @Override
    public List<Map<String, Object>> findCandidateFinalReviews(String projectId) {
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = 'project_result_candidate_final_review'
                          and input_snapshot->>'projectId' = ?
                        order by created_at asc
                        """, projectId)
                .fetch(record -> PostgresJson.map(record.get("output_snapshot", JSONB.class)));
    }

    @Override
    public boolean hasCandidateSupport(String candidateId, String accountId) {
        return dsl.resultQuery("""
                        select exists(
                          select 1
                          from work_events
                          where subject_type = 'project_result_candidate_support'
                            and subject_id = ?
                            and actor_account_id = ?
                        ) as exists_flag
                        """, candidateId, accountId)
                .fetchOne(record -> Boolean.TRUE.equals(record.get("exists_flag", Boolean.class)));
    }

    @Override
    public void saveCandidateSupport(
            String candidateId,
            String projectId,
            String taskId,
            Integer prNumber,
            String headSha,
            String accountId,
            int weight,
            String reason) {
        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "taskId", taskId,
                "prNumber", prNumber == null ? "" : prNumber,
                "headSha", headSha == null ? "" : headSha);
        Map<String, Object> output = Map.of(
                "candidateId", candidateId,
                "projectId", projectId,
                "taskId", taskId,
                "prNumber", prNumber == null ? "" : prNumber,
                "accountId", accountId,
                "weight", weight,
                "reason", reason == null ? "" : reason,
                "headSha", headSha == null ? "" : headSha,
                "status", "active");
        dsl.query("""
                        insert into work_events (
                          id, subject_type, subject_id, actor_account_id, event_type, action_id, input_snapshot, output_snapshot
                        ) values (?, 'project_result_candidate_support', ?, ?, 'candidate_supported', 'project_result_candidate.support', ?::jsonb, ?::jsonb)
                        """, "crs-" + UUID.randomUUID(), candidateId, accountId, PostgresJson.jsonb(input).data(), PostgresJson.jsonb(output).data())
                .execute();
    }

    @Override
    public void saveCandidateFinalReview(
            String candidateId,
            String projectId,
            String taskId,
            Integer prNumber,
            String reviewedCommitSha,
            String accountId,
            String decision,
            String reason) {
        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "taskId", taskId,
                "prNumber", prNumber == null ? "" : prNumber,
                "reviewedCommitSha", reviewedCommitSha == null ? "" : reviewedCommitSha);
        Map<String, Object> output = Map.of(
                "candidateId", candidateId,
                "projectId", projectId,
                "taskId", taskId,
                "prNumber", prNumber == null ? "" : prNumber,
                "reviewerAccountId", accountId,
                "decision", decision,
                "reason", reason == null ? "" : reason,
                "reviewedCommitSha", reviewedCommitSha == null ? "" : reviewedCommitSha);
        dsl.query("""
                        insert into work_events (
                          id, subject_type, subject_id, actor_account_id, event_type, action_id, input_snapshot, output_snapshot
                        ) values (?, 'project_result_candidate_final_review', ?, ?, 'candidate_final_reviewed', 'project_result_candidate.final_review', ?::jsonb, ?::jsonb)
                        """, "crr-" + UUID.randomUUID(), candidateId, accountId, PostgresJson.jsonb(input).data(), PostgresJson.jsonb(output).data())
                .execute();
    }

    @Override
    public List<Map<String, Object>> findActiveCandidateWindowSkips(String projectId, String accountId, Instant now) {
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = 'project_candidate_window_skip'
                          and input_snapshot->>'projectId' = ?
                          and actor_account_id = ?
                          and (output_snapshot->>'expiresAt')::timestamptz > ?::timestamptz
                        order by created_at desc
                        """, projectId, accountId, now.toString())
                .fetch(record -> PostgresJson.map(record.get("output_snapshot", JSONB.class)));
    }

    @Override
    public void saveCandidateWindowSkip(
            String projectId,
            String candidateId,
            String accountId,
            String reasonCode,
            String reason,
            Instant expiresAt) {
        // 中文注释：跳过租约只影响当前 actor 的决策窗口，候选仍保留给其他用户和 agent。
        Map<String, Object> input = Map.of(
                "projectId", projectId,
                "candidateId", candidateId);
        Map<String, Object> output = Map.of(
                "projectId", projectId,
                "candidateId", candidateId,
                "accountId", accountId,
                "reasonCode", reasonCode == null ? "" : reasonCode,
                "reason", reason == null ? "" : reason,
                "expiresAt", expiresAt.toString(),
                "status", "active");
        dsl.query("""
                        insert into work_events (
                          id, subject_type, subject_id, actor_account_id, event_type, action_id, input_snapshot, output_snapshot
                        ) values (?, 'project_candidate_window_skip', ?, ?, 'candidate_window_skipped', 'project_result_candidate.window_skip', ?::jsonb, ?::jsonb)
                        """, "cws-" + UUID.randomUUID(), accountId + ":" + candidateId, accountId, PostgresJson.jsonb(input).data(), PostgresJson.jsonb(output).data())
                .execute();
    }

    private String selectPrSql() {
        return """
                select id, project_id, validation_task_id as ledger_task_id, repo_url, pr_number, pr_url, head_sha, base_branch, branch_name, state,
                       merged_at, last_synced_at, raw_payload, created_at, updated_at
                from project_external_refs
                """;
    }

    private String selectCiSql() {
        return """
                select id, project_id, validation_task_id as ledger_task_id, pr_number, check_name, status, conclusion, details_url,
                       started_at, completed_at, raw_payload, created_at, updated_at
                from project_external_refs
                """;
    }

    private ProjectRepoBindingEntity mapRepoBinding(Record record) {
        return new ProjectRepoBindingEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("provider", String.class),
                record.get("repo_url", String.class),
                record.get("repo_owner", String.class),
                record.get("repo_name", String.class),
                record.get("default_branch", String.class),
                record.get("installation_id", String.class),
                record.get("created_by_account_id", String.class),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private ProjectPrLinkEntity mapPrLink(Record record) {
        return new ProjectPrLinkEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("ledger_task_id", String.class),
                record.get("repo_url", String.class),
                record.get("pr_number", Integer.class),
                record.get("pr_url", String.class),
                record.get("head_sha", String.class),
                record.get("base_branch", String.class),
                record.get("branch_name", String.class),
                record.get("state", String.class),
                PostgresJson.instant(record.get("merged_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("last_synced_at", OffsetDateTime.class)),
                PostgresJson.map(record.get("raw_payload", JSONB.class)),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private ProjectCiCheckEntity mapCiCheck(Record record) {
        return new ProjectCiCheckEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("ledger_task_id", String.class),
                record.get("pr_number", Integer.class),
                record.get("check_name", String.class),
                record.get("status", String.class),
                record.get("conclusion", String.class),
                record.get("details_url", String.class),
                PostgresJson.instant(record.get("started_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("completed_at", OffsetDateTime.class)),
                PostgresJson.map(record.get("raw_payload", JSONB.class)),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", OffsetDateTime.class)));
    }

    private void invalidateSiblingPullRequests(String projectId, String repoUrl, int mergedPrNumber, String mergedHeadSha) {
        // 中文注释：任一 PR 合入后，同仓库其他候选 PR 必须基于新 main 重新检查可合并性。
        dsl.query("""
                        update project_external_refs
                        set raw_payload =
                              raw_payload
                              - 'mergeable'
                              - 'mergeable_state'
                              - 'mergeableState'
                              || jsonb_build_object(
                                'mergeabilityInvalidatedReason', 'mainline_merged',
                                'mergeabilityInvalidatedAt', now()::text,
                                'invalidatedByPrNumber', ?,
                                'invalidatedByHeadSha', coalesce(?, '')
                              ),
                            updated_at = now()
                        where ref_type = 'pull_request'
                          and project_id = ?
                          and repo_url = ?
                          and pr_number is distinct from ?
                          and lower(coalesce(state, 'open')) in ('open', 'ready', 'checks_passed')
                        """, mergedPrNumber, mergedHeadSha, projectId, repoUrl, mergedPrNumber)
                .execute();
    }

    private boolean mergedPullRequest(String state, Map<String, Object> rawPayload) {
        if ("merged".equals(state == null ? "" : state.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        Object direct = rawPayload == null ? null : rawPayload.get("merged");
        if (direct instanceof Boolean bool) {
            return bool;
        }
        if (direct instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        Object pullRequest = rawPayload == null ? null : rawPayload.get("pull_request");
        if (pullRequest instanceof Map<?, ?> nested) {
            Object nestedMerged = nested.get("merged");
            if (nestedMerged instanceof Boolean bool) {
                return bool;
            }
            if (nestedMerged instanceof String text) {
                return Boolean.parseBoolean(text);
            }
        }
        return false;
    }

}
