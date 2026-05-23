package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.MarketStatus;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.MARKETS;

@Repository
public class PostgresMarketRepository implements MarketRepository {
    private final DSLContext dsl;

    public PostgresMarketRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<MarketEntity> findAll() {
        return dsl.selectFrom(MARKETS)
                .orderBy(MARKETS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(MARKETS.STATUS, org.jooq.impl.DSL.count())
                .from(MARKETS)
                .groupBy(MARKETS.STATUS)
                .fetch(record -> counts.put(String.valueOf(record.value1()).toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public Optional<MarketEntity> findById(String id) {
        return dsl.selectFrom(MARKETS)
                .where(MARKETS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public MarketEntity save(MarketEntity market) {
        dsl.insertInto(MARKETS)
                .set(MARKETS.ID, market.id())
                .set(MARKETS.NAME, market.name())
                .set(MARKETS.SUMMARY, market.summary())
                .set(MARKETS.LISTING_GOAL, market.listingGoal())
                .set(MARKETS.LEAD_ACCOUNT_ID, market.leadAccountId())
                .set(MARKETS.SOURCE_REF, market.sourceRef())
                .set(MARKETS.SURFACE_URL, market.surfaceUrl())
                .set(MARKETS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, market.settlementType()))
                .set(MARKETS.NEXT_CURVE_SLOT, market.nextCurveSlot())
                .set(MARKETS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.MarketStatus.class, market.status()))
                .set(MARKETS.LEAD_LAST_ACTIVE_AT, PostgresJson.offsetDateTime(market.leadLastActiveAt()))
                .set(MARKETS.LEAD_SEAT_STATUS, market.leadSeatStatus())
                .set(MARKETS.METADATA, PostgresJson.jsonb(market.metadata()))
                .set(MARKETS.CREATED_AT, PostgresJson.offsetDateTime(market.createdAt()))
                .set(MARKETS.UPDATED_AT, PostgresJson.offsetDateTime(market.updatedAt()))
                .onConflict(MARKETS.ID)
                .doUpdate()
                .set(MARKETS.NAME, market.name())
                .set(MARKETS.SUMMARY, market.summary())
                .set(MARKETS.LISTING_GOAL, market.listingGoal())
                .set(MARKETS.LEAD_ACCOUNT_ID, market.leadAccountId())
                .set(MARKETS.SOURCE_REF, market.sourceRef())
                .set(MARKETS.SURFACE_URL, market.surfaceUrl())
                .set(MARKETS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, market.settlementType()))
                .set(MARKETS.NEXT_CURVE_SLOT, market.nextCurveSlot())
                .set(MARKETS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.MarketStatus.class, market.status()))
                .set(MARKETS.LEAD_LAST_ACTIVE_AT, PostgresJson.offsetDateTime(market.leadLastActiveAt()))
                .set(MARKETS.LEAD_SEAT_STATUS, market.leadSeatStatus())
                .set(MARKETS.METADATA, PostgresJson.jsonb(market.metadata()))
                .set(MARKETS.UPDATED_AT, PostgresJson.offsetDateTime(market.updatedAt()))
                .execute();
        return market;
    }

    private MarketEntity mapRecord(Record record) {
        return new MarketEntity(
                record.get(MARKETS.ID),
                record.get(MARKETS.NAME),
                record.get(MARKETS.SUMMARY),
                record.get(MARKETS.LISTING_GOAL),
                record.get(MARKETS.LEAD_ACCOUNT_ID),
                record.get(MARKETS.SOURCE_REF),
                record.get(MARKETS.SURFACE_URL),
                PostgresJson.modelEnum(SettlementType.class, record.get(MARKETS.SETTLEMENT_TYPE)),
                record.get(MARKETS.NEXT_CURVE_SLOT),
                PostgresJson.modelEnum(MarketStatus.class, record.get(MARKETS.STATUS)),
                PostgresJson.instant(record.get(MARKETS.LEAD_LAST_ACTIVE_AT)),
                record.get(MARKETS.LEAD_SEAT_STATUS),
                PostgresJson.map(record.get(MARKETS.METADATA)),
                PostgresJson.instant(record.get(MARKETS.CREATED_AT)),
                PostgresJson.instant(record.get(MARKETS.UPDATED_AT)));
    }
}
