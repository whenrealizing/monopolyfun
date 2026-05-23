package com.monopolyfun.modules.identity.domain;

import java.time.Instant;

public record OAuthStateEntity(
        String id,
        String provider,
        String stateToken,
        String returnTo,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt
) {
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public OAuthStateEntity markUsed(Instant at) {
        return new OAuthStateEntity(id, provider, stateToken, returnTo, expiresAt, at, createdAt);
    }
}
