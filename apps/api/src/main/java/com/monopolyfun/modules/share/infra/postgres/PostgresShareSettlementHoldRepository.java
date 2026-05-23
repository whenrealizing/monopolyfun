package com.monopolyfun.modules.share.infra.postgres;

import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.ShareSettlementHoldEntity;
import com.monopolyfun.modules.share.infra.ShareSettlementHoldRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PostgresShareSettlementHoldRepository implements ShareSettlementHoldRepository {
    private final DSLContext dsl;

    public PostgresShareSettlementHoldRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ShareSettlementHoldEntity> findByOrderId(String orderId) {
        return dsl.resultQuery(selectSql() + " where order_id = ?", orderId)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<ShareSettlementHoldEntity> findByAccountId(String accountId) {
        return dsl.resultQuery(selectSql() + " where account_id = ? order by created_at asc", accountId)
                .fetch(this::mapRecord);
    }

    @Override
    public ShareSettlementHoldEntity save(ShareSettlementHoldEntity hold) {
        dsl.query("""
                                insert into share_settlement_holds (
                                  id, order_id, proof_id, share_release_request_id, market_id, project_id, item_id, account_id, amount, curve_slot,
                                  reason, status, lock_reason, release_reason, released_at, cancelled_at, metadata, created_at, updated_at
                                )
                                values (
                                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::ledger_reason, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::jsonb, ?::timestamptz, ?::timestamptz
                                )
                                on conflict (order_id) do update
                                set proof_id = excluded.proof_id,
                                    share_release_request_id = excluded.share_release_request_id,
                                    market_id = excluded.market_id,
                                    project_id = excluded.project_id,
                                    item_id = excluded.item_id,
                                    account_id = excluded.account_id,
                                    amount = excluded.amount,
                                    curve_slot = excluded.curve_slot,
                                    reason = excluded.reason,
                                    status = excluded.status,
                                    lock_reason = excluded.lock_reason,
                                    release_reason = excluded.release_reason,
                                    released_at = excluded.released_at,
                                    cancelled_at = excluded.cancelled_at,
                                    metadata = excluded.metadata,
                                    updated_at = excluded.updated_at
                                """,
                        hold.id(),
                        hold.orderId(),
                        hold.proofId(),
                        hold.shareReleaseRequestId(),
                        hold.marketId(),
                        hold.projectId(),
                        hold.itemId(),
                        hold.accountId(),
                        hold.amount(),
                        hold.curveSlot(),
                        hold.reason().name().toLowerCase(),
                        hold.status(),
                        hold.lockReason(),
                        hold.releaseReason(),
                        PostgresJson.offsetDateTime(hold.releasedAt()),
                        PostgresJson.offsetDateTime(hold.cancelledAt()),
                        PostgresJson.jsonb(hold.metadata()).data(),
                        PostgresJson.offsetDateTime(hold.createdAt()),
                        PostgresJson.offsetDateTime(hold.updatedAt()))
                .execute();
        return findByOrderId(hold.orderId()).orElseThrow();
    }

    private String selectSql() {
        return """
                select id, order_id, proof_id, share_release_request_id, market_id, project_id, item_id, account_id, amount, curve_slot,
                       reason::text as reason, status, lock_reason, release_reason, released_at, cancelled_at, metadata, created_at, updated_at
                from share_settlement_holds
                """;
    }

    private ShareSettlementHoldEntity mapRecord(Record record) {
        return new ShareSettlementHoldEntity(
                record.get("id", String.class),
                record.get("order_id", String.class),
                record.get("proof_id", String.class),
                record.get("share_release_request_id", String.class),
                record.get("market_id", String.class),
                record.get("project_id", String.class),
                record.get("item_id", String.class),
                record.get("account_id", String.class),
                record.get("amount", Integer.class),
                record.get("curve_slot", Integer.class),
                LedgerReason.valueOf(record.get("reason", String.class).toUpperCase()),
                record.get("status", String.class),
                record.get("lock_reason", String.class),
                record.get("release_reason", String.class),
                PostgresJson.instant(record.get("released_at", java.time.OffsetDateTime.class)),
                PostgresJson.instant(record.get("cancelled_at", java.time.OffsetDateTime.class)),
                PostgresJson.map(record.get("metadata", org.jooq.JSONB.class)),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", java.time.OffsetDateTime.class)));
    }
}
