package com.monopolyfun.modules.work.infra.postgres;

import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkCommerceTrustRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Repository
public class PostgresWorkCommerceTrustRepository implements WorkCommerceTrustRepository {
    private static final Table<?> WORK_TRUST_EVENTS = DSL.table(DSL.name("work_trust_events"));
    private static final Table<?> ORDERS = DSL.table(DSL.name("orders"));

    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> EVENT_NO = DSL.field(DSL.name("event_no"), String.class);
    private static final Field<String> EVENT_TYPE = DSL.field(DSL.name("event_type"), String.class);
    private static final Field<String> WORK_RUN_ID = DSL.field(DSL.name("work_run_id"), String.class);
    private static final Field<String> REVIEW_ID = DSL.field(DSL.name("review_id"), String.class);
    private static final Field<String> ORDER_ID = DSL.field(DSL.name("order_id"), String.class);
    private static final Field<String> ACTOR_ACCOUNT_ID = DSL.field(DSL.name("actor_account_id"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> REASON = DSL.field(DSL.name("reason"), String.class);
    private static final Field<JSONB> INPUT_SNAPSHOT = DSL.field(DSL.name("input_snapshot"), JSONB.class);
    private static final Field<JSONB> OUTPUT_SNAPSHOT = DSL.field(DSL.name("output_snapshot"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private static final Field<String> ORD_ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> ORD_ORDER_NO = DSL.field(DSL.name("order_no"), String.class);

    private final DSLContext dsl;

    public PostgresWorkCommerceTrustRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void savePaymentAuthorization(
            WorkRunEntity run,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            Map<String, Object> input,
            Map<String, Object> output) {
        OffsetDateTime now = PostgresJson.offsetDateTime(Instant.now());
        saveTrustEvent("payment_authorization", "pa", run, null, item, actorAccountId, status, null, input, output, now);
    }

    @Override
    public void saveSettlementRecord(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            Map<String, Object> input,
            Map<String, Object> output) {
        OffsetDateTime now = PostgresJson.offsetDateTime(Instant.now());
        saveTrustEvent("settlement", "sr", run, review, item, actorAccountId, status, null, input, output, now);
    }

    @Override
    public void saveAfterSaleCase(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            String reason,
            Map<String, Object> input) {
        OffsetDateTime now = PostgresJson.offsetDateTime(Instant.now());
        saveTrustEvent("after_sale", "asc", run, review, item, actorAccountId, status, reason, input,
                Map.of("itemNo", item.itemNo(), "sourceType", item.sourceType(), "sourceId", item.sourceId()), now);
    }

    @Override
    public void saveArbitrationCase(
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            String reason,
            Map<String, Object> input) {
        OffsetDateTime now = PostgresJson.offsetDateTime(Instant.now());
        saveTrustEvent("arbitration", "arb", run, review, item, actorAccountId, status, reason, input,
                Map.of("itemNo", item.itemNo(), "sourceType", item.sourceType(), "sourceId", item.sourceId()), now);
    }

    private void saveTrustEvent(
            String eventType,
            String prefix,
            WorkRunEntity run,
            WorkReviewEntity review,
            WorkItemEntity item,
            String actorAccountId,
            String status,
            String reason,
            Map<String, Object> input,
            Map<String, Object> output,
            OffsetDateTime now) {
        // 中文注释：所有信任动作落到一张事件表，event_type 承担业务分类，WorkRun 承担生命周期锚点。
        dsl.insertInto(WORK_TRUST_EVENTS)
                .set(ID, prefix + "-" + UUID.randomUUID())
                .set(EVENT_NO, prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .set(EVENT_TYPE, eventType)
                .set(WORK_RUN_ID, run.id())
                .set(REVIEW_ID, review == null ? null : review.id())
                .set(ORDER_ID, orderId(item))
                .set(ACTOR_ACCOUNT_ID, actorAccountId)
                .set(STATUS, status)
                .set(REASON, reason)
                .set(INPUT_SNAPSHOT, PostgresJson.jsonb(input == null ? Map.of() : input))
                .set(OUTPUT_SNAPSHOT, PostgresJson.jsonb(output == null ? Map.of() : output))
                .set(CREATED_AT, now)
                .set(UPDATED_AT, now)
                .execute();
    }

    private String orderId(WorkItemEntity item) {
        if (!"order".equals(item.sourceType())) {
            return null;
        }
        return dsl.select(ORD_ID)
                .from(ORDERS)
                .where(ORD_ORDER_NO.eq(item.sourceId()).or(ORD_ID.eq(item.sourceId())))
                .limit(1)
                .fetchOne(ORD_ID);
    }
}
