package com.monopolyfun.modules.identity.service.view;

public record IdentityVerificationStartResponse(
        IdentityVerificationChallengeView challenge,
        String actionUrl
) {
}
