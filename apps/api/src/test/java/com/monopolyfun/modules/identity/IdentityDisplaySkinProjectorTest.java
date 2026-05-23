package com.monopolyfun;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinPreference;
import com.monopolyfun.modules.identity.service.display.IdentityDisplaySkinProjector;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityDisplaySkinProjectorTest {
    private final IdentityDisplaySkinProjector projector = new IdentityDisplaySkinProjector();

    @Test
    void defaultsToNativeDisplaySkin() {
        var projection = projector.project(account(Map.of("avatarUrl", "https://cdn.example/founder.png")), List.of());

        assertEquals("native", projection.selected().source());
        assertEquals("Founder", projection.selected().displayName());
        assertEquals("founder", projection.selected().displayHandle());
        assertEquals("https://cdn.example/founder.png", projection.selected().avatarUrl());
        assertFalse(projection.selected().verified());
        assertEquals(1, projection.candidates().size());
        assertTrue(projection.candidates().getFirst().selected());
    }

    @Test
    void selectsVerifiedCertifierWhenPreferenceMatches() {
        Map<String, Object> metadata = projector.writePreference(Map.of(), new IdentityDisplaySkinPreference("verified_identity", "github_oauth"));
        var projection = projector.project(account(metadata), List.of(githubFact("verified", null, null)));

        assertEquals("verified_identity", projection.selected().source());
        assertEquals("github_oauth", projection.selected().certifierId());
        assertEquals("Octo Founder", projection.selected().displayName());
        assertEquals("octo", projection.selected().displayHandle());
        assertTrue(projection.selected().verified());
        assertTrue(projection.candidates().stream().anyMatch(candidate -> candidate.source().equals("native")));
        assertTrue(projection.candidates().stream().anyMatch(candidate -> candidate.source().equals("verified_identity") && candidate.selected()));
    }

    @Test
    void keepsUnverifiedFactsAsVerificationPromptOptions() {
        Instant now = Instant.now();
        var projection = projector.project(
                account(projector.writePreference(Map.of(), new IdentityDisplaySkinPreference("verified_identity", "github_oauth"))),
                List.of(
                        githubFact("pending", null, null),
                        githubFact("verified", now.minusSeconds(1), null),
                        githubFact("verified", null, now.minusSeconds(1))));

        assertEquals("native", projection.selected().source());
        assertTrue(projection.candidates().stream().anyMatch(candidate ->
                "external_identity".equals(candidate.source())
                        && "github_oauth".equals(candidate.certifierId())
                        && !candidate.verified()));
        assertFalse(projector.hasSelectableCertifier(account(Map.of()), List.of(githubFact("pending", null, null)), "github_oauth"));
    }

    private AccountEntity account(Map<String, Object> metadata) {
        Instant now = Instant.now();
        return new AccountEntity("acct-1", "founder", "Founder", "hash", RiskAccountStatus.ACTIVE, RiskLevel.NORMAL, null, null, null, metadata, now, now);
    }

    private IdentityFactEntity githubFact(String status, Instant expiresAt, Instant revokedAt) {
        Instant now = Instant.now();
        return new IdentityFactEntity(
                "ifact-1",
                "acct-1",
                "challenge-1",
                "github_oauth",
                "github",
                "external_identity",
                "oauth",
                status,
                "octo",
                Map.of(
                        "handle", "octo",
                        "displayName", "Octo Founder",
                        "avatarUrl", "https://avatars.example/octo.png",
                        "profileUrl", "https://github.com/octo"),
                now,
                expiresAt,
                revokedAt,
                now,
                now);
    }
}
