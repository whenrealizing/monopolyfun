package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.infra.ProjectTimelineRepository;
import com.monopolyfun.modules.project.service.view.ProjectTimelineEventView;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PostgresProjectTimelineRepository implements ProjectTimelineRepository {
    private final DSLContext dsl;

    public PostgresProjectTimelineRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<ProjectTimelineEventView> findByProjectId(String projectId) {
        // 中文注释：Project timeline 查询只读事件表，避免每次打开项目都扫描 item、order 和 proof 源表。
        return dsl.resultQuery("""
                        select id, event_type, source_type, source_id, actor_account_id, occurred_at, payload
                        from project_timeline_events
                        where project_id = ?
                        order by occurred_at asc, id asc
                        """, projectId)
                .fetch(this::mapRecord);
    }

    private ProjectTimelineEventView mapRecord(Record record) {
        var payload = PostgresJson.map(record.get("payload", org.jooq.JSONB.class));
        return new ProjectTimelineEventView(
                record.get("id", String.class),
                record.get("event_type", String.class),
                stringValue(payload.get("title")),
                stringValue(payload.get("summary")),
                record.get("actor_account_id", String.class),
                record.get("source_type", String.class),
                record.get("source_id", String.class),
                PostgresJson.instant(record.get("occurred_at", OffsetDateTime.class)),
                payload);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
