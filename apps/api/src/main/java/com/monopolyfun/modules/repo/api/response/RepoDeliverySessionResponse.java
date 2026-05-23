package com.monopolyfun.modules.repo.api.response;

import java.time.Instant;
import java.util.Map;

public record RepoDeliverySessionResponse(
        String deliverySessionId,
        String projectNo,
        String orderNo,
        String repoUrl,
        String provider,
        String baseBranch,
        String headBranch,
        String prUrl,
        String headCommit,
        String ciStatus,
        String status,
        String runtime,
        String tokenSecretRef,
        Instant expiresAt,
        Map<String, Object> metadata
) {
}
