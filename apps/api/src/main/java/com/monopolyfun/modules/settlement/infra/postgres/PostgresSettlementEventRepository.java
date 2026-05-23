package com.monopolyfun.modules.settlement.infra.postgres;

import com.monopolyfun.modules.settlement.domain.SettlementEventEntity;
import com.monopolyfun.modules.settlement.infra.SettlementEventRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresSettlementEventRepository implements SettlementEventRepository {
    private static final String SELECT_SQL = """
            select
              e.output_snapshot->>'settlementEventId' as id,
              e.subject_id as order_id,
              e.input_snapshot->>'paymentIntentId' as payment_intent_id,
              e.event_type,
              e.action_id as idempotency_key,
              nullif(e.output_snapshot->>'amountMinor', '')::integer as amount_minor,
              e.output_snapshot->>'currency' as currency,
              coalesce(e.output_snapshot->>'actorAccountId', e.actor_account_id) as actor_account_id,
              coalesce(e.output_snapshot->'payload', '{}'::jsonb) as payload,
              e.created_at
            from work_events e
            """;

    private final DSLContext dsl;

    public PostgresSettlementEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<SettlementEventEntity> findByUniqueKey(String orderId, String eventType, String idempotencyKey) {
        return dsl.resultQuery(SELECT_SQL + """
                        where e.subject_type = 'settlement_event'
                          and e.subject_id = ?
                          and e.event_type = ?
                          and e.action_id = ?
                        """, orderId, eventType, idempotencyKey)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public SettlementEventEntity save(SettlementEventEntity event) {
        // 中文注释：结算流水统一写入 WorkEvent，subject_id 保持 order 维度，action_id 继续承担幂等键。
        dsl.query("""
                                insert into work_events (
                                  id, subject_type, subject_id, actor_account_id, event_type, action_id,
                                  input_snapshot, output_snapshot, created_at
                                )
                                select
                                  ?,
                                  'settlement_event',
                                  ?,
                                  coalesce(?, o.claimed_by_account_id),
                                  ?,
                                  ?,
                                  jsonb_build_object(
                                    'orderId', ?,
                                    'paymentIntentId', ?,
                                    'idempotencyKey', ?
                                  ),
                                  jsonb_build_object(
                                    'settlementEventId', ?,
                                    'amountMinor', ?,
                                    'currency', ?,
                                    'actorAccountId', ?,
                                    'payload', ?::jsonb
                                  ),
                                  ?::timestamptz
                                from orders o
                                where o.id = ?
                                on conflict (subject_type, subject_id, event_type, action_id)
                                  where subject_type = 'settlement_event'
                                do nothing
                                """,
                        event.id(),
                        event.orderId(),
                        event.actorAccountId(),
                        event.eventType(),
                        event.idempotencyKey(),
                        event.orderId(),
                        event.paymentIntentId(),
                        event.idempotencyKey(),
                        event.id(),
                        event.amountMinor(),
                        event.currency(),
                        event.actorAccountId(),
                        PostgresJson.jsonb(event.payload()).data(),
                        PostgresJson.offsetDateTime(event.createdAt()),
                        event.orderId())
                .execute();
        return findByUniqueKey(event.orderId(), event.eventType(), event.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Settlement event order was not found"));
    }

    @Override
    public List<SettlementEventEntity> findByOrderId(String orderId) {
        return dsl.resultQuery(SELECT_SQL + """
                        where e.subject_type = 'settlement_event'
                          and e.subject_id = ?
                        order by e.created_at asc, e.id asc
                        """, orderId)
                .fetch(this::mapRecord);
    }

    @Override
    public List<SettlementEventEntity> findByProjectId(String projectId) {
        // 中文注释：项目维度读模型通过 WorkEvent 的 order subject 回查，避免结算事实再维护项目外键。
        return dsl.resultQuery(SELECT_SQL + """
                        join orders o on o.id = e.subject_id
                        where e.subject_type = 'settlement_event'
                          and o.post_id = ?
                        order by e.created_at asc, e.id asc
                        """, projectId)
                .fetch(this::mapRecord);
    }

    @Override
    public List<SettlementEventEntity> findRecent(int limit) {
        return dsl.resultQuery(SELECT_SQL + """
                        where e.subject_type = 'settlement_event'
                        order by e.created_at desc, e.id desc
                        limit ?
                        """, Math.max(1, Math.min(limit, 200)))
                .fetch(this::mapRecord);
    }

    private SettlementEventEntity mapRecord(Record record) {
        return new SettlementEventEntity(
                record.get("id", String.class),
                record.get("order_id", String.class),
                record.get("payment_intent_id", String.class),
                record.get("event_type", String.class),
                record.get("idempotency_key", String.class),
                record.get("amount_minor", Integer.class),
                record.get("currency", String.class),
                record.get("actor_account_id", String.class),
                PostgresJson.map(record.get("payload", JSONB.class)),
                PostgresJson.instant(record.get("created_at", OffsetDateTime.class)));
    }
}
