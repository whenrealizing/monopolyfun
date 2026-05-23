package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.config.OAuthConfig;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.service.security.GitHubOAuthClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class GitHubIdentityCertifier implements IdentityCertifier {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);

    private final OAuthConfig oAuthConfig;
    private final GitHubOAuthClient gitHubOAuthClient;

    public GitHubIdentityCertifier(OAuthConfig oAuthConfig, GitHubOAuthClient gitHubOAuthClient) {
        this.oAuthConfig = oAuthConfig;
        this.gitHubOAuthClient = gitHubOAuthClient;
    }

    @Override
    public IdentityCertifierManifest manifest() {
        return IdentityCertifierCatalog.githubOAuthManifest();
    }

    @Override
    public IdentityVerificationStartResult beginVerification(
            String accountId,
            String challengeId,
            String challengeToken,
            Map<String, Object> input) {
        ensureEnabled();
        Instant now = Instant.now();
        IdentityVerificationChallengeEntity challenge = new IdentityVerificationChallengeEntity(
                challengeId,
                accountId,
                manifest().id(),
                manifest().provider(),
                "pending",
                manifest().verificationMethod(),
                challengeToken,
                Map.of("provider", manifest().provider()),
                Map.of("actionLabel", "连接 GitHub"),
                now.plus(CHALLENGE_TTL),
                null,
                null,
                null,
                now);
        return new IdentityVerificationStartResult(
                challenge,
                gitHubOAuthClient.buildAuthorizeUrl(oAuthConfig.getVerificationRedirectUri(), challengeToken));
    }

    @Override
    public IdentityVerificationCompleteResult completeVerification(
            String accountId,
            IdentityVerificationChallengeEntity challenge,
            Map<String, Object> input) {
        ensureEnabled();
        String code = stringValue(input.get("code"));
        String state = stringValue(input.get("state"));
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth callback code and state are required");
        }
        if (!challenge.challengeToken().equals(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity verification callback state mismatch");
        }
        GitHubOAuthClient.GitHubUserProfile profile = gitHubOAuthClient.fetchUserProfile(code, state, oAuthConfig.getVerificationRedirectUri());
        Instant now = Instant.now();
        IdentityFactEntity fact = new IdentityFactEntity(
                "ifact-" + UUID.randomUUID(),
                accountId,
                challenge.id(),
                manifest().id(),
                manifest().provider(),
                "external_identity",
                manifest().verificationMethod(),
                "verified",
                profile.login().toLowerCase(),
                Map.of(
                        "handle", profile.login(),
                        "displayName", profile.displayName(),
                        "avatarUrl", profile.avatarUrl() == null ? "" : profile.avatarUrl(),
                        "profileUrl", profile.profileUrl() == null ? "" : profile.profileUrl()),
                now,
                null,
                null,
                now,
                now);
        return new IdentityVerificationCompleteResult(fact);
    }

    private void ensureEnabled() {
        if (!gitHubOAuthClient.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "GitHub OAuth is not configured");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
