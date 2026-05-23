package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.OAuthIdentityEntity;
import com.monopolyfun.modules.identity.infra.OAuthIdentityRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresOAuthIdentityRepository implements OAuthIdentityRepository {
    // 中文注释：oauth_identities 是新安全绑定表，动态字段避免本地生成类滞后影响编译。
    private static final Table<Record> OAUTH_IDENTITIES = DSL.table(DSL.name("oauth_identities"));
    private static final Field<String> ID = DSL.field(DSL.name("oauth_identities", "id"), String.class);
    private static final Field<String> PROVIDER = DSL.field(DSL.name("oauth_identities", "provider"), String.class);
    private static final Field<String> EXTERNAL_USER_ID = DSL.field(DSL.name("oauth_identities", "external_user_id"), String.class);
    private static final Field<String> ACCOUNT_ID = DSL.field(DSL.name("oauth_identities", "account_id"), String.class);
    private static final Field<String> EXTERNAL_LOGIN = DSL.field(DSL.name("oauth_identities", "external_login"), String.class);
    private static final Field<JSONB> PAYLOAD = DSL.field(DSL.name("oauth_identities", "payload"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("oauth_identities", "created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("oauth_identities", "updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresOAuthIdentityRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<OAuthIdentityEntity> findByProviderAndExternalUserId(String provider, String externalUserId) {
        return dsl.select(fields())
                .from(OAUTH_IDENTITIES)
                .where(PROVIDER.eq(provider))
                .and(EXTERNAL_USER_ID.eq(externalUserId))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public OAuthIdentityEntity save(OAuthIdentityEntity identity) {
        dsl.insertInto(OAUTH_IDENTITIES)
                .set(ID, identity.id())
                .set(PROVIDER, identity.provider())
                .set(EXTERNAL_USER_ID, identity.externalUserId())
                .set(ACCOUNT_ID, identity.accountId())
                .set(EXTERNAL_LOGIN, identity.externalLogin())
                .set(PAYLOAD, PostgresJson.jsonb(identity.payload()))
                .set(CREATED_AT, PostgresJson.offsetDateTime(identity.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(identity.updatedAt()))
                .onConflict(PROVIDER, EXTERNAL_USER_ID)
                .doUpdate()
                .set(EXTERNAL_LOGIN, identity.externalLogin())
                .set(PAYLOAD, PostgresJson.jsonb(identity.payload()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(identity.updatedAt()))
                .execute();
        return identity;
    }

    private List<Field<?>> fields() {
        return List.of(ID, PROVIDER, EXTERNAL_USER_ID, ACCOUNT_ID, EXTERNAL_LOGIN, PAYLOAD, CREATED_AT, UPDATED_AT);
    }

    private OAuthIdentityEntity mapRecord(Record record) {
        return new OAuthIdentityEntity(
                record.get(ID),
                record.get(PROVIDER),
                record.get(EXTERNAL_USER_ID),
                record.get(ACCOUNT_ID),
                record.get(EXTERNAL_LOGIN),
                PostgresJson.map(record.get(PAYLOAD)),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }
}
