package com.monopolyfun.modules.post.infra.postgres;

import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.infra.MarketItemReadModelRepository;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Repository
public class PostgresMarketItemReadModelRepository implements MarketItemReadModelRepository {
    private static final Table<?> MARKET_ITEMS = DSL.table(DSL.name("market_items_read_model"));
    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> KIND = DSL.field(DSL.name("kind"), String.class);
    private static final Field<String> SOURCE_ID = DSL.field(DSL.name("source_id"), String.class);
    private static final Field<String> PUBLIC_NO = DSL.field(DSL.name("public_no"), String.class);
    private static final Field<String> ACTOR_ACCOUNT_ID = DSL.field(DSL.name("actor_account_id"), String.class);
    private static final Field<String> TITLE = DSL.field(DSL.name("title"), String.class);
    private static final Field<String> SUMMARY = DSL.field(DSL.name("summary"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> VISIBILITY = DSL.field(DSL.name("visibility"), String.class);
    private static final Field<OffsetDateTime> SORT_AT = DSL.field(DSL.name("sort_at"), OffsetDateTime.class);
    private static final Field<String> SEARCH_TEXT = DSL.field(DSL.name("search_text"), String.class);
    private static final Field<String> SEARCH_TEXT_NORMALIZED = DSL.field(DSL.name("search_text_normalized"), String.class);
    private static final Field<Object> SEARCH_VECTOR = DSL.field(DSL.name("search_vector"));
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);
    private static final Field<org.jooq.JSONB> PAYLOAD = DSL.field(DSL.name("payload"), org.jooq.JSONB.class);

    private final DSLContext dsl;

    public PostgresMarketItemReadModelRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PageResult<MarketItemRef> findPublic(String kind, String status, String q, String sort, PageQuery pageQuery) {
        // 中文注释：公开市场使用单表 cursor，all tab 拥有稳定的跨类型排序边界。
        List<MarketItemRef> fetched = dsl.select(ID, KIND, SOURCE_ID, ACTOR_ACCOUNT_ID, TITLE, SORT_AT)
                .from(MARKET_ITEMS)
                .where(VISIBILITY.eq("market_public"))
                .and(kindCondition(kind))
                .and(statusCondition(kind, status))
                .and(searchCondition(q))
                .and(cursorCondition(sort, pageQuery.cursorKey()))
                .orderBy(sortField(sort), idSortField(sort))
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRef);
        return PageResult.fromFetched(fetched, pageQuery.limit(), ref -> cursorFor(ref, sort));
    }

    @Override
    public void upsertOffer(OfferEntity offer) {
        upsert("offer:" + offer.id(), "offer", offer.id(), offer.offerNo(), offer.actorAccountId(), offer.title(), offer.description(),
                offer.status().name().toLowerCase(), visibility(offer.metadata()), offer.createdAt(), offer.updatedAt(),
                searchText(offer.offerNo(), offer.title(), offer.description()), offer.metadata());
    }

    @Override
    public void upsertRequest(RequestEntity request) {
        upsert("request:" + request.id(), "request", request.id(), request.requestNo(), request.actorAccountId(), request.title(), request.description(),
                request.status().name().toLowerCase(), visibility(request.metadata()), request.createdAt(), request.updatedAt(),
                searchText(request.requestNo(), request.title(), request.description()), request.metadata());
    }

    @Override
    public void upsertProject(ProjectEntity project) {
        if (project.projectLevel() != ProjectLevel.CHILD) {
            return;
        }
        upsert("project:" + project.id(), "project", project.id(), project.projectNo(), project.ownerAccountId(), project.title(), project.summary(),
                project.status().name().toLowerCase(), visibility(project.metadata()), project.createdAt(), project.updatedAt(),
                searchText(project.projectNo(), project.title(), project.summary(), project.oneSentence(), value(project.metadata(), "description"), value(project.metadata(), "goal"), value(project.metadata(), "deliverables")),
                project.metadata());
    }

