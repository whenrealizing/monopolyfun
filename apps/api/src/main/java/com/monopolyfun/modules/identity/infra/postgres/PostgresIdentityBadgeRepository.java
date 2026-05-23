package com.monopolyfun.modules.identity.infra.postgres;

import com.monopolyfun.modules.identity.domain.IdentityBadgeEntity;
import com.monopolyfun.modules.identity.infra.IdentityBadgeRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.monopolyfun.generated.jooq.Tables.IDENTITY_BADGES;

@Repository
public class PostgresIdentityBadgeRepository implements IdentityBadgeRepository {
    private final DSLContext dsl;

    public PostgresIdentityBadgeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<IdentityBadgeEntity> findByAccountId(String accountId) {
        return dsl.selectFrom(IDENTITY_BADGES)
                .where(IDENTITY_BADGES.ACCOUNT_ID.eq(accountId))
                .orderBy(IDENTITY_BADGES.WEIGHT.desc(), IDENTITY_BADGES.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public void replaceForAccount(String accountId, List<IdentityBadgeEntity> badges) {
        dsl.deleteFrom(IDENTITY_BADGES)
                .where(IDENTITY_BADGES.ACCOUNT_ID.eq(accountId))
                .execute();

        for (IdentityBadgeEntity badge : badges) {
            dsl.insertInto(IDENTITY_BADGES)
                    .set(IDENTITY_BADGES.ID, badge.id())
                    .set(IDENTITY_BADGES.ACCOUNT_ID, badge.accountId())
                    .set(IDENTITY_BADGES.KIND, badge.kind())
                    .set(IDENTITY_BADGES.CODE, badge.code())
                    .set(IDENTITY_BADGES.LABEL, badge.label())
                    .set(IDENTITY_BADGES.ICON, badge.icon())
                    .set(IDENTITY_BADGES.SOURCE_CERTIFIER_ID, badge.sourceCertifierId())
                    .set(IDENTITY_BADGES.SOURCE_FACT_ID, badge.sourceFactId())
                    .set(IDENTITY_BADGES.WEIGHT, badge.weight())
                    .set(IDENTITY_BADGES.CREATED_AT, PostgresJson.offsetDateTime(badge.createdAt()))
                    .set(IDENTITY_BADGES.UPDATED_AT, PostgresJson.offsetDateTime(badge.updatedAt()))
                    .execute();
        }
    }

    private IdentityBadgeEntity mapRecord(Record record) {
        return new IdentityBadgeEntity(
                record.get(IDENTITY_BADGES.ID),
                record.get(IDENTITY_BADGES.ACCOUNT_ID),
                record.get(IDENTITY_BADGES.KIND),
                record.get(IDENTITY_BADGES.CODE),
                record.get(IDENTITY_BADGES.LABEL),
                record.get(IDENTITY_BADGES.ICON),
                record.get(IDENTITY_BADGES.SOURCE_CERTIFIER_ID),
                record.get(IDENTITY_BADGES.SOURCE_FACT_ID),
                record.get(IDENTITY_BADGES.WEIGHT),
                PostgresJson.instant(record.get(IDENTITY_BADGES.CREATED_AT)),
                PostgresJson.instant(record.get(IDENTITY_BADGES.UPDATED_AT)));
    }
}
