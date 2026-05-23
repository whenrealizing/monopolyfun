package com.monopolyfun.modules.identity.infra;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IdentityFactRepository {
    IdentityFactEntity save(IdentityFactEntity fact);

    List<IdentityFactEntity> findByAccountId(String accountId);

    Optional<IdentityFactEntity> findVerifiedByCertifierAndPlatformUserId(String certifierId, String platformUserId);

    void revokeVerifiedByAccountIdAndCertifierId(String accountId, String certifierId, Instant revokedAt);
}
