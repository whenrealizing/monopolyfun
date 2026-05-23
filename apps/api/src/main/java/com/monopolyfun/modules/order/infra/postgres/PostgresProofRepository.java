package com.monopolyfun.modules.order.infra.postgres;

import com.monopolyfun.modules.order.domain.ExecutionMode;
import com.monopolyfun.modules.order.domain.ProofEntity;
import com.monopolyfun.modules.order.domain.ProofKind;
import com.monopolyfun.modules.order.domain.ReviewDecision;
import com.monopolyfun.modules.order.infra.ProofRepository;
import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresProofRepository implements ProofRepository {
    private final DSLContext dsl;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresProofRepository(DSLContext dsl, ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<ProofEntity> findAll() {
        return dsl.resultQuery(selectSql() + " order by created_at desc")
                .fetch(this::mapRecord);
    }

    @Override
    public List<ProofEntity> findBySubmittedByAccountId(String accountId, int limit) {
        return dsl.resultQuery(selectSql() + """
                         where submitted_by_account_id = ?
                         order by created_at desc
                         limit ?
                        """, accountId, Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<ProofEntity> findById(String id) {
        return dsl.resultQuery(selectSql() + " where id = ?", id)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public ProofEntity save(ProofEntity proof) {
        // 中文注释：proof 表读写固定在迁移定义的列集合上，避免本地 jOOQ 生成库漂移导致测试库读取不存在字段。
        dsl.query("""
                                insert into proofs (
                                  id, order_id, kind, parent_order_id, submitted_by_account_id, summary,
                                  links, artifacts, proof_payload, execution_mode, agent_session_id,
                                  agent_runtime, decision, evidence_refs, content_hashes, criteria_refs,
                                  visibility, execution_trace_ref, created_at
                                )
                                values (
                                  ?, ?, ?::proof_kind, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb,
                                  ?::execution_mode, ?, ?, ?::review_decision, ?::jsonb, ?::jsonb,
                                  ?::jsonb, ?, ?, ?::timestamptz
                                )
                                on conflict (id) do update
                                set order_id = excluded.order_id,
                                    kind = excluded.kind,
                                    parent_order_id = excluded.parent_order_id,
                                    submitted_by_account_id = excluded.submitted_by_account_id,
                                    summary = excluded.summary,
                                    links = excluded.links,
                                    artifacts = excluded.artifacts,
                                    proof_payload = excluded.proof_payload,
                                    execution_mode = excluded.execution_mode,
                                    agent_session_id = excluded.agent_session_id,
                                    agent_runtime = excluded.agent_runtime,
                                    decision = excluded.decision,
                                    evidence_refs = excluded.evidence_refs,
                                    content_hashes = excluded.content_hashes,
                                    criteria_refs = excluded.criteria_refs,
                                    visibility = excluded.visibility,
                                    execution_trace_ref = excluded.execution_trace_ref
                                """,
                        proof.id(),
                        proof.orderId(),
                        proof.kind().name().toLowerCase(),
                        proof.parentOrderId(),
                        proof.submittedByAccountId(),
                        proof.summary(),
                        PostgresJson.jsonb(proof.links()).data(),
                        PostgresJson.jsonb(proof.artifacts()).data(),
                        PostgresJson.jsonb(proof.proofPayload()).data(),
                        proof.executionMode().name().toLowerCase(),
                        proof.agentSessionId(),
                        proof.agentRuntime(),
                        proof.decision() == null ? null : proof.decision().name().toLowerCase(),
                        PostgresJson.jsonb(proof.evidenceRefs()).data(),
                        PostgresJson.jsonb(proof.contentHashes()).data(),
                        PostgresJson.jsonb(proof.criteriaRefs()).data(),
                        proof.visibility(),
                        proof.executionTraceRef(),
                        PostgresJson.offsetDateTime(proof.createdAt()))
                .execute();
        projectTimelineProjectionWriter.syncProof(proof);
        return proof;
    }

    private String selectSql() {
        return """
                select id, order_id, kind::text as kind, parent_order_id, submitted_by_account_id, summary,
                       links, artifacts, proof_payload, execution_mode::text as execution_mode,
                       agent_session_id, agent_runtime, decision::text as decision,
                       evidence_refs, content_hashes, criteria_refs, visibility, execution_trace_ref, created_at
                from proofs
                """;
    }

    private ProofEntity mapRecord(Record record) {
        return new ProofEntity(
                record.get("id", String.class),
                record.get("order_id", String.class),
                ProofKind.valueOf(record.get("kind", String.class).toUpperCase()),
                record.get("parent_order_id", String.class),
                record.get("submitted_by_account_id", String.class),
                record.get("summary", String.class),
                PostgresJson.proofLinks(record.get("links", org.jooq.JSONB.class)),
                PostgresJson.stringList(record.get("artifacts", org.jooq.JSONB.class)),
                PostgresJson.map(record.get("proof_payload", org.jooq.JSONB.class)),
                ExecutionMode.valueOf(record.get("execution_mode", String.class).toUpperCase()),
                record.get("agent_session_id", String.class),
                record.get("agent_runtime", String.class),
                record.get("decision", String.class) == null ? null : ReviewDecision.valueOf(record.get("decision", String.class).toUpperCase()),
                PostgresJson.stringList(record.get("evidence_refs", org.jooq.JSONB.class)),
                PostgresJson.stringList(record.get("content_hashes", org.jooq.JSONB.class)),
                PostgresJson.stringList(record.get("criteria_refs", org.jooq.JSONB.class)),
                record.get("visibility", String.class),
                record.get("execution_trace_ref", String.class),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)));
    }
}
