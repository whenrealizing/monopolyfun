package com.monopolyfun.modules.post.infra.postgres;

import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.OfferStatus;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.OFFERS;

@Repository
public class PostgresOfferRepository implements OfferRepository {
    private static final Field<String> OFFER_NO = DSL.field(DSL.name("offer_no"), String.class);

    private final DSLContext dsl;
    private final MarketItemReadModelRepository marketItemReadModelRepository;

    public PostgresOfferRepository(DSLContext dsl, MarketItemReadModelRepository marketItemReadModelRepository) {
        this.dsl = dsl;
        this.marketItemReadModelRepository = marketItemReadModelRepository;
    }

    @Override
    public List<OfferEntity> findAll() {
        return dsl.select(offerFields())
                .from(OFFERS)
                .orderBy(OFFERS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<OfferEntity> findPublic(String status, String q, String sort, PageQuery pageQuery) {
        List<OfferEntity> fetched = dsl.select(offerFields())
                .from(OFFERS)
                .where(publicCondition().and(statusCondition(status)).and(searchCondition(q)))
                .and(cursorCondition(sort, pageQuery.cursorKey()))
                .orderBy(sortField(sort), idSortField(sort))
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), offer -> cursorFor(offer, sort));
    }

    @Override
    public List<OfferEntity> findPublicByActorAccountId(String actorAccountId, int limit) {
        // 中文注释：公开主页只读取账号自己对外可见的 Offer，私有成交条目继续留在订单上下文。
        return dsl.select(offerFields())
                .from(OFFERS)
                .where(OFFERS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .and(publicCondition())
                .orderBy(OFFERS.CREATED_AT.desc(), OFFERS.ID.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<OfferEntity> findByActorAccountId(String actorAccountId, int limit) {
        return dsl.select(offerFields())
                .from(OFFERS)
                .where(OFFERS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .orderBy(OFFERS.CREATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<OfferEntity> findWorkbenchCandidates(String actorAccountId, int limit) {
        return dsl.select(offerFields())
                .from(OFFERS)
                .where(OFFERS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .and(OFFERS.STATUS.eq(OfferStatus.OPEN.name().toLowerCase()))
                .and(OFFERS.STOCK_SOLD.eq(0))
                .orderBy(OFFERS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<OfferEntity> findById(String id) {
        return dsl.select(offerFields())
                .from(OFFERS)
                .where(OFFERS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<OfferEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(offerFields())
                .from(OFFERS)
                .where(OFFERS.ID.in(ids))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<OfferEntity> findByOfferNo(String offerNo) {
        // 中文注释：业务编号查询直接绑定迁移后的物理列，避免生成代码与运行库字段状态不一致。
        return dsl.select(offerFields())
                .from(OFFERS)
                .where("offer_no = ?", offerNo)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public OfferEntity save(OfferEntity offer) {
        dsl.insertInto(OFFERS)
                .set(OFFERS.ID, offer.id())
                .set(OFFER_NO, offer.offerNo())
                .set(OFFERS.ACTOR_ACCOUNT_ID, offer.actorAccountId())
                .set(OFFERS.TITLE, offer.title())
                .set(OFFERS.DESCRIPTION, offer.description())
                .set(OFFERS.DELIVERY_STANDARD, offer.deliveryStandard())
                .set(OFFERS.PRICE_AMOUNT, offer.priceAmount())
                .set(OFFERS.CURRENCY, offer.currency())
                .set(OFFERS.PAYMENT_METHOD, offer.paymentMethod())
                .set(OFFERS.PAYMENT_PROFILE, offer.paymentProfile())
                .set(OFFERS.PAYMENT_NETWORK, offer.paymentNetwork())
                .set(OFFERS.PAYMENT_ASSET, offer.paymentAsset())
                .set(OFFERS.INVENTORY_POLICY, offer.inventoryPolicy().name().toLowerCase())
                .set(OFFERS.STOCK_TOTAL, offer.stockTotal())
                .set(OFFERS.STOCK_SOLD, offer.stockSold())
                .set(OFFERS.STATUS, offer.status().name().toLowerCase())
                .set(OFFERS.METADATA, PostgresJson.jsonb(offer.metadata()))
                .set(OFFERS.CREATED_AT, PostgresJson.offsetDateTime(offer.createdAt()))
                .set(OFFERS.UPDATED_AT, PostgresJson.offsetDateTime(offer.updatedAt()))
                .onConflict(OFFERS.ID)
                .doUpdate()
                .set(OFFER_NO, offer.offerNo())
                .set(OFFERS.TITLE, offer.title())
                .set(OFFERS.DESCRIPTION, offer.description())
                .set(OFFERS.DELIVERY_STANDARD, offer.deliveryStandard())
                .set(OFFERS.PRICE_AMOUNT, offer.priceAmount())
                .set(OFFERS.CURRENCY, offer.currency())
                .set(OFFERS.PAYMENT_METHOD, offer.paymentMethod())
                .set(OFFERS.PAYMENT_PROFILE, offer.paymentProfile())
                .set(OFFERS.PAYMENT_NETWORK, offer.paymentNetwork())
                .set(OFFERS.PAYMENT_ASSET, offer.paymentAsset())
                .set(OFFERS.INVENTORY_POLICY, offer.inventoryPolicy().name().toLowerCase())
                .set(OFFERS.STOCK_TOTAL, offer.stockTotal())
                .set(OFFERS.STOCK_SOLD, offer.stockSold())
                .set(OFFERS.STATUS, offer.status().name().toLowerCase())
                .set(OFFERS.METADATA, PostgresJson.jsonb(offer.metadata()))
                .set(OFFERS.UPDATED_AT, PostgresJson.offsetDateTime(offer.updatedAt()))
                .execute();
        marketItemReadModelRepository.upsertOffer(offer);
        return offer;
    }

    private List<? extends SelectFieldOrAsterisk> offerFields() {
        // 中文注释：offer_no 使用动态字段，代码生成落后于迁移时也能保持编译和运行一致。
        return List.of(OFFERS.asterisk(), OFFER_NO);
    }

    private Condition publicCondition() {
        // 中文注释：公开市场只读取 market_public 条目，participant_only 留给订单参与方页面读取。
        return DSL.coalesce(DSL.field("offers.metadata ->> 'visibility'", String.class), "market_public").eq("market_public");
    }

    private Condition statusCondition(String status) {
        String normalized = normalize(status);
        return normalized == null ? DSL.trueCondition() : OFFERS.STATUS.eq(normalized);
    }

    private Condition searchCondition(String q) {
        String normalized = normalize(q);
        if (normalized == null) {
            return DSL.trueCondition();
        }
        String likePattern = "%" + escapeLike(normalized) + "%";
        // 中文注释：报价列表保留 FTS，同时补中文连续短词匹配，避免“绘图”漏掉“AI绘图能力”。
        return DSL.condition("""
                        to_tsvector('simple', coalesce(offer_no, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
                        @@ plainto_tsquery('simple', ?)
                        """, normalized)
                .or(DSL.condition("""
                        lower(coalesce(offer_no, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
                        like lower(?) escape '!'
                        """, likePattern));
    }

    private String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private SortField<?> sortField(String sort) {
        return switch (normalize(sort) == null ? "recent" : normalize(sort)) {
            case "oldest" -> OFFERS.CREATED_AT.asc();
            case "title" -> OFFERS.TITLE.asc();
            default -> OFFERS.CREATED_AT.desc();
        };
    }

    private SortField<?> idSortField(String sort) {
        return "oldest".equals(normalize(sort)) || "title".equals(normalize(sort)) ? OFFERS.ID.asc() : OFFERS.ID.desc();
    }

    private Condition cursorCondition(String sort, CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        String normalized = normalize(sort) == null ? "recent" : normalize(sort);
        // 中文注释：cursor 与排序字段一一绑定，保证新增数据进入列表时翻页边界稳定。
        return switch (normalized) {
            case "oldest" -> createdAtCursor(cursor, true);
            case "title" ->
                    OFFERS.TITLE.gt(cursor.value()).or(OFFERS.TITLE.eq(cursor.value()).and(OFFERS.ID.gt(cursor.id())));
            default -> createdAtCursor(cursor, false);
        };
    }

    private Condition createdAtCursor(CursorKey cursor, boolean asc) {
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return asc
                ? OFFERS.CREATED_AT.gt(value).or(OFFERS.CREATED_AT.eq(value).and(OFFERS.ID.gt(cursor.id())))
                : OFFERS.CREATED_AT.lt(value).or(OFFERS.CREATED_AT.eq(value).and(OFFERS.ID.lt(cursor.id())));
    }

    private String cursorFor(OfferEntity offer, String sort) {
        return "title".equals(normalize(sort))
                ? CursorCodec.encode(offer.title(), offer.id())
                : CursorCodec.encode(offer.createdAt().toString(), offer.id());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private OfferEntity mapRecord(Record record) {
        return new OfferEntity(
                record.get(OFFERS.ID),
                record.get(OFFER_NO),
                record.get(OFFERS.ACTOR_ACCOUNT_ID),
                record.get(OFFERS.TITLE),
                record.get(OFFERS.DESCRIPTION),
                record.get(OFFERS.DELIVERY_STANDARD),
                record.get(OFFERS.PRICE_AMOUNT),
                record.get(OFFERS.CURRENCY),
                record.get(OFFERS.PAYMENT_METHOD),
                record.get(OFFERS.PAYMENT_PROFILE),
                record.get(OFFERS.PAYMENT_NETWORK),
                record.get(OFFERS.PAYMENT_ASSET),
                metadataString(record, "paymentRecipient"),
                PostgresJson.modelEnum(InventoryPolicy.class, record.get(OFFERS.INVENTORY_POLICY)),
                record.get(OFFERS.STOCK_TOTAL),
                record.get(OFFERS.STOCK_SOLD),
                PostgresJson.modelEnum(OfferStatus.class, record.get(OFFERS.STATUS)),
                PostgresJson.map(record.get(OFFERS.METADATA)),
                PostgresJson.instant(record.get(OFFERS.CREATED_AT)),
                PostgresJson.instant(record.get(OFFERS.UPDATED_AT)));
    }

    private String metadataString(Record record, String key) {
        // paymentRecipient 随发布 metadata 持久化，读取时还原为领域字段。
        Object value = PostgresJson.map(record.get(OFFERS.METADATA)).get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
