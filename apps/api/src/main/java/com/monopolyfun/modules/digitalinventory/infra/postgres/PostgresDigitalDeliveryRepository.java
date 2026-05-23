package com.monopolyfun.modules.digitalinventory.infra.postgres;

import com.monopolyfun.modules.digitalinventory.domain.DigitalDeliveryEntity;
import com.monopolyfun.modules.digitalinventory.infra.DigitalDeliveryRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresDigitalDeliveryRepository implements DigitalDeliveryRepository {
    private static final Table<?> DELIVERY_ATTEMPTS = DSL.table(DSL.name("delivery_attempts"));
    private static final String PROVIDER = "digital_inventory";
    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> ORDER_ID = DSL.field(DSL.name("order_id"), String.class);
    private static final Field<String> PROVIDER_FIELD = DSL.field(DSL.name("provider"), String.class);
    private static final Field<String> PROVIDER_ORDER_ID = DSL.field(DSL.name("provider_order_id"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> IDEMPOTENCY_KEY = DSL.field(DSL.name("idempotency_key"), String.class);
    private static final Field<JSONB> REQUEST_PAYLOAD = DSL.field(DSL.name("request_payload"), JSONB.class);
    private static final Field<JSONB> RECEIPT_PAYLOAD = DSL.field(DSL.name("receipt_payload"), JSONB.class);
    private static final Field<String> ERROR_MESSAGE = DSL.field(DSL.name("error_message"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresDigitalDeliveryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public DigitalDeliveryEntity save(DigitalDeliveryEntity delivery) {
        // 中文注释：数字库存交付复用 delivery_attempts，provider/idempotency_key 保证每个订单只交付一次。
        dsl.insertInto(DELIVERY_ATTEMPTS)
                .set(ID, delivery.id())
                .set(ORDER_ID, delivery.orderId())
                .set(PROVIDER_FIELD, PROVIDER)
                .set(PROVIDER_ORDER_ID, delivery.inventoryItemId())
                .set(STATUS, attemptStatus(delivery.status()))
                .set(IDEMPOTENCY_KEY, idempotencyKey(delivery.orderId()))
                .set(REQUEST_PAYLOAD, PostgresJson.jsonb(Map.of("inventoryItemId", delivery.inventoryItemId())))
                .set(RECEIPT_PAYLOAD, PostgresJson.jsonb(receiptPayload(delivery)))
                .set(ERROR_MESSAGE, delivery.errorMessage())
                .set(CREATED_AT, PostgresJson.offsetDateTime(delivery.createdAt()))
                .set(UPDATED_AT, PostgresJson.offsetDateTime(delivery.updatedAt()))
                .onConflict(PROVIDER_FIELD, IDEMPOTENCY_KEY)
                .doUpdate()
                .set(PROVIDER_ORDER_ID, delivery.inventoryItemId())
                .set(STATUS, attemptStatus(delivery.status()))
                .set(RECEIPT_PAYLOAD, PostgresJson.jsonb(receiptPayload(delivery)))
                .set(ERROR_MESSAGE, delivery.errorMessage())
                .set(UPDATED_AT, PostgresJson.offsetDateTime(delivery.updatedAt()))
                .execute();
        return findByOrderId(delivery.orderId()).orElse(delivery);
    }

    @Override
    public Optional<DigitalDeliveryEntity> findByOrderId(String orderId) {
        return dsl.selectFrom(DELIVERY_ATTEMPTS)
                .where(ORDER_ID.eq(orderId))
                .and(PROVIDER_FIELD.eq(PROVIDER))
                .orderBy(CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    private DigitalDeliveryEntity mapRecord(Record record) {
        Map<String, Object> receipt = PostgresJson.map(record.get(RECEIPT_PAYLOAD));
        return new DigitalDeliveryEntity(
                record.get(ID),
                record.get(ORDER_ID),
                record.get(PROVIDER_ORDER_ID),
                deliveryStatus(record.get(STATUS)),
                receipt,
                record.get(ERROR_MESSAGE),
                instantFromReceipt(receipt.get("deliveredAt")),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }

    private String idempotencyKey(String orderId) {
        return "digital:" + orderId;
    }

    private String attemptStatus(String status) {
        return DigitalDeliveryEntity.STATUS_DELIVERED.equals(status) ? "succeeded" : "failed";
    }

    private String deliveryStatus(String status) {
        return "succeeded".equals(status) ? DigitalDeliveryEntity.STATUS_DELIVERED : DigitalDeliveryEntity.STATUS_FAILED;
    }

    private Map<String, Object> receiptPayload(DigitalDeliveryEntity delivery) {
        Map<String, Object> payload = new LinkedHashMap<>(delivery.deliverySnapshot());
        payload.put("inventoryItemId", delivery.inventoryItemId());
        payload.put("digitalDeliveryStatus", delivery.status());
        payload.put("deliveredAt", delivery.deliveredAt() == null ? "" : delivery.deliveredAt().toString());
        return payload;
    }

    private java.time.Instant instantFromReceipt(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return java.time.Instant.parse(text);
        }
        return null;
    }
}
