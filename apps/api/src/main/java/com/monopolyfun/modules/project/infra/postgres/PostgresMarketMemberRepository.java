package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.MarketMemberEntity;
import com.monopolyfun.modules.project.domain.MarketMemberRole;
import com.monopolyfun.modules.project.infra.MarketMemberRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.MARKET_MEMBERS;

@Repository
public class PostgresMarketMemberRepository implements MarketMemberRepository {
    private final DSLContext dsl;

    public PostgresMarketMemberRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<MarketMemberEntity> findByMarketId(String marketId) {
        return dsl.selectFrom(MARKET_MEMBERS)
                .where(MARKET_MEMBERS.MARKET_ID.eq(marketId))
                .orderBy(MARKET_MEMBERS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<MarketMemberEntity> findByMarketIdAndAccountId(String marketId, String accountId) {
        return dsl.selectFrom(MARKET_MEMBERS)
                .where(MARKET_MEMBERS.MARKET_ID.eq(marketId))
                .and(MARKET_MEMBERS.ACCOUNT_ID.eq(accountId))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public MarketMemberEntity save(MarketMemberEntity member) {
        dsl.insertInto(MARKET_MEMBERS)
                .set(MARKET_MEMBERS.ID, member.id())
                .set(MARKET_MEMBERS.MARKET_ID, member.marketId())
                .set(MARKET_MEMBERS.ACCOUNT_ID, member.accountId())
                .set(MARKET_MEMBERS.ROLE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.MarketMemberRole.class, member.role()))
                .set(MARKET_MEMBERS.CREATED_AT, PostgresJson.offsetDateTime(member.createdAt()))
                .onConflictDoNothing()
                .execute();
        return member;
    }

    @Override
    public void deleteByMarketIdAndAccountId(String marketId, String accountId) {
        dsl.deleteFrom(MARKET_MEMBERS)
                .where(MARKET_MEMBERS.MARKET_ID.eq(marketId))
                .and(MARKET_MEMBERS.ACCOUNT_ID.eq(accountId))
                .execute();
    }

    private MarketMemberEntity mapRecord(Record record) {
        return new MarketMemberEntity(
                record.get(MARKET_MEMBERS.ID),
                record.get(MARKET_MEMBERS.MARKET_ID),
                record.get(MARKET_MEMBERS.ACCOUNT_ID),
                PostgresJson.modelEnum(MarketMemberRole.class, record.get(MARKET_MEMBERS.ROLE)),
                PostgresJson.instant(record.get(MARKET_MEMBERS.CREATED_AT)));
    }
}
