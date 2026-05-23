package com.monopolyfun.modules.repo.infra.postgres;

import com.monopolyfun.modules.repo.infra.GitHubWebhookDeliveryRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

@Repository
public class PostgresGitHubWebhookDeliveryRepository implements GitHubWebhookDeliveryRepository {
    private static final Table<Record> EXTERNAL_EVENT_DEDUP = DSL.table(DSL.name("external_event_dedup"));
    private static final Field<String> DELIVERY_ID = DSL.field(DSL.name("external_event_dedup", "delivery_id"), String.class);
    private static final Field<String> EVENT = DSL.field(DSL.name("external_event_dedup", "event"), String.class);
    private static final Field<String> REPO_URL = DSL.field(DSL.name("external_event_dedup", "repo_url"), String.class);
    private static final Field<String> HEAD_BRANCH = DSL.field(DSL.name("external_event_dedup", "head_branch"), String.class);
    private static final Field<String> SESSION_ID = DSL.field(DSL.name("external_event_dedup", "session_id"), String.class);
    private static final Field<JSONB> METADATA = DSL.field(DSL.name("external_event_dedup", "metadata"), JSONB.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("external_event_dedup", "created_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresGitHubWebhookDeliveryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean recordOnce(
            String deliveryId,
            String event,
            String repoUrl,
            String headBranch,
            String sessionId,
            Map<String, Object> metadata,
            Instant now) {
        // 中文注释：GitHub Delivery ID 是 webhook 的幂等键，重复事件保留首条记录并阻断后续续期。
        return dsl.insertInto(EXTERNAL_EVENT_DEDUP)
                .set(DELIVERY_ID, deliveryId)
                .set(EVENT, event)
                .set(REPO_URL, repoUrl)
                .set(HEAD_BRANCH, headBranch)
                .set(SESSION_ID, sessionId)
                .set(METADATA, PostgresJson.jsonb(metadata))
                .set(CREATED_AT, PostgresJson.offsetDateTime(now))
                .onConflict(DELIVERY_ID)
                .doNothing()
                .execute() == 1;
    }
}
