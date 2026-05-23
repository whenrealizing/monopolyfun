package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IdentityVerificationChallengeRepository {
    IdentityVerificationChallengeEntity save(IdentityVerificationChallengeEntity challenge);

    Optional<IdentityVerificationChallengeEntity> findById(String id);

    Optional<IdentityVerificationChallengeEntity> findByChallengeToken(String challengeToken);

    List<IdentityVerificationChallengeEntity> findByAccountId(String accountId, int limit);

    void markCompleted(String id, Instant completedAt);

    void markFailed(String id, String reason, Instant failedAt);
}
