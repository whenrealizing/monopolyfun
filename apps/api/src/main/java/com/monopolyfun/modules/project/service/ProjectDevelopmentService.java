package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.api.request.ProjectPrCiEventRequest;
import com.monopolyfun.modules.project.api.request.ProjectRepoBindingRequest;
import com.monopolyfun.modules.project.api.request.ReviewProjectResultCandidateRequest;
import com.monopolyfun.modules.project.api.request.SkipProjectCandidateWindowRequest;
import com.monopolyfun.modules.project.api.request.SupportProjectResultCandidateRequest;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectCiCheckEntity;
import com.monopolyfun.modules.project.domain.ProjectPrLinkEntity;
import com.monopolyfun.modules.project.domain.ProjectRepoBindingEntity;
import com.monopolyfun.modules.project.infra.ProjectDevelopmentRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.TaskView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolService;
import com.monopolyfun.modules.project.service.view.ProjectCandidateBlockedSummaryView;
import com.monopolyfun.modules.project.service.view.ProjectCandidateHistorySummaryView;
import com.monopolyfun.modules.project.service.view.ProjectPrCiStatusView;
import com.monopolyfun.modules.project.service.view.ProjectRepoBindingView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidatePageView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateSummaryView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateWindowItemView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateWindowView;
import com.monopolyfun.modules.projectmemory.service.ProjectMemoryMaintenanceService;
import com.monopolyfun.modules.work.service.ProjectWorkItemPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class ProjectDevelopmentService {
    private static final int SUPPORT_THRESHOLD = 3;
    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 100;
    private static final int DEFAULT_WINDOW_LIMIT = 5;
    private static final int MAX_WINDOW_LIMIT = 10;
    private static final int DEFAULT_SKIP_TTL_MINUTES = 30;

    private final ProjectDevelopmentRepository developmentRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ProjectMemoryMaintenanceService projectMemoryMaintenanceService;
    private final ProjectWorkItemPublisher projectWorkItemPublisher;
    private final ProjectValidationProtocolService validationProtocolService;

    public ProjectDevelopmentService(
            ProjectDevelopmentRepository developmentRepository,
            ProjectRepository projectRepository,
            OrganizationAuthorityService organizationAuthorityService,
            ProjectMemoryMaintenanceService projectMemoryMaintenanceService,
            ProjectWorkItemPublisher projectWorkItemPublisher,
            ProjectValidationProtocolService validationProtocolService) {
        this.developmentRepository = developmentRepository;
        this.projectRepository = projectRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.projectMemoryMaintenanceService = projectMemoryMaintenanceService;
        this.projectWorkItemPublisher = projectWorkItemPublisher;
        this.validationProtocolService = validationProtocolService;
    }

    public ProjectRepoBindingView bindRepo(String projectNo, ProjectRepoBindingRequest request, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireRootProjectMaintenance(projectId);
        organizationAuthorityService.requireProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_MANAGE);
        // 绑定仓库是项目级治理入口，只保留规范化后的远端地址，后续 PR/CI 事件按同一地址归集。
        String repoUrl = normalizeUrl(request.repoUrl(), "repoUrl");
        ProjectRepoBindingEntity binding = developmentRepository.saveRepoBinding(
                projectId,
                blank(request.provider()) ? "github" : request.provider().trim().toLowerCase(Locale.ROOT),
                repoUrl,
                required(request.repoOwner(), "repoOwner", 120),
                required(request.repoName(), "repoName", 120),
                optional(request.defaultBranch(), 120),
                optional(request.installationId(), 120),
                actorAccountId);
        return repoBinding(binding);
    }

    public List<ProjectRepoBindingView> listRepoBindings(String projectNo, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        return developmentRepository.findRepoBindings(projectId).stream().map(this::repoBinding).toList();
    }

    public ProjectPrCiStatusView ingestEvent(String projectNo, ProjectPrCiEventRequest request, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireRootProjectMaintenance(projectId);
        organizationAuthorityService.requireProjectCapability(actorAccountId, projectId, ProjectCapability.PROOF_TECH_REVIEW);
        String eventType = required(request.eventType(), "eventType", 40).toLowerCase(Locale.ROOT);
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        // 外部开发事件落库后直接刷新 WorkItem 投影，GitHub/CI 回调仍只产生待办和 memory source。
        if ("pull_request".equals(eventType) || "pr".equals(eventType)) {
            ProjectPrLinkEntity link = developmentRepository.savePrLink(
                    projectId,
                    optional(request.validationTaskId(), 80),
                    normalizeUrl(request.repoUrl(), "repoUrl"),
                    requiredPrNumber(request.prNumber()),
                    normalizeUrl(request.prUrl(), "prUrl"),
                    optional(request.headSha(), 80),
                    optional(request.baseBranch(), 120),
                    optional(request.branchName(), 120),
                    blank(request.state()) ? "open" : request.state().trim().toLowerCase(Locale.ROOT),
                    payload);
            projectWorkItemPublisher.publishPrLink(link, organizationAuthorityService.listProjectRoles(projectId), Instant.now());
        } else if ("check_run".equals(eventType) || "workflow_run".equals(eventType) || "ci".equals(eventType)) {
            ProjectCiCheckEntity check = developmentRepository.saveCiCheck(
                    projectId,
                    optional(request.validationTaskId(), 80),
                    request.prNumber(),
                    required(request.checkName(), "checkName", 160),
                    blank(request.status()) ? "completed" : request.status().trim().toLowerCase(Locale.ROOT),
                    optional(request.conclusion(), 40),
                    optionalUrl(request.detailsUrl(), "detailsUrl"),
                    payload);
            projectWorkItemPublisher.publishCiCheck(check, organizationAuthorityService.listProjectRoles(projectId), Instant.now());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is unsupported");
        }
        projectMemoryMaintenanceService.capturePrCiEvent(projectId, request);
        return getStatus(projectNo, actorAccountId);
    }

    public ProjectPrCiStatusView getStatus(String projectNo, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        return new ProjectPrCiStatusView(
                developmentRepository.findPrLinks(projectId).stream().map(this::pr).toList(),
                developmentRepository.findCiChecks(projectId).stream().map(this::check).toList());
    }

    public ProjectResultCandidatePageView listResultCandidatePage(
            String projectNo,
            String status,
            Integer limit,
            String cursor,
            String actorAccountId) {
        List<ProjectResultCandidateView> allCandidates = listResultCandidates(projectNo, actorAccountId);
        List<ProjectResultCandidateView> candidates = filterByStatus(allCandidates, status);
        List<ProjectResultCandidateView> window = applyCursor(candidates, cursor);
        int pageLimit = clampLimit(limit, DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT);
        List<ProjectResultCandidateView> items = window.stream().limit(pageLimit).toList();
        String nextCursor = window.size() > pageLimit ? cursorFor(items.getLast()) : null;
        return new ProjectResultCandidatePageView(items, nextCursor, candidateSummary(allCandidates));
    }

    public ProjectResultCandidateWindowView nextCandidateWindow(String projectNo, Integer limit, String after, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        List<ProjectResultCandidateView> candidates = listResultCandidates(projectNo, actorAccountId);
        Set<String> skippedCandidateIds = activeSkippedCandidateIds(projectId, actorAccountId);
        int windowLimit = clampLimit(limit, DEFAULT_WINDOW_LIMIT, MAX_WINDOW_LIMIT);
        List<ProjectResultCandidateWindowItemView> sortedActionable = candidates.stream()
                .filter(candidate -> !skippedCandidateIds.contains(candidate.candidateId()))
                .map(candidate -> candidateWindowItem(candidate, actorAccountId))
                .filter(item -> item.actionScore() > 0)
                .sorted(Comparator
                        .comparing(ProjectResultCandidateWindowItemView::actionScore).reversed()
                        .thenComparing(ProjectResultCandidateWindowItemView::taskUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProjectResultCandidateWindowItemView::candidateId))
                .toList();
        List<ProjectResultCandidateWindowItemView> remaining = applyWindowCursor(sortedActionable, after);
        List<ProjectResultCandidateWindowItemView> actionable = remaining.stream()
                .limit(windowLimit)
                .toList();
        String nextCursor = remaining.size() > windowLimit ? cursorFor(actionable.getLast().taskUpdatedAt(), actionable.getLast().candidateId()) : null;
        return new ProjectResultCandidateWindowView(actionable, nextCursor, blockedSummary(candidates), historySummary(candidates));
    }

    public ProjectResultCandidateWindowView skipCandidateWindow(
            String projectNo,
            SkipProjectCandidateWindowRequest request,
            String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        int ttlMinutes = request.ttlMinutes() == null ? DEFAULT_SKIP_TTL_MINUTES : request.ttlMinutes();
        Instant expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);
        String reasonCode = blank(request.reasonCode()) ? "no_action_available" : request.reasonCode().trim();
        // 中文注释：当前窗口处理不了时记录 actor 级跳过租约，后续窗口自动推进到下一批候选。
        for (String candidateId : new HashSet<>(request.skippedCandidateIds())) {
            if (!blank(candidateId)) {
                developmentRepository.saveCandidateWindowSkip(projectId, candidateId, actorAccountId, reasonCode, request.reason(), expiresAt);
            }
        }
        return nextCandidateWindow(projectNo, DEFAULT_WINDOW_LIMIT, null, actorAccountId);
    }

    private List<ProjectResultCandidateView> listResultCandidates(String projectNo, String actorAccountId) {
        String projectId = requireProjectId(projectNo);
        requireProjectAccess(projectId, actorAccountId);
        // 中文注释：候选池只从已验收任务生成，投票对象必须先完成结果验收。
        List<TaskView> completedTasks = validationProtocolService.listProjectTasks(projectNo).stream()
                .filter(this::completedTask)
                .sorted(Comparator.comparing(TaskView::updatedAt).reversed())
                .toList();
        List<ProjectPrLinkEntity> pullRequests = developmentRepository.findPrLinks(projectId);
        List<ProjectCiCheckEntity> checks = developmentRepository.findCiChecks(projectId);
        CandidateFacts facts = candidateFacts(projectId);
        List<ProjectResultCandidateView> candidates = new ArrayList<>();
        for (TaskView task : completedTasks) {
            List<ProjectPrLinkEntity> taskPullRequests = pullRequests.stream()
                    .filter(pr -> task.id().equals(pr.validationTaskId()))
                    .toList();
            if (taskPullRequests.isEmpty()) {
                candidates.add(candidateWithoutPullRequest(task, facts));
                continue;
            }
            for (ProjectPrLinkEntity pullRequest : taskPullRequests) {
                candidates.add(candidateWithPullRequest(task, pullRequest, checksForPullRequest(checks, pullRequest.prNumber()), facts));
            }
        }
        return candidates.stream()
                .sorted(candidateComparator())
                .toList();
    }

    public ProjectResultCandidateView supportCandidate(
            String projectNo,
            String taskId,
            SupportProjectResultCandidateRequest request,
            String actorAccountId) {
        ProjectResultCandidateView candidate = requireCandidateReady(projectNo, taskId, request.prNumber(), actorAccountId);
        if (developmentRepository.hasCandidateSupport(candidate.candidateId(), actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already supported this candidate");
        }
        developmentRepository.saveCandidateSupport(
                candidate.candidateId(),
                candidate.projectId(),
                candidate.taskId(),
                candidate.prNumber(),
                candidate.headSha(),
                actorAccountId,
                1,
                request.reason());
        return requireCandidate(projectNo, taskId, request.prNumber(), actorAccountId);
    }

    public ProjectResultCandidateView finalReviewCandidate(
            String projectNo,
            String taskId,
            ReviewProjectResultCandidateRequest request,
            String actorAccountId) {
        ProjectResultCandidateView candidate = requireCandidateReady(projectNo, taskId, request.prNumber(), actorAccountId);
        // 中文注释：最终复核只处理已达到支持阈值的候选，避免少量早期支持直接触发主线共识。
        if (candidate.weightedSupport() < SUPPORT_THRESHOLD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate has not reached support threshold");
        }
        TaskView task = validationProtocolService.listProjectTasks(projectNo).stream()
                .filter(value -> value.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (actorAccountId.equals(task.claimedByAccountId()) || actorAccountId.equals(task.createdByAccountId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Candidate author cannot final-review the same result");
        }
        String decision = normalizeFinalReviewDecision(request.decision());
        developmentRepository.saveCandidateFinalReview(
                candidate.candidateId(),
                candidate.projectId(),
                candidate.taskId(),
                candidate.prNumber(),
                candidate.headSha(),
                actorAccountId,
                decision,
                request.reason());
        return requireCandidate(projectNo, taskId, request.prNumber(), actorAccountId);
    }

    public List<ProjectCiCheckEntity> actionableCiChecks(String accountId) {
        return developmentRepository.findActionableCiChecks(accountId);
    }

    public List<ProjectPrLinkEntity> actionablePrLinks(String accountId) {
        return developmentRepository.findActionablePrLinks(accountId);
    }

    private ProjectRepoBindingView repoBinding(ProjectRepoBindingEntity binding) {
        return new ProjectRepoBindingView(
                binding.id(),
                binding.projectId(),
                binding.provider(),
                binding.repoUrl(),
                binding.repoOwner(),
                binding.repoName(),
                binding.defaultBranch(),
                binding.installationId());
    }

    private Map<String, Object> pr(ProjectPrLinkEntity link) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", link.id());
        value.put("validationTaskId", link.validationTaskId());
        value.put("repoUrl", link.repoUrl());
        value.put("prNumber", link.prNumber());
        value.put("prUrl", link.prUrl());
        value.put("headSha", link.headSha());
        value.put("baseBranch", link.baseBranch());
        value.put("branchName", link.branchName());
        value.put("state", link.state());
        value.put("lastSyncedAt", link.lastSyncedAt());
        return value;
    }

    private Map<String, Object> check(ProjectCiCheckEntity check) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", check.id());
        value.put("validationTaskId", check.validationTaskId());
        value.put("prNumber", check.prNumber());
        value.put("checkName", check.checkName());
        value.put("status", check.status());
        value.put("conclusion", check.conclusion());
        value.put("detailsUrl", check.detailsUrl());
        value.put("completedAt", check.completedAt());
        return value;
    }

    private ProjectResultCandidateView candidateWithoutPullRequest(TaskView task, CandidateFacts facts) {
        boolean codeTask = requiresPullRequest(task);
        String candidateId = candidateId(task.id(), null);
        SupportSummary support = facts.supports().getOrDefault(candidateId, new SupportSummary(0, 0));
        FinalReviewSummary finalReview = facts.finalReviews().get(candidateId);
        String finalReviewStatus = finalReviewStatus(finalReview, null);
        String candidateStatus = codeTask ? "integration_blocked" : "candidate_ready";
        String consensusStatus = consensusStatus(candidateStatus, support, finalReviewStatus);
        return new ProjectResultCandidateView(
                candidateId,
                task.projectId(),
                task.id(),
                task.launchId(),
                task.title(),
                task.status(),
                true,
                task.createdByAccountId(),
                task.claimedByAccountId(),
                codeTask ? "code" : "evidence",
                candidateStatus,
                codeTask ? "missing_pr" : "not_required",
                codeTask ? "completed code task has no pull request link" : "accepted non-code task can enter candidate support",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                codeTask ? "missing" : "not_required",
                !codeTask,
                support.count(),
                support.weight(),
                SUPPORT_THRESHOLD,
                consensusStatus,
                finalReviewStatus,
                finalReview == null ? null : finalReview.reviewedCommitSha(),
                List.of(),
                null,
                task.updatedAt());
    }

    private ProjectResultCandidateView candidateWithPullRequest(
            TaskView task,
            ProjectPrLinkEntity pullRequest,
            List<ProjectCiCheckEntity> checks,
            CandidateFacts facts) {
        CiSummary ci = summarizeCi(pullRequest, checks);
        Mergeability mergeability = mergeability(pullRequest, ci);
        String candidateId = candidateId(task.id(), pullRequest.prNumber());
        SupportSummary support = facts.supports().getOrDefault(candidateId, new SupportSummary(0, 0));
        FinalReviewSummary finalReview = facts.finalReviews().get(candidateId);
        String finalReviewStatus = finalReviewStatus(finalReview, pullRequest.headSha());
        String candidateStatus = switch (mergeability.status()) {
            case "ready" -> "candidate_ready";
            case "merged" -> "merged_mainline";
            case "checking" -> "integration_checking";
            default -> "integration_blocked";
        };
        String consensusStatus = consensusStatus(candidateStatus, support, finalReviewStatus);
        return new ProjectResultCandidateView(
                candidateId,
                task.projectId(),
                task.id(),
                task.launchId(),
                task.title(),
                task.status(),
                true,
                task.createdByAccountId(),
                task.claimedByAccountId(),
                "code",
                candidateStatus,
                mergeability.status(),
                mergeability.reason(),
                pullRequest.repoUrl(),
                pullRequest.prNumber(),
                pullRequest.prUrl(),
                pullRequest.headSha(),
                pullRequest.baseBranch(),
                pullRequest.branchName(),
                pullRequest.state(),
                ci.status(),
                ci.passed(),
                support.count(),
                support.weight(),
                SUPPORT_THRESHOLD,
                consensusStatus,
                finalReviewStatus,
                finalReview == null ? null : finalReview.reviewedCommitSha(),
                checks.stream().map(this::check).toList(),
                pullRequest.lastSyncedAt(),
                task.updatedAt());
    }

    private ProjectResultCandidateView requireCandidateReady(String projectNo, String taskId, Integer prNumber, String actorAccountId) {
        ProjectResultCandidateView candidate = requireCandidate(projectNo, taskId, prNumber, actorAccountId);
        if (!"candidate_ready".equals(candidate.candidateStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ready candidates can be supported or reviewed");
        }
        return candidate;
    }

    private ProjectResultCandidateView requireCandidate(String projectNo, String taskId, Integer prNumber, String actorAccountId) {
        return listResultCandidates(projectNo, actorAccountId).stream()
                .filter(candidate -> candidate.taskId().equals(taskId))
                .filter(candidate -> prNumber == null || Objects.equals(candidate.prNumber(), prNumber))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }

    private Comparator<ProjectResultCandidateView> candidateComparator() {
        return Comparator
                .comparingInt((ProjectResultCandidateView candidate) -> candidateRank(candidate)).reversed()
                .thenComparing(ProjectResultCandidateView::taskUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProjectResultCandidateView::candidateId);
    }

    private int candidateRank(ProjectResultCandidateView candidate) {
        return switch (candidate.consensusStatus()) {
            case "final_review_required" -> 100;
            case "support_open", "candidate_ready", "consensus_ready" -> 80;
            case "integration_checking" -> 40;
            case "integration_blocked" -> 20;
            default -> 0;
        };
    }

    private List<ProjectResultCandidateView> filterByStatus(List<ProjectResultCandidateView> candidates, String status) {
        if (blank(status)) {
            return candidates;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> normalized.equals(candidate.candidateStatus()) || normalized.equals(candidate.consensusStatus()))
                .toList();
    }

    private List<ProjectResultCandidateView> applyCursor(List<ProjectResultCandidateView> candidates, String cursor) {
        String cursorCandidateId = cursorCandidateId(cursor);
        if (blank(cursorCandidateId)) {
            return candidates;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (cursorCandidateId.equals(candidates.get(index).candidateId())) {
                return candidates.subList(index + 1, candidates.size());
            }
        }
        return candidates;
    }

    private List<ProjectResultCandidateWindowItemView> applyWindowCursor(List<ProjectResultCandidateWindowItemView> items, String cursor) {
        String cursorCandidateId = cursorCandidateId(cursor);
        if (blank(cursorCandidateId)) {
            return items;
        }
        for (int index = 0; index < items.size(); index++) {
            if (cursorCandidateId.equals(items.get(index).candidateId())) {
                return items.subList(index + 1, items.size());
            }
        }
        return items;
    }

    private String cursorFor(ProjectResultCandidateView candidate) {
        return cursorFor(candidate.taskUpdatedAt(), candidate.candidateId());
    }

    private String cursorFor(Instant updatedAt, String candidateId) {
        return (updatedAt == null ? "" : updatedAt.toString()) + "|" + candidateId;
    }

    private String cursorCandidateId(String cursor) {
        if (blank(cursor)) {
            return null;
        }
        int separator = cursor.lastIndexOf('|');
        return separator >= 0 ? cursor.substring(separator + 1) : cursor;
    }

    private ProjectResultCandidateSummaryView candidateSummary(List<ProjectResultCandidateView> candidates) {
        return new ProjectResultCandidateSummaryView(
                (int) candidates.stream().filter(candidate -> "candidate_ready".equals(candidate.candidateStatus())).count(),
                (int) candidates.stream().filter(candidate -> "final_review_required".equals(candidate.consensusStatus())).count(),
                (int) candidates.stream().filter(candidate -> "integration_checking".equals(candidate.candidateStatus())).count(),
                (int) candidates.stream().filter(candidate -> "integration_blocked".equals(candidate.candidateStatus())).count(),
                (int) candidates.stream().filter(candidate -> "merged_mainline".equals(candidate.candidateStatus())).count());
    }

    private Set<String> activeSkippedCandidateIds(String projectId, String actorAccountId) {
        Set<String> skipped = new HashSet<>();
        for (Map<String, Object> skip : developmentRepository.findActiveCandidateWindowSkips(projectId, actorAccountId, Instant.now())) {
            String candidateId = text(skip.get("candidateId"));
            if (!blank(candidateId)) {
                skipped.add(candidateId);
            }
        }
        return skipped;
    }

    private ProjectResultCandidateWindowItemView candidateWindowItem(ProjectResultCandidateView candidate, String actorAccountId) {
        ActionIntent intent = actionIntent(candidate, actorAccountId);
        return new ProjectResultCandidateWindowItemView(
                candidate.candidateId(),
                candidate.taskId(),
                candidate.taskTitle(),
                candidate.resultType(),
                candidate.candidateStatus(),
                candidate.consensusStatus(),
                candidate.supportCount(),
                candidate.supportThreshold(),
                intent.reasonToAct(),
                intent.nextAction(),
                intent.score(),
                candidate.prNumber(),
                candidate.headSha(),
                candidate.taskUpdatedAt());
    }

    private ActionIntent actionIntent(ProjectResultCandidateView candidate, String actorAccountId) {
        if ("final_review_required".equals(candidate.consensusStatus())) {
            if (actorAccountId.equals(candidate.createdByAccountId()) || actorAccountId.equals(candidate.claimedByAccountId())) {
                return new ActionIntent("conflict_of_interest", "wait", 0);
            }
            return new ActionIntent("needs_final_review", "final_review", 100);
        }
        if ("support_open".equals(candidate.consensusStatus())) {
            if (developmentRepository.hasCandidateSupport(candidate.candidateId(), actorAccountId)) {
                return new ActionIntent("already_supported", "wait", 0);
            }
            int closeToThresholdBonus = Math.max(0, SUPPORT_THRESHOLD - candidate.weightedSupport()) <= 1 ? 10 : 0;
            return new ActionIntent("needs_support", "support", 80 + closeToThresholdBonus);
        }
        if ("integration_checking".equals(candidate.candidateStatus())) {
            return new ActionIntent("needs_recheck", "recheck_mergeability", 40 + invalidatedBonus(candidate));
        }
        if ("integration_blocked".equals(candidate.candidateStatus()) && fixableBlocker(candidate)) {
            return new ActionIntent("needs_repair", "create_repair_task", 20);
        }
        return new ActionIntent("no_action_available", "wait", 0);
    }

    private int invalidatedBonus(ProjectResultCandidateView candidate) {
        return "mainline changed after this candidate was checked".equals(candidate.mergeabilityReason()) ? 5 : 0;
    }

    private boolean fixableBlocker(ProjectResultCandidateView candidate) {
        return List.of("missing_pr", "blocked").contains(candidate.mergeabilityStatus());
    }

    private ProjectCandidateBlockedSummaryView blockedSummary(List<ProjectResultCandidateView> candidates) {
        Map<String, Integer> reasons = new HashMap<>();
        for (ProjectResultCandidateView candidate : candidates) {
            if (!List.of("integration_checking", "integration_blocked").contains(candidate.candidateStatus())) {
                continue;
            }
            String reason = blank(candidate.mergeabilityReason()) ? candidate.candidateStatus() : candidate.mergeabilityReason();
            reasons.put(reason, reasons.getOrDefault(reason, 0) + 1);
        }
        List<Map<String, Object>> topReasons = reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> Map.<String, Object>of("reason", entry.getKey(), "count", entry.getValue()))
                .toList();
        return new ProjectCandidateBlockedSummaryView(
                (int) candidates.stream().filter(candidate -> "integration_checking".equals(candidate.candidateStatus())).count(),
                (int) candidates.stream().filter(candidate -> "integration_blocked".equals(candidate.candidateStatus())).count(),
                topReasons);
    }

    private ProjectCandidateHistorySummaryView historySummary(List<ProjectResultCandidateView> candidates) {
        List<ProjectResultCandidateView> merged = candidates.stream()
                .filter(candidate -> "merged_mainline".equals(candidate.candidateStatus()))
                .toList();
        Instant latestMergedAt = merged.stream()
                .map(ProjectResultCandidateView::lastSyncedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ProjectCandidateHistorySummaryView(merged.size(), latestMergedAt);
    }

    private int clampLimit(Integer requested, int defaultLimit, int maxLimit) {
        if (requested == null) {
            return defaultLimit;
        }
        return Math.max(1, Math.min(requested, maxLimit));
    }

    private CandidateFacts candidateFacts(String projectId) {
        Map<String, SupportSummary> supports = new HashMap<>();
        for (Map<String, Object> support : developmentRepository.findCandidateSupports(projectId)) {
            String candidateId = text(support.get("candidateId"));
            if (blank(candidateId)) {
                continue;
            }
            SupportSummary current = supports.getOrDefault(candidateId, new SupportSummary(0, 0));
            supports.put(candidateId, new SupportSummary(current.count() + 1, current.weight() + intValue(support.get("weight"), 1)));
        }
        Map<String, FinalReviewSummary> reviews = new HashMap<>();
        for (Map<String, Object> review : developmentRepository.findCandidateFinalReviews(projectId)) {
            String candidateId = text(review.get("candidateId"));
            if (blank(candidateId)) {
                continue;
            }
            reviews.put(candidateId, new FinalReviewSummary(
                    text(review.get("decision")),
                    text(review.get("reviewedCommitSha"))));
        }
        return new CandidateFacts(supports, reviews);
    }

    private String finalReviewStatus(FinalReviewSummary review, String headSha) {
        if (review == null) {
            return "required";
        }
        if ("rejected".equals(review.decision())) {
            return "rejected";
        }
        if (!Objects.equals(blankToNull(review.reviewedCommitSha()), blankToNull(headSha))) {
            return "stale";
        }
        return "accepted".equals(review.decision()) ? "passed" : "required";
    }

    private String consensusStatus(String candidateStatus, SupportSummary support, String finalReviewStatus) {
        if (!"candidate_ready".equals(candidateStatus)) {
            return candidateStatus;
        }
        if (support.weight() < SUPPORT_THRESHOLD) {
            return "support_open";
        }
        if ("rejected".equals(finalReviewStatus)) {
            return "final_review_rejected";
        }
        if (!"passed".equals(finalReviewStatus)) {
            return "final_review_required";
        }
        return "consensus_ready";
    }

    private String candidateId(String taskId, Integer prNumber) {
        return "candidate-" + taskId + "-" + (prNumber == null ? "no-pr" : prNumber);
    }

    private List<ProjectCiCheckEntity> checksForPullRequest(List<ProjectCiCheckEntity> checks, Integer prNumber) {
        if (prNumber == null) {
            return List.of();
        }
        return checks.stream()
                .filter(check -> Objects.equals(check.prNumber(), prNumber))
                .toList();
    }

    private CiSummary summarizeCi(ProjectPrLinkEntity pullRequest, List<ProjectCiCheckEntity> checks) {
        if (checks.isEmpty()) {
            String rawStatus = firstRawText(pullRequest.rawPayload(), "ciStatus", "statusCheckRollup", "checkStatus");
            if (blank(rawStatus)) {
                return new CiSummary("missing", false);
            }
            String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
            return new CiSummary(normalized, ciSuccess(normalized));
        }
        boolean pending = checks.stream().anyMatch(check -> !completedCheck(check.status()));
        if (pending) {
            return new CiSummary("pending", false);
        }
        boolean failed = checks.stream()
                .map(ProjectCiCheckEntity::conclusion)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> !ciSuccess(value));
        return new CiSummary(failed ? "failed" : "success", !failed);
    }

    private Mergeability mergeability(ProjectPrLinkEntity pullRequest, CiSummary ci) {
        // 中文注释：PR 冲突、draft、CI 未过都会暂停候选，避免未复核代码进入主线投票。
        String state = lower(pullRequest.state());
        if ("merged".equals(state) || Boolean.TRUE.equals(rawBoolean(pullRequest.rawPayload(), "merged"))) {
            return new Mergeability("merged", "pull request already merged");
        }
        String invalidatedReason = firstRawText(pullRequest.rawPayload(), "mergeabilityInvalidatedReason");
        if (!blank(invalidatedReason)) {
            return new Mergeability("checking", "mainline changed after this candidate was checked");
        }
        if (!"open".equals(state) && !"ready".equals(state) && !"checks_passed".equals(state)) {
            return new Mergeability("blocked", "pull request is not open");
        }
        if (Boolean.TRUE.equals(rawBoolean(pullRequest.rawPayload(), "draft"))) {
            return new Mergeability("blocked", "pull request is draft");
        }
        Boolean mergeable = rawBoolean(pullRequest.rawPayload(), "mergeable");
        String mergeableState = firstRawText(pullRequest.rawPayload(), "mergeableState", "mergeable_state");
        if (Boolean.FALSE.equals(mergeable) || "dirty".equals(lower(mergeableState))) {
            return new Mergeability("blocked", "pull request has merge conflicts");
        }
        if (mergeable == null && blank(mergeableState)) {
            return new Mergeability("checking", "mergeability has not been checked");
        }
        if (!ci.passed()) {
            return new Mergeability("checking".equals(ci.status()) || "pending".equals(ci.status()) ? "checking" : "blocked",
                    "ci status is " + ci.status());
        }
        return new Mergeability("ready", "completed task has mergeable pull request and passing checks");
    }

    private boolean completedTask(TaskView task) {
        return List.of("accepted", "settled").contains(task.status());
    }

    private boolean requiresPullRequest(TaskView task) {
        // 中文注释：任务类型由结构化 evidence 决定，增长、运营等已验收结果直接进入候选支持流程。
        return task.suggestedEvidence().stream()
                .map(this::evidenceKind)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(value -> List.of("pull_request", "pr", "code_change", "repository_branch", "ci_check").contains(value));
    }

    private String evidenceKind(Map<String, Object> evidence) {
        for (String key : List.of("kind", "type", "refType")) {
            Object value = evidence.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean completedCheck(String status) {
        return "completed".equals(lower(status)) || "success".equals(lower(status));
    }

    private boolean ciSuccess(String value) {
        return List.of("success", "neutral", "skipped").contains(lower(value));
    }

    private String normalizeFinalReviewDecision(String decision) {
        String normalized = lower(decision);
        if (!List.of("accepted", "rejected").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Final review decision must be accepted or rejected");
        }
        return normalized;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return defaultValue;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Object rawValue(Map<String, Object> payload, String key) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        if (payload.containsKey(key)) {
            return payload.get(key);
        }
        Object pullRequest = payload.get("pull_request");
        if (pullRequest instanceof Map<?, ?> nested && nested.containsKey(key)) {
            return ((Map<String, Object>) nested).get(key);
        }
        return null;
    }

    private String firstRawText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = rawValue(payload, key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Boolean rawBoolean(Map<String, Object> payload, String key) {
        Object value = rawValue(payload, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private String requireProjectId(String projectNo) {
        if (blank(projectNo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        return projectRepository.findByProjectNo(projectNo.trim())
                .map(project -> project.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void requireProjectAccess(String projectId, String actorAccountId) {
        if (!organizationAuthorityService.hasProjectCapability(actorAccountId, projectId, ProjectCapability.PROJECT_PARTICIPATE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project participation required");
        }
    }

    private void requireRootProjectMaintenance(String projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (!RootProjectService.ROOT_PROJECT_ID.equals(project.id())
                && !RootProjectService.ROOT_PROJECT_NO.equals(project.projectNo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project development maintenance is a Root Project operation");
        }
    }

    private int requiredPrNumber(Integer value) {
        if (value == null || value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prNumber is required");
        }
        return value;
    }

    private String optionalUrl(String value, String field) {
        return blank(value) ? null : normalizeUrl(value, field);
    }

    private String normalizeUrl(String value, String field) {
        String normalized = required(value, field, 500);
        try {
            URI uri = new URI(normalized);
            if (uri.getScheme() == null || uri.getHost() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be http or https URL");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be valid URL", exception);
        }
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (blank(value)) {
            return null;
        }
        return required(value, "text", maxLength);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record CiSummary(String status, boolean passed) {
    }

    private record Mergeability(String status, String reason) {
    }

    private record SupportSummary(int count, int weight) {
    }

    private record FinalReviewSummary(String decision, String reviewedCommitSha) {
    }

    private record CandidateFacts(Map<String, SupportSummary> supports, Map<String, FinalReviewSummary> finalReviews) {
    }

    private record ActionIntent(String reasonToAct, String nextAction, int score) {
    }
}
