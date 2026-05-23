package com.monopolyfun.modules.projectmemory.infra;

import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryEntryEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryRootEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySourceEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySyncEventEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectMemoryRepository {
    ProjectMemoryRootEntity saveRoot(
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
            Instant syncedAt);

    ProjectMemorySourceEntity saveSource(
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
            Map<String, Object> metadata);

    ProjectMemoryEntryEntity saveEntry(ProjectMemoryEntryEntity entry);

    Optional<ProjectMemoryEntryEntity> findEntry(String projectId, String memoryId);

    Optional<ProjectMemoryRootEntity> findLatestRoot(String projectId);

    List<ProjectMemorySourceEntity> findSources(String projectId, int limit);

    List<ProjectMemoryEntryEntity> findEntries(String projectId, List<String> statuses);

    ProjectMemorySyncEventEntity saveEvent(String projectId, String rootId, String eventType, String status, String message, Map<String, Object> payload);

    void updateEventStatus(String eventId, String status, String message);

    List<ProjectMemorySyncEventEntity> findEvents(String projectId, List<String> eventTypes, List<String> statuses, int limit);

    List<ProjectMemorySyncEventEntity> findActionableSourceReviewEvents(String accountId);
}
