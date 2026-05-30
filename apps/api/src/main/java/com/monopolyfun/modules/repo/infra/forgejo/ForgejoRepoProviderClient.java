package com.monopolyfun.modules.repo.infra.forgejo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.ForgejoRepoConfig;
import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ForgejoRepoProviderClient implements RepoProviderClient {
    private final ForgejoRepoConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ForgejoRepoProviderClient(ForgejoRepoConfig config, ObjectMapper objectMapper) {
        this(config, objectMapper, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
    }

    ForgejoRepoProviderClient(ForgejoRepoConfig config, ObjectMapper objectMapper, HttpClient httpClient) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public ProvisionedRepository provisionPublicRepository(ProvisionRepositoryCommand command) {
        try {
            String path = config.usesOrganization()
                    ? "/orgs/%s/repos".formatted(config.getOrganization().trim())
                    : "/user/repos";
            JsonNode response = sendJson("POST", path, Map.of(
                    "name", command.repoName(),
                    "description", command.description(),
                    "private", false,
                    "auto_init", true,
                    "default_branch", defaultBranch()));
            String owner = text(response.path("owner"), "login");
            String repoName = text(response, "name");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("providerRepoId", text(response, "id"));
            metadata.put("createdByAccountId", command.actorAccountId());
            metadata.put("forgejoApiBaseUrl", config.getApiBaseUrl());
            if (command.projectNo() != null && !command.projectNo().isBlank()) {
                metadata.put("projectNo", command.projectNo().trim());
            }
            // 中文注释：本地 Forgejo 是默认轻量 provider，metadata 只记录可审计的仓库事实。
            return new ProvisionedRepository(
                    "forgejo",
                    repoHtmlUrl(response, owner, repoName),
                    cloneUrl(response, owner, repoName),
                    owner,
                    repoName,
                    firstNonBlank(text(response, "default_branch"), defaultBranch()),
                    text(response, "private").equals("true") ? "private" : "public",
                    Map.copyOf(metadata));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Forgejo repository provisioning failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public RepositoryAccess issueRepositoryAccess(IssueRepositoryAccessCommand command) {
        requireConfiguredCredentials();
        // 中文注释：Forgejo 本地开发使用固定服务账号凭据，交付会话仍保持短租约语义。
        return new RepositoryAccess(
                firstNonBlank(config.getAccessToken(), config.getPassword()),
                Instant.now().plus(12, ChronoUnit.HOURS),
                Map.of(
                        "repoOwner", command.repoOwner(),
                        "repoName", command.repoName(),
                        "orderNo", command.orderNo(),
                        "headBranch", command.headBranch(),
                        "provider", "forgejo"));
    }

    @Override
    public PullRequestInspection inspectPullRequest(InspectPullRequestCommand command) {
        PullRequestRef ref = parsePullRequestUrl(command.prUrl());
        if (!command.repoOwner().equals(ref.owner()) || !command.repoName().equals(ref.repoName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request repository does not match current delivery session");
        }
        try {
            JsonNode pull = sendJson("GET", "/repos/%s/%s/pulls/%s".formatted(ref.owner(), ref.repoName(), ref.number()), null);
            String headCommit = firstNonBlank(text(pull.path("head"), "sha"), text(pull.path("head"), "ref"));
            if (command.expectedHeadCommit() != null && !command.expectedHeadCommit().isBlank()
                    && !command.expectedHeadCommit().equals(headCommit)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request head commit mismatch");
            }
            String state = lower(text(pull, "state"));
            String diffSummary = "changed_files=%s additions=%s deletions=%s".formatted(
                    safeInt(pull, "changed_files"),
                    safeInt(pull, "additions"),
                    safeInt(pull, "deletions"));
            return new PullRequestInspection(
                    repoUrl(ref.owner(), ref.repoName()),
                    firstNonBlank(text(pull, "html_url"), command.prUrl()),
                    headCommit,
                    text(pull.path("base"), "ref"),
                    text(pull.path("head"), "ref"),
                    state,
                    "closed".equals(state) && pull.path("merged").asBoolean(false),
                    pull.path("draft").asBoolean(false),
                    "not_required",
                    "NOT_REQUIRED",
                    diffSummary,
                    Map.of(
                            "prNumber", ref.number(),
                            "provider", "forgejo"));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Forgejo pull request inspection failed: " + exception.getMessage(), exception);
        }
    }

    private JsonNode sendJson(String method, String path, Map<String, Object> body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiUrl(path))
                .header("Accept", "application/json");
        applyAuthorization(builder);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(response.body());
        if (response.statusCode() >= 400) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, forgejoErrorMessage(json));
        }
        return json;
    }

    private void applyAuthorization(HttpRequest.Builder builder) {
        if (config.getAccessToken() != null && !config.getAccessToken().isBlank()) {
            builder.header("Authorization", "token " + config.getAccessToken().trim());
            return;
        }
        requireConfiguredCredentials();
        String basic = config.getUsername().trim() + ":" + config.getPassword().trim();
        builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
    }

    private void requireConfiguredCredentials() {
        if (config.getUsername() == null || config.getUsername().isBlank()
                || config.getPassword() == null || config.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Forgejo repository service credentials are not configured");
        }
    }

    private PullRequestRef parsePullRequestUrl(String value) {
        try {
            URI uri = URI.create(value);
            String[] parts = uri.getPath().replaceFirst("^/+", "").split("/");
            if (parts.length < 4 || (!"pulls".equals(parts[2]) && !"pull".equals(parts[2]))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request URL must be a Forgejo pull request");
            }
            return new PullRequestRef(parts[0], parts[1].replaceFirst("\\.git$", ""), parts[3]);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pull request URL must be valid");
        }
    }

    private URI apiUrl(String path) {
        return URI.create(trimRight(config.getApiBaseUrl(), "/") + path);
    }

    private String repoHtmlUrl(JsonNode response, String owner, String repoName) {
        return firstNonBlank(text(response, "html_url"), repoUrl(owner, repoName));
    }

    private String cloneUrl(JsonNode response, String owner, String repoName) {
        return firstNonBlank(text(response, "clone_url"), repoUrl(owner, repoName) + ".git");
    }

    private String repoUrl(String owner, String repoName) {
        return trimRight(config.getWebBaseUrl(), "/") + "/" + owner + "/" + repoName;
    }

    private String defaultBranch() {
        return firstNonBlank(config.getDefaultBranch(), "main");
    }

    private String forgejoErrorMessage(JsonNode json) {
        String message = firstNonBlank(text(json, "message"), text(json, "error"));
        return message.isBlank() ? "Forgejo API request failed" : "Forgejo API request failed: " + message;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int safeInt(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isNumber() ? 0 : value.asInt();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimRight(String value, String suffix) {
        String text = value == null ? "" : value.trim();
        while (text.endsWith(suffix)) {
            text = text.substring(0, text.length() - suffix.length());
        }
        return text;
    }

    private record PullRequestRef(String owner, String repoName, String number) {
    }
}
