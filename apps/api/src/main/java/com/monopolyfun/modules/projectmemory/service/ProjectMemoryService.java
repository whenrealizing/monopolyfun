package com.monopolyfun.modules.projectmemory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRepoBindingEntity;
import com.monopolyfun.modules.project.infra.ProjectDevelopmentRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolService;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemoryEntryRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemoryRepoSyncRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemorySourceRequest;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryEntryEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemoryRootEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySourceEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySyncEventEntity;
import com.monopolyfun.modules.projectmemory.infra.ProjectMemoryRepository;
import com.monopolyfun.modules.projectmemory.service.view.ProjectAgentContextView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryEntryView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryOverviewView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryRootView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemorySourceView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemorySyncEventView;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.modules.work.service.ProjectWorkItemPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectMemoryService {
    private static final Set<String> MEMORY_KINDS = Set.of(
            "identity", "positioning", "audience", "strategy", "voice", "experiment", "result", "lesson", "priority", "risk_boundary");
    private static final Set<String> RISK_LEVELS = Set.of("normal", "approval_required", "blocked");
    private static final Set<String> VISIBILITIES = Set.of("public", "team", "private");

    private final ProjectMemoryRepository memoryRepository;
    private final ProjectRepository projectRepository;
    private final ProjectDevelopmentRepository developmentRepository;
    private final ProjectValidationProtocolService validationProtocolService;
    private final WorkRepository workRepository;
    private final OrganizationAuthorityService authorityService;
    private final ProjectWorkItemPublisher projectWorkItemPublisher;
    private final ObjectMapper objectMapper;

    public ProjectMemoryService(
            ProjectMemoryRepository memoryRepository,
            ProjectRepository projectRepository,
            ProjectDevelopmentRepository developmentRepository,
            ProjectValidationProtocolService validationProtocolService,
            WorkRepository workRepository,
            OrganizationAuthorityService authorityService,
            ProjectWorkItemPublisher projectWorkItemPublisher,
            ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.projectRepository = projectRepository;
        this.developmentRepository = developmentRepository;
        this.validationProtocolService = validationProtocolService;
        this.workRepository = workRepository;
        this.authorityService = authorityService;
        this.projectWorkItemPublisher = projectWorkItemPublisher;
        this.objectMapper = objectMapper;
    }

    public ProjectMemoryOverviewView overview(String projectNo, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        return new ProjectMemoryOverviewView(
                memoryRepository.findLatestRoot(projectId).map(this::rootView).orElse(null),
                memoryRepository.findSources(projectId, 30).stream().map(this::sourceView).toList(),
                memoryRepository.findEntries(projectId, List.of()).stream().map(this::entryView).toList(),
                memoryRepository.findEvents(projectId, List.of(), List.of(), 30).stream().map(this::eventView).toList());
    }

    public ProjectMemoryRootView syncRepo(String projectNo, ProjectMemoryRepoSyncRequest request, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        authorityService.requireProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_MANAGE);
        ProjectRepoBindingEntity binding = resolveBinding(projectId, request.repoBindingId());
        String commitSha = blank(request.commitSha()) ? "manual-" + Instant.now().toEpochMilli() : normalizeText(request.commitSha(), "commitSha", 80);
        String rootHash = blank(request.rootHash()) ? sha256(projectId + ":" + commitSha) : normalizeText(request.rootHash(), "rootHash", 120);
        ProjectMemoryRootEntity root = memoryRepository.saveRoot(
                projectId,
                binding == null ? null : binding.id(),
                binding == null ? "monopolyfun" : binding.provider(),
                binding == null ? "internal" : binding.repoOwner(),
                binding == null ? projectNo : binding.repoName(),
                binding == null || blank(binding.defaultBranch()) ? "main" : binding.defaultBranch(),
                commitSha,
                rootHash,
                blank(request.latestPath()) ? ".monopoly-memory/roots/latest.json" : normalizeText(request.latestPath(), "latestPath", 500),
                "synced",
                null,
                null,
                request.rawRoot() == null ? Map.of() : request.rawRoot(),
                Instant.now());
        memoryRepository.saveEvent(projectId, root.id(), "repo_sync", "synced", "Project memory root synced", Map.of("commitSha", commitSha, "rootHash", rootHash));
        return rootView(root);
    }

    public ProjectMemorySourceView createSource(String projectNo, ProjectMemorySourceRequest request, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        ProjectMemorySourceEntity source = saveSource(projectId, null, request, actorAccountId, "manual_source");
        ProjectMemorySyncEventEntity event = memoryRepository.saveEvent(projectId, null, "source_review_ready", "pending", "New project memory source requires team review", Map.of(
                "sourceId", source.sourceId(),
                "kind", source.kind(),
                "path", source.path(),
                "createdByAccountId", actorAccountId));
        projectWorkItemPublisher.publishProjectMemorySourceReview(event, authorityService.listProjectRoles(projectId), Instant.now());
        return sourceView(source);
    }

    ProjectMemorySourceEntity saveSource(String projectId, String rootId, ProjectMemorySourceRequest request, String actorAccountId, String reason) {
        String sourceId = blank(request.sourceId()) ? "src_" + UUID.randomUUID().toString().replace("-", "") : normalizeId(request.sourceId(), "sourceId");
        String externalUrl = blank(request.externalUrl()) ? null : normalizeUrl(request.externalUrl(), "externalUrl");
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(request.metadata() == null ? Map.of() : request.metadata());
        metadata.put("createdByAccountId", actorAccountId);
        metadata.put("maintenanceReason", reason);
        // 中文注释：source 是系统事实层，只做索引和风险标记，团队共识留到 memory 审批阶段。
        return memoryRepository.saveSource(
                projectId,
                rootId,
                sourceId,
                normalizeText(request.kind(), "kind", 40),
                normalizePath(request.path()),
                blank(request.sha256()) ? sha256(sourceId + ":" + request.path() + ":" + metadata) : normalizeText(request.sha256(), "sha256", 120),
                normalizeVisibility(request.visibility()),
                blank(request.provider()) ? "monopolyfun" : normalizeText(request.provider(), "provider", 40),
                externalUrl,
                normalizeOptionalText(request.externalFileId(), 120),
                normalizeOptionalText(request.externalRevisionId(), 120),
                request.externalSize(),
                "metadata_synced",
                metadata);
    }

    public ProjectMemoryEntryView createEntry(String projectNo, ProjectMemoryEntryRequest request, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        ProjectMemoryEntryEntity entry = buildEntry(projectId, null, request, "proposed", actorAccountId, null, null);
        ProjectMemoryEntryEntity saved = memoryRepository.saveEntry(entry);
        if (!blank(request.originEventId())) {
            // 中文注释：团队提交 memory 后关闭 source review 提醒，避免同一 evidence package 反复驱动待办。
            memoryRepository.updateEventStatus(request.originEventId(), "reviewed", "Team proposed memory from this source review");
            projectWorkItemPublisher.closeProjectMemorySourceReview(request.originEventId());
        }
        return entryView(saved);
    }

    public ProjectMemoryEntryView approveEntry(String projectNo, String memoryId, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireMemoryApproval(projectId, actorAccountId);
        ProjectMemoryEntryEntity current = memoryRepository.findEntry(projectId, normalizeId(memoryId, "memoryId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project memory entry not found"));
        ProjectMemoryEntryEntity next = copyEntry(current, "active", current.createdByAccountId(), actorAccountId, Instant.now());
        memoryRepository.saveEvent(projectId, current.rootId(), "project.memory.approved", "approved", "Project memory entered active context", Map.of("memoryId", current.memoryId(), "kind", current.kind()));
        return entryView(memoryRepository.saveEntry(next));
    }

    public ProjectMemoryEntryView supersedeEntry(String projectNo, String memoryId, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireMemoryApproval(projectId, actorAccountId);
        ProjectMemoryEntryEntity current = memoryRepository.findEntry(projectId, normalizeId(memoryId, "memoryId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project memory entry not found"));
        memoryRepository.saveEvent(projectId, current.rootId(), "project.memory.superseded", "superseded", "Project memory superseded by team decision", Map.of("memoryId", current.memoryId()));
        return entryView(memoryRepository.saveEntry(copyEntry(current, "superseded", current.createdByAccountId(), actorAccountId, Instant.now())));
    }

    public ProjectAgentContextView agentContext(String projectNo, String actorAccountId) {
        ProjectEntity project = requireProject(projectNo);
        requireProjectAccess(project.id(), actorAccountId);
        // 中文注释：agent context 是 GET 读取视图，使用纯读取快照，避免 dashboard 或 agent 预览请求产生隐式写入。
        List<ProjectMemoryEntryView> entries = memoryRepository.findEntries(project.id(), List.of("active")).stream()
                .map(this::entryView)
                .toList();
        Map<String, List<ProjectMemoryEntryView>> grouped = entries.stream()
                .collect(Collectors.groupingBy(ProjectMemoryEntryView::kind, LinkedHashMap::new, Collectors.toList()));
        ProjectMemoryRootEntity latestRoot = memoryRepository.findLatestRoot(project.id()).orElse(null);
        LinkedHashMap<String, Object> projectPayload = new LinkedHashMap<>();
        projectPayload.put("projectNo", project.projectNo());
        projectPayload.put("title", project.title());
        projectPayload.put("repo", referenceLinks(project).stream().findFirst().orElse(null));
        LinkedHashMap<String, Object> memorySource = new LinkedHashMap<>();
        memorySource.put("repoCommit", latestRoot == null ? null : latestRoot.commitSha());
        memorySource.put("rootHash", latestRoot == null ? null : latestRoot.rootHash());
        memorySource.put("syncedAt", latestRoot == null || latestRoot.syncedAt() == null ? null : latestRoot.syncedAt().toString());
        List<WorkItemEntity> workItems = projectWorkItems(project, actorAccountId);
        return new ProjectAgentContextView(
                projectPayload,
                grouped,
                validationProtocolService.agentContext(project.projectNo()),
                workbenchPayload(workItems),
                toolContractsPayload(project),
                memorySource);
    }

    public Map<String, Object> sourceContract(String projectNo, String actorAccountId) {
        ProjectEntity project = requireProject(projectNo);
        requireProjectAccess(project.id(), actorAccountId);
        ProjectMemoryRootEntity root = memoryRepository.findLatestRoot(project.id()).orElse(null);
        // 中文注释：contract hash 对外用于校验，同一事实集合必须先稳定排序再进入 canonical JSON。
        List<ProjectMemorySourceEntity> sources = memoryRepository.findSources(project.id(), 200).stream()
                .sorted(Comparator
                        .comparing(ProjectMemorySourceEntity::sourceId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ProjectMemorySourceEntity::sha256, Comparator.nullsLast(String::compareTo)))
                .toList();
        List<ProjectMemoryEntryEntity> activeEntries = memoryRepository.findEntries(project.id(), List.of("active")).stream()
                .sorted(Comparator.comparing(ProjectMemoryEntryEntity::memoryId, Comparator.nullsLast(String::compareTo)))
                .toList();
        LinkedHashMap<String, Object> contract = new LinkedHashMap<>();
        contract.put("version", "project-source-contract-v1");
        contract.put("projectNo", project.projectNo());
        contract.put("rootHash", root == null ? null : root.rootHash());
        contract.put("repoCommit", root == null ? null : root.commitSha());
        contract.put("sources", sources.stream().map(source -> Map.of(
                "sourceId", source.sourceId(),
                "kind", source.kind(),
                "path", source.path(),
                "sha256", source.sha256(),
                "visibility", source.visibility(),
                "provider", source.provider() == null ? "" : source.provider(),
                "externalUrl", source.externalUrl() == null ? "" : source.externalUrl())).toList());
        contract.put("activeMemory", activeEntries.stream().map(entry -> Map.of(
                "memoryId", entry.memoryId(),
                "kind", entry.kind(),
                "sourceRefs", entry.sourceRefs(),
                "confidence", entry.confidence(),
                "approvedAt", entry.approvedAt() == null ? "" : entry.approvedAt().toString())).toList());
        contract.put("readbackUrl", "/api/v1/projects/" + project.projectNo() + "/agent-context");
        // 中文注释：source contract hash 基于 canonical JSON，避免 Map 字符串形态导致同一契约产生不同 hash。
        contract.put("contractHash", sha256(canonicalJsonBytes(contract)));
        return contract;
    }

    private Map<String, Object> workbenchPayload(List<WorkItemEntity> items) {
        return Map.of(
                "currentApprovalItems", items.stream()
                        .filter(item -> List.of("project_pr", "project_ci_check", "project_memory", "share_release_request").contains(item.sourceType()))
                        .map(this::workItemPayload)
                        .toList(),
                "recommendedNextActions", items.stream().map(this::recommendedActionPayload).toList());
    }

    private Map<String, Object> toolContractsPayload(ProjectEntity project) {
        return Map.of(
                "entryModes", List.of("ui", "api"),
                "proofTypes", List.of("git_pr", "deployment_url", "doc", "sheet", "metric_snapshot", "url"),
                "validationLaunchesUrl", "/api/v1/projects/" + project.projectNo() + "/launches",
                "validationFeedbackUrl", "/api/v1/projects/" + project.projectNo() + "/validation-feedback",
                "sourceContractUrl", "/api/v1/projects/" + project.projectNo() + "/memory/source-contract",
                "readbackUrl", "/api/v1/projects/" + project.projectNo() + "/agent-context");
    }

    private List<WorkItemEntity> projectWorkItems(ProjectEntity project, String actorAccountId) {
        return workRepository.findItemsByAccountId(actorAccountId).stream()
                .filter(item -> belongsToProject(project, item))
                .toList();
    }

    private boolean belongsToProject(ProjectEntity project, WorkItemEntity item) {
        if (item.inputRefs().contains("project:" + project.projectNo()) || item.inputRefs().contains("project:" + project.id())) {
            return true;
        }
        Object projectId = item.outputSchema().get("projectId");
        Object projectNo = item.outputSchema().get("projectNo");
        return project.id().equals(projectId) || project.projectNo().equals(projectNo);
    }

    private Map<String, Object> workItemPayload(WorkItemEntity item) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemNo", item.itemNo());
        payload.put("sourceType", item.sourceType());
        payload.put("sourceId", item.sourceId());
        payload.put("title", item.title());
        payload.put("status", item.status());
        payload.put("requiredRole", item.requiredRole());
        payload.put("requiredCapability", item.requiredCapability());
        payload.put("urgency", item.urgency());
        payload.put("sourceRefs", item.inputRefs());
        payload.put("action", item.outputSchema().get("action"));
        payload.put("actionUrl", "/api/v1/workbench");
        return payload;
    }

    private Map<String, Object> recommendedActionPayload(WorkItemEntity item) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemNo", item.itemNo());
        payload.put("recommendedAction", item.outputSchema().getOrDefault("action", "open"));
        payload.put("title", item.title());
        payload.put("sourceType", item.sourceType());
        payload.put("sourceId", item.sourceId());
        payload.put("readbackUrl", "/api/v1/workbench");
        return payload;
    }

    private ProjectMemoryEntryEntity buildEntry(String projectId, String rootId, ProjectMemoryEntryRequest request, String status, String createdBy, String approvedBy, Instant approvedAt) {
        String kind = normalizeKind(request.kind());
        List<String> sourceRefs = normalizeList(request.sourceRefs(), "sourceRefs", 120);
        requireSourcesExist(projectId, sourceRefs);
        return new ProjectMemoryEntryEntity(
                null,
                projectId,
                rootId,
                blank(request.memoryId()) ? "mem_" + UUID.randomUUID().toString().replace("-", "") : normalizeId(request.memoryId(), "memoryId"),
                kind,
                normalizeText(request.content(), "content", 1000),
                sourceRefs,
                normalizeConfidence(request.confidence()),
                normalizeVisibility(request.visibility()),
                normalizeRiskLevel(request.riskLevel()),
                normalizeList(request.retrievalTags(), "retrievalTags", 80),
                normalizeList(request.supersedes(), "supersedes", 80),
                normalizeOptionalText(request.originEventType(), 80),
                normalizeOptionalText(request.originEventId(), 120),
                normalizeOptionalText(request.maintenanceReason(), 200),
                Instant.now(),
                null,
                null,
                status,
                createdBy,
                approvedBy,
                approvedAt,
                Instant.now(),
                Instant.now());
    }

    private ProjectMemoryEntryEntity copyEntry(ProjectMemoryEntryEntity current, String status, String createdBy, String approvedBy, Instant approvedAt) {
        return copyEntry(current, status, createdBy, approvedBy, approvedAt, current.lastUsedAt());
    }

    private ProjectMemoryEntryEntity copyEntry(ProjectMemoryEntryEntity current, String status, String createdBy, String approvedBy, Instant approvedAt, Instant lastUsedAt) {
        return new ProjectMemoryEntryEntity(
                current.id(), current.projectId(), current.rootId(), current.memoryId(), current.kind(), current.content(),
                current.sourceRefs(), current.confidence(), current.visibility(), current.riskLevel(), current.retrievalTags(), current.supersedes(),
                current.originEventType(), current.originEventId(), current.maintenanceReason(), current.validFrom(), current.expiresAt(), lastUsedAt,
                status, createdBy, approvedBy, approvedAt, current.createdAt(), Instant.now());
    }

    private void requireSourcesExist(String projectId, List<String> sourceRefs) {
        if (sourceRefs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceRefs is required");
        }
        Set<String> sourceIds = memoryRepository.findSources(projectId, 500).stream().map(ProjectMemorySourceEntity::sourceId).collect(Collectors.toSet());
        for (String ref : sourceRefs) {
            if (!sourceIds.contains(ref)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceRef is missing: " + ref);
            }
        }
    }

    private ProjectRepoBindingEntity resolveBinding(String projectId, String repoBindingId) {
        List<ProjectRepoBindingEntity> bindings = developmentRepository.findRepoBindings(projectId);
        if (!blank(repoBindingId)) {
            return bindings.stream().filter(binding -> binding.id().equals(repoBindingId.trim()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project repo binding not found"));
        }
        return bindings.stream().findFirst().orElse(null);
    }

    private String requireProjectId(String projectNo) {
        return requireProject(projectNo).id();
    }

    private ProjectEntity requireProject(String projectNo) {
        if (blank(projectNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void requireProjectAccess(String projectId, String actorAccountId) {
        if (!authorityService.hasProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_PARTICIPATE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project participation required");
        }
    }

    private void requireMemoryApproval(String projectId, String actorAccountId) {
        boolean allowed = authorityService.hasProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_MANAGE)
                || authorityService.hasProjectCapability(actorAccountId, projectId, ProjectCapability.PROOF_TECH_REVIEW);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project memory approval requires project manage or proof review authority");
        }
    }

    public ProjectMemoryRootView rootView(ProjectMemoryRootEntity root) {
        return new ProjectMemoryRootView(root.id(), root.provider(), root.repoOwner(), root.repoName(), root.branch(), root.commitSha(), root.rootHash(), root.syncStatus(), root.errorCode(), root.errorMessage(), root.rawRoot(), stringify(root.syncedAt()));
    }

    public ProjectMemorySourceView sourceView(ProjectMemorySourceEntity source) {
        return new ProjectMemorySourceView(source.id(), source.sourceId(), source.kind(), source.path(), source.sha256(), source.visibility(), source.provider(), source.externalUrl(), source.syncStatus(), source.metadata(), stringify(source.createdAt()));
    }

    public ProjectMemoryEntryView entryView(ProjectMemoryEntryEntity entry) {
        return new ProjectMemoryEntryView(entry.id(), entry.memoryId(), entry.kind(), entry.content(), entry.sourceRefs(), entry.confidence(), entry.visibility(), entry.riskLevel(), entry.retrievalTags(), entry.supersedes(), entry.status(), entry.createdByAccountId(), entry.approvedByAccountId(), stringify(entry.approvedAt()), stringify(entry.updatedAt()));
    }

    public ProjectMemorySyncEventView eventView(ProjectMemorySyncEventEntity event) {
        return new ProjectMemorySyncEventView(event.id(), event.eventType(), event.status(), event.message(), event.payload(), stringify(event.createdAt()));
    }

    private String normalizeKind(String value) {
        String normalized = normalizeText(value, "kind", 40).toLowerCase(Locale.ROOT);
        if (!MEMORY_KINDS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memory kind is invalid");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String value) {
        String normalized = blank(value) ? "normal" : normalizeText(value, "riskLevel", 40).toLowerCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "riskLevel is invalid");
        }
        return normalized;
    }

    private String normalizeVisibility(String value) {
        String normalized = blank(value) ? "team" : normalizeText(value, "visibility", 40).toLowerCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "visibility is invalid");
        }
        return normalized;
    }

    private BigDecimal normalizeConfidence(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ONE : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.ONE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confidence must be between 0 and 1");
        }
        return normalized;
    }

    private List<String> normalizeList(List<String> values, String field, int maxLength) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> normalizeText(value, field, maxLength)).distinct().toList();
    }

    private String normalizePath(String value) {
        String normalized = normalizeText(value, "path", 500);
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path is invalid");
        }
        return normalized;
    }

    private String normalizeUrl(String value, String field) {
        String normalized = normalizeText(value, field, 500);
        try {
            URI uri = new URI(normalized);
            if (uri.getScheme() == null || uri.getHost() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be http or https URL");
            }
            if (Set.of("localhost", "127.0.0.1", "0.0.0.0").contains(uri.getHost().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " host is blocked");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be valid URL", exception);
        }
    }

    private String normalizeId(String value, String field) {
        String normalized = normalizeText(value, field, 120);
        if (!normalized.matches("[a-zA-Z0-9_.:-]{1,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " format is invalid");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        return blank(value) ? null : normalizeText(value, "text", maxLength);
    }

    private String normalizeText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private byte[] canonicalJsonBytes(Map<String, Object> contract) {
        try {
            ObjectMapper canonicalMapper = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return canonicalMapper.writeValueAsBytes(contract);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Project source contract cannot be serialized", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String stringify(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private List<String> referenceLinks(ProjectEntity project) {
        Object value = project.metadata() == null ? null : project.metadata().get("referenceLinks");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }
}
