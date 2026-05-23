package com.monopolyfun.modules.projection;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderProgressUpdateEntity;
import com.monopolyfun.modules.order.domain.ProofEntity;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectLevel;
import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProjectTimelineProjectionWriter {
    private final DSLContext dsl;

    public ProjectTimelineProjectionWriter(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void syncOrder(OrderEntity order) {
        if (order.postKind() != PostKind.PROJECT || order.postId() == null) {
            return;
        }
        // 中文注释：项目 timeline 统一由 projector 写入，项目详情只消费稳定事件快照。
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, order_id, actor_account_id, occurred_at, payload)
                        VALUES (?, ?, ?, 'order', ?, ?, ?, ?::timestamptz, ?::jsonb)
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET event_type = EXCLUDED.event_type,
                            order_id = EXCLUDED.order_id,
                            actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "order-" + order.id(),
                order.postId(),
                "order_" + order.status().name().toLowerCase(),
                order.id(),
                order.id(),
                actorForOrder(order),
                PostgresJson.offsetDateTime(order.updatedAt()),
                PostgresJson.jsonb(Map.of(
                        "title", order.displayPhase(),
                        "summary", "订单当前阶段：" + order.displayPhase(),
                        "subjectType", "order",
                        "subjectId", order.id(),
                        "metadata", order.metadata())).data());
    }

    public void syncShare(SharesLedgerEntryEntity entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Shares released");
        payload.put("summary", "发放 " + entry.amount() + " shares");
        payload.put("subjectType", "share");
        payload.put("subjectId", entry.id());
        payload.put("amount", entry.amount());
        payload.put("curveSlot", entry.curveSlot());
        payload.put("orderId", entry.orderId());
        payload.put("proofId", entry.proofId());
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, order_id, actor_account_id, occurred_at, payload)
                        SELECT
                          ?,
                          COALESCE(?, orders.post_id),
                          'shares_minted',
                          'share',
                          ?,
                          ?,
                          ?,
                          ?::timestamptz,
                          ?::jsonb
                        FROM (SELECT 1) seed
                        LEFT JOIN orders ON orders.id = ?
                        WHERE COALESCE(?, orders.post_id) IS NOT NULL
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET order_id = EXCLUDED.order_id,
                            actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "share-" + entry.id(),
                entry.projectId(),
                entry.id(),
                entry.orderId(),
                entry.accountId(),
                PostgresJson.offsetDateTime(entry.createdAt()),
                PostgresJson.jsonb(payload).data(),
                entry.orderId(),
                entry.projectId());
    }

    public void syncListingItem(ListingEntity listing) {
        if (listing.kind() != ListingKind.WORK || !"post_item".equalsIgnoreCase(listing.subjectType())) {
            return;
        }
        if (!"project".equalsIgnoreCase(String.valueOf(listing.metadata().get("postKind")))) {
            return;
        }
        Object projectId = listing.metadata().get("postId");
        if (projectId == null) {
            return;
        }
        Map<String, Object> payload = basePayload(listing.title(), listing.deliverableSpec(), "item", listing.id());
        payload.put("metadata", listing.metadata());
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, actor_account_id, occurred_at, payload)
                        VALUES (?, ?, 'item_opened', 'item', ?, ?, ?::timestamptz, ?::jsonb)
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "item-opened-" + listing.id(),
                String.valueOf(projectId),
                listing.id(),
                listing.openedByAccountId(),
                PostgresJson.offsetDateTime(listing.createdAt()),
                PostgresJson.jsonb(payload).data());
    }

    public void syncProject(ProjectEntity project) {
        if (project.projectLevel() != ProjectLevel.CHILD) {
            return;
        }
        Map<String, Object> payload = basePayload(project.title(), project.summary(), "project", project.id());
        payload.put("metadata", project.metadata());
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, actor_account_id, occurred_at, payload)
                        VALUES (?, ?, 'project_created', 'project', ?, ?, ?::timestamptz, ?::jsonb)
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "project-created-" + project.id(),
                project.id(),
                project.id(),
                project.ownerAccountId(),
                PostgresJson.offsetDateTime(project.createdAt()),
                PostgresJson.jsonb(payload).data());
    }

    public void syncProof(ProofEntity proof) {
        Map<String, Object> payload = basePayload(
                proof.kind().name().toLowerCase(),
                proof.summary(),
                "proof",
                proof.id());
        payload.put("metadata", proof.proofPayload());
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, order_id, actor_account_id, occurred_at, payload)
                        SELECT
                          ?,
                          orders.post_id,
                          'proof_submitted',
                          'proof',
                          ?,
                          ?,
                          ?,
                          ?::timestamptz,
                          ?::jsonb
                        FROM orders
                        WHERE orders.id = ?
                          AND lower(orders.post_kind) = 'project'
                          AND orders.post_id IS NOT NULL
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET order_id = EXCLUDED.order_id,
                            actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "proof-" + proof.id(),
                proof.id(),
                proof.orderId(),
                proof.submittedByAccountId(),
                PostgresJson.offsetDateTime(proof.createdAt()),
                PostgresJson.jsonb(payload).data(),
                proof.orderId());
    }

    public void syncProgress(OrderProgressUpdateEntity update) {
        Map<String, Object> payload = basePayload(update.stepTitle(), update.summary(), "order", update.orderId());
        payload.put("metadata", update.progressPayload());
        dsl.execute("""
                        INSERT INTO project_timeline_events (id, project_id, event_type, source_type, source_id, order_id, actor_account_id, occurred_at, payload)
                        SELECT
                          ?,
                          orders.post_id,
                          'progress_submitted',
                          'progress',
                          ?,
                          ?,
                          ?,
                          ?::timestamptz,
                          ?::jsonb
                        FROM orders
                        WHERE orders.id = ?
                          AND lower(orders.post_kind) = 'project'
                          AND orders.post_id IS NOT NULL
                        ON CONFLICT (project_id, source_type, source_id) DO UPDATE
                        SET order_id = EXCLUDED.order_id,
                            actor_account_id = EXCLUDED.actor_account_id,
                            occurred_at = EXCLUDED.occurred_at,
                            payload = EXCLUDED.payload
                        """,
                "progress-" + update.id(),
                update.id(),
                update.orderId(),
                update.submittedByAccountId(),
                PostgresJson.offsetDateTime(update.createdAt()),
                PostgresJson.jsonb(payload).data(),
                update.orderId());
    }

    private String actorForOrder(OrderEntity order) {
        if (order.acceptedByAccountId() != null) return order.acceptedByAccountId();
        if (order.submittedByAccountId() != null) return order.submittedByAccountId();
        return order.fulfillerAccountId();
    }

    private Map<String, Object> basePayload(String title, String summary, String subjectType, String subjectId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("summary", summary);
        payload.put("subjectType", subjectType);
        payload.put("subjectId", subjectId);
        return payload;
    }
}
