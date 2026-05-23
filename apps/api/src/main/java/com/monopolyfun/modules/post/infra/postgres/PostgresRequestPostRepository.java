package com.monopolyfun.modules.post.infra.postgres;

import com.monopolyfun.modules.post.domain.InventoryPolicy;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.domain.RequestStatus;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
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

import static com.monopolyfun.generated.jooq.Tables.REQUESTS;

@Repository
public class PostgresRequestPostRepository implements RequestPostRepository {
    private static final Field<String> REQUEST_NO = DSL.field(DSL.name("request_no"), String.class);

    private final DSLContext dsl;
    private final MarketItemReadModelRepository marketItemReadModelRepository;

    public PostgresRequestPostRepository(DSLContext dsl, MarketItemReadModelRepository marketItemReadModelRepository) {
        this.dsl = dsl;
        this.marketItemReadModelRepository = marketItemReadModelRepository;
    }

    @Override
    public List<RequestEntity> findAll() {
        return dsl.select(requestFields())
                .from(REQUESTS)
                .orderBy(REQUESTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<RequestEntity> findPublic(String status, String q, String sort, PageQuery pageQuery) {
        List<RequestEntity> fetched = dsl.select(requestFields())
                .from(REQUESTS)
                .where(publicCondition().and(statusCondition(status)).and(searchCondition(q)))
                .and(cursorCondition(sort, pageQuery.cursorKey()))
                .orderBy(sortField(sort), idSortField(sort))
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), request -> cursorFor(request, sort));
    }

