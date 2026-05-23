package com.monopolyfun.modules.identity.domain;

import java.time.Instant;

public record PasswordResetTokenEntity(
        String id,
        String accountId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt
) {
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public PasswordResetTokenEntity markUsed(Instant at) {
        return new PasswordResetTokenEntity(id, accountId, tokenHash, expiresAt, at, createdAt);
    }
}
