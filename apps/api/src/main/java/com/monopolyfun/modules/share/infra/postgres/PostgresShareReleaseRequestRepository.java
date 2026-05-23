package com.monopolyfun.modules.share.infra.postgres;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.share.domain.ShareIssuerType;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestEntity;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestStatus;
import com.monopolyfun.modules.share.infra.ShareReleaseRequestRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PostgresShareReleaseRequestRepository implements ShareReleaseRequestRepository {
    private final DSLContext dsl;

    public PostgresShareReleaseRequestRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ShareReleaseRequestEntity> findById(String id) {
        return dsl.resultQuery(selectSql() + " where id = ?", id)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<ShareReleaseRequestEntity> findByOrderId(String orderId) {
        return dsl.resultQuery(selectSql() + " where order_id = ?", orderId)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<ShareReleaseRequestEntity> findPendingForRoleAssignee(String accountId) {
        return dsl.resultQuery("""
                        select distinct r.id, r.issuer_type::text as issuer_type, r.issuer_id, r.market_id, r.project_id,
                               r.order_id, r.proof_id, r.account_id, r.amount, r.curve_slot, r.status::text as status,
                               r.required_role_codes, r.approved_role_codes, r.skipped_role_codes, r.requested_by_account_id,
                               r.resolved_at, r.metadata, r.created_at, r.updated_at
                        from share_release_requests r
                        left join project_roles psr
                          on psr.project_id = r.project_id
                         and psr.account_id = ?
                        where r.status = 'pending'::share_release_request_status
                          and psr.role_code is not null
                          and jsonb_exists(r.required_role_codes, psr.role_code::text)
                          and not jsonb_exists(r.approved_role_codes, psr.role_code::text)
                        order by r.created_at asc
                        """, accountId)
                .fetch(this::mapRecord);
    }

    @Override
    public ShareReleaseRequestEntity save(ShareReleaseRequestEntity request) {
        dsl.query("""
                                insert into share_release_requests (
                                  id, issuer_type, issuer_id, market_id, project_id, order_id, proof_id, account_id, amount, curve_slot,
                                  status, required_role_codes, approved_role_codes, skipped_role_codes, requested_by_account_id,
                                  resolved_at, metadata, created_at, updated_at
                                )
                                values (
                                  ?, ?::share_issuer_type, ?, ?, ?, ?, ?, ?, ?, ?, ?::share_release_request_status,
                                  ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::timestamptz, ?::jsonb, ?::timestamptz, ?::timestamptz
                                )
                                on conflict (order_id) do update
                                set status = excluded.status,
                                    required_role_codes = excluded.required_role_codes,
                                    approved_role_codes = excluded.approved_role_codes,
                                    skipped_role_codes = excluded.skipped_role_codes,
                                    resolved_at = excluded.resolved_at,
                                    metadata = excluded.metadata,
                                    updated_at = excluded.updated_at
                                """,
                        request.id(),
                        request.issuerType().code(),
                        request.issuerId(),
                        request.marketId(),
                        request.projectId(),
                        request.orderId(),
                        request.proofId(),
                        request.accountId(),
                        request.amount(),
                        request.curveSlot(),
                        request.status().code(),
                        PostgresJson.jsonb(roleCodeStrings(request.requiredRoleCodes())).data(),
                        PostgresJson.jsonb(roleCodeStrings(request.approvedRoleCodes())).data(),
                        PostgresJson.jsonb(roleCodeStrings(request.skippedRoleCodes())).data(),
                        request.requestedByAccountId(),
                        PostgresJson.offsetDateTime(request.resolvedAt()),
                        PostgresJson.jsonb(request.metadata()).data(),
                        PostgresJson.offsetDateTime(request.createdAt()),
                        PostgresJson.offsetDateTime(request.updatedAt()))
                .execute();
        return findByOrderId(request.orderId()).orElseThrow();
    }

    @Override
    public ShareReleaseRequestEntity markResolved(String requestId, ShareReleaseRequestStatus status, List<ProjectRoleCode> approvedRoles, Instant resolvedAt) {
        dsl.query("""
                                update share_release_requests
                                set status = ?::share_release_request_status,
                                    approved_role_codes = ?::jsonb,
                                    resolved_at = ?::timestamptz,
                                    updated_at = ?::timestamptz
                                where id = ?
                                """,
                        status.code(),
                        PostgresJson.jsonb(roleCodeStrings(approvedRoles)).data(),
                        PostgresJson.offsetDateTime(resolvedAt),
                        PostgresJson.offsetDateTime(resolvedAt),
                        requestId)
                .execute();
        return findById(requestId).orElseThrow();
    }

    private String selectSql() {
        return """
                select id, issuer_type::text as issuer_type, issuer_id, market_id, project_id, order_id, proof_id,
                       account_id, amount, curve_slot, status::text as status, required_role_codes,
                       approved_role_codes, skipped_role_codes, requested_by_account_id, resolved_at, metadata,
                       created_at, updated_at
                from share_release_requests
                """;
    }

    private ShareReleaseRequestEntity mapRecord(Record record) {
        return new ShareReleaseRequestEntity(
                record.get("id", String.class),
                ShareIssuerType.fromCode(record.get("issuer_type", String.class)),
                record.get("issuer_id", String.class),
                record.get("market_id", String.class),
                record.get("project_id", String.class),
                record.get("order_id", String.class),
                record.get("proof_id", String.class),
                record.get("account_id", String.class),
                record.get("amount", Integer.class),
                record.get("curve_slot", Integer.class),
                ShareReleaseRequestStatus.fromCode(record.get("status", String.class)),
                roleCodeEnums(PostgresJson.stringList(record.get("required_role_codes", org.jooq.JSONB.class))),
                roleCodeEnums(PostgresJson.stringList(record.get("approved_role_codes", org.jooq.JSONB.class))),
                roleCodeEnums(PostgresJson.stringList(record.get("skipped_role_codes", org.jooq.JSONB.class))),
                record.get("requested_by_account_id", String.class),
                PostgresJson.instant(record.get("resolved_at", java.time.OffsetDateTime.class)),
                PostgresJson.map(record.get("metadata", org.jooq.JSONB.class)),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)),
                PostgresJson.instant(record.get("updated_at", java.time.OffsetDateTime.class)));
    }

    private List<String> roleCodeStrings(List<ProjectRoleCode> roleCodes) {
        return roleCodes.stream().map(ProjectRoleCode::code).toList();
    }

    private List<ProjectRoleCode> roleCodeEnums(List<String> roleCodes) {
        return roleCodes.stream().map(ProjectRoleCode::fromCode).toList();
    }
}
