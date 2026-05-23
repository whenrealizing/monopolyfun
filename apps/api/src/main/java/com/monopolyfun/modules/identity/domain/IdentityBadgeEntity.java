package com.monopolyfun.modules.identity.domain;

import java.time.Instant;

public record IdentityBadgeEntity(
        String id,
        String accountId,
        String kind,
        String code,
        String label,
        String icon,
        String sourceCertifierId,
        String sourceFactId,
        int weight,
        Instant createdAt,
        Instant updatedAt
) {
}
