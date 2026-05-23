package com.monopolyfun.modules.share.infra.postgres;

import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.modules.share.domain.LedgerReason;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.domain.SharesLedgerEntryEntity;
import com.monopolyfun.modules.share.infra.SharesLedgerRepository;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.monopolyfun.generated.jooq.Tables.SHARES_LEDGER;

@Repository
public class PostgresSharesLedgerRepository implements SharesLedgerRepository {
    private final DSLContext dsl;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresSharesLedgerRepository(DSLContext dsl, ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<SharesLedgerEntryEntity> findByMarketId(String marketId) {
        return dsl.resultQuery("""
                        select id, source_type, source_id, issuer_type::text as issuer_type, issuer_id, market_id, order_id, proof_id,
                               share_release_request_id, project_id, item_id, account_id, amount, curve_slot,
                               reason::text as reason, settlement_type_snapshot::text as settlement_type_snapshot, created_at
                        from shares_ledger
                        where market_id = ?
                        order by created_at asc
                        """, marketId)
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<SharesLedgerEntryEntity> findByMarketId(String marketId, PageQuery pageQuery) {
        return findLedgerPage("market_id", marketId, pageQuery);
    }

    @Override
    public List<SharesLedgerEntryEntity> findByAccountId(String accountId) {
        return dsl.resultQuery("""
                        select id, source_type, source_id, issuer_type::text as issuer_type, issuer_id, market_id, order_id, proof_id,
                               share_release_request_id, project_id, item_id, account_id, amount, curve_slot,
                               reason::text as reason, settlement_type_snapshot::text as settlement_type_snapshot, created_at
                        from shares_ledger
                        where account_id = ?
                        order by created_at asc
                        """, accountId)
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<SharesLedgerEntryEntity> findByAccountId(String accountId, PageQuery pageQuery) {
        return findLedgerPage("account_id", accountId, pageQuery);
    }

    private PageResult<SharesLedgerEntryEntity> findLedgerPage(String column, String value, PageQuery pageQuery) {
        CursorKey cursor = pageQuery.cursorKey();
        String cursorSql = cursor == null ? "" : " and (created_at > ?::timestamptz or (created_at = ?::timestamptz and id > ?))";
        ArrayList<Object> params = new ArrayList<>();
        params.add(value);
        if (cursor != null) {
            params.add(cursor.value());
            params.add(cursor.value());
            params.add(cursor.id());
        }
        params.add(pageQuery.fetchLimit());
        // 中文注释：账本列表按不可变写入时间做 keyset 翻页，保留审计顺序并避免全量加载。
        List<SharesLedgerEntryEntity> fetched = dsl.resultQuery("""
                        select id, source_type, source_id, issuer_type::text as issuer_type, issuer_id, market_id, order_id, proof_id,
                               share_release_request_id, project_id, item_id, account_id, amount, curve_slot,
                               reason::text as reason, settlement_type_snapshot::text as settlement_type_snapshot, created_at
                        from shares_ledger
                        where %s = ?
                        %s
                        order by created_at asc, id asc
                        limit ?
                        """.formatted(column, cursorSql), params.toArray())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), entry -> CursorCodec.encode(entry.createdAt().toString(), entry.id()));
    }

    @Override
    public boolean existsByOrderId(String orderId) {
        return dsl.fetchExists(dsl.selectOne().from(SHARES_LEDGER).where(SHARES_LEDGER.ORDER_ID.eq(orderId)));
    }

    @Override
    public SharesLedgerEntryEntity save(SharesLedgerEntryEntity entry) {
        dsl.query("""
                                insert into shares_ledger (
                                  id, source_type, source_id, issuer_type, issuer_id, market_id, order_id, proof_id, share_release_request_id,
                                  project_id, item_id, account_id, amount, curve_slot, reason, settlement_type_snapshot, created_at
                                )
                                values (
                                  ?, ?, ?, ?::share_issuer_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::ledger_reason,
                                  ?::settlement_type, ?::timestamptz
                                )
                                on conflict (id) do update
                                set source_type = excluded.source_type,
                                    source_id = excluded.source_id,
                                    issuer_type = excluded.issuer_type,
                                    issuer_id = excluded.issuer_id,
                                    market_id = excluded.market_id,
                                    order_id = excluded.order_id,
                                    proof_id = excluded.proof_id,
                                    share_release_request_id = excluded.share_release_request_id,
                                    project_id = excluded.project_id,
                                    item_id = excluded.item_id,
                                    account_id = excluded.account_id,
                                    amount = excluded.amount,
                                    curve_slot = excluded.curve_slot,
                                    reason = excluded.reason,
                                    settlement_type_snapshot = excluded.settlement_type_snapshot
                                """,
                        entry.id(),
                        entry.sourceType(),
                        entry.sourceId(),
                        entry.issuerType().code(),
                        entry.issuerId(),
                        entry.marketId(),
                        entry.orderId(),
                        entry.proofId(),
                        entry.shareReleaseRequestId(),
                        entry.projectId(),
                        entry.itemId(),
                        entry.accountId(),
                        entry.amount(),
                        entry.curveSlot(),
                        entry.reason().name().toLowerCase(),
                        entry.settlementTypeSnapshot().name().toLowerCase(),
                        PostgresJson.offsetDateTime(entry.createdAt()))
                .execute();
        projectTimelineProjectionWriter.syncShare(entry);
        return entry;
    }

    @Override
    public boolean saveIfAbsent(SharesLedgerEntryEntity entry) {
        // 中文注释：shares 账本以业务来源做幂等键，审批重试和并发 release 只会生成一条收益事实。
        boolean inserted = dsl.resultQuery("""
                                insert into shares_ledger (
                                  id, source_type, source_id, issuer_type, issuer_id, market_id, order_id, proof_id, share_release_request_id,
                                  project_id, item_id, account_id, amount, curve_slot, reason, settlement_type_snapshot, created_at
                                )
                                values (
                                  ?, ?, ?, ?::share_issuer_type, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::ledger_reason,
                                  ?::settlement_type, ?::timestamptz
                                )
                                on conflict (source_type, source_id, account_id, reason) do nothing
                                returning id
                                """,
                        entry.id(),
                        entry.sourceType(),
                        entry.sourceId(),
                        entry.issuerType().code(),
                        entry.issuerId(),
                        entry.marketId(),
                        entry.orderId(),
                        entry.proofId(),
                        entry.shareReleaseRequestId(),
                        entry.projectId(),
                        entry.itemId(),
                        entry.accountId(),
                        entry.amount(),
                        entry.curveSlot(),
                        entry.reason().name().toLowerCase(),
                        entry.settlementTypeSnapshot().name().toLowerCase(),
                        PostgresJson.offsetDateTime(entry.createdAt()))
                .fetchOptional()
                .isPresent();
        if (inserted) {
            projectTimelineProjectionWriter.syncShare(entry);
        }
        return inserted;
    }

    private SharesLedgerEntryEntity mapRecord(Record record) {
        return new SharesLedgerEntryEntity(
                record.get("id", String.class),
                record.get("source_type", String.class),
                record.get("source_id", String.class),
                ShareIssuerType.fromCode(record.get("issuer_type", String.class)),
                record.get("issuer_id", String.class),
                record.get("market_id", String.class),
                record.get("order_id", String.class),
                record.get("proof_id", String.class),
                record.get("share_release_request_id", String.class),
                record.get("project_id", String.class),
                record.get("item_id", String.class),
                record.get("account_id", String.class),
                record.get("amount", Integer.class),
                record.get("curve_slot", Integer.class),
                LedgerReason.valueOf(record.get("reason", String.class).toUpperCase()),
                SettlementType.valueOf(record.get("settlement_type_snapshot", String.class).toUpperCase()),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)));
    }
}