    private void upsert(
            String id,
            String kind,
            String sourceId,
            String publicNo,
            String actorAccountId,
            String title,
            String summary,
            String status,
            String visibility,
            java.time.Instant sortAt,
            java.time.Instant updatedAt,
            String searchText,
            java.util.Map<String, Object> payload) {
        dsl.insertInto(MARKET_ITEMS)
                .columns(ID, KIND, SOURCE_ID, PUBLIC_NO, ACTOR_ACCOUNT_ID, TITLE, SUMMARY, STATUS, VISIBILITY, SORT_AT, SEARCH_TEXT, UPDATED_AT, PAYLOAD)
                .values(id, kind, sourceId, publicNo, actorAccountId, title, summary, status, visibility, PostgresJson.offsetDateTime(sortAt), searchText, PostgresJson.offsetDateTime(updatedAt), PostgresJson.jsonb(payload))
                .onConflict(ID)
                .doUpdate()
                .set(PUBLIC_NO, publicNo)
                .set(ACTOR_ACCOUNT_ID, actorAccountId)
                .set(TITLE, title)
                .set(SUMMARY, summary)
                .set(STATUS, status)
                .set(VISIBILITY, visibility)
                .set(SORT_AT, PostgresJson.offsetDateTime(sortAt))
                .set(SEARCH_TEXT, searchText)
                .set(UPDATED_AT, PostgresJson.offsetDateTime(updatedAt))
                .set(PAYLOAD, PostgresJson.jsonb(payload))
                .execute();
    }

    private Condition kindCondition(String kind) {
        String normalized = normalize(kind);
        return normalized == null || "all".equals(normalized) ? DSL.trueCondition() : KIND.eq(normalized);
    }

    private Condition statusCondition(String kind, String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return DSL.trueCondition();
        }
        String resolvedKind = normalize(kind);
        if ("project".equals(resolvedKind)) {
            return STATUS.eq(projectStatus(normalized));
        }
        if ("offer".equals(resolvedKind) || "request".equals(resolvedKind)) {
            return STATUS.eq(normalized);
        }
        return KIND.eq("project").and(STATUS.eq(projectStatus(normalized)))
                .or(KIND.in("offer", "request").and(STATUS.eq(normalized)));
    }

    private Condition searchCondition(String q) {
        String normalized = normalize(q);
        if (normalized == null) {
            return DSL.trueCondition();
        }
        String likePattern = "%" + escapeLike(normalized) + "%";
        // 中文注释：搜索命中持久化 tsvector 和归一化文本列，避免列表页按行动态拼接 listing 大字段。
        return DSL.condition("{0} @@ plainto_tsquery('simple', ?)", SEARCH_VECTOR, normalized)
                .or(SEARCH_TEXT_NORMALIZED.like(likePattern, '!'))
                .or(DSL.condition("""
                        exists (
                          select 1
                          from post_items_read_model item_snapshot
                          where item_snapshot.post_kind = market_items_read_model.kind
                            and item_snapshot.post_id = market_items_read_model.source_id
                            and (
                              item_snapshot.search_vector @@ plainto_tsquery('simple', ?)
                              or item_snapshot.search_text_normalized like ? escape '!'
                            )
                        )
                        """, normalized, likePattern));
    }

    private String escapeLike(String value) {
        return value
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private Condition cursorCondition(String sort, CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        return switch (normalize(sort) == null ? "recent" : normalize(sort)) {
            case "oldest" -> createdAtCursor(cursor, true);
            case "title" -> TITLE.gt(cursor.value()).or(TITLE.eq(cursor.value()).and(ID.gt(cursor.id())));
            default -> createdAtCursor(cursor, false);
        };
    }

    private Condition createdAtCursor(CursorKey cursor, boolean asc) {
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return asc
                ? SORT_AT.gt(value).or(SORT_AT.eq(value).and(ID.gt(cursor.id())))
                : SORT_AT.lt(value).or(SORT_AT.eq(value).and(ID.lt(cursor.id())));
    }

    private SortField<?> sortField(String sort) {
        return switch (normalize(sort) == null ? "recent" : normalize(sort)) {
            case "oldest" -> SORT_AT.asc();
            case "title" -> TITLE.asc();
            default -> SORT_AT.desc();
        };
    }

    private SortField<?> idSortField(String sort) {
        return "oldest".equals(normalize(sort)) || "title".equals(normalize(sort)) ? ID.asc() : ID.desc();
    }

    private String cursorFor(MarketItemRef ref, String sort) {
        return "title".equals(normalize(sort))
                ? CursorCodec.encode(ref.title(), ref.id())
                : CursorCodec.encode(ref.sortAt().toString(), ref.id());
    }

    private String projectStatus(String status) {
        return "open".equals(status) ? "active" : status;
    }

    private String visibility(java.util.Map<String, Object> metadata) {
        return value(metadata, "visibility").isBlank() ? "market_public" : value(metadata, "visibility");
    }

    private String value(java.util.Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String searchText(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private MarketItemRef mapRef(Record record) {
        return new MarketItemRef(
                record.get(ID),
                record.get(KIND),
                record.get(SOURCE_ID),
                record.get(ACTOR_ACCOUNT_ID),
                record.get(TITLE),
                PostgresJson.instant(record.get(SORT_AT)));
    }
}
