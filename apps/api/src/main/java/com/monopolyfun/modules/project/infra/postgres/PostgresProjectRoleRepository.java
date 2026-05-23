package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class PostgresProjectRoleRepository implements ProjectRoleRepository {
    private final DSLContext dsl;

    public PostgresProjectRoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<ProjectRoleEntity> findByProjectId(String projectId) {
        return dsl.resultQuery("""
                        select id, project_id, role_code::text as role_code, account_id, assigned_by_account_id,
                               assigned_at, metadata, created_at, updated_at
                        from project_roles
                        where project_id = ?
                        order by role_code, account_id
                        """, projectId)
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, List<ProjectRoleEntity>> findByProjectIds(Collection<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = projectIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>(ids);
        // 中文注释：项目列表批量加载职位，消除列表页按项目循环访问 project_roles 的 N+1。
        return dsl.resultQuery("""
                        select id, project_id, role_code::text as role_code, account_id, assigned_by_account_id,
                               assigned_at, metadata, created_at, updated_at
                        from project_roles
                        where project_id in (%s)
                        order by project_id, role_code, account_id
                        """.formatted(placeholders), args.toArray())
                .fetch(this::mapRecord)
                .stream()
                .collect(Collectors.groupingBy(
                        ProjectRoleEntity::projectId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    @Override
    public List<ProjectRoleEntity> findAssignedRoles(String projectId, String accountId) {
        return dsl.resultQuery("""
                        select id, project_id, role_code::text as role_code, account_id, assigned_by_account_id,
                               assigned_at, metadata, created_at, updated_at
                        from project_roles
                        where project_id = ? and account_id = ?
                        order by role_code
                        """, projectId, accountId)
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProjectRoleEntity> findAssignedRolesByAccountId(String accountId) {
        return dsl.resultQuery("""
                        select id, project_id, role_code::text as role_code, account_id, assigned_by_account_id,
                               assigned_at, metadata, created_at, updated_at
                        from project_roles
                        where account_id = ?
                        order by project_id, role_code
                        """, accountId)
                .fetch(this::mapRecord);
    }

    @Override
    public ProjectRoleEntity assignRole(
            String projectId,
            ProjectRoleCode roleCode,
            String accountId,
            String assignedByAccountId) {
        Instant now = Instant.now();
        // 中文注释：Root Project 维护席位固定为单席位，重新任命同一席位会转移授权账号。
        dsl.query("""
                                insert into project_roles (
                                  id, project_id, role_code, account_id, assigned_by_account_id,
                                  assigned_at, metadata, created_at, updated_at
                                )
                                values (?, ?, ?::project_role_code, ?, ?, ?::timestamptz, ?::jsonb, ?::timestamptz, ?::timestamptz)
                                on conflict (project_id, role_code) do update
                                set account_id = excluded.account_id,
                                    assigned_by_account_id = excluded.assigned_by_account_id,
                                    assigned_at = excluded.assigned_at,
                                    updated_at = excluded.updated_at
                                """,
                        "pr-" + UUID.randomUUID(),
                        projectId,
                        roleCode.code(),
                        accountId,
                        assignedByAccountId,
                        PostgresJson.offsetDateTime(now),
                        PostgresJson.jsonb(Map.of()).data(),
                        PostgresJson.offsetDateTime(now),
                        PostgresJson.offsetDateTime(now))
                .execute();
        return findRole(projectId, roleCode, accountId);
    }

    @Override
    public ProjectRoleEntity vacateRole(String projectId, ProjectRoleCode roleCode, String accountId, String actorAccountId) {
        ProjectRoleEntity role = findRole(projectId, roleCode, accountId);
        dsl.query("""
                        delete from project_roles
                        where project_id = ? and role_code = ?::project_role_code and account_id = ?
                        """, projectId, roleCode.code(), accountId)
                .execute();
        return role;
    }

    @Override
    public void initializeProjectRoles(String projectId, String ownerAccountId, Instant now) {
        assignInitialRole(projectId, ProjectRoleCode.SYSTEM_CEO, ownerAccountId, now);
    }

    private void assignInitialRole(String projectId, ProjectRoleCode roleCode, String accountId, Instant now) {
        dsl.query("""
                                insert into project_roles (
                                  id, project_id, role_code, account_id, assigned_by_account_id,
                                  assigned_at, metadata, created_at, updated_at
                                )
                                values (?, ?, ?::project_role_code, ?, ?, ?::timestamptz, ?::jsonb, ?::timestamptz, ?::timestamptz)
                                on conflict do nothing
                                """,
                        "pr-" + UUID.randomUUID(),
                        projectId,
                        roleCode.code(),
                        accountId,
                        accountId,
                        PostgresJson.offsetDateTime(now),
                        PostgresJson.jsonb(Map.of()).data(),
                        PostgresJson.offsetDateTime(now),
                        PostgresJson.offsetDateTime(now))
                .execute();
    }

    private ProjectRoleEntity findRole(String projectId, ProjectRoleCode roleCode, String accountId) {
        return dsl.resultQuery("""
                        select id, project_id, role_code::text as role_code, account_id, assigned_by_account_id,
                               assigned_at, metadata, created_at, updated_at
                        from project_roles
                        where project_id = ? and role_code = ?::project_role_code and account_id = ?
                        """, projectId, roleCode.code(), accountId)
                .fetchOptional(this::mapRecord)
                .orElseThrow(() -> new IllegalStateException("Project role was not found"));
    }

    private ProjectRoleEntity mapRecord(Record record) {
        return new ProjectRoleEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                ProjectRoleCode.fromCode(record.get("role_code", String.class)),
                record.get("account_id", String.class),
                record.get("assigned_by_account_id", String.class),
                PostgresJson.instant(record.get("assigned_at", java.time.OffsetDateTime.class)),
                PostgresJson.map(record.get("metadata", org.jooq.JSONB.class)),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", java.time.OffsetDateTime.class)));
    }
}
