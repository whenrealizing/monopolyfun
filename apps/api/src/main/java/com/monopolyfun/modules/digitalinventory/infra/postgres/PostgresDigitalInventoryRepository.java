package com.monopolyfun.modules.digitalinventory.infra.postgres;

import com.monopolyfun.modules.digitalinventory.domain.DigitalInventoryItemEntity;
import com.monopolyfun.modules.digitalinventory.infra.DigitalInventoryRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostgresDigitalInventoryRepository implements DigitalInventoryRepository {
    private static final Table<?> DIGITAL_INVENTORY_ITEMS = DSL.table(DSL.name("digital_inventory_items"));
    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> LISTING_ID = DSL.field(DSL.name("listing_id"), String.class);
    private static final Field<String> ENCRYPTED_PAYLOAD = DSL.field(DSL.name("encrypted_payload"), String.class);
    private static final Field<String> PAYLOAD_PREVIEW = DSL.field(DSL.name("payload_preview"), String.class);
    private static final Field<String> PAYLOAD_HASH = DSL.field(DSL.name("payload_hash"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> RESERVED_ORDER_ID = DSL.field(DSL.name("reserved_order_id"), String.class);
    private static final Field<String> DELIVERED_ORDER_ID = DSL.field(DSL.name("delivered_order_id"), String.class);
    private static final Field<String> CREATED_BY_ACCOUNT_ID = DSL.field(DSL.name("created_by_account_id"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresDigitalInventoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public DigitalInventoryItemEntity save(DigitalInventoryItemEntity item) {
        try {
            dsl.insertInto(DIGITAL_INVENTORY_ITEMS)
                    .set(ID, item.id())
                    .set(LISTING_ID, item.listingId())
                    .set(ENCRYPTED_PAYLOAD, item.encryptedPayload())
                    .set(PAYLOAD_PREVIEW, item.payloadPreview())
                    .set(PAYLOAD_HASH, item.payloadHash())
                    .set(STATUS, item.status())
                    .set(RESERVED_ORDER_ID, item.reservedOrderId())
                    .set(DELIVERED_ORDER_ID, item.deliveredOrderId())
                    .set(CREATED_BY_ACCOUNT_ID, item.createdByAccountId())
                    .set(CREATED_AT, PostgresJson.offsetDateTime(item.createdAt()))
                    .set(UPDATED_AT, PostgresJson.offsetDateTime(item.updatedAt()))
                    .onConflict(ID)
                    .doUpdate()
                    .set(STATUS, item.status())
                    .set(RESERVED_ORDER_ID, item.reservedOrderId())
                    .set(DELIVERED_ORDER_ID, item.deliveredOrderId())
                    .set(UPDATED_AT, PostgresJson.offsetDateTime(item.updatedAt()))
                    .execute();
            return findById(item.id()).orElse(item);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Digital inventory payload already exists for this item");
        }
    }

    @Override
    public List<DigitalInventoryItemEntity> saveAll(List<DigitalInventoryItemEntity> items) {
        return items.stream().map(this::save).toList();
    }

    @Override
    public Optional<DigitalInventoryItemEntity> reserveAvailable(String listingId, String orderId, Instant now) {
        Record record = dsl.selectFrom(DIGITAL_INVENTORY_ITEMS)
                .where(LISTING_ID.eq(listingId))
                .and(STATUS.eq(DigitalInventoryItemEntity.STATUS_AVAILABLE))
                .orderBy(CREATED_AT.asc(), ID.asc())
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        DigitalInventoryItemEntity reserved = mapRecord(record).withReserved(orderId, now);
        save(reserved);
        return findById(reserved.id());
    }

    @Override
    public Optional<DigitalInventoryItemEntity> findReservedByOrderId(String orderId) {
        return dsl.selectFrom(DIGITAL_INVENTORY_ITEMS)
                .where(RESERVED_ORDER_ID.eq(orderId))
                .and(STATUS.eq(DigitalInventoryItemEntity.STATUS_RESERVED))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<DigitalInventoryItemEntity> findDeliveredByOrderId(String orderId) {
        return dsl.selectFrom(DIGITAL_INVENTORY_ITEMS)
                .where(DELIVERED_ORDER_ID.eq(orderId))
                .and(STATUS.eq(DigitalInventoryItemEntity.STATUS_DELIVERED))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<DigitalInventoryItemEntity> findById(String id) {
        return dsl.selectFrom(DIGITAL_INVENTORY_ITEMS)
                .where(ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Map<String, Integer> countByListingId(String listingId) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put(DigitalInventoryItemEntity.STATUS_AVAILABLE, 0);
        counts.put(DigitalInventoryItemEntity.STATUS_RESERVED, 0);
        counts.put(DigitalInventoryItemEntity.STATUS_DELIVERED, 0);
        counts.put(DigitalInventoryItemEntity.STATUS_VOIDED, 0);
        dsl.select(STATUS, DSL.count())
                .from(DIGITAL_INVENTORY_ITEMS)
                .where(LISTING_ID.eq(listingId))
                .groupBy(STATUS)
                .fetch()
                .forEach(record -> counts.put(record.get(STATUS), record.get(DSL.count()).intValue()));
        return Map.copyOf(counts);
    }

    private DigitalInventoryItemEntity mapRecord(Record record) {
        return new DigitalInventoryItemEntity(
                record.get(ID),
                record.get(LISTING_ID),
                record.get(ENCRYPTED_PAYLOAD),
                record.get(PAYLOAD_PREVIEW),
                record.get(PAYLOAD_HASH),
                record.get(STATUS),
                record.get(RESERVED_ORDER_ID),
                record.get(DELIVERED_ORDER_ID),
                record.get(CREATED_BY_ACCOUNT_ID),
                PostgresJson.instant(record.get(CREATED_AT)),
                PostgresJson.instant(record.get(UPDATED_AT)));
    }
}
