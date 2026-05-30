package com.monopolyfun.modules.repo.service;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.ProofLink;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.repo.api.request.FinalizeRepoProofRequest;
import com.monopolyfun.modules.repo.api.request.ReportPullRequestProofRequest;
import com.monopolyfun.modules.repo.api.response.RepoDeliverySessionResponse;
import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;
import com.monopolyfun.modules.repo.infra.RepoDeliverySessionRepository;
import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import com.monopolyfun.modules.work.api.request.SubmitWorkReceiptRequest;
import com.monopolyfun.modules.work.service.WorkCommandService;
import com.monopolyfun.shared.command.CommandReceipt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RepoDeliveryService {
    private static final int MAX_SESSIONS_PER_ACCOUNT_PER_HOUR = 6;
    private static final int MAX_SESSIONS_PER_PROJECT_PER_DAY = 20;

    private final OrderRepository orderRepository;
    private final ProjectRepository projectRepository;
    private final RepoDeliverySessionRepository repoDeliverySessionRepository;
    private final RepoProviderClient repoProviderClient;
    private final RepoProofVerifier repoProofVerifier;
    private final WorkCommandService workCommandService;

    public RepoDeliveryService(
            OrderRepository orderRepository,
            ProjectRepository projectRepository,
            RepoDeliverySessionRepository repoDeliverySessionRepository,
            RepoProviderClient repoProviderClient,
            RepoProofVerifier repoProofVerifier,
            WorkCommandService workCommandService) {
        this.orderRepository = orderRepository;
        this.projectRepository = projectRepository;
        this.repoDeliverySessionRepository = repoDeliverySessionRepository;
        this.repoProviderClient = repoProviderClient;
        this.repoProofVerifier = repoProofVerifier;
        this.workCommandService = workCommandService;
    }

    public RepoDeliverySessionResponse createDeliverySession(String actorAccountId, String orderNo, String projectNo, String runtime) {
        OrderEntity order = requireOrder(orderNo);
        if (!actorAccountId.equals(order.fulfillerAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only order fulfiller can start project code delivery");
        }
        if (order.postKind() != com.monopolyfun.modules.post.domain.PostKind.PROJECT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository delivery sessions are only available for project orders");
        }
        ProjectEntity project = requireProject(order, projectNo);
        String repoUrl = requireProjectRepoUrl(project);
        String repoManagementMode = metadataText(project.metadata(), "repoManagementMode");
        if (!"platform_managed_repo".equals(repoManagementMode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current project does not use platform managed repository delivery");
        }
        RepoCoordinates repo = parseRepoUrl(repoUrl);
        String repoProvider = firstNonBlank(metadataText(project.metadata(), "repoProvider"), repo.provider());
        String headBranch = buildHeadBranch(order.orderNo(), actorAccountId);
        RepoDeliverySessionEntity existing = repoDeliverySessionRepository.findActiveByOrderNo(order.orderNo()).orElse(null);
        if (existing != null) {
            if (!actorAccountId.equals(existing.issuedToAccountId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repository delivery session belongs to another account");
            }
            return toResponse(refreshDeliverySessionAccess(existing, repo, actorAccountId));
        }
        enforceRepoDeliveryQuota(actorAccountId, project.projectNo());
        RepoProviderClient.RepositoryAccess access = repoProviderClient.issueRepositoryAccess(
                new RepoProviderClient.IssueRepositoryAccessCommand(repo.owner(), repo.name(), order.orderNo(), headBranch, actorAccountId));
        String cloneUrl = safeCloneUrl(repo);
        Instant now = Instant.now();
        RepoDeliverySessionEntity session = new RepoDeliverySessionEntity(
                "repo-delivery-" + UUID.randomUUID(),
                project.projectNo(),
                order.orderNo(),
                repoProvider,
                repoUrl,
                cloneUrl,
                defaultBranch(project),
                headBranch,
                null,
                null,
                null,
                "issued",
                runtime == null || runtime.isBlank() ? "openclaw" : runtime.trim(),
                actorAccountId,
                "secret://repo-delivery-sessions/%s/%s-token".formatted(order.orderNo(), repoProvider),
                access.expiresAt(),
                Map.of(
                        "repoOwner", repo.owner(),
                        "repoName", repo.name(),
                        "projectId", project.id(),
                        "projectNo", project.projectNo(),
                        "orderId", order.id()),
                now,
                now);
        return toResponse(repoDeliverySessionRepository.save(session));
    }

    public RepoDeliverySessionResponse reportPullRequest(
            String actorAccountId,
            String sessionId,
            ReportPullRequestProofRequest request) {
        RepoDeliverySessionEntity session = requireOwnedSession(sessionId, actorAccountId);
        requireActiveSession(session);
        if (session.prUrl() != null && !session.prUrl().isBlank() && !session.prUrl().equals(request.prUrl())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository delivery session already has an active pull request");
        }
        RepoCoordinates repo = parseRepoUrl(session.repoUrl());
        RepoProviderClient.PullRequestInspection inspection = repoProofVerifier.inspectPullRequest(
                session, repo.owner(), repo.name(), request.prUrl(), request.headCommit());
        enforcePullRequestPolicy(session, inspection);
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(session.metadata());
        metadata.put("statusCheckRollup", inspection.statusCheckRollup());
        metadata.put("diffSummary", firstNonBlank(request.diffSummary(), inspection.diffSummary()));
        metadata.put("prState", inspection.state());
        metadata.put("reportedAt", now.toString());
        RepoDeliverySessionEntity updated = session.reportPullRequest(
                inspection.prUrl(),
                inspection.headCommit(),
                inspection.ciStatus(),
                Map.copyOf(metadata),
                now);
        return toResponse(repoDeliverySessionRepository.save(updated));
    }

    public CommandReceipt finalizeProof(
            String actorAccountId,
            String sessionId,
            FinalizeRepoProofRequest request) {
        RepoDeliverySessionEntity session = requireOwnedSession(sessionId, actorAccountId);
        if (session.prUrl() == null || session.prUrl().isBlank() || session.headCommit() == null || session.headCommit().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository delivery session has no reported pull request");
        }
        RepoCoordinates repo = parseRepoUrl(session.repoUrl());
        RepoProviderClient.PullRequestInspection inspection = repoProofVerifier.inspectPullRequest(
                session, repo.owner(), repo.name(), session.prUrl(), session.headCommit());
        OrderEntity order = requireOrder(session.orderNo());
        List<String> criteriaRefs = request.criteriaRefs() == null || request.criteriaRefs().isEmpty()
                ? order.acceptanceCriteriaSnapshot()
                : request.criteriaRefs();
        LinkedHashMap<String, Object> prSecurity = new LinkedHashMap<>();
        prSecurity.put("repoUrl", session.repoUrl());
        prSecurity.put("prUrl", session.prUrl());
        prSecurity.put("commitSha", session.headCommit());
        prSecurity.put("ciStatus", inspection.ciStatus());
        prSecurity.put("statusCheckRollup", inspection.statusCheckRollup());
        prSecurity.put("securityPolicyResult", "passed");
        prSecurity.put("orderNo", session.orderNo());
        prSecurity.put("diffSummary", firstNonBlank(stringValue(session.metadata().get("diffSummary")), inspection.diffSummary()));

        LinkedHashMap<String, Object> proofPayload = new LinkedHashMap<>();
        proofPayload.put("deliveryType", "project_code_pr");
        proofPayload.put("repoDeliverySessionId", session.id());
        proofPayload.put("repoDelivery", Map.of(
                "provider", session.provider(),
                "runtime", session.runtime(),
                "baseBranch", session.baseBranch(),
                "headBranch", session.headBranch()));
        proofPayload.put("prSecurity", prSecurity);
        proofPayload.put("criteriaRefs", criteriaRefs);

        List<ProofLink> links = List.of(
                new ProofLink("Repository", session.repoUrl()),
                new ProofLink("Pull Request", session.prUrl()),
                new ProofLink("Commit", session.repoUrl().replaceFirst("\\.git$", "") + "/commit/" + session.headCommit()));
        List<String> evidenceRefs = request.evidenceRefs() == null || request.evidenceRefs().isEmpty()
                ? List.of(session.repoUrl(), session.prUrl(), session.headCommit())
                : request.evidenceRefs();
        // 中文注释：仓库交付最终证明写入 Project WorkRun 工具链，由 Work receipt 统一推进交付事实。
        CommandReceipt receipt = workCommandService.submitReceipt("wb-delivery-result-" + session.orderNo(), new SubmitWorkReceiptRequest(
                actorAccountId,
                request.summary(),
                proofPayload,
                Map.of("repoDeliverySessionId", session.id(), "executionMode", "agent"),
                evidenceRefs,
                List.of("repo_delivery_session:" + session.id()),
                List.of(),
                links,
                request.artifacts(),
                session.runtime()));

        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(session.metadata());
        metadata.put("proofReceiptStatus", receipt.status());
        metadata.put("proofSubmittedAt", now.toString());
        repoDeliverySessionRepository.save(session.markProofSubmitted(Map.copyOf(metadata), now));
        return receipt;
    }

    private RepoDeliverySessionEntity requireOwnedSession(String sessionId, String actorAccountId) {
        RepoDeliverySessionEntity session = repoDeliverySessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository delivery session not found"));
        if (!actorAccountId.equals(session.issuedToAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repository delivery session belongs to another account");
        }
        return session;
    }

    private RepoDeliverySessionEntity refreshDeliverySessionAccess(
            RepoDeliverySessionEntity session,
            RepoCoordinates repo,
            String actorAccountId) {
        if (session.expiresAt() != null && session.expiresAt().isAfter(Instant.now().plusSeconds(300))) {
            return session;
        }
        RepoProviderClient.RepositoryAccess access = repoProviderClient.issueRepositoryAccess(
                new RepoProviderClient.IssueRepositoryAccessCommand(repo.owner(), repo.name(), session.orderNo(), session.headBranch(), actorAccountId));
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(session.metadata());
        metadata.put("accessRefreshedAt", now.toString());
        // 中文注释：复用同一个订单会话，只刷新短期 token，避免一个普通任务被重复开多个 PR。
        RepoDeliverySessionEntity refreshed = new RepoDeliverySessionEntity(
                session.id(),
                session.projectNo(),
                session.orderNo(),
                session.provider(),
                session.repoUrl(),
                safeCloneUrl(repo),
                session.baseBranch(),
                session.headBranch(),
                session.prUrl(),
                session.headCommit(),
                session.ciStatus(),
                session.status(),
                session.runtime(),
                session.issuedToAccountId(),
                session.tokenSecretRef(),
                access.expiresAt(),
                Map.copyOf(metadata),
                session.createdAt(),
                now);
        return repoDeliverySessionRepository.save(refreshed);
    }

    private void enforceRepoDeliveryQuota(String actorAccountId, String projectNo) {
        Instant now = Instant.now();
        int actorCount = repoDeliverySessionRepository.countCreatedByAccountSince(actorAccountId, now.minusSeconds(3600));
        if (actorCount >= MAX_SESSIONS_PER_ACCOUNT_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Repository delivery hourly limit reached for this account");
        }
        int projectCount = repoDeliverySessionRepository.countCreatedByProjectSince(projectNo, now.minusSeconds(86_400));
        if (projectCount >= MAX_SESSIONS_PER_PROJECT_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Repository delivery daily limit reached for this project");
        }
    }

    private void requireActiveSession(RepoDeliverySessionEntity session) {
        if (session.expiresAt() != null && session.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository delivery session expired");
        }
        if (!List.of("issued", "pr_reported", "progress_observed", "proof_submitted").contains(session.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository delivery session is closed");
        }
    }

    private void enforcePullRequestPolicy(
            RepoDeliverySessionEntity session,
            RepoProviderClient.PullRequestInspection inspection) {
        if (!session.baseBranch().equals(inspection.baseBranch())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request base branch does not match delivery session");
        }
        if (!session.headBranch().equals(inspection.headBranch())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request head branch does not match delivery session");
        }
    }

    private ProjectEntity requireProject(OrderEntity order, String expectedProjectNo) {
        ProjectEntity project = projectRepository.findById(order.postId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (expectedProjectNo != null && !expectedProjectNo.isBlank() && !expectedProjectNo.trim().equals(project.projectNo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project number does not match current order");
        }
        return project;
    }

    private OrderEntity requireOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private String requireProjectRepoUrl(ProjectEntity project) {
        Object linksValue = project.metadata() == null ? null : project.metadata().get("referenceLinks");
        if (linksValue instanceof List<?> links && !links.isEmpty() && links.getFirst() != null && !String.valueOf(links.getFirst()).isBlank()) {
            return String.valueOf(links.getFirst()).trim();
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Project repository URL is missing");
    }

    private String defaultBranch(ProjectEntity project) {
        return firstNonBlank(metadataText(project.metadata(), "repoDefaultBranch"), metadataText(project.metadata(), "defaultBranch"), "main");
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        return stringValue(metadata.get(key));
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String buildHeadBranch(String orderNo, String actorAccountId) {
        String handle = actorAccountId == null ? "worker" : actorAccountId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return "mf/%s-%s".formatted(orderNo.toLowerCase(Locale.ROOT), handle);
    }

    private RepoCoordinates parseRepoUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String[] parts = uri.getPath().replaceFirst("^/+", "").split("/");
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme)
                    || uri.getHost() == null
                    || parts.length < 2
                    || parts[0].isBlank()
                    || parts[1].isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Project repository must be an http(s) Git repository");
            }
            return new RepoCoordinates(providerFromHost(uri.getHost()), uri, parts[0], parts[1].replaceFirst("\\.git$", ""));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project repository URL is invalid");
        }
    }

    private String safeCloneUrl(RepoCoordinates repo) {
        // 中文注释：delivery session 只保存无凭据仓库地址，provider 凭据由执行时通道按需签发。
        String port = repo.uri().getPort() < 0 ? "" : ":" + repo.uri().getPort();
        return "%s://%s%s/%s/%s.git".formatted(repo.uri().getScheme(), repo.uri().getHost(), port, repo.owner(), repo.name());
    }

    private String providerFromHost(String host) {
        // 中文注释：仓库交付围绕本地 Forgejo 语义建模，远端 host 只影响 URL 展示和 clone 地址。
        return "forgejo";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private RepoDeliverySessionResponse toResponse(RepoDeliverySessionEntity session) {
        return new RepoDeliverySessionResponse(
                session.id(),
                session.projectNo(),
                session.orderNo(),
                session.repoUrl(),
                session.provider(),
                session.baseBranch(),
                session.headBranch(),
                session.prUrl(),
                session.headCommit(),
                session.ciStatus(),
                session.status(),
                session.runtime(),
                session.tokenSecretRef(),
                session.expiresAt(),
                session.metadata());
    }

    private record RepoCoordinates(String provider, java.net.URI uri, String owner, String name) {
    }
}
