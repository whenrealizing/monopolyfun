package com.monopolyfun.modules.risk.infra.postgres;

import com.monopolyfun.modules.risk.domain.RiskEventEntity;
import com.monopolyfun.modules.risk.infra.RiskEventRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.monopolyfun.generated.jooq.Tables.RISK_EVENTS;

@Repository
public class PostgresRiskEventRepository implements RiskEventRepository {
    private final DSLContext dsl;

    public PostgresRiskEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public RiskEventEntity save(RiskEventEntity event) {
        dsl.insertInto(RISK_EVENTS)
                .set(RISK_EVENTS.ID, event.id())
                .set(RISK_EVENTS.KIND, event.kind())
                .set(RISK_EVENTS.SUBJECT_TYPE, event.subjectType())
                .set(RISK_EVENTS.SUBJECT_ID, event.subjectId())
                .set(RISK_EVENTS.ACTOR_REF, event.actorRef())
                .set(RISK_EVENTS.SEVERITY, event.severity())
                .set(RISK_EVENTS.REASON, event.reason())
                .set(RISK_EVENTS.PAYLOAD, PostgresJson.jsonb(event.payload()))
                .set(RISK_EVENTS.CREATED_AT, PostgresJson.offsetDateTime(event.createdAt()))
                .onConflictDoNothing()
                .execute();
        return event;
    }

    @Override
    public List<RiskEventEntity> findAll() {
        return dsl.selectFrom(RISK_EVENTS)
                .orderBy(RISK_EVENTS.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countBySeverity() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(RISK_EVENTS.SEVERITY, org.jooq.impl.DSL.count())
                .from(RISK_EVENTS)
                .groupBy(RISK_EVENTS.SEVERITY)
                .fetch(record -> counts.put(record.value1().toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public List<RiskEventEntity> findRecent(int limit) {
        return dsl.selectFrom(RISK_EVENTS)
                .orderBy(RISK_EVENTS.CREATED_AT.desc())
                .limit(limit)
                .fetch(this::mapRecord);
    }

    @Override
    public List<RiskEventEntity> findRecentByAccount(String accountId, int limit) {
        // 中文注释：账号风险卡片只读取目标账号相关事件，避免每个账号都扫描全部 risk_events。
        return dsl.selectFrom(RISK_EVENTS)
                .where(RISK_EVENTS.SUBJECT_ID.eq(accountId).or(RISK_EVENTS.ACTOR_REF.eq(accountId)))
                .orderBy(RISK_EVENTS.CREATED_AT.desc(), RISK_EVENTS.ID.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, List<RiskEventEntity>> findRecentByAccounts(Collection<String> accountIds, int limitPerAccount) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        int limit = Math.max(1, limitPerAccount);
        // 中文注释：风险账号列表用窗口函数一次取每个账号最新事件，消除账号数增长带来的 N+1 查询。
        List<Record> rows = dsl.resultQuery("""
                        WITH account_ids(account_id) AS (
                          SELECT DISTINCT unnest(?::text[])
                        ), ranked_events AS (
                          SELECT
                            account_ids.account_id,
                            risk_events.*,
                            row_number() OVER (
                              PARTITION BY account_ids.account_id
                              ORDER BY risk_events.created_at DESC, risk_events.id DESC
                            ) AS event_rank
                          FROM account_ids
                          JOIN risk_events
                            ON risk_events.subject_id = account_ids.account_id
                            OR risk_events.actor_ref = account_ids.account_id
                        )
                        SELECT *
                        FROM ranked_events
                        WHERE event_rank <= ?
                        ORDER BY account_id ASC, created_at DESC, id DESC
                        """, accountIds.toArray(String[]::new), limit)
                .fetch();
        Map<String, List<RiskEventEntity>> grouped = new LinkedHashMap<>();
        for (Record row : rows) {
            String accountId = row.get("account_id", String.class);
            grouped.computeIfAbsent(accountId, ignored -> new ArrayList<>()).add(mapRecord(row));
        }
        return grouped;
    }

    private RiskEventEntity mapRecord(Record record) {
        return new RiskEventEntity(
                record.get(RISK_EVENTS.ID),
                record.get(RISK_EVENTS.KIND),
                record.get(RISK_EVENTS.SUBJECT_TYPE),
                record.get(RISK_EVENTS.SUBJECT_ID),
                record.get(RISK_EVENTS.ACTOR_REF),
                record.get(RISK_EVENTS.SEVERITY),
                record.get(RISK_EVENTS.REASON),
                PostgresJson.map(record.get(RISK_EVENTS.PAYLOAD)),
                PostgresJson.instant(record.get(RISK_EVENTS.CREATED_AT)));
    }
}
