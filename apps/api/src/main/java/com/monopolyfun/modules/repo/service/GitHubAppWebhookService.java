package com.monopolyfun.modules.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.GitHubAppConfig;
import com.monopolyfun.modules.repo.api.response.GitHubAppWebhookResponse;
import com.monopolyfun.modules.repo.domain.RepoDeliverySessionEntity;
import com.monopolyfun.modules.repo.infra.GitHubWebhookDeliveryRepository;
import com.monopolyfun.modules.repo.infra.RepoDeliverySessionRepository;
import com.monopolyfun.modules.work.infra.WorkRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class GitHubAppWebhookService {
    private static final Duration CLAIM_LEASE = Duration.ofHours(3);
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubAppConfig config;
    private final ObjectMapper objectMapper;
    private final RepoDeliverySessionRepository repoDeliverySessionRepository;
    private final GitHubWebhookDeliveryRepository webhookDeliveryRepository;
    private final WorkRepository workRepository;

    public GitHubAppWebhookService(
            GitHubAppConfig config,
            ObjectMapper objectMapper,
            RepoDeliverySessionRepository repoDeliverySessionRepository,
            GitHubWebhookDeliveryRepository webhookDeliveryRepository,
            WorkRepository workRepository) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.repoDeliverySessionRepository = repoDeliverySessionRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.workRepository = workRepository;
    }

    private static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify GitHub webhook signature", exception);
        }
    }

    private static String branchFromRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
    }

    private static String ciStatus(String status, String conclusion) {
        if (conclusion != null && !conclusion.isBlank()) {
            return conclusion.trim().toLowerCase(Locale.ROOT);
        }
        return status == null ? null : status.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeEvent(String event) {
        return event == null ? "" : event.trim();
    }

    private static String safeDeliveryId(String deliveryId) {
        return deliveryId == null ? "" : deliveryId.trim();
    }

    private static String requireDeliveryId(String deliveryId) {
        String value = safeDeliveryId(deliveryId);
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub delivery id is missing");
        }
        return value;
    }

    private static String textAt(JsonNode node, String pointer) {
        JsonNode value = node == null ? null : node.at(pointer);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    public GitHubAppWebhookResponse handle(String event, String deliveryId, String signature, String payload) {
        verifySignature(signature, payload);
        String safeDeliveryId = requireDeliveryId(deliveryId);
        ProgressSignal signal = parseSignal(event, payload);
        if (signal == null || signal.repoUrl().isBlank() || signal.headBranch().isBlank()) {
            return new GitHubAppWebhookResponse("ignored", safeEvent(event), safeDeliveryId, 0);
        }
        RepoDeliverySessionEntity session = repoDeliverySessionRepository
                .findActiveByRepoUrlAndHeadBranch(signal.repoUrl(), signal.headBranch())
                .orElse(null);
        if (session == null) {
            return new GitHubAppWebhookResponse("ignored", safeEvent(event), safeDeliveryId, 0);
        }

        Instant now = Instant.now();
        Map<String, Object> deliveryMetadata = webhookDeliveryMetadata(signal);
        if (!webhookDeliveryRepository.recordOnce(
                safeDeliveryId,
                safeEvent(event),
                signal.repoUrl(),
                signal.headBranch(),
                session.id(),
                deliveryMetadata,
                now)) {
            return new GitHubAppWebhookResponse("duplicate", safeEvent(event), safeDeliveryId, 0);
        }

        Instant claimExpiresAt = now.plus(CLAIM_LEASE);
        int renewedClaims = workRepository.renewClaimLease(
                session.issuedToAccountId(),
                "wb-delivery-result-" + session.orderNo(),
                claimExpiresAt,
                now);
        repoDeliverySessionRepository.save(session.observeWebhookProgress(
                signal.prUrl(),
                signal.headCommit(),
                signal.ciStatus(),
                metadataWithWebhook(session, signal, event, safeDeliveryId, claimExpiresAt, now, renewedClaims),
                now));
        return new GitHubAppWebhookResponse("observed", safeEvent(event), safeDeliveryId, renewedClaims);
    }

    private Map<String, Object> webhookDeliveryMetadata(ProgressSignal signal) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "headCommit", signal.headCommit());
        putIfPresent(metadata, "prUrl", signal.prUrl());
        putIfPresent(metadata, "ciStatus", signal.ciStatus());
        return Map.copyOf(metadata);
    }

    private Map<String, Object> metadataWithWebhook(
            RepoDeliverySessionEntity session,
            ProgressSignal signal,
            String event,
            String deliveryId,
            Instant claimExpiresAt,
            Instant now,
            int renewedClaims) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(session.metadata() == null ? Map.of() : session.metadata());
        LinkedHashMap<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("event", safeEvent(event));
        webhook.put("deliveryId", safeDeliveryId(deliveryId));
        webhook.put("repoUrl", signal.repoUrl());
        webhook.put("headBranch", signal.headBranch());
        putIfPresent(webhook, "headCommit", signal.headCommit());
        putIfPresent(webhook, "prUrl", signal.prUrl());
        putIfPresent(webhook, "ciStatus", signal.ciStatus());
        webhook.put("claimExpiresAt", claimExpiresAt.toString());
        webhook.put("renewedClaims", renewedClaims);
        webhook.put("observedAt", now.toString());
        metadata.put("lastGitHubWebhook", Map.copyOf(webhook));
        return Map.copyOf(metadata);
    }

    private ProgressSignal parseSignal(String event, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            return switch (safeEvent(event)) {
                case "push" -> parsePush(root);
                case "pull_request" -> parsePullRequest(root);
                case "check_suite" -> parseCheckSuite(root);
                case "check_run" -> parseCheckRun(root);
                default -> null;
            };
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GitHub webhook payload", exception);
        }
    }

    private ProgressSignal parsePush(JsonNode root) {
        String branch = branchFromRef(textAt(root, "/ref"));
        return new ProgressSignal(
                textAt(root, "/repository/html_url"),
                branch,
                textAt(root, "/after"),
                null,
                null);
    }

    private ProgressSignal parsePullRequest(JsonNode root) {
        JsonNode pullRequest = root.path("pull_request");
        return new ProgressSignal(
                firstNonBlank(textAt(pullRequest, "/base/repo/html_url"), textAt(root, "/repository/html_url")),
                textAt(pullRequest, "/head/ref"),
                textAt(pullRequest, "/head/sha"),
                textAt(pullRequest, "/html_url"),
                null);
    }

    private ProgressSignal parseCheckSuite(JsonNode root) {
        JsonNode checkSuite = root.path("check_suite");
        return new ProgressSignal(
                textAt(root, "/repository/html_url"),
                textAt(checkSuite, "/head_branch"),
                textAt(checkSuite, "/head_sha"),
                null,
                ciStatus(textAt(checkSuite, "/status"), textAt(checkSuite, "/conclusion")));
    }

    private ProgressSignal parseCheckRun(JsonNode root) {
        JsonNode checkRun = root.path("check_run");
        String branch = firstNonBlank(
                textAt(checkRun, "/check_suite/head_branch"),
                textAt(checkRun, "/pull_requests/0/head/ref"));
        String sha = firstNonBlank(textAt(checkRun, "/head_sha"), textAt(checkRun, "/check_suite/head_sha"));
        return new ProgressSignal(
                textAt(root, "/repository/html_url"),
                branch,
                sha,
                null,
                ciStatus(textAt(checkRun, "/status"), textAt(checkRun, "/conclusion")));
    }

    private void verifySignature(String signature, String payload) {
        String secret = config.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GitHub webhook secret is missing");
        }
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub webhook signature is missing");
        }
        String expected = SIGNATURE_PREFIX + hmacSha256(secret, payload == null ? "" : payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "GitHub webhook signature is invalid");
        }
    }

    private record ProgressSignal(
            String repoUrl,
            String headBranch,
            String headCommit,
            String prUrl,
            String ciStatus
    ) {
    }
}
