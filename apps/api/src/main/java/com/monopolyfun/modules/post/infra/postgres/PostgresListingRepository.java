package com.monopolyfun.modules.post.infra.postgres;

import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.ListingStatus;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.infra.ListingRepository;
import com.monopolyfun.modules.projection.PostItemProjectionWriter;
import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.LISTINGS;

@Repository
public class PostgresListingRepository implements ListingRepository {
    private static final Table<?> POST_ITEMS_READ_MODEL = DSL.table(DSL.name("post_items_read_model"));
    private static final Field<String> PIRM_ITEM_ID = DSL.field(DSL.name("post_items_read_model", "item_id"), String.class);
    private static final Field<String> PIRM_POST_KIND = DSL.field(DSL.name("post_items_read_model", "post_kind"), String.class);
    private static final Field<String> PIRM_POST_ID = DSL.field(DSL.name("post_items_read_model", "post_id"), String.class);
    private static final Field<String> PIRM_LATEST_ORDER_ID = DSL.field(DSL.name("post_items_read_model", "latest_order_id"), String.class);
    private static final Field<String> PIRM_LATEST_PAYMENT_STATUS = DSL.field(DSL.name("post_items_read_model", "latest_payment_status"), String.class);
    private static final Field<Integer> PUBLISHED_ITEM_POSITION =
            DSL.field("coalesce((listings.metadata ->> 'publishedItemPosition')::int, 2147483647)", Integer.class);

