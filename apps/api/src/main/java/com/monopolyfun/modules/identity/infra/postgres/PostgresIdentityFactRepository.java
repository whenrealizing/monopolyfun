package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.IdentityFactEntity;
import com.monopolyfun.modules.identity.infra.IdentityFactRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.IDENTITY_FACTS;

@Repository
public class PostgresIdentityFactRepository implements IdentityFactRepository {
    private final DSLContext dsl;

    public PostgresIdentityFactRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public IdentityFactEntity save(IdentityFactEntity fact) {
        dsl.insertInto(IDENTITY_FACTS)
                .set(IDENTITY_FACTS.ID, fact.id())
                .set(IDENTITY_FACTS.ACCOUNT_ID, fact.accountId())
                .set(IDENTITY_FACTS.CHALLENGE_ID, fact.challengeId())
                .set(IDENTITY_FACTS.CERTIFIER_ID, fact.certifierId())
                .set(IDENTITY_FACTS.PROVIDER, fact.provider())
                .set(IDENTITY_FACTS.FACT_TYPE, fact.factType())
                .set(IDENTITY_FACTS.VERIFICATION_METHOD, fact.verificationMethod())
                .set(IDENTITY_FACTS.STATUS, fact.status())
                .set(IDENTITY_FACTS.PLATFORM_USER_ID, fact.platformUserId())
                .set(IDENTITY_FACTS.PAYLOAD, PostgresJson.jsonb(fact.payload()))
                .set(IDENTITY_FACTS.VERIFIED_AT, PostgresJson.offsetDateTime(fact.verifiedAt()))
                .set(IDENTITY_FACTS.EXPIRES_AT, PostgresJson.offsetDateTime(fact.expiresAt()))
                .set(IDENTITY_FACTS.REVOKED_AT, PostgresJson.offsetDateTime(fact.revokedAt()))
                .set(IDENTITY_FACTS.CREATED_AT, PostgresJson.offsetDateTime(fact.createdAt()))
                .set(IDENTITY_FACTS.UPDATED_AT, PostgresJson.offsetDateTime(fact.updatedAt()))
                .onConflict(IDENTITY_FACTS.ID)
                .doUpdate()
                .set(IDENTITY_FACTS.STATUS, fact.status())
                .set(IDENTITY_FACTS.PAYLOAD, PostgresJson.jsonb(fact.payload()))
                .set(IDENTITY_FACTS.VERIFIED_AT, PostgresJson.offsetDateTime(fact.verifiedAt()))
                .set(IDENTITY_FACTS.EXPIRES_AT, PostgresJson.offsetDateTime(fact.expiresAt()))
                .set(IDENTITY_FACTS.REVOKED_AT, PostgresJson.offsetDateTime(fact.revokedAt()))
                .set(IDENTITY_FACTS.UPDATED_AT, PostgresJson.offsetDateTime(fact.updatedAt()))
                .execute();
        return fact;
    }

    @Override
    public List<IdentityFactEntity> findByAccountId(String accountId) {
        return dsl.selectFrom(IDENTITY_FACTS)
                .where(IDENTITY_FACTS.ACCOUNT_ID.eq(accountId))
                .orderBy(IDENTITY_FACTS.VERIFIED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<IdentityFactEntity> findVerifiedByCertifierAndPlatformUserId(String certifierId, String platformUserId) {
        return dsl.selectFrom(IDENTITY_FACTS)
                .where(IDENTITY_FACTS.CERTIFIER_ID.eq(certifierId)
                        .and(IDENTITY_FACTS.PLATFORM_USER_ID.eq(platformUserId))
                        .and(IDENTITY_FACTS.STATUS.eq("verified"))
                        .and(IDENTITY_FACTS.REVOKED_AT.isNull()))
                .orderBy(IDENTITY_FACTS.VERIFIED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public void revokeVerifiedByAccountIdAndCertifierId(String accountId, String certifierId, Instant revokedAt) {
        dsl.update(IDENTITY_FACTS)
                .set(IDENTITY_FACTS.STATUS, "revoked")
                .set(IDENTITY_FACTS.REVOKED_AT, PostgresJson.offsetDateTime(revokedAt))
                .set(IDENTITY_FACTS.UPDATED_AT, PostgresJson.offsetDateTime(revokedAt))
                .where(IDENTITY_FACTS.ACCOUNT_ID.eq(accountId)
                        .and(IDENTITY_FACTS.CERTIFIER_ID.eq(certifierId))
                        .and(IDENTITY_FACTS.STATUS.eq("verified"))
                        .and(IDENTITY_FACTS.REVOKED_AT.isNull()))
                .execute();
    }

    private IdentityFactEntity mapRecord(Record record) {
        return new IdentityFactEntity(
                record.get(IDENTITY_FACTS.ID),
                record.get(IDENTITY_FACTS.ACCOUNT_ID),
                record.get(IDENTITY_FACTS.CHALLENGE_ID),
                record.get(IDENTITY_FACTS.CERTIFIER_ID),
                record.get(IDENTITY_FACTS.PROVIDER),
                record.get(IDENTITY_FACTS.FACT_TYPE),
                record.get(IDENTITY_FACTS.VERIFICATION_METHOD),
                record.get(IDENTITY_FACTS.STATUS),
                record.get(IDENTITY_FACTS.PLATFORM_USER_ID),
                PostgresJson.map(record.get(IDENTITY_FACTS.PAYLOAD)),
                PostgresJson.instant(record.get(IDENTITY_FACTS.VERIFIED_AT)),
                PostgresJson.instant(record.get(IDENTITY_FACTS.EXPIRES_AT)),
                PostgresJson.instant(record.get(IDENTITY_FACTS.REVOKED_AT)),
                PostgresJson.instant(record.get(IDENTITY_FACTS.CREATED_AT)),
                PostgresJson.instant(record.get(IDENTITY_FACTS.UPDATED_AT)));
    }
}
