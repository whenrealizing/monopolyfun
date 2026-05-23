package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.PasswordResetTokenEntity;
import com.monopolyfun.modules.identity.infra.PasswordResetTokenRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.PASSWORD_RESET_TOKENS;

@Repository
public class PostgresPasswordResetTokenRepository implements PasswordResetTokenRepository {
    private final DSLContext dsl;

    public PostgresPasswordResetTokenRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash) {
        return dsl.selectFrom(PASSWORD_RESET_TOKENS)
                .where(PASSWORD_RESET_TOKENS.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public PasswordResetTokenEntity save(PasswordResetTokenEntity token) {
        dsl.insertInto(PASSWORD_RESET_TOKENS)
                .set(PASSWORD_RESET_TOKENS.ID, token.id())
                .set(PASSWORD_RESET_TOKENS.ACCOUNT_ID, token.accountId())
                .set(PASSWORD_RESET_TOKENS.TOKEN_HASH, token.tokenHash())
                .set(PASSWORD_RESET_TOKENS.EXPIRES_AT, PostgresJson.offsetDateTime(token.expiresAt()))
                .set(PASSWORD_RESET_TOKENS.USED_AT, PostgresJson.offsetDateTime(token.usedAt()))
                .set(PASSWORD_RESET_TOKENS.CREATED_AT, PostgresJson.offsetDateTime(token.createdAt()))
                .onConflict(PASSWORD_RESET_TOKENS.ID)
                .doUpdate()
                .set(PASSWORD_RESET_TOKENS.ACCOUNT_ID, token.accountId())
                .set(PASSWORD_RESET_TOKENS.TOKEN_HASH, token.tokenHash())
                .set(PASSWORD_RESET_TOKENS.EXPIRES_AT, PostgresJson.offsetDateTime(token.expiresAt()))
                .set(PASSWORD_RESET_TOKENS.USED_AT, PostgresJson.offsetDateTime(token.usedAt()))
                .execute();
        return token;
    }

    private PasswordResetTokenEntity mapRecord(Record record) {
        return new PasswordResetTokenEntity(
                record.get(PASSWORD_RESET_TOKENS.ID),
                record.get(PASSWORD_RESET_TOKENS.ACCOUNT_ID),
                record.get(PASSWORD_RESET_TOKENS.TOKEN_HASH),
                PostgresJson.instant(record.get(PASSWORD_RESET_TOKENS.EXPIRES_AT)),
                PostgresJson.instant(record.get(PASSWORD_RESET_TOKENS.USED_AT)),
                PostgresJson.instant(record.get(PASSWORD_RESET_TOKENS.CREATED_AT)));
    }
}
