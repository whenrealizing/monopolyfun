package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.IdentityVerificationChallengeEntity;
import com.monopolyfun.modules.identity.infra.IdentityVerificationChallengeRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.IDENTITY_VERIFICATION_CHALLENGES;

@Repository
public class PostgresIdentityVerificationChallengeRepository implements IdentityVerificationChallengeRepository {
    private final DSLContext dsl;

    public PostgresIdentityVerificationChallengeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public IdentityVerificationChallengeEntity save(IdentityVerificationChallengeEntity challenge) {
        dsl.insertInto(IDENTITY_VERIFICATION_CHALLENGES)
                .set(IDENTITY_VERIFICATION_CHALLENGES.ID, challenge.id())
                .set(IDENTITY_VERIFICATION_CHALLENGES.ACCOUNT_ID, challenge.accountId())
                .set(IDENTITY_VERIFICATION_CHALLENGES.CERTIFIER_ID, challenge.certifierId())
                .set(IDENTITY_VERIFICATION_CHALLENGES.PROVIDER, challenge.provider())
                .set(IDENTITY_VERIFICATION_CHALLENGES.STATUS, challenge.status())
                .set(IDENTITY_VERIFICATION_CHALLENGES.VERIFICATION_METHOD, challenge.verificationMethod())
                .set(IDENTITY_VERIFICATION_CHALLENGES.CHALLENGE_TOKEN, challenge.challengeToken())
                .set(IDENTITY_VERIFICATION_CHALLENGES.CONTEXT, PostgresJson.jsonb(challenge.context()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.INSTRUCTIONS, PostgresJson.jsonb(challenge.instructions()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.EXPIRES_AT, PostgresJson.offsetDateTime(challenge.expiresAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.COMPLETED_AT, PostgresJson.offsetDateTime(challenge.completedAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILED_AT, PostgresJson.offsetDateTime(challenge.failedAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILURE_REASON, challenge.failureReason())
                .set(IDENTITY_VERIFICATION_CHALLENGES.CREATED_AT, PostgresJson.offsetDateTime(challenge.createdAt()))
                .onConflict(IDENTITY_VERIFICATION_CHALLENGES.ID)
                .doUpdate()
                .set(IDENTITY_VERIFICATION_CHALLENGES.STATUS, challenge.status())
                .set(IDENTITY_VERIFICATION_CHALLENGES.CONTEXT, PostgresJson.jsonb(challenge.context()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.INSTRUCTIONS, PostgresJson.jsonb(challenge.instructions()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.EXPIRES_AT, PostgresJson.offsetDateTime(challenge.expiresAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.COMPLETED_AT, PostgresJson.offsetDateTime(challenge.completedAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILED_AT, PostgresJson.offsetDateTime(challenge.failedAt()))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILURE_REASON, challenge.failureReason())
                .execute();
        return challenge;
    }

    @Override
    public Optional<IdentityVerificationChallengeEntity> findById(String id) {
        return dsl.selectFrom(IDENTITY_VERIFICATION_CHALLENGES)
                .where(IDENTITY_VERIFICATION_CHALLENGES.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<IdentityVerificationChallengeEntity> findByChallengeToken(String challengeToken) {
        return dsl.selectFrom(IDENTITY_VERIFICATION_CHALLENGES)
                .where(IDENTITY_VERIFICATION_CHALLENGES.CHALLENGE_TOKEN.eq(challengeToken))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<IdentityVerificationChallengeEntity> findByAccountId(String accountId, int limit) {
        return dsl.selectFrom(IDENTITY_VERIFICATION_CHALLENGES)
                .where(IDENTITY_VERIFICATION_CHALLENGES.ACCOUNT_ID.eq(accountId))
                .orderBy(IDENTITY_VERIFICATION_CHALLENGES.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    @Override
    public void markCompleted(String id, Instant completedAt) {
        dsl.update(IDENTITY_VERIFICATION_CHALLENGES)
                .set(IDENTITY_VERIFICATION_CHALLENGES.STATUS, "completed")
                .set(IDENTITY_VERIFICATION_CHALLENGES.COMPLETED_AT, PostgresJson.offsetDateTime(completedAt))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILED_AT, (java.time.OffsetDateTime) null)
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILURE_REASON, (String) null)
                .where(IDENTITY_VERIFICATION_CHALLENGES.ID.eq(id))
                .execute();
    }

    @Override
    public void markFailed(String id, String reason, Instant failedAt) {
        dsl.update(IDENTITY_VERIFICATION_CHALLENGES)
                .set(IDENTITY_VERIFICATION_CHALLENGES.STATUS, "failed")
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILED_AT, PostgresJson.offsetDateTime(failedAt))
                .set(IDENTITY_VERIFICATION_CHALLENGES.FAILURE_REASON, reason)
                .where(IDENTITY_VERIFICATION_CHALLENGES.ID.eq(id))
                .execute();
    }

    private IdentityVerificationChallengeEntity mapRecord(Record record) {
        return new IdentityVerificationChallengeEntity(
                record.get(IDENTITY_VERIFICATION_CHALLENGES.ID),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.ACCOUNT_ID),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.CERTIFIER_ID),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.PROVIDER),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.STATUS),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.VERIFICATION_METHOD),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.CHALLENGE_TOKEN),
                PostgresJson.map(record.get(IDENTITY_VERIFICATION_CHALLENGES.CONTEXT)),
                PostgresJson.map(record.get(IDENTITY_VERIFICATION_CHALLENGES.INSTRUCTIONS)),
                PostgresJson.instant(record.get(IDENTITY_VERIFICATION_CHALLENGES.EXPIRES_AT)),
                PostgresJson.instant(record.get(IDENTITY_VERIFICATION_CHALLENGES.COMPLETED_AT)),
                PostgresJson.instant(record.get(IDENTITY_VERIFICATION_CHALLENGES.FAILED_AT)),
                record.get(IDENTITY_VERIFICATION_CHALLENGES.FAILURE_REASON),
                PostgresJson.instant(record.get(IDENTITY_VERIFICATION_CHALLENGES.CREATED_AT)));
    }
}