    @Override
    public List<RequestEntity> findPublicByActorAccountId(String actorAccountId, int limit) {
        // 中文注释：公开主页只暴露市场可见的 Request，成交后转 participant_only 的条目不会回流到公开档案。
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where(REQUESTS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .and(publicCondition())
                .orderBy(REQUESTS.CREATED_AT.desc(), REQUESTS.ID.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<RequestEntity> findByActorAccountId(String actorAccountId, int limit) {
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where(REQUESTS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .orderBy(REQUESTS.CREATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<RequestEntity> findWorkbenchCandidates(String actorAccountId, int limit) {
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where(REQUESTS.ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .and(REQUESTS.STATUS.eq(RequestStatus.OPEN.name().toLowerCase()))
                .and(REQUESTS.STOCK_FILLED.eq(0))
                .orderBy(REQUESTS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<RequestEntity> findById(String id) {
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where(REQUESTS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<RequestEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where(REQUESTS.ID.in(ids))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<RequestEntity> findByRequestNo(String requestNo) {
        // 中文注释：业务编号查询直接绑定迁移后的物理列，保持本地 codegen 滞后时也能稳定读取。
        return dsl.select(requestFields())
                .from(REQUESTS)
                .where("request_no = ?", requestNo)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public RequestEntity save(RequestEntity request) {
        dsl.insertInto(REQUESTS)
                .set(REQUESTS.ID, request.id())
                .set(REQUEST_NO, request.requestNo())
                .set(REQUESTS.ACTOR_ACCOUNT_ID, request.actorAccountId())
                .set(REQUESTS.TITLE, request.title())
                .set(REQUESTS.DESCRIPTION, request.description())
                .set(REQUESTS.DELIVERY_STANDARD, request.deliveryStandard())
                .set(REQUESTS.BUDGET_AMOUNT, request.budgetAmount())
                .set(REQUESTS.CURRENCY, request.currency())
                .set(REQUESTS.PAYMENT_METHOD, request.paymentMethod())
                .set(REQUESTS.PAYMENT_PROFILE, request.paymentProfile())
                .set(REQUESTS.PAYMENT_NETWORK, request.paymentNetwork())
                .set(REQUESTS.PAYMENT_ASSET, request.paymentAsset())
                .set(REQUESTS.INVENTORY_POLICY, request.inventoryPolicy().name().toLowerCase())
                .set(REQUESTS.STOCK_TOTAL, request.stockTotal())
                .set(REQUESTS.STOCK_FILLED, request.stockFilled())
                .set(REQUESTS.STATUS, request.status().name().toLowerCase())
                .set(REQUESTS.DEADLINE_AT, PostgresJson.offsetDateTime(request.deadlineAt()))
                .set(REQUESTS.METADATA, PostgresJson.jsonb(request.metadata()))
                .set(REQUESTS.CREATED_AT, PostgresJson.offsetDateTime(request.createdAt()))
                .set(REQUESTS.UPDATED_AT, PostgresJson.offsetDateTime(request.updatedAt()))
                .onConflict(REQUESTS.ID)
                .doUpdate()
                .set(REQUEST_NO, request.requestNo())
                .set(REQUESTS.TITLE, request.title())
                .set(REQUESTS.DESCRIPTION, request.description())
                .set(REQUESTS.DELIVERY_STANDARD, request.deliveryStandard())
                .set(REQUESTS.BUDGET_AMOUNT, request.budgetAmount())
                .set(REQUESTS.CURRENCY, request.currency())
                .set(REQUESTS.PAYMENT_METHOD, request.paymentMethod())
                .set(REQUESTS.PAYMENT_PROFILE, request.paymentProfile())
                .set(REQUESTS.PAYMENT_NETWORK, request.paymentNetwork())
                .set(REQUESTS.PAYMENT_ASSET, request.paymentAsset())
                .set(REQUESTS.INVENTORY_POLICY, request.inventoryPolicy().name().toLowerCase())
                .set(REQUESTS.STOCK_TOTAL, request.stockTotal())
                .set(REQUESTS.STOCK_FILLED, request.stockFilled())
                .set(REQUESTS.STATUS, request.status().name().toLowerCase())
                .set(REQUESTS.DEADLINE_AT, PostgresJson.offsetDateTime(request.deadlineAt()))
                .set(REQUESTS.METADATA, PostgresJson.jsonb(request.metadata()))
                .set(REQUESTS.UPDATED_AT, PostgresJson.offsetDateTime(request.updatedAt()))
                .execute();
        marketItemReadModelRepository.upsertRequest(request);
        return request;
    }

    private List<? extends SelectFieldOrAsterisk> requestFields() {
        // 中文注释：request_no 使用动态字段，迁移先行时仓库层立即具备编号读写能力。
        return List.of(REQUESTS.asterisk(), REQUEST_NO);
    }

    private Condition publicCondition() {
        // 中文注释：公开需求列表只读取市场可见条目，撮合后的 participant_only 留给订单上下文。
        return DSL.coalesce(DSL.field("requests.metadata ->> 'visibility'", String.class), "market_public").eq("market_public");
    }

    private Condition statusCondition(String status) {
        String normalized = normalize(status);
        return normalized == null ? DSL.trueCondition() : REQUESTS.STATUS.eq(normalized);
    }

    private Condition searchCondition(String q) {
        String normalized = normalize(q);
        if (normalized == null) {
            return DSL.trueCondition();
        }
        String likePattern = "%" + escapeLike(normalized) + "%";
        // 中文注释：需求列表同样需要中文短词兜底，agent 才能按“接绘图单”召回需求。
        return DSL.condition("""
                        to_tsvector('simple', coalesce(request_no, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
                        @@ plainto_tsquery('simple', ?)
                        """, normalized)
                .or(DSL.condition("""
                        lower(coalesce(request_no, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
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
            case "oldest" -> REQUESTS.CREATED_AT.asc();
            case "title" -> REQUESTS.TITLE.asc();
            default -> REQUESTS.CREATED_AT.desc();
        };
    }

    private SortField<?> idSortField(String sort) {
        return "oldest".equals(normalize(sort)) || "title".equals(normalize(sort)) ? REQUESTS.ID.asc() : REQUESTS.ID.desc();
    }

    private Condition cursorCondition(String sort, CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        String normalized = normalize(sort) == null ? "recent" : normalize(sort);
        // 中文注释：需求列表分页绑定业务排序字段，新增条目后仍保持翻页稳定。
        return switch (normalized) {
            case "oldest" -> createdAtCursor(cursor, true);
            case "title" ->
                    REQUESTS.TITLE.gt(cursor.value()).or(REQUESTS.TITLE.eq(cursor.value()).and(REQUESTS.ID.gt(cursor.id())));
            default -> createdAtCursor(cursor, false);
        };
    }

    private Condition createdAtCursor(CursorKey cursor, boolean asc) {
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return asc
                ? REQUESTS.CREATED_AT.gt(value).or(REQUESTS.CREATED_AT.eq(value).and(REQUESTS.ID.gt(cursor.id())))
                : REQUESTS.CREATED_AT.lt(value).or(REQUESTS.CREATED_AT.eq(value).and(REQUESTS.ID.lt(cursor.id())));
    }

    private String cursorFor(RequestEntity request, String sort) {
        return "title".equals(normalize(sort))
                ? CursorCodec.encode(request.title(), request.id())
                : CursorCodec.encode(request.createdAt().toString(), request.id());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private RequestEntity mapRecord(Record record) {
        return new RequestEntity(
                record.get(REQUESTS.ID),
                record.get(REQUEST_NO),
                record.get(REQUESTS.ACTOR_ACCOUNT_ID),
                record.get(REQUESTS.TITLE),
                record.get(REQUESTS.DESCRIPTION),
                record.get(REQUESTS.DELIVERY_STANDARD),
                record.get(REQUESTS.BUDGET_AMOUNT),
                record.get(REQUESTS.CURRENCY),
                record.get(REQUESTS.PAYMENT_METHOD),
                record.get(REQUESTS.PAYMENT_PROFILE),
                record.get(REQUESTS.PAYMENT_NETWORK),
                record.get(REQUESTS.PAYMENT_ASSET),
                metadataString(record, "paymentRecipient"),
                PostgresJson.modelEnum(InventoryPolicy.class, record.get(REQUESTS.INVENTORY_POLICY)),
                record.get(REQUESTS.STOCK_TOTAL),
                record.get(REQUESTS.STOCK_FILLED),
                PostgresJson.modelEnum(RequestStatus.class, record.get(REQUESTS.STATUS)),
                PostgresJson.instant(record.get(REQUESTS.DEADLINE_AT)),
                PostgresJson.map(record.get(REQUESTS.METADATA)),
                PostgresJson.instant(record.get(REQUESTS.CREATED_AT)),
                PostgresJson.instant(record.get(REQUESTS.UPDATED_AT)));
    }

    private String metadataString(Record record, String key) {
        // paymentRecipient 随发布 metadata 持久化，读取时还原为领域字段。
        Object value = PostgresJson.map(record.get(REQUESTS.METADATA)).get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }
}
