package com.monopolyfun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.OAuthConfig;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper;
import com.monopolyfun.modules.identity.service.security.GitHubOAuthClient;
import com.monopolyfun.modules.identity.service.verification.GitHubIdentityCertifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubIdentityCertifierTest {
    @Test
    void completeVerificationRejectsStateMismatch() {
        GitHubIdentityCertifier certifier = new GitHubIdentityCertifier(new OAuthConfig(), new EnabledGitHubOAuthClient());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                certifier.completeVerification("acct-1", challenge(), Map.of("code", "oauth-code", "state", "wrong-state")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Identity verification callback state mismatch", error.getReason());
    }

    @Test
    void oauthChallengeViewDoesNotExposeStateToken() {
        var view = IdentityViewMapper.identityChallenge(challenge());

        assertNull(view.challengeToken());
    }

    private IdentityVerificationChallengeEntity challenge() {
        Instant now = Instant.now();
        return new IdentityVerificationChallengeEntity(
                "challenge-1",
                "acct-1",
                "github_oauth",
                "github",
                "pending",
                "oauth",
                "expected-state",
                Map.of(),
                Map.of(),
                now.plusSeconds(600),
                null,
                null,
                null,
                now);
    }

    private static final class EnabledGitHubOAuthClient extends GitHubOAuthClient {
        private EnabledGitHubOAuthClient() {
            super(new OAuthConfig(), new ObjectMapper());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
