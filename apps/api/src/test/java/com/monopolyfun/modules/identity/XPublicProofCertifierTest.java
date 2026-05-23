package com.monopolyfun;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper;
import com.monopolyfun.modules.identity.service.verification.PublicProofDocument;
import com.monopolyfun.modules.identity.service.verification.PublicProofFetchClient;
import com.monopolyfun.modules.identity.service.verification.RedditPublicProofCertifier;
import com.monopolyfun.modules.identity.service.verification.XPublicProofCertifier;
import com.monopolyfun.modules.identity.service.verification.YouTubePublicProofCertifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XPublicProofCertifierTest {
    @Test
    void completesPostProofWhenHandleAndTokenMatch() {
        XPublicProofCertifier certifier = new XPublicProofCertifier(List.of(new FakePublicProofFetchClient(
                new PublicProofDocument(
                        "moonmoon",
                        "Moon Moon",
                        "https://x.com/moonmoon",
                        "MonopolyFun verify verify-token",
                        null,
                        Instant.now(),
                        "https://x.com/moonmoon/status/1"))));

        IdentityFactEntity fact = certifier.completeVerification("acct-1", challenge("moonmoon", "post"), Map.of("proofUrl", "https://x.com/moonmoon/status/1")).fact();

        assertEquals("x_public_proof", fact.certifierId());
        assertEquals("x", fact.provider());
        assertEquals("public_proof", fact.verificationMethod());
        assertEquals("moonmoon", fact.platformUserId());
        assertEquals("https://x.com/moonmoon/status/1", fact.payload().get("proofUrl"));
    }

    @Test
    void publicProofChallengeViewExposesTokenForUserPosting() {
        var view = IdentityViewMapper.identityChallenge(challenge("moonmoon", "post"));

        assertEquals("verify-token", view.challengeToken());
    }

    @Test
    void rejectsProofFromAnotherHandle() {
        XPublicProofCertifier certifier = new XPublicProofCertifier(List.of(new FakePublicProofFetchClient(
                new PublicProofDocument("other", "Other", "https://x.com/other", "MonopolyFun verify verify-token", null, Instant.now(), "https://x.com/other/status/1"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                certifier.completeVerification("acct-1", challenge("moonmoon", "post"), Map.of("proofUrl", "https://x.com/other/status/1")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Proof author does not match the requested handle", error.getReason());
    }

    @Test
    void rejectsProofWithoutChallengeToken() {
        XPublicProofCertifier certifier = new XPublicProofCertifier(List.of(new FakePublicProofFetchClient(
                new PublicProofDocument("moonmoon", "Moon Moon", "https://x.com/moonmoon", "hello", null, Instant.now(), "https://x.com/moonmoon/status/1"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                certifier.completeVerification("acct-1", challenge("moonmoon", "comment"), Map.of("proofUrl", "https://x.com/moonmoon/status/1")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Proof token is missing", error.getReason());
    }

    @Test
    void startRejectsUnsupportedPlacement() {
        XPublicProofCertifier certifier = new XPublicProofCertifier(List.of(new FakePublicProofFetchClient(null)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                certifier.beginVerification("acct-1", "challenge-1", "verify-token", Map.of("handle", "moonmoon", "proofPlacement", "video")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals("Unsupported proof placement", error.getReason());
    }

    @Test
    void redditProofUsesSamePublicProofContract() {
        RedditPublicProofCertifier certifier = new RedditPublicProofCertifier(List.of(new FakePublicProofFetchClient(
                new PublicProofDocument("redditfounder", "Reddit Founder", "https://www.reddit.com/user/redditfounder", "MonopolyFun verify verify-token", null, Instant.now(), "https://www.reddit.com/r/test/comments/1"))));

        IdentityFactEntity fact = certifier.completeVerification("acct-1", challenge("reddit_public_proof", "reddit", "redditfounder", "comment"), Map.of("proofUrl", "https://www.reddit.com/r/test/comments/1")).fact();

        assertEquals("reddit_public_proof", fact.certifierId());
        assertEquals("redditfounder", fact.platformUserId());
    }

    @Test
    void youtubeProofUsesSamePublicProofContract() {
        YouTubePublicProofCertifier certifier = new YouTubePublicProofCertifier(List.of(new FakePublicProofFetchClient(
                new PublicProofDocument("creator", "Creator", "https://www.youtube.com/@creator", "MonopolyFun verify verify-token", null, Instant.now(), "https://www.youtube.com/watch?v=1"))));

        IdentityFactEntity fact = certifier.completeVerification("acct-1", challenge("youtube_public_proof", "youtube", "creator", "video_description"), Map.of("proofUrl", "https://www.youtube.com/watch?v=1")).fact();

        assertEquals("youtube_public_proof", fact.certifierId());
        assertEquals("creator", fact.platformUserId());
    }

    private IdentityVerificationChallengeEntity challenge(String handle, String proofPlacement) {
        return challenge("x_public_proof", "x", handle, proofPlacement);
    }

    private IdentityVerificationChallengeEntity challenge(String certifierId, String provider, String handle, String proofPlacement) {
        Instant now = Instant.now();
        return new IdentityVerificationChallengeEntity(
                "challenge-1",
                "acct-1",
                certifierId,
                provider,
                "pending",
                "public_proof",
                "verify-token",
                Map.of("handle", handle, "proofPlacement", proofPlacement),
                Map.of("tokenText", "MonopolyFun verify verify-token"),
                now.plusSeconds(1800),
                null,
                null,
                null,
                now);
    }

    private record FakePublicProofFetchClient(PublicProofDocument document) implements PublicProofFetchClient {
        @Override
        public boolean supports(String provider) {
            return List.of("x", "reddit", "youtube").contains(provider);
        }

        @Override
        public PublicProofDocument fetch(String provider, URI proofUri, String proofPlacement) {
            return document;
        }
    }
}
