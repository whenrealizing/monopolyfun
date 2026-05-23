package com.monopolyfun.modules.identity.domain;

import java.time.Instant;
import java.util.Map;

public record IdentityVerificationChallengeEntity(
        String id,
        String accountId,
        String certifierId,
        String provider,
        String status,
        String verificationMethod,
        String challengeToken,
        Map<String, Object> context,
        Map<String, Object> instructions,
        Instant expiresAt,
        Instant completedAt,
        Instant failedAt,
        String failureReason,
        Instant createdAt
) {
}
