package com.monopolyfun.modules.identity.domain;

import java.time.Instant;
import java.util.Map;

public record IdentityFactEntity(
        String id,
        String accountId,
        String challengeId,
        String certifierId,
        String provider,
        String factType,
        String verificationMethod,
        String status,
        String platformUserId,
        Map<String, Object> payload,
        Instant verifiedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
