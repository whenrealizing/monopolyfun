package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;

public record IdentityVerificationStartResult(
        IdentityVerificationChallengeEntity challenge,
        String actionUrl
) {
}
