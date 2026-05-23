package com.monopolyfun.modules.payment.infra.postgres;

import com.monopolyfun.modules.payment.domain.PaymentProviderEventEntity;
import com.monopolyfun.modules.payment.infra.PaymentProviderEventRepository;
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
public class PostgresPaymentProviderEventRepository implements PaymentProviderEventRepository {
    private static final Table<?> PAYMENT_PROVIDER_EVENTS = DSL.table(DSL.name("payment_provider_events"));
    private static final Field<String> ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> PROVIDER = DSL.field(DSL.name("provider"), String.class);
    private static final Field<String> PROVIDER_EVENT_ID = DSL.field(DSL.name("provider_event_id"), String.class);
    private static final Field<String> PAYMENT_INTENT_ID = DSL.field(DSL.name("payment_intent_id"), String.class);
    private static final Field<String> PROVIDER_PAYMENT_REF = DSL.field(DSL.name("provider_payment_ref"), String.class);
    private static final Field<String> TX_HASH = DSL.field(DSL.name("tx_hash"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<JSONB> PAYLOAD = DSL.field(DSL.name("payload"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresPaymentProviderEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<PaymentProviderEventEntity> findByProviderEventId(String provider, String providerEventId) {
        return dsl.selectFrom(PAYMENT_PROVIDER_EVENTS)
                .where(PROVIDER.eq(provider))
                .and(PROVIDER_EVENT_ID.eq(providerEventId))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public PaymentProviderEventEntity save(PaymentProviderEventEntity event) {
        dsl.insertInto(PAYMENT_PROVIDER_EVENTS)
                .set(ID, event.id())
                .set(PROVIDER, event.provider())
                .set(PROVIDER_EVENT_ID, event.providerEventId())
                .set(PAYMENT_INTENT_ID, event.paymentIntentId())
                .set(PROVIDER_PAYMENT_REF, event.providerPaymentRef())
                .set(TX_HASH, event.txHash())
                .set(STATUS, event.status())
                .set(PAYLOAD, PostgresJson.jsonb(event.payload()))
                .set(CREATED_AT, PostgresJson.offsetDateTime(event.createdAt()))
                .onConflict(PROVIDER, PROVIDER_EVENT_ID)
                .doNothing()
                .execute();
        return findByProviderEventId(event.provider(), event.providerEventId()).orElse(event);
    }

    @Override
    public List<PaymentProviderEventEntity> findRecent(int limit) {
        return dsl.selectFrom(PAYMENT_PROVIDER_EVENTS)
                .orderBy(CREATED_AT.desc(), ID.desc())
                .limit(Math.max(1, Math.min(limit, 200)))
                .fetch(this::mapRecord);
    }

    private PaymentProviderEventEntity mapRecord(Record record) {
        return new PaymentProviderEventEntity(
                record.get(ID),
                record.get(PROVIDER),
                record.get(PROVIDER_EVENT_ID),
                record.get(PAYMENT_INTENT_ID),
                record.get(PROVIDER_PAYMENT_REF),
                record.get(TX_HASH),
                record.get(STATUS),
                PostgresJson.map(record.get(PAYLOAD)),
                PostgresJson.instant(record.get(CREATED_AT)));
    }
}
