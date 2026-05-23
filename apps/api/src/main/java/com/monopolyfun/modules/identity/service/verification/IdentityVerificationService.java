package com.monopolyfun.modules.identity.service.verification;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.modules.identity.infra.IdentityVerificationChallengeRepository;
import com.monopolyfun.modules.identity.service.view.IdentityVerificationStartResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityVerificationService {
    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationService.class);
    private final IdentityVerificationChallengeRepository challengeRepository;
    private final IdentityFactRepository identityFactRepository;
    private final IdentityCertifierRegistry certifierRegistry;
    private final IdentityBadgeProjector identityBadgeProjector;
    private final AccountRepository accountRepository;

    public IdentityVerificationService(
            IdentityVerificationChallengeRepository challengeRepository,
            IdentityFactRepository identityFactRepository,
            IdentityCertifierRegistry certifierRegistry,
            IdentityBadgeProjector identityBadgeProjector,
            AccountRepository accountRepository) {
        this.challengeRepository = challengeRepository;
        this.identityFactRepository = identityFactRepository;
        this.certifierRegistry = certifierRegistry;
        this.identityBadgeProjector = identityBadgeProjector;
        this.accountRepository = accountRepository;
    }

    public List<IdentityCertifierManifest> listCertifiers() {
        return certifierRegistry.listManifests();
    }

    public IdentityVerificationStartResponse beginVerification(String accountId, String certifierId, Map<String, Object> input) {
        IdentityCertifier certifier = certifierRegistry.find(certifierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity certifier not found"));
        IdentityVerificationStartResult result = certifier.beginVerification(
                accountId,
                "ichallenge-" + UUID.randomUUID(),
                "verify-" + UUID.randomUUID(),
                input == null ? Map.of() : input);
        challengeRepository.save(result.challenge());
        log.info("identity_verification_start accountId={} certifierId={} provider={} method={}", accountId, certifierId, result.challenge().provider(), result.challenge().verificationMethod());
        return new IdentityVerificationStartResponse(com.monopolyfun.modules.identity.service.mapper.IdentityViewMapper.identityChallenge(result.challenge()), result.actionUrl());
    }

    public void completeVerification(String accountId, String challengeId, Map<String, Object> input) {
        IdentityVerificationChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity verification challenge not found"));
        if (!challenge.accountId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Challenge does not belong to the current account");
        }
        if (!"pending".equals(challenge.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity verification challenge already resolved");
        }
        if (challenge.expiresAt().isBefore(Instant.now())) {
            challengeRepository.markFailed(challenge.id(), "Challenge expired", Instant.now());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity verification challenge expired");
        }

        IdentityCertifier certifier = certifierRegistry.find(challenge.certifierId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity certifier not found"));

        IdentityVerificationCompleteResult result;
        try {
            result = certifier.completeVerification(accountId, challenge, input == null ? Map.of() : input);
        } catch (ResponseStatusException exception) {
            challengeRepository.markFailed(challenge.id(), exception.getReason(), Instant.now());
            throw exception;
        }

        IdentityFactEntity fact = result.fact();
        identityFactRepository.findVerifiedByCertifierAndPlatformUserId(fact.certifierId(), fact.platformUserId())
                .filter(existing -> !existing.accountId().equals(accountId))
                .ifPresent(existing -> {
                    challengeRepository.markFailed(challenge.id(), "External identity already connected to another account", Instant.now());
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "External identity already connected to another account");
                });

        identityFactRepository.revokeVerifiedByAccountIdAndCertifierId(accountId, fact.certifierId(), Instant.now());
        identityFactRepository.save(fact);
        challengeRepository.markCompleted(challenge.id(), Instant.now());
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        identityBadgeProjector.project(account, identityFactRepository.findByAccountId(accountId));
        log.info("identity_verification_complete accountId={} certifierId={} provider={} platformUserId={}", accountId, fact.certifierId(), fact.provider(), fact.platformUserId());
    }
}
