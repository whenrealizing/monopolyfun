package com.monopolyfun.modules.work.infra.postgres;

import com.fasterxml.jackson.core.type.TypeReference;
import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkReviewEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class PostgresWorkRepository implements WorkRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };
    private static final Table<?> WORK_ITEMS = DSL.table(DSL.name("work_items"));
    private static final Table<?> WORK_RUNS = DSL.table(DSL.name("work_runs"));
    private static final Table<?> WORK_RECEIPTS = DSL.table(DSL.name("work_receipts"));
    private static final Table<?> WORK_REVIEWS = DSL.table(DSL.name("work_reviews"));
    private static final Table<?> WORK_EVENTS = DSL.table(DSL.name("work_events"));

    private static final Field<String> WI_ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> WI_ITEM_NO = DSL.field(DSL.name("item_no"), String.class);
    private static final Field<String> WI_SOURCE_TYPE = DSL.field(DSL.name("source_type"), String.class);
    private static final Field<String> WI_SOURCE_ID = DSL.field(DSL.name("source_id"), String.class);
    private static final Field<String> WI_ACCOUNT_ID = DSL.field(DSL.name("account_id"), String.class);
    private static final Field<String> WI_TITLE = DSL.field(DSL.name("title"), String.class);
    private static final Field<String> WI_GOAL = DSL.field(DSL.name("goal"), String.class);
    private static final Field<JSONB> WI_ACCEPTANCE = DSL.field(DSL.name("acceptance_criteria"), JSONB.class);
    private static final Field<JSONB> WI_INPUT_REFS = DSL.field(DSL.name("input_refs"), JSONB.class);
    private static final Field<JSONB> WI_OUTPUT_SCHEMA = DSL.field(DSL.name("output_schema"), JSONB.class);
    private static final Field<String> WI_REQUIRED_ROLE = DSL.field(DSL.name("required_role"), String.class);
    private static final Field<String> WI_REQUIRED_CAPABILITY = DSL.field(DSL.name("required_capability"), String.class);
    private static final Field<String> WI_URGENCY = DSL.field(DSL.name("urgency"), String.class);
    private static final Field<String> WI_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<OffsetDateTime> WI_CLAIM_EXPIRES_AT = DSL.field(DSL.name("claim_expires_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WI_READY_AT = DSL.field(DSL.name("ready_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WI_CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WI_UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private static final Field<String> WR_ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> WR_RUN_NO = DSL.field(DSL.name("run_no"), String.class);
    private static final Field<String> WR_WORK_ITEM_ID = DSL.field(DSL.name("work_item_id"), String.class);
    private static final Field<String> WR_ACTOR_ACCOUNT_ID = DSL.field(DSL.name("actor_account_id"), String.class);
    private static final Field<String> WR_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> WR_EXECUTION_MODE = DSL.field(DSL.name("execution_mode"), String.class);
    private static final Field<OffsetDateTime> WR_STARTED_AT = DSL.field(DSL.name("started_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WR_SUBMITTED_AT = DSL.field(DSL.name("submitted_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WR_ACCEPTED_AT = DSL.field(DSL.name("accepted_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WR_UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private static final Field<String> WRC_ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> WRC_RECEIPT_NO = DSL.field(DSL.name("receipt_no"), String.class);
    private static final Field<String> WRC_WORK_RUN_ID = DSL.field(DSL.name("work_run_id"), String.class);
    private static final Field<String> WRC_SUMMARY = DSL.field(DSL.name("summary"), String.class);
    private static final Field<JSONB> WRC_OUTPUT = DSL.field(DSL.name("output"), JSONB.class);
    private static final Field<JSONB> WRC_EVIDENCE_REFS = DSL.field(DSL.name("evidence_refs"), JSONB.class);
    private static final Field<JSONB> WRC_TRACE_REFS = DSL.field(DSL.name("trace_refs"), JSONB.class);
    private static final Field<JSONB> WRC_CONTENT_HASHES = DSL.field(DSL.name("content_hashes"), JSONB.class);
    private static final Field<OffsetDateTime> WRC_CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);

    private static final Field<String> WREV_ID = DSL.field(DSL.name("id"), String.class);
    private static final Field<String> WREV_REVIEW_NO = DSL.field(DSL.name("review_no"), String.class);
    private static final Field<String> WREV_WORK_RUN_ID = DSL.field(DSL.name("work_run_id"), String.class);
    private static final Field<String> WREV_REVIEWER_ACCOUNT_ID = DSL.field(DSL.name("reviewer_account_id"), String.class);
    private static final Field<String> WREV_STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<String> WREV_DECISION = DSL.field(DSL.name("decision"), String.class);
    private static final Field<String> WREV_DECISION_REASON = DSL.field(DSL.name("decision_reason"), String.class);
    private static final Field<OffsetDateTime> WREV_CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> WREV_RESOLVED_AT = DSL.field(DSL.name("resolved_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public PostgresWorkRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void upsertItem(WorkItemEntity item) {
        // 中文注释：item_no 是 WorkItem 幂等边界，同一订单可以同时存在付款、交付、验收多个执行待办。
        dsl.insertInto(WORK_ITEMS)
                .set(WI_ID, item.id())
                .set(WI_ITEM_NO, item.itemNo())
                .set(WI_SOURCE_TYPE, item.sourceType())
                .set(WI_SOURCE_ID, item.sourceId())
                .set(WI_ACCOUNT_ID, item.accountId())
                .set(WI_TITLE, item.title())
                .set(WI_GOAL, item.goal())
                .set(WI_ACCEPTANCE, PostgresJson.jsonb(item.acceptanceCriteria()))
                .set(WI_INPUT_REFS, PostgresJson.jsonb(item.inputRefs()))
                .set(WI_OUTPUT_SCHEMA, PostgresJson.jsonb(item.outputSchema()))
                .set(WI_REQUIRED_ROLE, item.requiredRole())
                .set(WI_REQUIRED_CAPABILITY, item.requiredCapability())
                .set(WI_URGENCY, item.urgency())
                .set(WI_STATUS, item.status())
                .set(WI_CLAIM_EXPIRES_AT, PostgresJson.offsetDateTime(item.claimExpiresAt()))
                .set(WI_READY_AT, PostgresJson.offsetDateTime(item.readyAt()))
                .set(WI_CREATED_AT, PostgresJson.offsetDateTime(item.createdAt()))
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(item.updatedAt()))
                .onConflict(WI_ACCOUNT_ID, WI_ITEM_NO)
                .doUpdate()
                .set(WI_TITLE, item.title())
                .set(WI_GOAL, item.goal())
                .set(WI_ACCEPTANCE, PostgresJson.jsonb(item.acceptanceCriteria()))
                .set(WI_INPUT_REFS, PostgresJson.jsonb(item.inputRefs()))
                .set(WI_OUTPUT_SCHEMA, PostgresJson.jsonb(item.outputSchema()))
                .set(WI_REQUIRED_ROLE, item.requiredRole())
                .set(WI_REQUIRED_CAPABILITY, item.requiredCapability())
                .set(WI_URGENCY, item.urgency())
                .set(WI_STATUS, item.status())
                .set(WI_CLAIM_EXPIRES_AT, PostgresJson.offsetDateTime(item.claimExpiresAt()))
                .set(WI_READY_AT, PostgresJson.offsetDateTime(item.readyAt()))
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(item.updatedAt()))
                .execute();
    }

    @Override
    public List<WorkItemEntity> findItemsByAccountId(String accountId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(WI_ACCOUNT_ID.eq(accountId))
                .and(WI_STATUS.in("ready", "claimed", "submitted", "revision_requested", "disputed"))
                .orderBy(WI_READY_AT.desc(), WI_ITEM_NO.desc())
                .fetch(this::mapItem);
    }

    @Override
    public List<WorkItemEntity> findSubmittedProjectRoleItems() {
        // 中文注释：跨账号验收队列只暴露已提交的授权任务，权限过滤留给服务层按能力判断。
        return dsl.selectFrom(WORK_ITEMS)
                .where(WI_SOURCE_TYPE.eq("project_role_task"))
                .and(WI_STATUS.eq("submitted"))
                .orderBy(WI_UPDATED_AT.desc(), WI_ITEM_NO.desc())
                .fetch(this::mapItem);
    }

    @Override
    public int releaseExpiredClaims(Instant now) {
        var expiredItemIds = dsl.select(WI_ID)
                .from(WORK_ITEMS)
                .where(WI_STATUS.eq("claimed"))
                .and(WI_CLAIM_EXPIRES_AT.isNotNull())
                .and(WI_CLAIM_EXPIRES_AT.le(PostgresJson.offsetDateTime(now)));
        int closedRuns = dsl.update(WORK_RUNS)
                .set(WR_STATUS, "closed")
                .set(WR_UPDATED_AT, PostgresJson.offsetDateTime(now))
                .where(WR_WORK_ITEM_ID.in(expiredItemIds))
                .and(WR_STATUS.in("claimed", "running"))
                .execute();
        // 中文注释：claim 租约到期只释放执行权，任务本身回到 ready，后续可被重新领取。
        int releasedItems = dsl.update(WORK_ITEMS)
                .set(WI_STATUS, "ready")
                .set(WI_CLAIM_EXPIRES_AT, (OffsetDateTime) null)
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(now))
                .where(WI_STATUS.eq("claimed"))
                .and(WI_CLAIM_EXPIRES_AT.isNotNull())
                .and(WI_CLAIM_EXPIRES_AT.le(PostgresJson.offsetDateTime(now)))
                .execute();
        return Math.max(releasedItems, closedRuns);
    }

    @Override
    public int renewClaimLease(String accountId, String itemNo, Instant claimExpiresAt, Instant now) {
        if (accountId == null || accountId.isBlank() || itemNo == null || itemNo.isBlank() || claimExpiresAt == null) {
            return 0;
        }
        // 中文注释：外部代码进度只延长当前领取人的租约，避免 webhook 让未领取任务进入锁定状态。
        return dsl.update(WORK_ITEMS)
                .set(WI_CLAIM_EXPIRES_AT, PostgresJson.offsetDateTime(claimExpiresAt))
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(now))
                .where(WI_ACCOUNT_ID.eq(accountId))
                .and(WI_ITEM_NO.eq(itemNo))
                .and(WI_STATUS.eq("claimed"))
                .execute();
    }

    @Override
    public int closeStaleSourceItems(String accountId, Set<String> sourceTypes, Set<String> activeItemNos, String reason) {
        if (accountId == null || accountId.isBlank() || sourceTypes == null || sourceTypes.isEmpty()) {
            return 0;
        }
        var condition = WI_ACCOUNT_ID.eq(accountId)
                .and(WI_SOURCE_TYPE.in(sourceTypes))
                .and(WI_STATUS.in("ready", "claimed", "submitted", "revision_requested", "disputed"));
        if (activeItemNos != null && !activeItemNos.isEmpty()) {
            condition = condition.and(WI_ITEM_NO.notIn(activeItemNos));
        }
        // 中文注释：关闭源已消失的执行项，保留历史记录，同时让 workbench 查询自然过滤掉旧任务。
        return dsl.update(WORK_ITEMS)
                .set(WI_STATUS, "closed")
                .set(WI_CLAIM_EXPIRES_AT, (OffsetDateTime) null)
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(Instant.now()))
                .where(condition)
                .execute();
    }

    @Override
    public int closeOpenItemsBySource(String sourceType, String sourceId, String reason) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            return 0;
        }
        // 中文注释：业务终局主动关闭同源 WorkItem，减少等待下一次 source sync 才清队列的延迟。
        return dsl.update(WORK_ITEMS)
                .set(WI_STATUS, "closed")
                .set(WI_CLAIM_EXPIRES_AT, (OffsetDateTime) null)
                .set(WI_UPDATED_AT, PostgresJson.offsetDateTime(Instant.now()))
                .where(WI_SOURCE_TYPE.eq(sourceType))
                .and(WI_SOURCE_ID.eq(sourceId))
                .and(WI_STATUS.in("ready", "claimed", "submitted", "revision_requested", "disputed"))
                .execute();
    }

    @Override
    public List<WorkItemEntity> findItemsBySource(String sourceType, String sourceId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(WI_SOURCE_TYPE.eq(sourceType))
                .and(WI_SOURCE_ID.eq(sourceId))
                .orderBy(WI_READY_AT.desc(), WI_ITEM_NO.desc())
                .fetch(this::mapItem);
    }

    @Override
    public Optional<WorkItemEntity> findItemByNoOrId(String itemNoOrId) {
        return dsl.selectFrom(WORK_ITEMS)
                .where(WI_ID.eq(itemNoOrId).or(WI_ITEM_NO.eq(itemNoOrId)))
                .fetchOptional(this::mapItem);
    }

    @Override
    public Optional<WorkRunEntity> findRunByItemAndActor(String workItemId, String actorAccountId) {
        return dsl.selectFrom(WORK_RUNS)
                .where(WR_WORK_ITEM_ID.eq(workItemId))
                .and(WR_ACTOR_ACCOUNT_ID.eq(actorAccountId))
                .fetchOptional(this::mapRun);
    }

    @Override
    public Optional<WorkRunEntity> findRunByItemId(String workItemId) {
        return dsl.selectFrom(WORK_RUNS)
                .where(WR_WORK_ITEM_ID.eq(workItemId))
                .orderBy(WR_UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRun);
    }

    @Override
    public Optional<WorkRunEntity> findRunByNoOrId(String runNoOrId) {
        return dsl.selectFrom(WORK_RUNS)
                .where(WR_ID.eq(runNoOrId).or(WR_RUN_NO.eq(runNoOrId)))
                .fetchOptional(this::mapRun);
    }

    @Override
    public WorkRunEntity saveRun(WorkRunEntity run) {
        dsl.insertInto(WORK_RUNS)
                .set(WR_ID, run.id())
                .set(WR_RUN_NO, run.runNo())
                .set(WR_WORK_ITEM_ID, run.workItemId())
                .set(WR_ACTOR_ACCOUNT_ID, run.actorAccountId())
                .set(WR_STATUS, run.status())
                .set(WR_EXECUTION_MODE, run.executionMode())
                .set(WR_STARTED_AT, PostgresJson.offsetDateTime(run.startedAt()))
                .set(WR_SUBMITTED_AT, PostgresJson.offsetDateTime(run.submittedAt()))
                .set(WR_ACCEPTED_AT, PostgresJson.offsetDateTime(run.acceptedAt()))
                .set(WR_UPDATED_AT, PostgresJson.offsetDateTime(run.updatedAt()))
                .onConflict(WR_WORK_ITEM_ID, WR_ACTOR_ACCOUNT_ID)
                .doUpdate()
                .set(WR_STATUS, run.status())
                .set(WR_EXECUTION_MODE, run.executionMode())
                .set(WR_SUBMITTED_AT, PostgresJson.offsetDateTime(run.submittedAt()))
                .set(WR_ACCEPTED_AT, PostgresJson.offsetDateTime(run.acceptedAt()))
                .set(WR_UPDATED_AT, PostgresJson.offsetDateTime(run.updatedAt()))
                .execute();
        return findRunByItemAndActor(run.workItemId(), run.actorAccountId()).orElse(run);
    }

    @Override
    public WorkItemEntity saveItem(WorkItemEntity item) {
        upsertItem(item);
        return findItemByNoOrId(item.id()).orElse(item);
    }

    @Override
    public WorkReceiptEntity saveReceipt(WorkReceiptEntity receipt) {
        dsl.insertInto(WORK_RECEIPTS)
                .set(WRC_ID, receipt.id())
                .set(WRC_RECEIPT_NO, receipt.receiptNo())
                .set(WRC_WORK_RUN_ID, receipt.workRunId())
                .set(WRC_SUMMARY, receipt.summary())
                .set(WRC_OUTPUT, PostgresJson.jsonb(receipt.output()))
                .set(WRC_EVIDENCE_REFS, PostgresJson.jsonb(receipt.evidenceRefs()))
                .set(WRC_TRACE_REFS, PostgresJson.jsonb(receipt.traceRefs()))
                .set(WRC_CONTENT_HASHES, PostgresJson.jsonb(receipt.contentHashes()))
                .set(WRC_CREATED_AT, PostgresJson.offsetDateTime(receipt.createdAt()))
                .execute();
        return receipt;
    }

    @Override
    public Optional<WorkReceiptEntity> findLatestReceiptByRunId(String workRunId) {
        return dsl.selectFrom(WORK_RECEIPTS)
                .where(WRC_WORK_RUN_ID.eq(workRunId))
                .orderBy(WRC_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapReceipt);
    }

    @Override
    public WorkReviewEntity saveReview(WorkReviewEntity review) {
        dsl.insertInto(WORK_REVIEWS)
                .set(WREV_ID, review.id())
                .set(WREV_REVIEW_NO, review.reviewNo())
                .set(WREV_WORK_RUN_ID, review.workRunId())
                .set(WREV_REVIEWER_ACCOUNT_ID, review.reviewerAccountId())
                .set(WREV_STATUS, review.status())
                .set(WREV_DECISION, review.decision())
                .set(WREV_DECISION_REASON, review.decisionReason())
                .set(WREV_CREATED_AT, PostgresJson.offsetDateTime(review.createdAt()))
                .set(WREV_RESOLVED_AT, PostgresJson.offsetDateTime(review.resolvedAt()))
                .onConflict(WREV_REVIEW_NO)
                .doUpdate()
                .set(WREV_WORK_RUN_ID, review.workRunId())
                .set(WREV_REVIEWER_ACCOUNT_ID, review.reviewerAccountId())
                .set(WREV_STATUS, review.status())
                .set(WREV_DECISION, review.decision())
                .set(WREV_DECISION_REASON, review.decisionReason())
                .set(WREV_RESOLVED_AT, PostgresJson.offsetDateTime(review.resolvedAt()))
                .execute();
        return findReviewByRunId(review.workRunId()).orElse(review);
    }

    @Override
    public Optional<WorkReviewEntity> findReviewByRunId(String workRunId) {
        return dsl.selectFrom(WORK_REVIEWS)
                .where(WREV_WORK_RUN_ID.eq(workRunId))
                .orderBy(WREV_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapReview);
    }

    @Override
    public void saveEvent(WorkEventEntity event) {
        dsl.insertInto(WORK_EVENTS)
                .set(DSL.field(DSL.name("id"), String.class), event.id())
                .set(DSL.field(DSL.name("subject_type"), String.class), event.subjectType())
                .set(DSL.field(DSL.name("subject_id"), String.class), event.subjectId())
                .set(DSL.field(DSL.name("actor_account_id"), String.class), event.actorAccountId())
                .set(DSL.field(DSL.name("event_type"), String.class), event.eventType())
                .set(DSL.field(DSL.name("action_id"), String.class), event.actionId())
                .set(DSL.field(DSL.name("input_snapshot"), JSONB.class), PostgresJson.jsonb(event.inputSnapshot()))
                .set(DSL.field(DSL.name("output_snapshot"), JSONB.class), PostgresJson.jsonb(event.outputSnapshot()))
                .set(DSL.field(DSL.name("receipt_id"), String.class), event.receiptId())
                .set(DSL.field(DSL.name("created_at"), OffsetDateTime.class), PostgresJson.offsetDateTime(event.createdAt()))
                .execute();
    }

    private WorkItemEntity mapItem(Record record) {
        return new WorkItemEntity(
                record.get(WI_ID),
                record.get(WI_ITEM_NO),
                record.get(WI_SOURCE_TYPE),
                record.get(WI_SOURCE_ID),
                record.get(WI_ACCOUNT_ID),
                record.get(WI_TITLE),
                record.get(WI_GOAL),
                PostgresJson.jsonbValue(record.get(WI_ACCEPTANCE), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get(WI_INPUT_REFS), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get(WI_OUTPUT_SCHEMA), OBJECT_MAP, Map.of()),
                record.get(WI_REQUIRED_ROLE),
                record.get(WI_REQUIRED_CAPABILITY),
                record.get(WI_URGENCY),
                record.get(WI_STATUS),
                PostgresJson.instant(record.get(WI_CLAIM_EXPIRES_AT)),
                PostgresJson.instant(record.get(WI_READY_AT)),
                PostgresJson.instant(record.get(WI_CREATED_AT)),
                PostgresJson.instant(record.get(WI_UPDATED_AT)));
    }

    private WorkRunEntity mapRun(Record record) {
        return new WorkRunEntity(
                record.get(WR_ID),
                record.get(WR_RUN_NO),
                record.get(WR_WORK_ITEM_ID),
                record.get(WR_ACTOR_ACCOUNT_ID),
                record.get(WR_STATUS),
                record.get(WR_EXECUTION_MODE),
                PostgresJson.instant(record.get(WR_STARTED_AT)),
                PostgresJson.instant(record.get(WR_SUBMITTED_AT)),
                PostgresJson.instant(record.get(WR_ACCEPTED_AT)),
                PostgresJson.instant(record.get(WR_UPDATED_AT)));
    }

    private WorkReceiptEntity mapReceipt(Record record) {
        return new WorkReceiptEntity(
                record.get(WRC_ID),
                record.get(WRC_RECEIPT_NO),
                record.get(WRC_WORK_RUN_ID),
                record.get(WRC_SUMMARY),
                PostgresJson.jsonbValue(record.get(WRC_OUTPUT), OBJECT_MAP, Map.of()),
                PostgresJson.jsonbValue(record.get(WRC_EVIDENCE_REFS), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get(WRC_TRACE_REFS), STRING_LIST, List.of()),
                PostgresJson.jsonbValue(record.get(WRC_CONTENT_HASHES), STRING_LIST, List.of()),
                PostgresJson.instant(record.get(WRC_CREATED_AT)));
    }

    private WorkReviewEntity mapReview(Record record) {
        return new WorkReviewEntity(
                record.get(WREV_ID),
                record.get(WREV_REVIEW_NO),
                record.get(WREV_WORK_RUN_ID),
                record.get(WREV_REVIEWER_ACCOUNT_ID),
                record.get(WREV_STATUS),
                record.get(WREV_DECISION),
                record.get(WREV_DECISION_REASON),
                PostgresJson.instant(record.get(WREV_CREATED_AT)),
                PostgresJson.instant(record.get(WREV_RESOLVED_AT)));
    }
}
