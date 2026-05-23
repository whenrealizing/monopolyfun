package com.monopolyfun.modules.share.infra.postgres;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.share.domain.ShareReleaseApprovalEntity;
import com.monopolyfun.modules.share.infra.ShareReleaseApprovalRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PostgresShareReleaseApprovalRepository implements ShareReleaseApprovalRepository {
    private final DSLContext dsl;

    public PostgresShareReleaseApprovalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<ShareReleaseApprovalEntity> findByRequestId(String requestId) {
        return dsl.resultQuery("""
                        select
                          approval->>'id' as id,
                          r.id as request_id,
                          approval->>'roleCode' as role_code,
                          approval->>'approverAccountId' as approver_account_id,
                          (approval->>'createdAt')::timestamptz as created_at
                        from share_release_requests r
                        cross join lateral jsonb_array_elements(coalesce(r.metadata->'approvals', '[]'::jsonb)) approval
                        where r.id = ?
                        order by created_at asc
                        """, requestId)
                .fetch(this::mapRecord);
    }

    @Override
    public ShareReleaseApprovalEntity save(String requestId, ProjectRoleCode roleCode, String approverAccountId) {
        Instant now = Instant.now();
        String approvalId = "share-approval-" + UUID.randomUUID();
        // 中文注释：审批明细作为 request 元数据内的事实数组保存，request 行仍然是 release 生命周期唯一聚合根。
        dsl.query("""
                                with current_request as (
                                  select metadata
                                  from share_release_requests
                                  where id = ?
                                  for update
                                ),
                                next_approvals as (
                                  select coalesce(jsonb_agg(approval order by approval->>'createdAt'), '[]'::jsonb) as approvals
                                  from (
                                    select value as approval
                                    from current_request cr
                                    cross join lateral jsonb_array_elements(coalesce(cr.metadata->'approvals', '[]'::jsonb)) value
                                    where value->>'roleCode' <> ?
                                    union all
                                    select jsonb_build_object(
                                      'id', ?,
                                      'requestId', ?,
                                      'roleCode', ?,
                                      'approverAccountId', ?,
                                      'createdAt', ?
                                    ) as approval
                                  ) approval_rows
                                )
                                update share_release_requests r
                                set metadata = jsonb_set(coalesce(r.metadata, '{}'::jsonb), '{approvals}', next_approvals.approvals, true),
                                    updated_at = ?::timestamptz
                                from next_approvals
                                where r.id = ?
                                """,
                        requestId,
                        roleCode.code(),
                        approvalId,
                        requestId,
                        roleCode.code(),
                        approverAccountId,
                        now.toString(),
                        PostgresJson.offsetDateTime(now),
                        requestId)
                .execute();
        return findByRequestId(requestId).stream()
                .filter(approval -> approval.roleCode() == roleCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Share release request was not found"));
    }

    private ShareReleaseApprovalEntity mapRecord(Record record) {
        return new ShareReleaseApprovalEntity(
                record.get("id", String.class),
                record.get("request_id", String.class),
                ProjectRoleCode.fromCode(record.get("role_code", String.class)),
                record.get("approver_account_id", String.class),
                PostgresJson.instant(record.get("created_at", java.time.OffsetDateTime.class)));
    }
}
