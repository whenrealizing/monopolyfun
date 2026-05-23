package com.monopolyfun.modules.identity.service.view;

import java.util.Map;

public record IdentityVerificationChallengeView(
        String id,
        String certifierId,
        String provider,
        String status,
        String verificationMethod,
        String challengeToken,
        Map<String, Object> context,
        Map<String, Object> instructions,
        String failureReason,
        String createdAt,
        String expiresAt,
        String completedAt
) {
}
