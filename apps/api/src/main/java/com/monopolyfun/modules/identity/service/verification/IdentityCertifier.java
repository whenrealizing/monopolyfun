package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;

import java.util.Map;

public interface IdentityCertifier {
    IdentityCertifierManifest manifest();

    IdentityVerificationStartResult beginVerification(
            String accountId,
            String challengeId,
            String challengeToken,
            Map<String, Object> input
    );

    IdentityVerificationCompleteResult completeVerification(
            String accountId,
            IdentityVerificationChallengeEntity challenge,
            Map<String, Object> input
    );
}
