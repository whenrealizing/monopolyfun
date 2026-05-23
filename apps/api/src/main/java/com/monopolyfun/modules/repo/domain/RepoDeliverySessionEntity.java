package com.monopolyfun.modules.repo.domain;

import java.time.Instant;
import java.util.Map;

public record RepoDeliverySessionEntity(
        String id,
        String projectNo,
        String orderNo,
        String provider,
        String repoUrl,
        String cloneUrl,
        String baseBranch,
        String headBranch,
        String prUrl,
        String headCommit,
        String ciStatus,
        String status,
        String runtime,
        String issuedToAccountId,
        String tokenSecretRef,
        Instant expiresAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    public RepoDeliverySessionEntity reportPullRequest(
            String nextPrUrl,
            String nextHeadCommit,
            String nextCiStatus,
            Map<String, Object> nextMetadata,
            Instant now) {
        return new RepoDeliverySessionEntity(
                id,
                projectNo,
                orderNo,
                provider,
                repoUrl,
                cloneUrl,
                baseBranch,
                headBranch,
                nextPrUrl,
                nextHeadCommit,
                nextCiStatus,
                "pr_reported",
                runtime,
                issuedToAccountId,
                tokenSecretRef,
                expiresAt,
                nextMetadata,
                createdAt,
                now);
    }

    public RepoDeliverySessionEntity markProofSubmitted(Map<String, Object> nextMetadata, Instant now) {
        return new RepoDeliverySessionEntity(
                id,
                projectNo,
                orderNo,
                provider,
                repoUrl,
                cloneUrl,
                baseBranch,
                headBranch,
                prUrl,
                headCommit,
                ciStatus,
                "proof_submitted",
                runtime,
                issuedToAccountId,
                tokenSecretRef,
                expiresAt,
                nextMetadata,
                createdAt,
                now);
    }

    public RepoDeliverySessionEntity observeWebhookProgress(
            String nextPrUrl,
            String nextHeadCommit,
            String nextCiStatus,
            Map<String, Object> nextMetadata,
            Instant now) {
        return new RepoDeliverySessionEntity(
                id,
                projectNo,
                orderNo,
                provider,
                repoUrl,
                cloneUrl,
                baseBranch,
                headBranch,
                firstNonBlank(nextPrUrl, prUrl),
                firstNonBlank(nextHeadCommit, headCommit),
                firstNonBlank(nextCiStatus, ciStatus),
                "progress_observed",
                runtime,
                issuedToAccountId,
                tokenSecretRef,
                expiresAt,
                nextMetadata,
                createdAt,
                now);
    }
}
