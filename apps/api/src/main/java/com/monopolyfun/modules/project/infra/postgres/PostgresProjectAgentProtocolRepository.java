package com.monopolyfun.modules.project.infra.postgres;

import com.monopolyfun.modules.project.infra.ProjectAgentProtocolRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PostgresProjectAgentProtocolRepository implements ProjectAgentProtocolRepository {
    private final DSLContext dsl;

    public PostgresProjectAgentProtocolRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void saveProposalPack(String projectId, String packId, String actorAccountId, Map<String, Object> pack) {
        Map<String, Object> input = Map.of("projectId", projectId, "packId", packId);
        dsl.query("""
                        insert into work_events (
                          id, subject_type, subject_id, actor_account_id, event_type, action_id, input_snapshot, output_snapshot
                        ) values (?, 'project_proposal_pack', ?, ?, 'proposal_pack_submitted', 'project.agent.submit_pack', ?::jsonb, ?::jsonb)
                        """, "ppk-" + UUID.randomUUID(), packId, actorAccountId, PostgresJson.jsonb(input).data(), PostgresJson.jsonb(pack).data())
                .execute();
    }

    @Override
    public void savePackEvent(
            String projectId,
            String packId,
            String actorAccountId,
            String subjectType,
            String eventType,
            String actionId,
            Map<String, Object> output) {
        Map<String, Object> input = Map.of("projectId", projectId, "packId", packId);
        dsl.query("""
                                insert into work_events (
                                  id, subject_type, subject_id, actor_account_id, event_type, action_id, input_snapshot, output_snapshot
                                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                                """, "ppe-" + UUID.randomUUID(), subjectType, packId, actorAccountId, eventType, actionId,
                        PostgresJson.jsonb(input).data(), PostgresJson.jsonb(output).data())
                .execute();
    }

    @Override
    public boolean saveShareLedgerEntry(
            String projectId,
            String packId,
            String accountId,
            String role,
            int amount,
            int curveSlot) {
        String reason = switch (role) {
            case "author" -> "proposal_pack_author";
            case "final_reviewer" -> "proposal_pack_final_review";
            default -> "proposal_pack_validator";
        };
        // 中文注释：ProposalPack 股份使用 project/proposal_pack 来源键做幂等，和 order 账本共享同一查询面。
        return dsl.resultQuery("""
                                insert into shares_ledger (
                                  id, source_type, source_id, issuer_type, issuer_id, market_id, order_id, proof_id,
                                  project_id, item_id, account_id, amount, curve_slot, reason, settlement_type_snapshot, created_at
                                )
                                values (
                                  ?, 'proposal_pack', ?, 'project'::share_issuer_type, ?, null, null, null,
                                  ?, null, ?, ?, ?, ?::ledger_reason, 'shares'::settlement_type, now()
                                )
                                on conflict (source_type, source_id, account_id, reason) do nothing
                                returning id
                                """,
                        "pps-" + UUID.randomUUID(),
                        packId,
                        projectId,
                        projectId,
                        accountId,
                        amount,
                        curveSlot,
                        reason)
                .fetchOptional()
                .isPresent();
    }

    @Override
    public List<Map<String, Object>> findProposalPacks(String projectId) {
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = 'project_proposal_pack'
                          and input_snapshot->>'projectId' = ?
                        order by created_at asc
                        """, projectId)
                .fetch(record -> PostgresJson.map(record.get("output_snapshot", JSONB.class)));
    }

    @Override
    public List<Map<String, Object>> findPackEvents(String projectId, String subjectType) {
        return dsl.resultQuery("""
                        select output_snapshot
                        from work_events
                        where subject_type = ?
                          and input_snapshot->>'projectId' = ?
                        order by created_at asc
                        """, subjectType, projectId)
                .fetch(record -> PostgresJson.map(record.get("output_snapshot", JSONB.class)));
    }

    @Override
    public boolean hasShareAllocation(String packId) {
        return dsl.resultQuery("""
                        select exists(
                          select 1
                          from work_events
                          where subject_type = 'project_proposal_pack_share_allocation'
                            and subject_id = ?
                        ) as exists_flag
                        """, packId)
                .fetchOne(record -> Boolean.TRUE.equals(record.get("exists_flag", Boolean.class)));
    }
}
