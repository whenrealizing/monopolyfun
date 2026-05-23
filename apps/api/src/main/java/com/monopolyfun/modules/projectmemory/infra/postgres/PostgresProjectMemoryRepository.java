package com.monopolyfun.modules.projectmemory.infra.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryEntryEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryRootEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySourceEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySyncEventEntity;
import com.monopolyfun.modules.projectmemory.infra.ProjectMemoryRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PostgresProjectMemoryRepository implements ProjectMemoryRepository {
    private final DSLContext dsl;

    public PostgresProjectMemoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ProjectMemoryRootEntity saveRoot(
            String projectId,
            String repoBindingId,
            String provider,
            String repoOwner,
            String repoName,
            String branch,
            String commitSha,
            String rootHash,
            String latestPath,
            String syncStatus,
            String errorCode,
            String errorMessage,
            Map<String, Object> rawRoot,
            Instant syncedAt) {
        dsl.query("""
                                insert into project_memory_repo_roots (
                                  id, project_id, repo_binding_id, provider, repo_owner, repo_name, branch, commit_sha,
                                  root_hash, latest_path, sync_status, error_code, error_message, raw_root, synced_at
                                )
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::timestamptz)
                                """,
                        "pmr-" + UUID.randomUUID(), projectId, repoBindingId, provider, repoOwner, repoName, branch, commitSha,
                        rootHash, latestPath, syncStatus, errorCode, errorMessage, PostgresJson.jsonb(rawRoot).data(), PostgresJson.offsetDateTime(syncedAt))
                .execute();
        return findLatestRoot(projectId).orElseThrow();
    }

    @Override
    public ProjectMemorySourceEntity saveSource(
            String projectId,
            String rootId,
            String sourceId,
            String kind,
            String path,
            String sha256,
            String visibility,
            String provider,
            String externalUrl,
            String externalFileId,
            String externalRevisionId,
            Long externalSize,
            String syncStatus,
            Map<String, Object> metadata) {
        dsl.query("""
                                insert into project_memory_repo_sources (
                                  id, project_id, root_id, source_id, kind, path, sha256, visibility, provider,
                                  external_url, external_file_id, external_revision_id, external_size, sync_status, metadata
                                )
                                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                                on conflict (project_id, source_id, sha256) do update
                                set root_id = excluded.root_id,
                                    kind = excluded.kind,
                                    path = excluded.path,
                                    visibility = excluded.visibility,
                                    provider = excluded.provider,
                                    external_url = excluded.external_url,
                                    external_file_id = excluded.external_file_id,
                                    external_revision_id = excluded.external_revision_id,
                                    external_size = excluded.external_size,
                                    sync_status = excluded.sync_status,
                                    metadata = excluded.metadata
                                """,
                        "pms-" + UUID.randomUUID(), projectId, rootId, sourceId, kind, path, sha256, visibility, provider,
                        externalUrl, externalFileId, externalRevisionId, externalSize, syncStatus, PostgresJson.jsonb(metadata).data())
                .execute();
        return findSources(projectId, 200).stream()
                .filter(source -> source.sourceId().equals(sourceId) && source.sha256().equals(sha256))
                .findFirst()
                .orElseThrow();
    }

    @Override
    public ProjectMemoryEntryEntity saveEntry(ProjectMemoryEntryEntity entry) {
        String id = entry.id() == null || entry.id().isBlank() ? "pme-" + UUID.randomUUID() : entry.id();
        dsl.query("""
                                insert into project_memory_repo_entries (
                                  id, project_id, root_id, memory_id, kind, content, source_refs, confidence, visibility,
                                  risk_level, retrieval_tags, supersedes, origin_event_type, origin_event_id, maintenance_reason,
                                  valid_from, expires_at, last_used_at, status, created_by_account_id, approved_by_account_id, approved_at
                                )
                                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?::timestamptz)
                                on conflict (project_id, memory_id) do update
                                set root_id = excluded.root_id,
                                    kind = excluded.kind,
                                    content = excluded.content,
                                    source_refs = excluded.source_refs,
                                    confidence = excluded.confidence,
                                    visibility = excluded.visibility,
                                    risk_level = excluded.risk_level,
                                    retrieval_tags = excluded.retrieval_tags,
                                    supersedes = excluded.supersedes,
                                    origin_event_type = excluded.origin_event_type,
                                    origin_event_id = excluded.origin_event_id,
                                    maintenance_reason = excluded.maintenance_reason,
                                    valid_from = excluded.valid_from,
                                    expires_at = excluded.expires_at,
                                    last_used_at = excluded.last_used_at,
                                    status = excluded.status,
                                    approved_by_account_id = excluded.approved_by_account_id,
                                    approved_at = excluded.approved_at,
                                    updated_at = now()
                                """,
                        id, entry.projectId(), entry.rootId(), entry.memoryId(), entry.kind(), entry.content(),
                        PostgresJson.jsonb(entry.sourceRefs() == null ? List.of() : entry.sourceRefs()).data(),
                        entry.confidence(), entry.visibility(), entry.riskLevel(),
                        PostgresJson.jsonb(entry.retrievalTags() == null ? List.of() : entry.retrievalTags()).data(),
                        PostgresJson.jsonb(entry.supersedes() == null ? List.of() : entry.supersedes()).data(),
                        entry.originEventType(), entry.originEventId(), entry.maintenanceReason(),
                        PostgresJson.offsetDateTime(entry.validFrom()), PostgresJson.offsetDateTime(entry.expiresAt()), PostgresJson.offsetDateTime(entry.lastUsedAt()),
                        entry.status(), entry.createdByAccountId(), entry.approvedByAccountId(), PostgresJson.offsetDateTime(entry.approvedAt()))
                .execute();
        return findEntry(entry.projectId(), entry.memoryId()).orElseThrow();
    }

    @Override
    public java.util.Optional<ProjectMemoryEntryEntity> findEntry(String projectId, String memoryId) {
        return dsl.resultQuery(selectEntrySql() + " where project_id = ? and memory_id = ?", projectId, memoryId)
                .fetchOptional(this::mapEntry);
    }

    @Override
    public java.util.Optional<ProjectMemoryRootEntity> findLatestRoot(String projectId) {
        return dsl.resultQuery(selectRootSql() + " where project_id = ? order by created_at desc limit 1", projectId)
                .fetchOptional(this::mapRoot);
    }

    @Override
    public List<ProjectMemorySourceEntity> findSources(String projectId, int limit) {
        return dsl.resultQuery(selectSourceSql() + " where project_id = ? order by created_at desc limit ?", projectId, limit)
                .fetch(this::mapSource);
    }

    @Override
    public List<ProjectMemoryEntryEntity> findEntries(String projectId, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return dsl.resultQuery(selectEntrySql() + " where project_id = ? order by status, kind, updated_at desc", projectId)
                    .fetch(this::mapEntry);
        }
        return dsl.resultQuery(selectEntrySql() + " where project_id = ? and status = any(?::text[]) order by status, kind, updated_at desc", projectId, statuses.toArray(String[]::new))
                .fetch(this::mapEntry);
    }

    @Override
    public ProjectMemorySyncEventEntity saveEvent(String projectId, String rootId, String eventType, String status, String message, Map<String, Object> payload) {
        String id = "pmev-" + UUID.randomUUID();
        dsl.query("""
                        insert into project_memory_sync_events (id, project_id, root_id, event_type, status, message, payload)
                        values (?, ?, ?, ?, ?, ?, ?::jsonb)
                        """, id, projectId, rootId, eventType, status, message, PostgresJson.jsonb(payload).data())
                .execute();
        return dsl.resultQuery(selectEventSql() + " where id = ?", id).fetchOne(this::mapEvent);
    }

    @Override
    public void updateEventStatus(String eventId, String status, String message) {
        dsl.query("""
                        update project_memory_sync_events
                        set status = ?, message = coalesce(?, message)
                        where id = ?
                        """, status, message, eventId)
                .execute();
    }

    @Override
    public List<ProjectMemorySyncEventEntity> findEvents(String projectId, List<String> eventTypes, List<String> statuses, int limit) {
        return dsl.resultQuery(selectEventSql() + """
                                where project_id = ?
                                  and (?::text[] is null or event_type = any(?::text[]))
                                  and (?::text[] is null or status = any(?::text[]))
                                order by created_at desc
                                limit ?
                                """,
                        projectId,
                        eventTypes == null || eventTypes.isEmpty() ? null : eventTypes.toArray(String[]::new),
                        eventTypes == null || eventTypes.isEmpty() ? null : eventTypes.toArray(String[]::new),
                        statuses == null || statuses.isEmpty() ? null : statuses.toArray(String[]::new),
                        statuses == null || statuses.isEmpty() ? null : statuses.toArray(String[]::new),
                        limit)
                .fetch(this::mapEvent);
    }

    @Override
    public List<ProjectMemorySyncEventEntity> findActionableSourceReviewEvents(String accountId) {
        return dsl.resultQuery(selectEventSql() + """
                        where event_type = 'source_review_ready'
                          and status = 'pending'
                          and project_id in (
                            select project_id from project_roles where account_id = ? and role_code in ('system_ceo', 'system_cto')
                          )
                        order by created_at desc
                        limit 50
                        """, accountId)
                .fetch(this::mapEvent);
    }

    private String selectRootSql() {
        return """
                select id, project_id, repo_binding_id, provider, repo_owner, repo_name, branch, commit_sha, root_hash,
                       latest_path, sync_status, error_code, error_message, raw_root, created_at, synced_at
                from project_memory_repo_roots
                """;
    }

    private String selectSourceSql() {
        return """
                select id, project_id, root_id, source_id, kind, path, sha256, visibility, provider, external_url,
                       external_file_id, external_revision_id, external_size, sync_status, metadata, created_at
                from project_memory_repo_sources
                """;
    }

    private String selectEntrySql() {
        return """
                select id, project_id, root_id, memory_id, kind, content, source_refs, confidence, visibility, risk_level,
                       retrieval_tags, supersedes, origin_event_type, origin_event_id, maintenance_reason, valid_from, expires_at,
                       last_used_at, status, created_by_account_id, approved_by_account_id, approved_at, created_at, updated_at
                from project_memory_repo_entries
                """;
    }

    private String selectEventSql() {
        return """
                select id, project_id, root_id, event_type, status, message, payload, created_at
                from project_memory_sync_events
                """;
    }

    private ProjectMemoryRootEntity mapRoot(Record record) {
        return new ProjectMemoryRootEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("repo_binding_id", String.class),
                record.get("provider", String.class),
                record.get("repo_owner", String.class),
                record.get("repo_name", String.class),
                record.get("branch", String.class),
                record.get("commit_sha", String.class),
                record.get("root_hash", String.class),
                record.get("latest_path", String.class),
                record.get("sync_status", String.class),
                record.get("error_code", String.class),
                record.get("error_message", String.class),
                PostgresJson.map(record.get("raw_root", JSONB.class)),
                instant(record, "created_at"),
                instant(record, "synced_at"));
    }

    private ProjectMemorySourceEntity mapSource(Record record) {
        return new ProjectMemorySourceEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("root_id", String.class),
                record.get("source_id", String.class),
                record.get("kind", String.class),
                record.get("path", String.class),
                record.get("sha256", String.class),
                record.get("visibility", String.class),
                record.get("provider", String.class),
                record.get("external_url", String.class),
                record.get("external_file_id", String.class),
                record.get("external_revision_id", String.class),
                record.get("external_size", Long.class),
                record.get("sync_status", String.class),
                PostgresJson.map(record.get("metadata", JSONB.class)),
                instant(record, "created_at"));
    }

    private ProjectMemoryEntryEntity mapEntry(Record record) {
        return new ProjectMemoryEntryEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("root_id", String.class),
                record.get("memory_id", String.class),
                record.get("kind", String.class),
                record.get("content", String.class),
                jsonStringList(record, "source_refs"),
                record.get("confidence", java.math.BigDecimal.class),
                record.get("visibility", String.class),
                record.get("risk_level", String.class),
                jsonStringList(record, "retrieval_tags"),
                jsonStringList(record, "supersedes"),
                record.get("origin_event_type", String.class),
                record.get("origin_event_id", String.class),
                record.get("maintenance_reason", String.class),
                instant(record, "valid_from"),
                instant(record, "expires_at"),
                instant(record, "last_used_at"),
                record.get("status", String.class),
                record.get("created_by_account_id", String.class),
                record.get("approved_by_account_id", String.class),
                instant(record, "approved_at"),
                instant(record, "created_at"),
                instant(record, "updated_at"));
    }

    private ProjectMemorySyncEventEntity mapEvent(Record record) {
        return new ProjectMemorySyncEventEntity(
                record.get("id", String.class),
                record.get("project_id", String.class),
                record.get("root_id", String.class),
                record.get("event_type", String.class),
                record.get("status", String.class),
                record.get("message", String.class),
                PostgresJson.map(record.get("payload", JSONB.class)),
                instant(record, "created_at"));
    }

    private List<String> jsonStringList(Record record, String field) {
        return PostgresJson.jsonbValue(record.get(field, JSONB.class), new TypeReference<>() {
        }, List.of());
    }

    private Instant instant(Record record, String field) {
        return PostgresJson.instant(record.get(field, OffsetDateTime.class));
    }
}
