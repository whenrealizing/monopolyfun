package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.domain.OrganizationEventEntity;
import com.monopolyfun.modules.project.infra.OrganizationEventRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresOrganizationEventRepository implements OrganizationEventRepository {
    private final DSLContext dsl;

    public PostgresOrganizationEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public OrganizationEventEntity save(OrganizationEventEntity event) {
        dsl.query("""
                                insert into organization_events (id, project_id, actor_account_id, event_type, payload, created_at)
                                values (?, ?, ?, ?, ?::jsonb, ?::timestamptz)
                                """,
                        event.id(),
                        event.projectId(),
                        event.actorAccountId(),
                        event.eventType(),
                        PostgresJson.jsonb(event.payload()).data(),
                        PostgresJson.offsetDateTime(event.createdAt()))
                .execute();
        return event;
    }
}
