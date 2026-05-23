package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public abstract class PublicProofIdentityCertifier implements IdentityCertifier {
    private static final Logger log = LoggerFactory.getLogger(PublicProofIdentityCertifier.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(30);

    private final PublicProofSpec spec;
    private final List<PublicProofFetchClient> fetchClients;

    protected PublicProofIdentityCertifier(PublicProofSpec spec, List<PublicProofFetchClient> fetchClients) {
        this.spec = spec;
        this.fetchClients = List.copyOf(fetchClients);
    }

    @Override
    public IdentityCertifierManifest manifest() {
        return spec.manifest();
    }

    @Override
    public IdentityVerificationStartResult beginVerification(
            String accountId,
            String challengeId,
            String challengeToken,
            Map<String, Object> input) {
        String handle = normalizeHandle(requiredString(input, "handle", "Proof handle is required"));
        String proofPlacement = proofPlacement(input);
        Instant now = Instant.now();
        String tokenText = "MonopolyFun verify " + challengeToken;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("provider", spec.provider());
        context.put("handle", handle);
        context.put("proofPlacement", proofPlacement);
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("actionLabel", "发布认证 token");
        instructions.put("tokenText", tokenText);
        instructions.put("proofPlacement", proofPlacement);
        instructions.put("allowedPlacements", spec.allowedPlacements());
        instructions.put("expiresAt", now.plus(CHALLENGE_TTL).toString());
        log.info("identity_public_proof_start accountId={} certifierId={} provider={} proofPlacement={}", accountId, spec.certifierId(), spec.provider(), proofPlacement);
        return new IdentityVerificationStartResult(
                new IdentityVerificationChallengeEntity(
                        challengeId,
                        accountId,
                        spec.certifierId(),
                        spec.provider(),
                        "pending",
                        IdentityCertifierCatalog.METHOD_PUBLIC_PROOF,
                        challengeToken,
                        context,
                        instructions,
                        now.plus(CHALLENGE_TTL),
                        null,
                        null,
                        null,
                        now),
                null);
    }

    @Override
    public IdentityVerificationCompleteResult completeVerification(
            String accountId,
            IdentityVerificationChallengeEntity challenge,
            Map<String, Object> input) {
        String proofUrl = requiredString(input, "proofUrl", "Proof URL is required").trim();
        URI proofUri = parseProofUri(proofUrl);
        PublicProofFetchClient fetchClient = fetchClients.stream()
                .filter(client -> client.supports(spec.provider()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Public proof fetch client is not configured"));
        String expectedHandle = normalizeHandle(requiredString(challenge.context(), "handle", "Proof handle is missing"));
        String proofPlacement = requiredString(challenge.context(), "proofPlacement", "Proof placement is missing");
        PublicProofDocument document = fetchClient.fetch(spec.provider(), proofUri, proofPlacement);
        String actualHandle = normalizeHandle(document.authorHandle());
        if (!expectedHandle.equals(actualHandle)) {
            log.warn("identity_public_proof_failed challengeId={} provider={} failureCode=proof.author.mismatch", challenge.id(), spec.provider());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof author does not match the requested handle");
        }
        String proofText = document.text() == null ? "" : document.text();
        if (!proofText.contains(challenge.challengeToken())) {
            log.warn("identity_public_proof_failed challengeId={} provider={} failureCode=proof.token.missing", challenge.id(), spec.provider());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof token is missing");
        }
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handle", expectedHandle);
        payload.put("displayName", blankToDefault(document.displayName(), expectedHandle));
        payload.put("profileUrl", blankToDefault(document.profileUrl(), profileUrl(expectedHandle)));
        payload.put("proofUrl", proofUrl);
        payload.put("canonicalProofUrl", blankToDefault(document.canonicalUrl(), proofUrl));
        payload.put("proofPlacement", proofPlacement);
        payload.put("proofTextHash", sha256(proofText));
        payload.put("observedAt", document.observedAt() == null ? now.toString() : document.observedAt().toString());
        if (document.publishedAt() != null) {
            payload.put("publishedAt", document.publishedAt().toString());
        }
        log.info("identity_public_proof_complete challengeId={} provider={} normalizedHandle={} result=verified", challenge.id(), spec.provider(), expectedHandle);
        IdentityFactEntity fact = new IdentityFactEntity(
                "ifact-" + UUID.randomUUID(),
                accountId,
                challenge.id(),
                spec.certifierId(),
                spec.provider(),
                "external_identity",
                IdentityCertifierCatalog.METHOD_PUBLIC_PROOF,
                "verified",
                expectedHandle,
                payload,
                now,
                now.plus(Duration.ofDays(manifest().expiresInDays())),
                null,
                now,
                now);
        return new IdentityVerificationCompleteResult(fact);
    }

    protected String profileUrl(String handle) {
        return "";
    }

    private String proofPlacement(Map<String, Object> input) {
        String placement = requiredString(input, "proofPlacement", "Proof placement is required").trim();
        if (!spec.allowedPlacements().contains(placement)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported proof placement");
        }
        return placement;
    }

    private URI parseProofUri(String proofUrl) {
        try {
            URI uri = URI.create(proofUrl);
            if (uri.getScheme() == null || uri.getHost() == null || !List.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proof URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proof URL");
        }
    }

    private String requiredString(Map<String, Object> input, String key, String message) {
        Object raw = input == null ? null : input.get(key);
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String normalizeHandle(String value) {
        String normalized = value == null ? "" : value.trim().replaceFirst("^@+", "").toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof handle is required");
        }
        return normalized;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Proof hash failed");
        }
    }
}
