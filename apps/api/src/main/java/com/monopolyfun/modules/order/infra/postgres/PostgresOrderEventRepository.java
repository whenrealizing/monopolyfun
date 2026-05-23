package com.monopolyfun.modules.order.infra.postgres;

import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.monopolyfun.generated.jooq.Tables.ORDER_EVENTS;

@Repository
public class PostgresOrderEventRepository implements OrderEventRepository {
    private final DSLContext dsl;

    public PostgresOrderEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<OrderEventEntity> findByOrderId(String orderId) {
        return dsl.selectFrom(ORDER_EVENTS)
                .where(ORDER_EVENTS.ORDER_ID.eq(orderId))
                .orderBy(ORDER_EVENTS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public OrderEventEntity save(OrderEventEntity event) {
        dsl.insertInto(ORDER_EVENTS)
                .set(ORDER_EVENTS.ID, event.id())
                .set(ORDER_EVENTS.ORDER_ID, event.orderId())
                .set(ORDER_EVENTS.EVENT_TYPE, event.eventType())
                .set(ORDER_EVENTS.ACTOR_ACCOUNT_ID, event.actorAccountId())
                .set(ORDER_EVENTS.PAYLOAD, PostgresJson.jsonb(event.payload()))
                .set(ORDER_EVENTS.CREATED_AT, PostgresJson.offsetDateTime(event.createdAt()))
                .onConflict(ORDER_EVENTS.ID)
                .doUpdate()
                .set(ORDER_EVENTS.ORDER_ID, event.orderId())
                .set(ORDER_EVENTS.EVENT_TYPE, event.eventType())
                .set(ORDER_EVENTS.ACTOR_ACCOUNT_ID, event.actorAccountId())
                .set(ORDER_EVENTS.PAYLOAD, PostgresJson.jsonb(event.payload()))
                .execute();
        return event;
    }

    private OrderEventEntity mapRecord(Record record) {
        return new OrderEventEntity(
                record.get(ORDER_EVENTS.ID),
                record.get(ORDER_EVENTS.ORDER_ID),
                record.get(ORDER_EVENTS.EVENT_TYPE),
                record.get(ORDER_EVENTS.ACTOR_ACCOUNT_ID),
                PostgresJson.map(record.get(ORDER_EVENTS.PAYLOAD)),
                PostgresJson.instant(record.get(ORDER_EVENTS.CREATED_AT)));
    }
}
