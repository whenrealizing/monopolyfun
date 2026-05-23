package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.OAuthStateEntity;
import com.monopolyfun.modules.identity.infra.OAuthStateRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.OAUTH_STATES;

@Repository
public class PostgresOAuthStateRepository implements OAuthStateRepository {
    private final DSLContext dsl;

    public PostgresOAuthStateRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<OAuthStateEntity> findByStateToken(String stateToken) {
        return dsl.selectFrom(OAUTH_STATES)
                .where(OAUTH_STATES.STATE_TOKEN.eq(stateToken))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public OAuthStateEntity save(OAuthStateEntity state) {
        dsl.insertInto(OAUTH_STATES)
                .set(OAUTH_STATES.ID, state.id())
                .set(OAUTH_STATES.PROVIDER, state.provider())
                .set(OAUTH_STATES.STATE_TOKEN, state.stateToken())
                .set(OAUTH_STATES.RETURN_TO, state.returnTo())
                .set(OAUTH_STATES.EXPIRES_AT, PostgresJson.offsetDateTime(state.expiresAt()))
                .set(OAUTH_STATES.USED_AT, PostgresJson.offsetDateTime(state.usedAt()))
                .set(OAUTH_STATES.CREATED_AT, PostgresJson.offsetDateTime(state.createdAt()))
                .onConflict(OAUTH_STATES.ID)
                .doUpdate()
                .set(OAUTH_STATES.USED_AT, PostgresJson.offsetDateTime(state.usedAt()))
                .execute();
        return state;
    }

    private OAuthStateEntity mapRecord(Record record) {
        return new OAuthStateEntity(
                record.get(OAUTH_STATES.ID),
                record.get(OAUTH_STATES.PROVIDER),
                record.get(OAUTH_STATES.STATE_TOKEN),
                record.get(OAUTH_STATES.RETURN_TO),
                PostgresJson.instant(record.get(OAUTH_STATES.EXPIRES_AT)),
                PostgresJson.instant(record.get(OAUTH_STATES.USED_AT)),
                PostgresJson.instant(record.get(OAUTH_STATES.CREATED_AT)));
    }
}
