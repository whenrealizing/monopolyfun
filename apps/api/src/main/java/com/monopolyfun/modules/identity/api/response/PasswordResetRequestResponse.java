package com.monopolyfun.modules.identity.api.response;

import java.time.Instant;

public record PasswordResetRequestResponse(
        String handle,
        Instant requestedAt
) {
}
