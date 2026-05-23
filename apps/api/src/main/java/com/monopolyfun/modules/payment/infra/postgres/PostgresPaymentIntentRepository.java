package com.monopolyfun.modules.payment.infra.postgres;

import com.monopolyfun.generated.jooq.tables.records.PaymentIntentsRecord;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.projection.OrderPaymentStateWriter;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.PAYMENT_INTENTS;

@Repository
public class PostgresPaymentIntentRepository implements PaymentIntentRepository {
    private static final Table<?> ORDER_PAYMENT_STATE = DSL.table(DSL.name("order_payment_state"));
    private static final Field<String> OPS_ORDER_ID = DSL.field(DSL.name("order_payment_state", "order_id"), String.class);
    private static final Field<String> OPS_LATEST_PAYMENT_INTENT_ID = DSL.field(DSL.name("order_payment_state", "latest_payment_intent_id"), String.class);

    private final DSLContext dsl;
    private final OrderPaymentStateWriter orderPaymentStateWriter;

    public PostgresPaymentIntentRepository(DSLContext dsl, OrderPaymentStateWriter orderPaymentStateWriter) {
        this.dsl = dsl;
        this.orderPaymentStateWriter = orderPaymentStateWriter;
    }

    @Override
    public Optional<PaymentIntentEntity> findById(String id) {
        return dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .where(PAYMENT_INTENTS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<PaymentIntentEntity> findByPaymentNo(String paymentNo) {
        return dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .where(PAYMENT_INTENTS.PAYMENT_NO.eq(paymentNo))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<PaymentIntentEntity> findByOrderId(String orderId) {
        // 中文注释：订单当前支付态从 order_payment_state 读取，详情页和工作台共享同一条最新支付事实。
        return dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .leftJoin(ORDER_PAYMENT_STATE).on(OPS_ORDER_ID.eq(PAYMENT_INTENTS.ORDER_ID))
                .where(PAYMENT_INTENTS.ORDER_ID.eq(orderId))
                .and(OPS_LATEST_PAYMENT_INTENT_ID.eq(PAYMENT_INTENTS.ID).or(OPS_ORDER_ID.isNull()))
                .orderBy(PAYMENT_INTENTS.CREATED_AT.desc(), PAYMENT_INTENTS.ID.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Map<String, PaymentIntentEntity> findByOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        // 中文注释：Workbench 批量读取订单当前支付态，避免按候选订单逐条查询 payment_intents。
        Map<String, PaymentIntentEntity> intents = new LinkedHashMap<>();
        dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .leftJoin(ORDER_PAYMENT_STATE).on(OPS_ORDER_ID.eq(PAYMENT_INTENTS.ORDER_ID))
                .where(PAYMENT_INTENTS.ORDER_ID.in(orderIds))
                .and(OPS_LATEST_PAYMENT_INTENT_ID.eq(PAYMENT_INTENTS.ID).or(OPS_ORDER_ID.isNull()))
                .orderBy(PAYMENT_INTENTS.ORDER_ID.asc(), PAYMENT_INTENTS.CREATED_AT.desc(), PAYMENT_INTENTS.ID.desc())
                .fetch(record -> {
                    PaymentIntentEntity intent = mapRecord(record);
                    intents.putIfAbsent(intent.orderId(), intent);
                    return intent;
                });
        return intents;
    }

    @Override
    public List<PaymentIntentEntity> findAll() {
        return dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .orderBy(PAYMENT_INTENTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(PAYMENT_INTENTS.STATUS, org.jooq.impl.DSL.count())
                .from(PAYMENT_INTENTS)
                .groupBy(PAYMENT_INTENTS.STATUS)
                .fetch(record -> counts.put(String.valueOf(record.value1()).toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public List<PaymentIntentEntity> findRecent(int limit) {
        return dsl.select(paymentIntentFields())
                .from(PAYMENT_INTENTS)
                .orderBy(PAYMENT_INTENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    @Override
    public PaymentIntentEntity save(PaymentIntentEntity paymentIntent) {
        dsl.insertInto(PAYMENT_INTENTS)
                .set(PAYMENT_INTENTS.ID, paymentIntent.id())
                .set(PAYMENT_INTENTS.PAYMENT_NO, paymentIntent.paymentNo())
                .set(PAYMENT_INTENTS.ORDER_ID, paymentIntent.orderId())
                .set(PAYMENT_INTENTS.ACCOUNT_ID, paymentIntent.accountId())
                .set(PAYMENT_INTENTS.PROVIDER, paymentIntent.provider())
                .set(PAYMENT_INTENTS.PROVIDER_PAYMENT_REF, paymentIntent.providerPaymentRef())
                .set(PAYMENT_INTENTS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.class, paymentIntent.status()))
                .set(PAYMENT_INTENTS.AMOUNT_MINOR, paymentIntent.amountMinor())
                .set(PAYMENT_INTENTS.CURRENCY, paymentIntent.currency())
                .set(PAYMENT_INTENTS.CALLBACK_TOKEN, paymentIntent.callbackToken())
                .set(PAYMENT_INTENTS.AUTHORIZED_AT, PostgresJson.offsetDateTime(paymentIntent.authorizedAt()))
                .set(PAYMENT_INTENTS.CAPTURED_AT, PostgresJson.offsetDateTime(paymentIntent.capturedAt()))
                .set(PAYMENT_INTENTS.REFUNDED_AT, PostgresJson.offsetDateTime(paymentIntent.refundedAt()))
                .set(PAYMENT_INTENTS.CANCELLED_AT, PostgresJson.offsetDateTime(paymentIntent.cancelledAt()))
                .set(PAYMENT_INTENTS.DISPUTED_AT, PostgresJson.offsetDateTime(paymentIntent.disputedAt()))
                .set(PAYMENT_INTENTS.METADATA, PostgresJson.jsonb(paymentIntent.metadata()))
                .set(PAYMENT_INTENTS.CREATED_AT, PostgresJson.offsetDateTime(paymentIntent.createdAt()))
                .set(PAYMENT_INTENTS.UPDATED_AT, PostgresJson.offsetDateTime(paymentIntent.updatedAt()))
                .onConflict(PAYMENT_INTENTS.ID)
                .doUpdate()
                .set(PAYMENT_INTENTS.PROVIDER_PAYMENT_REF, paymentIntent.providerPaymentRef())
                .set(PAYMENT_INTENTS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.class, paymentIntent.status()))
                .set(PAYMENT_INTENTS.AMOUNT_MINOR, paymentIntent.amountMinor())
                .set(PAYMENT_INTENTS.CURRENCY, paymentIntent.currency())
                .set(PAYMENT_INTENTS.CALLBACK_TOKEN, paymentIntent.callbackToken())
                .set(PAYMENT_INTENTS.AUTHORIZED_AT, PostgresJson.offsetDateTime(paymentIntent.authorizedAt()))
                .set(PAYMENT_INTENTS.CAPTURED_AT, PostgresJson.offsetDateTime(paymentIntent.capturedAt()))
                .set(PAYMENT_INTENTS.REFUNDED_AT, PostgresJson.offsetDateTime(paymentIntent.refundedAt()))
                .set(PAYMENT_INTENTS.CANCELLED_AT, PostgresJson.offsetDateTime(paymentIntent.cancelledAt()))
                .set(PAYMENT_INTENTS.DISPUTED_AT, PostgresJson.offsetDateTime(paymentIntent.disputedAt()))
                .set(PAYMENT_INTENTS.METADATA, PostgresJson.jsonb(paymentIntent.metadata()))
                .set(PAYMENT_INTENTS.UPDATED_AT, PostgresJson.offsetDateTime(paymentIntent.updatedAt()))
                .execute();
        orderPaymentStateWriter.upsert(paymentIntent);
        return paymentIntent;
    }

    private List<? extends SelectFieldOrAsterisk> paymentIntentFields() {
        // 固定读取支付实体使用的列，防止本机 codegen schema 把扩展编号列带入运行查询。
        return List.<TableField<PaymentIntentsRecord, ?>>of(
                PAYMENT_INTENTS.ID,
                PAYMENT_INTENTS.PAYMENT_NO,
                PAYMENT_INTENTS.ORDER_ID,
                PAYMENT_INTENTS.ACCOUNT_ID,
                PAYMENT_INTENTS.PROVIDER,
                PAYMENT_INTENTS.PROVIDER_PAYMENT_REF,
                PAYMENT_INTENTS.STATUS,
                PAYMENT_INTENTS.AMOUNT_MINOR,
                PAYMENT_INTENTS.CURRENCY,
                PAYMENT_INTENTS.CALLBACK_TOKEN,
                PAYMENT_INTENTS.AUTHORIZED_AT,
                PAYMENT_INTENTS.CAPTURED_AT,
                PAYMENT_INTENTS.REFUNDED_AT,
                PAYMENT_INTENTS.CANCELLED_AT,
                PAYMENT_INTENTS.DISPUTED_AT,
                PAYMENT_INTENTS.METADATA,
                PAYMENT_INTENTS.CREATED_AT,
                PAYMENT_INTENTS.UPDATED_AT);
    }

    private PaymentIntentEntity mapRecord(Record record) {
        return new PaymentIntentEntity(
                record.get(PAYMENT_INTENTS.ID),
                record.get(PAYMENT_INTENTS.PAYMENT_NO),
                record.get(PAYMENT_INTENTS.ORDER_ID),
                record.get(PAYMENT_INTENTS.ACCOUNT_ID),
                record.get(PAYMENT_INTENTS.PROVIDER),
                record.get(PAYMENT_INTENTS.PROVIDER_PAYMENT_REF),
                PostgresJson.modelEnum(PaymentIntentStatus.class, record.get(PAYMENT_INTENTS.STATUS)),
                record.get(PAYMENT_INTENTS.AMOUNT_MINOR),
                record.get(PAYMENT_INTENTS.CURRENCY),
                record.get(PAYMENT_INTENTS.CALLBACK_TOKEN),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.AUTHORIZED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CAPTURED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.REFUNDED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CANCELLED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.DISPUTED_AT)),
                PostgresJson.map(record.get(PAYMENT_INTENTS.METADATA)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CREATED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.UPDATED_AT)));
    }
}