    private final DSLContext dsl;
    private final PostItemProjectionWriter postItemProjectionWriter;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresListingRepository(
            DSLContext dsl,
            PostItemProjectionWriter postItemProjectionWriter,
            ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.postItemProjectionWriter = postItemProjectionWriter;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<ListingEntity> findAll() {
        return dsl.selectFrom(LISTINGS)
                .orderBy(LISTINGS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(LISTINGS.STATUS, org.jooq.impl.DSL.count())
                .from(LISTINGS)
                .groupBy(LISTINGS.STATUS)
                .fetch(record -> counts.put(String.valueOf(record.value1()).toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public List<ListingEntity> findByMarketId(String marketId) {
        return dsl.selectFrom(LISTINGS)
                .where(LISTINGS.MARKET_ID.eq(marketId))
                .orderBy(LISTINGS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public List<PostItemListing> findPostItems(PostKind postKind, String postId) {
        return findPostItems(postKind, List.of(postId));
    }

    @Override
    public List<PostItemListing> findPostItems(PostKind postKind, List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        // 中文注释：Post workspace 直接按 post_kind/post_id 读取条目读模型，避免扫 market 后再解析 listing metadata。
        return dsl.select(LISTINGS.asterisk())
                .select(PIRM_LATEST_ORDER_ID, PIRM_LATEST_PAYMENT_STATUS)
                .from(POST_ITEMS_READ_MODEL)
                .join(LISTINGS).on(LISTINGS.ID.eq(PIRM_ITEM_ID))
                .where(PIRM_POST_KIND.eq(postKind.name().toLowerCase()))
                .and(PIRM_POST_ID.in(postIds))
                .orderBy(PIRM_POST_ID.asc(), LISTINGS.CREATED_AT.asc(), PUBLISHED_ITEM_POSITION.asc(), LISTINGS.ID.asc())
                .fetch(this::mapPostItemListing);
    }

    @Override
    public Optional<ListingEntity> findById(String id) {
        return dsl.selectFrom(LISTINGS)
                .where(LISTINGS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ListingEntity> findByIdForUpdate(String id) {
        return dsl.selectFrom(LISTINGS)
                .where(LISTINGS.ID.eq(id))
                .forUpdate()
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<PostItemListing> findPostItemById(String itemId) {
        return dsl.select(LISTINGS.asterisk())
                .select(PIRM_LATEST_ORDER_ID, PIRM_LATEST_PAYMENT_STATUS)
                .from(POST_ITEMS_READ_MODEL)
                .join(LISTINGS).on(LISTINGS.ID.eq(PIRM_ITEM_ID))
                .where(PIRM_ITEM_ID.eq(itemId))
                .fetchOptional(this::mapPostItemListing);
    }

    @Override
    public ListingEntity save(ListingEntity listing) {
        dsl.insertInto(LISTINGS)
                .set(LISTINGS.ID, listing.id())
                .set(LISTINGS.MARKET_ID, listing.marketId())
                .set(LISTINGS.KIND, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingKind.class, listing.kind()))
                .set(LISTINGS.PARENT_ORDER_ID, listing.parentOrderId())
                .set(LISTINGS.TITLE, listing.title())
                .set(LISTINGS.SUBJECT_TYPE, listing.subjectType())
                .set(LISTINGS.SUBJECT_REF, listing.subjectRef())
                .set(LISTINGS.DELIVERABLE_SPEC, listing.deliverableSpec())
                .set(LISTINGS.PROOF_SPEC, listing.proofSpec())
                .set(LISTINGS.SETTLEMENT_SPEC, listing.settlementSpec())
                .set(LISTINGS.INVENTORY_LIMIT, listing.inventoryLimit())
                .set(LISTINGS.ACTIVE_ORDERS_COUNT, listing.activeOrdersCount())
                .set(LISTINGS.STOCK_TOTAL, listing.stockTotal())
                .set(LISTINGS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, listing.settlementType()))
                .set(LISTINGS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingStatus.class, listing.status()))
                .set(LISTINGS.OPENED_BY_ACCOUNT_ID, listing.openedByAccountId())
                .set(LISTINGS.METADATA, PostgresJson.jsonb(listing.metadata()))
                .set(LISTINGS.CREATED_AT, PostgresJson.offsetDateTime(listing.createdAt()))
                .set(LISTINGS.UPDATED_AT, PostgresJson.offsetDateTime(listing.updatedAt()))
                .onConflict(LISTINGS.ID)
                .doUpdate()
                .set(LISTINGS.MARKET_ID, listing.marketId())
                .set(LISTINGS.KIND, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingKind.class, listing.kind()))
                .set(LISTINGS.PARENT_ORDER_ID, listing.parentOrderId())
                .set(LISTINGS.TITLE, listing.title())
                .set(LISTINGS.SUBJECT_TYPE, listing.subjectType())
                .set(LISTINGS.SUBJECT_REF, listing.subjectRef())
                .set(LISTINGS.DELIVERABLE_SPEC, listing.deliverableSpec())
                .set(LISTINGS.PROOF_SPEC, listing.proofSpec())
                .set(LISTINGS.SETTLEMENT_SPEC, listing.settlementSpec())
                .set(LISTINGS.INVENTORY_LIMIT, listing.inventoryLimit())
                .set(LISTINGS.ACTIVE_ORDERS_COUNT, listing.activeOrdersCount())
                .set(LISTINGS.STOCK_TOTAL, listing.stockTotal())
                .set(LISTINGS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, listing.settlementType()))
                .set(LISTINGS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingStatus.class, listing.status()))
                .set(LISTINGS.OPENED_BY_ACCOUNT_ID, listing.openedByAccountId())
                .set(LISTINGS.METADATA, PostgresJson.jsonb(listing.metadata()))
                .set(LISTINGS.UPDATED_AT, PostgresJson.offsetDateTime(listing.updatedAt()))
                .execute();
        postItemProjectionWriter.syncListingState(listing);
        projectTimelineProjectionWriter.syncListingItem(listing);
        return listing;
    }

    private PostItemListing mapPostItemListing(Record record) {
        return new PostItemListing(
                mapRecord(record),
                record.get(PIRM_LATEST_ORDER_ID),
                record.get(PIRM_LATEST_PAYMENT_STATUS));
    }

    private ListingEntity mapRecord(Record record) {
        return new ListingEntity(
                record.get(LISTINGS.ID),
                record.get(LISTINGS.MARKET_ID),
                PostgresJson.modelEnum(ListingKind.class, record.get(LISTINGS.KIND)),
                record.get(LISTINGS.PARENT_ORDER_ID),
                record.get(LISTINGS.TITLE),
                record.get(LISTINGS.SUBJECT_TYPE),
                record.get(LISTINGS.SUBJECT_REF),
                record.get(LISTINGS.DELIVERABLE_SPEC),
                record.get(LISTINGS.PROOF_SPEC),
                record.get(LISTINGS.SETTLEMENT_SPEC),
                record.get(LISTINGS.INVENTORY_LIMIT),
                record.get(LISTINGS.ACTIVE_ORDERS_COUNT),
                record.get(LISTINGS.STOCK_TOTAL),
                PostgresJson.modelEnum(SettlementType.class, record.get(LISTINGS.SETTLEMENT_TYPE)),
                PostgresJson.modelEnum(ListingStatus.class, record.get(LISTINGS.STATUS)),
                record.get(LISTINGS.OPENED_BY_ACCOUNT_ID),
                PostgresJson.map(record.get(LISTINGS.METADATA)),
                PostgresJson.instant(record.get(LISTINGS.CREATED_AT)),
                PostgresJson.instant(record.get(LISTINGS.UPDATED_AT)));
    }
}
