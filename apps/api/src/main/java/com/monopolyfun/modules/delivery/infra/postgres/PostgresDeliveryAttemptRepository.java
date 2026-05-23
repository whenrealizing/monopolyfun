package com.monopolyfun.modules.delivery.infra.postgres;

import com.monopolyfun.modules.delivery.domain.DeliveryAttemptEntity;
import com.monopolyfun.modules.delivery.infra.DeliveryAttemptRepository;
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
public class PostgresDeliveryAttemptRepository implements DeliveryAttemptRepository {
    private static final Table<?> DELIVERY_ATTEMPTS = DSL.table(DSL.name("delivery_attempts"));
    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> ORDER_ID = DSL.field(DSL.name("order_id"), String.class);
    private static final Field<String> PAYMENT_INTENT_ID = DSL.field(DSL.name("payment_intent_id"), String.class);
    private static final Field<String> PROVIDER = DSL.field(DSL.name("provider"), String.class);
    private static final Field<String> PROVIDER_ORDER_ID = DSL.field(DSL.name("provider_order_id"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> IDEMPOTENCY_KEY = DSL.field(DSL.name("idempotency_key"), String.class);
    private static final Field<JSONB> REQUEST_PAYLOAD = DSL.field(DSL.name("request_payload"), JSONB.class);
    private static final Field<JSONB> RECEIPT_PAYLOAD = DSL.field(DSL.name("receipt_payload"), JSONB.class);
    private static final Field<String> ERROR_MESSAGE = DSL.field(DSL.name("error_message"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresDeliveryAttemptRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<DeliveryAttemptEntity> findByProviderIdempotencyKey(String provider, String idempotencyKey) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
                .where(PROVIDER.eq(provider))
                .and(IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<DeliveryAttemptEntity> findLatestByOrderId(String orderId) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
                .where(ORDER_ID.eq(orderId))
                .orderBy(CREATED_AT.desc(), ID.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<DeliveryAttemptEntity> findByOrderId(String orderId) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
                .where(ORDER_ID.eq(orderId))
                .orderBy(CREATED_AT.desc(), ID.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public DeliveryAttemptEntity save(DeliveryAttemptEntity attempt) {
        dsl.insertInto(DELIVERY_ATTEMPTS)
                .set(ID, attempt.id())
                .set(ORDER_ID, attempt.orderId())
                .set(PAYMENT_INTENT_ID, attempt.paymentIntentId())
                .set(PROVIDER, attempt.provider())
                .set(PROVIDER_ORDER_ID, attempt.providerOrderId())
                .set(STATUS, attempt.status())
                .set(IDEMPOTENCY_KEY, attempt.idempotencyKey())
                .set(REQUEST_PAYLOAD, PostgresJson.jsonb(attempt.requestPayload()))
                .set(RECEIPT_PAYLOAD, PostgresJson.jsonb(attempt.receiptPayload()))
                .set(ERROR_MESSAGE, attempt.errorMessage())
                .set(CREATED_AT, PostgresJson.offsetDateTime(attempt.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(attempt.updatedAt()))
                .onConflict(PROVIDER, IDEMPOTENCY_KEY)
                .doUpdate()
                .set(PROVIDER_ORDER_ID, attempt.providerOrderId())
                .set(STATUS, attempt.status())
                .set(RECEIPT_PAYLOAD, PostgresJson.jsonb(attempt.receiptPayload()))
                .set(ERROR_MESSAGE, attempt.errorMessage())
                .set(UPDATED_AT, PostgresJson.offsetDateTime(attempt.updatedAt()))
                .execute();
        return findByProviderIdempotencyKey(attempt.provider(), attempt.idempotencyKey()).orElse(attempt);
    }

    @Override
    public List<DeliveryAttemptEntity> findRecent(int limit) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
                .orderBy(UPDATED_AT.desc(), ID.desc())
                .limit(Math.max(1, Math.min(limit, 200)))
                .fetch(this::mapRecord);
    }

    private DeliveryAttemptEntity mapRecord(Record record) {
        return new DeliveryAttemptEntity(
                record.get(ID),
                record.get(ORDER_ID),
                record.get(PAYMENT_INTENT_ID),
                record.get(PROVIDER),
                record.get(PROVIDER_ORDER_ID),
                record.get(STATUS),
                record.get(IDEMPOTENCY_KEY),
                PostgresJson.map(record.get(REQUEST_PAYLOAD)),
                PostgresJson.map(record.get(RECEIPT_PAYLOAD)),
                record.get(ERROR_MESSAGE),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }
}
