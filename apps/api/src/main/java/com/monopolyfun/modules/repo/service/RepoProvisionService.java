package com.monopolyfun.modules.repo.service;

import com.monopolyfun.modules.repo.api.response.ProjectRepoProvisionResponse;
import com.monopolyfun.modules.repo.domain.RepoProvisionSessionEntity;
import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import com.monopolyfun.modules.repo.infra.RepoProvisionSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RepoProvisionService {
    private static final DateTimeFormatter REPO_NAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final RepoProviderClient repoProviderClient;
    private final RepoProvisionSessionRepository repoProvisionSessionRepository;

    public RepoProvisionService(
            RepoProviderClient repoProviderClient,
            RepoProvisionSessionRepository repoProvisionSessionRepository) {
        this.repoProviderClient = repoProviderClient;
        this.repoProvisionSessionRepository = repoProvisionSessionRepository;
    }

    public ProjectRepoProvisionResponse provisionPublicProjectRepo(String actorAccountId, String titleHint, String goal) {
        RepoProvisionSessionEntity session = provisionSession(actorAccountId, null, titleHint, goal);
        return toResponse(session);
    }

    public ResolvedProjectRepo resolveProjectRepo(
            String actorAccountId,
            String projectNo,
            String titleHint,
            String goal,
            String provisionSessionId) {
        // 中文注释：项目发布入口统一绑定平台仓库，外部 URL 输入路径收口到更新后的托管事实源。
        if (provisionSessionId != null && !provisionSessionId.isBlank()) {
            RepoProvisionSessionEntity session = bindProvisionSession(requireOwnedSession(provisionSessionId, actorAccountId), projectNo);
            return new ResolvedProjectRepo(List.of(session.repoUrl()), "platform_managed_repo", session.id());
        }

        RepoProvisionSessionEntity provisioned = provisionSession(actorAccountId, projectNo, titleHint, goal);
        return new ResolvedProjectRepo(List.of(provisioned.repoUrl()), "platform_managed_repo", provisioned.id());
    }

    private RepoProvisionSessionEntity provisionSession(String actorAccountId, String projectNo, String titleHint, String goal) {
        String repoName = generateRepoName(projectNo, titleHint, goal);
        String description = buildDescription(goal);
        // 中文注释：模板选择收口到平台配置，用户请求只决定业务内容和生成出的目标仓库名。
        RepoProviderClient.ProvisionedRepository repo = repoProviderClient.provisionPublicRepository(
                new RepoProviderClient.ProvisionRepositoryCommand(repoName, description, projectNo, actorAccountId));
        Instant now = Instant.now();
        RepoProvisionSessionEntity session = new RepoProvisionSessionEntity(
                "repo-prov-" + UUID.randomUUID(),
                projectNo,
                repo.provider(),
                repo.repoUrl(),
                repo.cloneUrl(),
                repo.repoOwner(),
                repo.repoName(),
                repo.defaultBranch(),
                repo.visibility(),
                projectNo == null || projectNo.isBlank() ? "provisioned" : "bound",
                actorAccountId,
                new LinkedHashMap<>(repo.metadata()),
                now,
                now);
        return repoProvisionSessionRepository.save(session);
    }

    private RepoProvisionSessionEntity bindProvisionSession(RepoProvisionSessionEntity session, String projectNo) {
        Instant now = Instant.now();
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(session.metadata());
        metadata.put("projectNo", projectNo);
        return repoProvisionSessionRepository.save(session.bindProject(projectNo, Map.copyOf(metadata), now));
    }

    private RepoProvisionSessionEntity requireOwnedSession(String sessionId, String actorAccountId) {
        RepoProvisionSessionEntity session = repoProvisionSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository provision session not found"));
        if (!actorAccountId.equals(session.createdByAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repository provision session belongs to another account");
        }
        return session;
    }

    private String generateRepoName(String projectNo, String titleHint, String goal) {
        String base = firstNonBlank(projectNo, titleHint, goal, "project");
        String slug = base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.isBlank()) {
            slug = "project";
        }
        slug = slug.length() > 32 ? slug.substring(0, 32).replaceAll("-+$", "") : slug;
        return "mf-%s-%s".formatted(REPO_NAME_TS.format(Instant.now()), slug);
    }

    private String buildDescription(String goal) {
        String text = firstNonBlank(goal, "MonopolyFun managed project repository");
        return text.length() > 160 ? text.substring(0, 160) : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private ProjectRepoProvisionResponse toResponse(RepoProvisionSessionEntity session) {
        return new ProjectRepoProvisionResponse(
                session.id(),
                session.repoUrl(),
                session.cloneUrl(),
                session.provider(),
                session.repoOwner(),
                session.repoName(),
                session.defaultBranch(),
                session.visibility());
    }

    public record ResolvedProjectRepo(
            List<String> referenceLinks,
            String repoManagementMode,
            String provisionSessionId
    ) {
    }
}
