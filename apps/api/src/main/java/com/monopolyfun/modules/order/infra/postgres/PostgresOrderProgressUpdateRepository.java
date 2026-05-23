package com.monopolyfun.modules.order.infra.postgres;

import com.monopolyfun.modules.order.domain.ExecutionMode;
import com.monopolyfun.modules.order.domain.OrderProgressUpdateEntity;
import com.monopolyfun.modules.order.infra.OrderProgressUpdateRepository;
import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.monopolyfun.generated.jooq.Tables.ORDER_PROGRESS_UPDATES;

@Repository
public class PostgresOrderProgressUpdateRepository implements OrderProgressUpdateRepository {
    private final DSLContext dsl;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresOrderProgressUpdateRepository(
            DSLContext dsl,
            ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<OrderProgressUpdateEntity> findByOrderId(String orderId) {
        return dsl.selectFrom(ORDER_PROGRESS_UPDATES)
                .where(ORDER_PROGRESS_UPDATES.ORDER_ID.eq(orderId))
                .orderBy(ORDER_PROGRESS_UPDATES.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public OrderProgressUpdateEntity save(OrderProgressUpdateEntity update) {
        dsl.insertInto(ORDER_PROGRESS_UPDATES)
                .set(ORDER_PROGRESS_UPDATES.ID, update.id())
                .set(ORDER_PROGRESS_UPDATES.ORDER_ID, update.orderId())
                .set(ORDER_PROGRESS_UPDATES.LISTING_ID, update.listingId())
                .set(ORDER_PROGRESS_UPDATES.STEP_INDEX, update.stepIndex())
                .set(ORDER_PROGRESS_UPDATES.STEP_TITLE, update.stepTitle())
                .set(ORDER_PROGRESS_UPDATES.SUMMARY, update.summary())
                .set(ORDER_PROGRESS_UPDATES.LINKS, PostgresJson.jsonb(update.links()))
                .set(ORDER_PROGRESS_UPDATES.ARTIFACTS, PostgresJson.jsonb(update.artifacts()))
                .set(ORDER_PROGRESS_UPDATES.PROGRESS_PAYLOAD, PostgresJson.jsonb(update.progressPayload()))
                .set(ORDER_PROGRESS_UPDATES.SUBMITTED_BY_ACCOUNT_ID, update.submittedByAccountId())
                .set(ORDER_PROGRESS_UPDATES.EXECUTION_MODE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ExecutionMode.class, update.executionMode()))
                .set(ORDER_PROGRESS_UPDATES.AGENT_SESSION_ID, update.agentSessionId())
                .set(ORDER_PROGRESS_UPDATES.AGENT_RUNTIME, update.agentRuntime())
                .set(ORDER_PROGRESS_UPDATES.CREATED_AT, PostgresJson.offsetDateTime(update.createdAt()))
                .onConflict(ORDER_PROGRESS_UPDATES.ID)
                .doUpdate()
                .set(ORDER_PROGRESS_UPDATES.STEP_TITLE, update.stepTitle())
                .set(ORDER_PROGRESS_UPDATES.SUMMARY, update.summary())
                .set(ORDER_PROGRESS_UPDATES.LINKS, PostgresJson.jsonb(update.links()))
                .set(ORDER_PROGRESS_UPDATES.ARTIFACTS, PostgresJson.jsonb(update.artifacts()))
                .set(ORDER_PROGRESS_UPDATES.PROGRESS_PAYLOAD, PostgresJson.jsonb(update.progressPayload()))
                .set(ORDER_PROGRESS_UPDATES.EXECUTION_MODE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ExecutionMode.class, update.executionMode()))
                .set(ORDER_PROGRESS_UPDATES.AGENT_SESSION_ID, update.agentSessionId())
                .set(ORDER_PROGRESS_UPDATES.AGENT_RUNTIME, update.agentRuntime())
                .execute();
        projectTimelineProjectionWriter.syncProgress(update);
        return update;
    }

    private OrderProgressUpdateEntity mapRecord(Record record) {
        return new OrderProgressUpdateEntity(
                record.get(ORDER_PROGRESS_UPDATES.ID),
                record.get(ORDER_PROGRESS_UPDATES.ORDER_ID),
                record.get(ORDER_PROGRESS_UPDATES.LISTING_ID),
                record.get(ORDER_PROGRESS_UPDATES.STEP_INDEX),
                record.get(ORDER_PROGRESS_UPDATES.STEP_TITLE),
                record.get(ORDER_PROGRESS_UPDATES.SUMMARY),
                PostgresJson.proofLinks(record.get(ORDER_PROGRESS_UPDATES.LINKS)),
                PostgresJson.stringList(record.get(ORDER_PROGRESS_UPDATES.ARTIFACTS)),
                PostgresJson.map(record.get(ORDER_PROGRESS_UPDATES.PROGRESS_PAYLOAD)),
                record.get(ORDER_PROGRESS_UPDATES.SUBMITTED_BY_ACCOUNT_ID),
                PostgresJson.modelEnum(ExecutionMode.class, record.get(ORDER_PROGRESS_UPDATES.EXECUTION_MODE)),
                record.get(ORDER_PROGRESS_UPDATES.AGENT_SESSION_ID),
                record.get(ORDER_PROGRESS_UPDATES.AGENT_RUNTIME),
                PostgresJson.instant(record.get(ORDER_PROGRESS_UPDATES.CREATED_AT)));
    }
}
