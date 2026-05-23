package com.monopolyfun.modules.identity.service.mapper;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.service.PublicIdentityRefs;
import com.monopolyfun.modules.identity.service.verification.IdentityCertifierManifest;
import com.monopolyfun.modules.identity.service.view.AccountSummary;
import com.monopolyfun.modules.identity.service.view.IdentityBadgeView;
import com.monopolyfun.modules.identity.service.view.IdentityCertifierView;
import com.monopolyfun.modules.identity.service.view.IdentityDisplaySkinView;
import com.monopolyfun.modules.identity.service.view.IdentityLinkedAccountView;
import com.monopolyfun.modules.identity.service.view.IdentityVerificationChallengeView;
import com.monopolyfun.modules.identity.service.view.PublicAccountSummary;

public final class IdentityViewMapper {
    private IdentityViewMapper() {
    }

    public static AccountSummary account(AccountEntity account) {
        if (account == null) return null;
        IdentityDisplaySkinView nativeSkin = new IdentityDisplaySkinView(
                "native",
                null,
                "monopolyfun",
                null,
                account.displayName(),
                account.handle(),
                emptyToNull(stringValue(account.metadata() == null ? null : account.metadata().get("avatarUrl"))),
                "/identity",
                "native",
                false,
                true);
        return account(account, nativeSkin);
    }

    public static AccountSummary account(AccountEntity account, IdentityDisplaySkinView displaySkin) {
        if (account == null) return null;
        Object agentSummary = account.metadata() == null ? null : account.metadata().get("agentSummary");
        return new AccountSummary(
                account.id(),
                account.handle(),
                account.displayName(),
                agentSummary == null ? null : agentSummary.toString(),
                displaySkin);
    }

    public static PublicAccountSummary publicAccount(AccountEntity account, IdentityDisplaySkinView displaySkin) {
        if (account == null) return null;
        Object agentSummary = account.metadata() == null ? null : account.metadata().get("agentSummary");
        return new PublicAccountSummary(
                PublicIdentityRefs.accountId(account.handle()),
                account.handle(),
                account.displayName(),
                agentSummary == null ? null : agentSummary.toString(),
                displaySkin);
    }

    public static IdentityBadgeView identityBadge(IdentityBadgeEntity badge) {
        if (badge == null) return null;
        return new IdentityBadgeView(badge.code(), badge.label(), badge.kind(), badge.icon(), badge.weight());
    }

    public static IdentityLinkedAccountView identityLinkedAccount(IdentityFactEntity fact) {
        if (fact == null) return null;
        String handle = stringValue(fact.payload().get("handle"));
        String displayName = stringValue(fact.payload().get("displayName"));
        String avatarUrl = emptyToNull(stringValue(fact.payload().get("avatarUrl")));
        String profileUrl = emptyToNull(stringValue(fact.payload().get("profileUrl")));
        return new IdentityLinkedAccountView(
                fact.certifierId(),
                fact.provider(),
                fact.platformUserId(),
                handle,
                displayName,
                avatarUrl,
                profileUrl,
                fact.verifiedAt().toString());
    }

    public static IdentityCertifierView identityCertifier(IdentityCertifierManifest manifest) {
        if (manifest == null) return null;
        return new IdentityCertifierView(
                manifest.id(),
                manifest.name(),
                manifest.provider(),
                manifest.verificationMethod(),
                manifest.description(),
                manifest.trustLevel(),
                manifest.badgeCode(),
                manifest.expiresInDays(),
                manifest.startInputSchema(),
                manifest.completeInputSchema());
    }

    public static IdentityVerificationChallengeView identityChallenge(IdentityVerificationChallengeEntity challenge) {
        if (challenge == null) return null;
        String publicToken = "public_proof".equals(challenge.verificationMethod()) ? challenge.challengeToken() : null;
        return new IdentityVerificationChallengeView(
                challenge.id(),
                challenge.certifierId(),
                challenge.provider(),
                challenge.status(),
                challenge.verificationMethod(),
                // 中文注释：公开证明需要把一次性 token 展示给用户发布，OAuth state 继续只走回调链路。
                publicToken,
                challenge.context(),
                challenge.instructions(),
                challenge.failureReason(),
                formatInstant(challenge.createdAt()),
                formatInstant(challenge.expiresAt()),
                formatInstant(challenge.completedAt()));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String formatInstant(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
