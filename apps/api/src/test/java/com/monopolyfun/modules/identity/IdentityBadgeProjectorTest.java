package com.monopolyfun;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.infra.IdentityBadgeRepository;
import com.monopolyfun.modules.identity.service.verification.IdentityBadgeProjector;
import com.monopolyfun.modules.risk.domain.RiskAccountStatus;
import com.monopolyfun.modules.risk.domain.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityBadgeProjectorTest {
    @Test
    void rebuildKeepsStableBadgeIdsAndDropsInvalidFacts() {
        InMemoryIdentityBadgeRepository repository = new InMemoryIdentityBadgeRepository();
        IdentityBadgeProjector projector = new IdentityBadgeProjector(repository);
        AccountEntity account = account();
        List<IdentityFactEntity> facts = List.of(
                identityFact("ifact-reddit", "reddit_public_proof", "reddit", "public_proof", "verified", null, null),
                identityFact("ifact-x", "x_public_proof", "x", "public_proof", "verified", null, null),
                identityFact("ifact-expired", "reddit_public_proof", "reddit", "public_proof", "verified", Instant.now().minusSeconds(1), null),
                identityFact("ifact-revoked", "reddit_public_proof", "reddit", "public_proof", "verified", null, Instant.now().minusSeconds(1)),
                identityFact("ifact-pending", "reddit_public_proof", "reddit", "public_proof", "pending", null, null));

        projector.project(account, facts);
        List<String> firstIds = repository.findByAccountId(account.id()).stream().map(IdentityBadgeEntity::id).sorted().toList();
        projector.project(account, facts);
        List<String> secondIds = repository.findByAccountId(account.id()).stream().map(IdentityBadgeEntity::id).sorted().toList();

        assertEquals(firstIds, secondIds);
        assertEquals(List.of(
                "ibadge:acct-1:tenure:tenure_newcomer:native",
                "ibadge:acct-1:verified:reddit_public_proof_verified:ifact-reddit",
                "ibadge:acct-1:verified:x_public_proof_verified:ifact-x"), secondIds);
    }

    private AccountEntity account() {
        Instant now = Instant.now();
        return new AccountEntity("acct-1", "founder", "Founder", "hash", RiskAccountStatus.ACTIVE, RiskLevel.NORMAL, null, null, null, Map.of(), now, now);
    }

    private IdentityFactEntity identityFact(String id, String certifierId, String provider, String method, String status, Instant expiresAt, Instant revokedAt) {
        Instant now = Instant.now();
        return new IdentityFactEntity(
                id,
                "acct-1",
                "challenge-1",
                certifierId,
                provider,
                "external_identity",
                method,
                status,
                "octo",
                Map.of("handle", "octo", "displayName", "Octo Founder"),
                now,
                expiresAt,
                revokedAt,
                now,
                now);
    }

    private static final class InMemoryIdentityBadgeRepository implements IdentityBadgeRepository {
        private List<IdentityBadgeEntity> badges = new ArrayList<>();

        @Override
        public List<IdentityBadgeEntity> findByAccountId(String accountId) {
            return badges.stream().filter(badge -> badge.accountId().equals(accountId)).toList();
        }

        @Override
        public void replaceForAccount(String accountId, List<IdentityBadgeEntity> badges) {
            this.badges = new ArrayList<>(badges);
        }
    }
}
