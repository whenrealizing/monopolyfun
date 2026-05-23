package com.monopolyfun.modules.identity.api.response;

import com.monopolyfun.modules.identity.service.view.AccountSummary;

import java.time.Instant;

public record AuthSessionResponse(
        String tokenType,
        Instant expiresAt,
        AccountSummary account
) {
}
