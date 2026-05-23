package com.monopolyfun.modules.identity.domain;

import java.time.Instant;
import java.util.Map;

public record OAuthIdentityEntity(
        String id,
        String provider,
        String externalUserId,
        String accountId,
        String externalLogin,
        Map<String, Object> payload,
        Instant createdAt,
        Instant updatedAt
) {
}
