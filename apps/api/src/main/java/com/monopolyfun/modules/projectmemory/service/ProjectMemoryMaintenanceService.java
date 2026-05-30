package com.monopolyfun.modules.projectmemory.service;

import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.api.request.ProjectPrCiEventRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemorySourceRequest;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySourceEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySyncEventEntity;
import com.monopolyfun.modules.projectmemory.infra.ProjectMemoryRepository;
import com.monopolyfun.modules.work.service.ProjectWorkItemPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class ProjectMemoryMaintenanceService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ProjectMemoryService memoryService;
    private final ProjectMemoryRepository memoryRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ProjectWorkItemPublisher projectWorkItemPublisher;

    public ProjectMemoryMaintenanceService(
            ProjectMemoryService memoryService,
            ProjectMemoryRepository memoryRepository,
            OrganizationAuthorityService organizationAuthorityService,
            ProjectWorkItemPublisher projectWorkItemPublisher) {
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.projectWorkItemPublisher = projectWorkItemPublisher;
    }

    public void capturePrCiEvent(String projectId, ProjectPrCiEventRequest request) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("eventType", request.eventType());
        metadata.put("validationTaskId", request.validationTaskId());
        metadata.put("repoUrl", request.repoUrl());
        metadata.put("prNumber", request.prNumber());
        metadata.put("prUrl", request.prUrl());
        metadata.put("headSha", request.headSha());
        metadata.put("status", request.status());
        metadata.put("conclusion", request.conclusion());
        metadata.put("payload", request.payload() == null ? Map.of() : request.payload());
        String subject = request.prNumber() == null ? sanitize(request.eventType()) : "pr_" + request.prNumber();
        ProjectMemorySourceEntity source = memoryService.saveSource(projectId, null, new ProjectMemorySourceRequest(
                "src_" + subject + "_ci",
                "pr_ci_event",
                ".monopoly-memory/sources/" + DATE.format(Instant.now()) + "-" + subject + "-ci.md",
                null,
                "team",
                "git",
                request.prUrl() == null || request.prUrl().isBlank() ? request.detailsUrl() : request.prUrl(),
                request.headSha(),
                null,
                null,
                metadata), "system", "pr_ci_event");
        ProjectMemorySyncEventEntity event = memoryRepository.saveEvent(projectId, null, "source_review_ready", "pending", "PR/CI event produced a project memory source for team review", Map.of(
                "sourceId", source.sourceId(),
                "eventType", request.eventType(),
                "prNumber", request.prNumber() == null ? "" : request.prNumber(),
                "sourceRefs", java.util.List.of(source.sourceId())));
        projectWorkItemPublisher.publishProjectMemorySourceReview(event, organizationAuthorityService.listProjectRoles(projectId), Instant.now());
    }

    private String sanitize(String value) {
        String normalized = value == null ? "source" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "-");
        return normalized.isBlank() ? "source" : normalized;
    }
}
