package com.monopolyfun.shared.observability.infra.postgres;

import com.monopolyfun.shared.observability.AuditEvent;
import com.monopolyfun.shared.observability.infra.AuditEventRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.monopolyfun.generated.jooq.Tables.AUDIT_EVENTS;

@Repository
public class PostgresAuditEventRepository implements AuditEventRepository {
    private final DSLContext dsl;

    public PostgresAuditEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        dsl.insertInto(AUDIT_EVENTS)
                .set(AUDIT_EVENTS.ID, event.id())
                .set(AUDIT_EVENTS.TYPE, event.type())
                .set(AUDIT_EVENTS.SUBJECT_TYPE, event.subjectType())
                .set(AUDIT_EVENTS.SUBJECT_ID, event.subjectId())
                .set(AUDIT_EVENTS.ACTOR_ACCOUNT_ID, event.actorAccountId())
                .set(AUDIT_EVENTS.TRACE_ID, event.traceId())
                .set(AUDIT_EVENTS.OUTCOME, event.outcome())
                .set(AUDIT_EVENTS.PAYLOAD, PostgresJson.jsonb(event.payload()))
                .set(AUDIT_EVENTS.CREATED_AT, PostgresJson.offsetDateTime(event.createdAt()))
                .onConflictDoNothing()
                .execute();
        return event;
    }

    @Override
    public List<AuditEvent> findAll() {
        return dsl.selectFrom(AUDIT_EVENTS)
                .orderBy(AUDIT_EVENTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public List<AuditEvent> findRecent(int limit) {
        return dsl.selectFrom(AUDIT_EVENTS)
                .orderBy(AUDIT_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    private AuditEvent mapRecord(Record record) {
        return new AuditEvent(
                record.get(AUDIT_EVENTS.ID),
                record.get(AUDIT_EVENTS.TYPE),
                record.get(AUDIT_EVENTS.SUBJECT_TYPE),
                record.get(AUDIT_EVENTS.SUBJECT_ID),
                record.get(AUDIT_EVENTS.ACTOR_ACCOUNT_ID),
                record.get(AUDIT_EVENTS.TRACE_ID),
                record.get(AUDIT_EVENTS.OUTCOME),
                PostgresJson.map(record.get(AUDIT_EVENTS.PAYLOAD)),
                PostgresJson.instant(record.get(AUDIT_EVENTS.CREATED_AT)));
    }
}
