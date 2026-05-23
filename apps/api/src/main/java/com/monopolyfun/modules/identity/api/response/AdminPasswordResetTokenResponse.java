package com.monopolyfun.modules.identity.api.response;

import java.time.Instant;

public record AdminPasswordResetTokenResponse(
        String handle,
        String resetToken,
        Instant expiresAt
) {
}
