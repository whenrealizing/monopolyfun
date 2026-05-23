package com.monopolyfun.modules.order.infra.postgres;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.projection.PostItemProjectionWriter;
import com.monopolyfun.modules.projection.ProjectTimelineProjectionWriter;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.shared.pagination.CursorCodec;
import com.monopolyfun.shared.pagination.CursorKey;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.monopolyfun.generated.jooq.Tables.ORDERS;
import static com.monopolyfun.generated.jooq.Tables.PAYMENT_INTENTS;

@Repository
public class PostgresOrderRepository implements OrderRepository {
    private static final Table<?> ORDER_PAYMENT_STATE = DSL.table(DSL.name("order_payment_state"));
    private static final Field<String> OPS_ORDER_ID = DSL.field(DSL.name("order_payment_state", "order_id"), String.class);
    private static final Field<String> OPS_LATEST_PAYMENT_INTENT_ID = DSL.field(DSL.name("order_payment_state", "latest_payment_intent_id"), String.class);
    private static final Table<?> ORDER_PARTICIPANTS = DSL.table(DSL.name("order_participants"));
    private static final Field<String> OP_ORDER_ID = DSL.field(DSL.name("order_id"), String.class);
    private static final Field<String> OP_ACCOUNT_ID = DSL.field(DSL.name("account_id"), String.class);
    private static final Field<String> OP_ROLE_CODE = DSL.field(DSL.name("role_code"), String.class);
    private static final Field<String> OP_SOURCE = DSL.field(DSL.name("source"), String.class);
    private static final Field<String> DISPUTE_OPENED_BY_ACCOUNT_ID = DSL.field(DSL.name("orders", "dispute_opened_by_account_id"), String.class);
    private static final Field<String> DISPUTE_OPENED_FROM_STATUS = DSL.field(DSL.name("orders", "dispute_opened_from_status"), String.class);
    private static final Field<String> DISPUTE_OPENED_FROM_WINDOW_STATUS = DSL.field(DSL.name("orders", "dispute_opened_from_window_status"), String.class);
    private static final Field<OffsetDateTime> DISPUTE_OPENED_FROM_WINDOW_EXPIRES_AT = DSL.field(DSL.name("orders", "dispute_opened_from_window_expires_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> DISPUTE_OPENED_AT = DSL.field(DSL.name("orders", "dispute_opened_at"), OffsetDateTime.class);
    private static final Field<String> DISPUTE_CANCELLED_BY_ACCOUNT_ID = DSL.field(DSL.name("orders", "dispute_cancelled_by_account_id"), String.class);
    private static final Field<OffsetDateTime> DISPUTE_CANCELLED_AT = DSL.field(DSL.name("orders", "dispute_cancelled_at"), OffsetDateTime.class);
    private static final Field<String> DISPUTE_CANCEL_REASON = DSL.field(DSL.name("orders", "dispute_cancel_reason"), String.class);
    private final DSLContext dsl;
    private final PostItemProjectionWriter postItemProjectionWriter;
    private final ProjectTimelineProjectionWriter projectTimelineProjectionWriter;

    public PostgresOrderRepository(
            DSLContext dsl,
            PostItemProjectionWriter postItemProjectionWriter,
            ProjectTimelineProjectionWriter projectTimelineProjectionWriter) {
        this.dsl = dsl;
        this.postItemProjectionWriter = postItemProjectionWriter;
        this.projectTimelineProjectionWriter = projectTimelineProjectionWriter;
    }

    @Override
    public List<OrderEntity> findAll() {
        return dsl.select(orderFields())
                .from(ORDERS)
                .orderBy(ORDERS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        dsl.select(ORDERS.STATUS, DSL.count())
                .from(ORDERS)
                .groupBy(ORDERS.STATUS)
                .fetch(record -> counts.put(String.valueOf(record.value1()).toLowerCase(), record.value2().longValue()));
        return counts;
    }

    @Override
    public List<OrderEntity> findByParticipantAccountId(String accountId, int limit) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(participantCondition(accountId))
                .orderBy(ORDERS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public PageResult<OrderEntity> findByParticipantAccountId(String accountId, PageQuery pageQuery) {
        CursorKey cursor = pageQuery.cursorKey();
        List<OrderEntity> fetched = dsl.select(orderFields())
                .from(ORDERS)
                .where(participantCondition(accountId))
                .and(updatedAtCursor(cursor))
                .orderBy(ORDERS.UPDATED_AT.desc(), ORDERS.ID.desc())
                .limit(pageQuery.fetchLimit())
                .fetch(this::mapRecord);
        return PageResult.fromFetched(fetched, pageQuery.limit(), order -> CursorCodec.encode(order.updatedAt().toString(), order.id()));
    }

    @Override
    public List<OrderEntity> findWorkbenchCandidates(String accountId, int limit) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(participantCondition(accountId))
                .and(ORDERS.STATUS.in(
                        com.monopolyfun.generated.jooq.enums.OrderStatus.claimed,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.delivered,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.accepted_open,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.disputed))
                .orderBy(ORDERS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<OrderEntity> findDisputed(int limit) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.STATUS.eq(com.monopolyfun.generated.jooq.enums.OrderStatus.disputed))
                .orderBy(ORDERS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<OrderEntity> findExpiredPaymentLocks(Instant dueAt, int limit) {
        Field<String> paymentDueAt = DSL.field("orders.metadata ->> 'paymentDueAt'", String.class);
        Field<String> lockExpiresAt = DSL.field("orders.metadata ->> 'lockExpiresAt'", String.class);
        Field<String> explicitDueAt = DSL.when(
                        ORDERS.SETTLEMENT_TYPE.eq(com.monopolyfun.generated.jooq.enums.SettlementType.money),
                        paymentDueAt)
                .otherwise(DSL.coalesce(paymentDueAt, lockExpiresAt));
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.STATUS.eq(com.monopolyfun.generated.jooq.enums.OrderStatus.claimed))
                .and(explicitDueAt.le(dueAt.toString()))
                .and(ORDERS.SETTLEMENT_TYPE.ne(com.monopolyfun.generated.jooq.enums.SettlementType.money)
                        .or(DSL.notExists(DSL.selectOne()
                                .from(PAYMENT_INTENTS)
                                .where(PAYMENT_INTENTS.ORDER_ID.eq(ORDERS.ID))
                                .and(PAYMENT_INTENTS.STATUS.eq(com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.captured)))))
                .orderBy(explicitDueAt.asc(), ORDERS.CREATED_AT.asc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<OrderEntity> findExpiredDisputeWindows(Instant dueAt, int limit) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.STATUS.eq(com.monopolyfun.generated.jooq.enums.OrderStatus.accepted_open))
                .and(ORDERS.DISPUTE_WINDOW_STATUS.eq(OrderEntity.DISPUTE_WINDOW_OPEN))
                .and(ORDERS.DISPUTE_WINDOW_EXPIRES_AT.isNotNull())
                .and(ORDERS.DISPUTE_WINDOW_EXPIRES_AT.le(PostgresJson.offsetDateTime(dueAt)))
                .orderBy(ORDERS.DISPUTE_WINDOW_EXPIRES_AT.asc())
                .limit(Math.max(1, limit))
                .fetch(this::mapRecord);
    }

    @Override
    public List<SettlementAnomaly> findSettlementAnomalies(int limit) {
        // 中文注释：结算异常用订单与最新支付意图的数据库 join 读取，避免后台逐单查询 payment_intents。
        return dsl.select(orderFields())
                .select(
                        PAYMENT_INTENTS.ID,
                        PAYMENT_INTENTS.PAYMENT_NO,
                        PAYMENT_INTENTS.ORDER_ID,
                        PAYMENT_INTENTS.ACCOUNT_ID,
                        PAYMENT_INTENTS.PROVIDER,
                        PAYMENT_INTENTS.PROVIDER_PAYMENT_REF,
                        PAYMENT_INTENTS.STATUS,
                        PAYMENT_INTENTS.AMOUNT_MINOR,
                        PAYMENT_INTENTS.CURRENCY,
                        PAYMENT_INTENTS.CALLBACK_TOKEN,
                        PAYMENT_INTENTS.AUTHORIZED_AT,
                        PAYMENT_INTENTS.CAPTURED_AT,
                        PAYMENT_INTENTS.REFUNDED_AT,
                        PAYMENT_INTENTS.CANCELLED_AT,
                        PAYMENT_INTENTS.DISPUTED_AT,
                        PAYMENT_INTENTS.METADATA,
                        PAYMENT_INTENTS.CREATED_AT,
                        PAYMENT_INTENTS.UPDATED_AT)
                .from(ORDERS)
                .leftJoin(ORDER_PAYMENT_STATE).on(OPS_ORDER_ID.eq(ORDERS.ID))
                .leftJoin(PAYMENT_INTENTS).on(PAYMENT_INTENTS.ID.eq(OPS_LATEST_PAYMENT_INTENT_ID))
                .where(ORDERS.SETTLEMENT_TYPE.eq(com.monopolyfun.generated.jooq.enums.SettlementType.money))
                .and(ORDERS.STATUS.in(
                        com.monopolyfun.generated.jooq.enums.OrderStatus.delivered,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.disputed))
                .and(PAYMENT_INTENTS.ID.isNull().or(PAYMENT_INTENTS.STATUS.in(
                        com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.pending,
                        com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.failed,
                        com.monopolyfun.generated.jooq.enums.PaymentIntentStatus.disputed)))
                .orderBy(ORDERS.UPDATED_AT.desc())
                .limit(Math.max(1, limit))
                .fetch(record -> {
                    OrderEntity order = mapRecord(record);
                    PaymentIntentEntity paymentIntent = mapPaymentIntent(record);
                    String reason = paymentIntent == null ? "missing_payment_intent" : "payment_" + paymentIntent.status().name().toLowerCase();
                    return new SettlementAnomaly(order, paymentIntent, reason);
                });
    }

    @Override
    public List<OrderEntity> findByMarketId(String marketId) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.MARKET_ID.eq(marketId))
                .orderBy(ORDERS.CREATED_AT.asc())
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<OrderEntity> findById(String id) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public List<OrderEntity> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.ID.in(ids))
                .fetch(this::mapRecord);
    }

    @Override
    public Optional<OrderEntity> findByOrderNo(String orderNo) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.ORDER_NO.eq(orderNo))
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<OrderEntity> findActiveByListingIdAndClaimedByAccountId(String listingId, String accountId) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.LISTING_ID.eq(listingId))
                .and(ORDERS.CLAIMED_BY_ACCOUNT_ID.eq(accountId))
                .and(ORDERS.STATUS.in(
                        com.monopolyfun.generated.jooq.enums.OrderStatus.claimed,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.delivered,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.accepted_open,
                        com.monopolyfun.generated.jooq.enums.OrderStatus.disputed))
                .orderBy(ORDERS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<OrderEntity> findFirstByListingId(String listingId) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.LISTING_ID.eq(listingId))
                .orderBy(ORDERS.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public Optional<OrderEntity> findFirstByParentOrderId(String parentOrderId) {
        return dsl.select(orderFields())
                .from(ORDERS)
                .where(ORDERS.PARENT_ORDER_ID.eq(parentOrderId))
                .orderBy(ORDERS.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::mapRecord);
    }

    @Override
    public OrderEntity save(OrderEntity order) {
        dsl.insertInto(ORDERS)
                .set(ORDERS.ID, order.id())
                .set(ORDERS.ORDER_NO, order.orderNo())
                .set(ORDERS.MARKET_ID, order.marketId())
                .set(ORDERS.LISTING_ID, order.listingId())
                .set(ORDERS.KIND, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingKind.class, order.kind()))
                .set(ORDERS.POST_KIND, order.postKind() == null ? null : order.postKind().name().toLowerCase())
                .set(ORDERS.POST_ID, order.postId())
                .set(ORDERS.PARENT_ORDER_ID, order.parentOrderId())
                .set(ORDERS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.OrderStatus.class, order.status()))
                .set(ORDERS.DISPLAY_PHASE, order.displayPhase())
                .set(ORDERS.CLAIMED_BY_ACCOUNT_ID, order.claimedByAccountId())
                .set(ORDERS.SUBMITTED_BY_ACCOUNT_ID, order.submittedByAccountId())
                .set(ORDERS.ACCEPTED_BY_ACCOUNT_ID, order.acceptedByAccountId())
                .set(ORDERS.REVIEWER_ACCOUNT_ID, order.reviewerAccountId())
                .set(ORDERS.REVIEW_DUE_AT, PostgresJson.offsetDateTime(order.reviewDueAt()))
                .set(ORDERS.PROOF_ID, order.proofId())
                .set(ORDERS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, order.settlementType()))
                .set(ORDERS.SETTLEMENT_AMOUNT, order.settlementAmount())
                .set(ORDERS.CLOSED_REASON, order.closedReason())
                .set(ORDERS.DISPUTE_REASON, order.disputeReason())
                .set(ORDERS.REVIEW_LISTING_ID, (String) null)
                .set(ORDERS.REVIEW_POST_ID, order.reviewPostId())
                .set(DISPUTE_OPENED_BY_ACCOUNT_ID, order.disputeOpenedByAccountId())
                .set(DISPUTE_OPENED_FROM_STATUS, statusName(order.disputeOpenedFromStatus()))
                .set(DISPUTE_OPENED_FROM_WINDOW_STATUS, order.disputeOpenedFromWindowStatus())
                .set(DISPUTE_OPENED_FROM_WINDOW_EXPIRES_AT, PostgresJson.offsetDateTime(order.disputeOpenedFromWindowExpiresAt()))
                .set(DISPUTE_OPENED_AT, PostgresJson.offsetDateTime(order.disputeOpenedAt()))
                .set(DISPUTE_CANCELLED_BY_ACCOUNT_ID, order.disputeCancelledByAccountId())
                .set(DISPUTE_CANCELLED_AT, PostgresJson.offsetDateTime(order.disputeCancelledAt()))
                .set(DISPUTE_CANCEL_REASON, order.disputeCancelReason())
                .set(ORDERS.BACKOFFICE_OVERRIDE_DECISION, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ReviewDecision.class, order.backofficeOverrideDecision()))
                .set(ORDERS.BACKOFFICE_OVERRIDE_REASON, order.backofficeOverrideReason())
                .set(ORDERS.CHALLENGE_NONCE, order.challengeNonce())
                .set(ORDERS.SETTLEMENT_FROZEN, order.settlementFrozen())
                .set(ORDERS.ACCEPTANCE_CRITERIA_SNAPSHOT, PostgresJson.jsonb(order.acceptanceCriteriaSnapshot()))
                .set(ORDERS.PROOF_SPEC_SNAPSHOT, order.proofSpecSnapshot())
                .set(ORDERS.SETTLEMENT_SPEC_SNAPSHOT, order.settlementSpecSnapshot())
                .set(ORDERS.REVIEW_STATUS, order.reviewStatus())
                .set(ORDERS.DISPUTE_WINDOW_STATUS, order.disputeWindowStatus())
                .set(ORDERS.DISPUTE_WINDOW_EXPIRES_AT, PostgresJson.offsetDateTime(order.disputeWindowExpiresAt()))
                .set(ORDERS.FINALIZED_AT, PostgresJson.offsetDateTime(order.finalizedAt()))
                .set(ORDERS.RISK_LEVEL, order.riskLevel())
                .set(ORDERS.MANUAL_REVIEW_REQUIRED, order.manualReviewRequired())
                .set(ORDERS.DELIVERY_SNAPSHOT, PostgresJson.jsonb(order.deliverySnapshot()))
                .set(ORDERS.SETTLEMENT_SNAPSHOT, PostgresJson.jsonb(order.settlementSnapshot()))
                .set(ORDERS.METADATA, PostgresJson.jsonb(order.metadata()))
                .set(ORDERS.CREATED_AT, PostgresJson.offsetDateTime(order.createdAt()))
                .set(ORDERS.UPDATED_AT, PostgresJson.offsetDateTime(order.updatedAt()))
                .onConflict(ORDERS.ID)
                .doUpdate()
                // 中文注释：订单展示编号是公开路由真相源，任何状态更新都显式保留编号列。
                .set(ORDERS.ORDER_NO, order.orderNo())
                .set(ORDERS.MARKET_ID, order.marketId())
                .set(ORDERS.LISTING_ID, order.listingId())
                .set(ORDERS.KIND, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ListingKind.class, order.kind()))
                .set(ORDERS.POST_KIND, order.postKind() == null ? null : order.postKind().name().toLowerCase())
                .set(ORDERS.POST_ID, order.postId())
                .set(ORDERS.PARENT_ORDER_ID, order.parentOrderId())
                .set(ORDERS.STATUS, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.OrderStatus.class, order.status()))
                .set(ORDERS.DISPLAY_PHASE, order.displayPhase())
                .set(ORDERS.CLAIMED_BY_ACCOUNT_ID, order.claimedByAccountId())
                .set(ORDERS.SUBMITTED_BY_ACCOUNT_ID, order.submittedByAccountId())
                .set(ORDERS.ACCEPTED_BY_ACCOUNT_ID, order.acceptedByAccountId())
                .set(ORDERS.REVIEWER_ACCOUNT_ID, order.reviewerAccountId())
                .set(ORDERS.REVIEW_DUE_AT, PostgresJson.offsetDateTime(order.reviewDueAt()))
                .set(ORDERS.PROOF_ID, order.proofId())
                .set(ORDERS.SETTLEMENT_TYPE, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.SettlementType.class, order.settlementType()))
                .set(ORDERS.SETTLEMENT_AMOUNT, order.settlementAmount())
                .set(ORDERS.CLOSED_REASON, order.closedReason())
                .set(ORDERS.DISPUTE_REASON, order.disputeReason())
                .set(ORDERS.REVIEW_LISTING_ID, (String) null)
                .set(ORDERS.REVIEW_POST_ID, order.reviewPostId())
                .set(DISPUTE_OPENED_BY_ACCOUNT_ID, order.disputeOpenedByAccountId())
                .set(DISPUTE_OPENED_FROM_STATUS, statusName(order.disputeOpenedFromStatus()))
                .set(DISPUTE_OPENED_FROM_WINDOW_STATUS, order.disputeOpenedFromWindowStatus())
                .set(DISPUTE_OPENED_FROM_WINDOW_EXPIRES_AT, PostgresJson.offsetDateTime(order.disputeOpenedFromWindowExpiresAt()))
                .set(DISPUTE_OPENED_AT, PostgresJson.offsetDateTime(order.disputeOpenedAt()))
                .set(DISPUTE_CANCELLED_BY_ACCOUNT_ID, order.disputeCancelledByAccountId())
                .set(DISPUTE_CANCELLED_AT, PostgresJson.offsetDateTime(order.disputeCancelledAt()))
                .set(DISPUTE_CANCEL_REASON, order.disputeCancelReason())
                .set(ORDERS.BACKOFFICE_OVERRIDE_DECISION, PostgresJson.jooqEnum(com.monopolyfun.generated.jooq.enums.ReviewDecision.class, order.backofficeOverrideDecision()))
                .set(ORDERS.BACKOFFICE_OVERRIDE_REASON, order.backofficeOverrideReason())
                .set(ORDERS.CHALLENGE_NONCE, order.challengeNonce())
                .set(ORDERS.SETTLEMENT_FROZEN, order.settlementFrozen())
                .set(ORDERS.ACCEPTANCE_CRITERIA_SNAPSHOT, PostgresJson.jsonb(order.acceptanceCriteriaSnapshot()))
                .set(ORDERS.PROOF_SPEC_SNAPSHOT, order.proofSpecSnapshot())
                .set(ORDERS.SETTLEMENT_SPEC_SNAPSHOT, order.settlementSpecSnapshot())
                .set(ORDERS.REVIEW_STATUS, order.reviewStatus())
                .set(ORDERS.DISPUTE_WINDOW_STATUS, order.disputeWindowStatus())
                .set(ORDERS.DISPUTE_WINDOW_EXPIRES_AT, PostgresJson.offsetDateTime(order.disputeWindowExpiresAt()))
                .set(ORDERS.FINALIZED_AT, PostgresJson.offsetDateTime(order.finalizedAt()))
                .set(ORDERS.RISK_LEVEL, order.riskLevel())
                .set(ORDERS.MANUAL_REVIEW_REQUIRED, order.manualReviewRequired())
                .set(ORDERS.DELIVERY_SNAPSHOT, PostgresJson.jsonb(order.deliverySnapshot()))
                .set(ORDERS.SETTLEMENT_SNAPSHOT, PostgresJson.jsonb(order.settlementSnapshot()))
                .set(ORDERS.METADATA, PostgresJson.jsonb(order.metadata()))
                .set(ORDERS.UPDATED_AT, PostgresJson.offsetDateTime(order.updatedAt()))
                .execute();
        syncOrderParticipants(order);
        postItemProjectionWriter.syncOrderState(order);
        projectTimelineProjectionWriter.syncOrder(order);
        return order;
    }

    private List<? extends SelectFieldOrAsterisk> orderFields() {
        // 固定读取业务实体使用的列，避免本机 codegen schema 带入运行库尚未提供的扩展列。
        return List.of(
                ORDERS.ID,
                ORDERS.ORDER_NO,
                ORDERS.MARKET_ID,
                ORDERS.LISTING_ID,
                ORDERS.KIND,
                ORDERS.POST_KIND,
                ORDERS.POST_ID,
                ORDERS.PARENT_ORDER_ID,
                ORDERS.STATUS,
                ORDERS.DISPLAY_PHASE,
                ORDERS.CLAIMED_BY_ACCOUNT_ID,
                ORDERS.SUBMITTED_BY_ACCOUNT_ID,
                ORDERS.ACCEPTED_BY_ACCOUNT_ID,
                ORDERS.REVIEWER_ACCOUNT_ID,
                ORDERS.REVIEW_DUE_AT,
                ORDERS.PROOF_ID,
                ORDERS.SETTLEMENT_TYPE,
                ORDERS.SETTLEMENT_AMOUNT,
                ORDERS.CLOSED_REASON,
                ORDERS.DISPUTE_REASON,
                ORDERS.REVIEW_LISTING_ID,
                ORDERS.REVIEW_POST_ID,
                DISPUTE_OPENED_BY_ACCOUNT_ID,
                DISPUTE_OPENED_FROM_STATUS,
                DISPUTE_OPENED_FROM_WINDOW_STATUS,
                DISPUTE_OPENED_FROM_WINDOW_EXPIRES_AT,
                DISPUTE_OPENED_AT,
                DISPUTE_CANCELLED_BY_ACCOUNT_ID,
                DISPUTE_CANCELLED_AT,
                DISPUTE_CANCEL_REASON,
                ORDERS.BACKOFFICE_OVERRIDE_DECISION,
                ORDERS.BACKOFFICE_OVERRIDE_REASON,
                ORDERS.CHALLENGE_NONCE,
                ORDERS.SETTLEMENT_FROZEN,
                ORDERS.ACCEPTANCE_CRITERIA_SNAPSHOT,
                ORDERS.PROOF_SPEC_SNAPSHOT,
                ORDERS.SETTLEMENT_SPEC_SNAPSHOT,
                ORDERS.REVIEW_STATUS,
                ORDERS.DISPUTE_WINDOW_STATUS,
                ORDERS.DISPUTE_WINDOW_EXPIRES_AT,
                ORDERS.FINALIZED_AT,
                ORDERS.RISK_LEVEL,
                ORDERS.MANUAL_REVIEW_REQUIRED,
                ORDERS.DELIVERY_SNAPSHOT,
                ORDERS.SETTLEMENT_SNAPSHOT,
                ORDERS.METADATA,
                ORDERS.CREATED_AT,
                ORDERS.UPDATED_AT);
    }

    private Condition updatedAtCursor(CursorKey cursor) {
        if (cursor == null) {
            return DSL.trueCondition();
        }
        // 中文注释：订单列表以更新时间倒序翻页，后续状态变化会自然回到第一页。
        OffsetDateTime value = OffsetDateTime.ofInstant(CursorCodec.instantValue(cursor), ZoneOffset.UTC);
        return ORDERS.UPDATED_AT.lt(value).or(ORDERS.UPDATED_AT.eq(value).and(ORDERS.ID.lt(cursor.id())));
    }

    private Condition participantCondition(String accountId) {
        // 中文注释：订单列表按参与方快照在数据库中过滤，避免身份页和订单页读取全量订单。
        return ORDERS.ID.in(DSL.select(OP_ORDER_ID).from(ORDER_PARTICIPANTS).where(OP_ACCOUNT_ID.eq(accountId)));
    }

    private void syncOrderParticipants(OrderEntity order) {
        // 中文注释：订单保存时重建参与方快照，让身份页、工作台和 agent 权限读取同一张参与关系表。
        dsl.deleteFrom(ORDER_PARTICIPANTS)
                .where(OP_ORDER_ID.eq(order.id()))
                .execute();
        insertParticipant(order.id(), order.buyerAccountId(), "buyer");
        insertParticipant(order.id(), order.sellerAccountId(), "seller");
        insertParticipant(order.id(), order.fulfillerAccountId(), "fulfiller");
        insertParticipant(order.id(), order.acceptorAccountId(), "acceptor");
        insertParticipant(order.id(), order.claimedByAccountId(), "claimer");
        insertParticipant(order.id(), order.submittedByAccountId(), "submitter");
        insertParticipant(order.id(), order.acceptedByAccountId(), "acceptance_actor");
        insertParticipant(order.id(), order.reviewerAccountId(), "reviewer");
    }

    private void insertParticipant(String orderId, String accountId, String roleCode) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        dsl.insertInto(ORDER_PARTICIPANTS)
                .columns(OP_ORDER_ID, OP_ACCOUNT_ID, OP_ROLE_CODE, OP_SOURCE)
                .values(orderId, accountId, roleCode, "order_repository")
                .onConflict(OP_ORDER_ID, OP_ACCOUNT_ID, OP_ROLE_CODE)
                .doNothing()
                .execute();
    }

    private OrderEntity mapRecord(Record record) {
        return new OrderEntity(
                record.get(ORDERS.ID),
                record.get(ORDERS.ORDER_NO),
                record.get(ORDERS.MARKET_ID),
                record.get(ORDERS.LISTING_ID),
                PostgresJson.modelEnum(ListingKind.class, record.get(ORDERS.KIND)),
                record.get(ORDERS.POST_KIND) == null ? null : PostgresJson.modelEnum(PostKind.class, record.get(ORDERS.POST_KIND)),
                record.get(ORDERS.POST_ID),
                record.get(ORDERS.PARENT_ORDER_ID),
                PostgresJson.modelEnum(OrderStatus.class, record.get(ORDERS.STATUS)),
                record.get(ORDERS.DISPLAY_PHASE),
                record.get(ORDERS.CLAIMED_BY_ACCOUNT_ID),
                record.get(ORDERS.SUBMITTED_BY_ACCOUNT_ID),
                record.get(ORDERS.ACCEPTED_BY_ACCOUNT_ID),
                record.get(ORDERS.REVIEWER_ACCOUNT_ID),
                PostgresJson.instant(record.get(ORDERS.REVIEW_DUE_AT)),
                record.get(ORDERS.PROOF_ID),
                PostgresJson.modelEnum(SettlementType.class, record.get(ORDERS.SETTLEMENT_TYPE)),
                record.get(ORDERS.SETTLEMENT_AMOUNT),
                record.get(ORDERS.CLOSED_REASON),
                record.get(ORDERS.DISPUTE_REASON),
                record.get(ORDERS.REVIEW_POST_ID) == null ? record.get(ORDERS.REVIEW_LISTING_ID) : record.get(ORDERS.REVIEW_POST_ID),
                record.get(DISPUTE_OPENED_BY_ACCOUNT_ID),
                modelStatus(record.get(DISPUTE_OPENED_FROM_STATUS)),
                record.get(DISPUTE_OPENED_FROM_WINDOW_STATUS),
                PostgresJson.instant(record.get(DISPUTE_OPENED_FROM_WINDOW_EXPIRES_AT)),
                PostgresJson.instant(record.get(DISPUTE_OPENED_AT)),
                record.get(DISPUTE_CANCELLED_BY_ACCOUNT_ID),
                PostgresJson.instant(record.get(DISPUTE_CANCELLED_AT)),
                record.get(DISPUTE_CANCEL_REASON),
                record.get(ORDERS.BACKOFFICE_OVERRIDE_DECISION) == null ? null : PostgresJson.modelEnum(com.monopolyfun.modules.order.domain.ReviewDecision.class, record.get(ORDERS.BACKOFFICE_OVERRIDE_DECISION)),
                record.get(ORDERS.BACKOFFICE_OVERRIDE_REASON),
                record.get(ORDERS.CHALLENGE_NONCE),
                Boolean.TRUE.equals(record.get(ORDERS.SETTLEMENT_FROZEN)),
                PostgresJson.stringList(record.get(ORDERS.ACCEPTANCE_CRITERIA_SNAPSHOT)),
                record.get(ORDERS.PROOF_SPEC_SNAPSHOT),
                record.get(ORDERS.SETTLEMENT_SPEC_SNAPSHOT),
                record.get(ORDERS.REVIEW_STATUS),
                record.get(ORDERS.DISPUTE_WINDOW_STATUS),
                PostgresJson.instant(record.get(ORDERS.DISPUTE_WINDOW_EXPIRES_AT)),
                PostgresJson.instant(record.get(ORDERS.FINALIZED_AT)),
                record.get(ORDERS.RISK_LEVEL),
                Boolean.TRUE.equals(record.get(ORDERS.MANUAL_REVIEW_REQUIRED)),
                PostgresJson.map(record.get(ORDERS.DELIVERY_SNAPSHOT)),
                PostgresJson.map(record.get(ORDERS.SETTLEMENT_SNAPSHOT)),
                PostgresJson.map(record.get(ORDERS.METADATA)),
                PostgresJson.instant(record.get(ORDERS.CREATED_AT)),
                PostgresJson.instant(record.get(ORDERS.UPDATED_AT)));
    }

    private String statusName(OrderStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    private OrderStatus modelStatus(String value) {
        return value == null || value.isBlank() ? null : OrderStatus.valueOf(value.toUpperCase());
    }

    private PaymentIntentEntity mapPaymentIntent(Record record) {
        if (record.get(PAYMENT_INTENTS.ID) == null) {
            return null;
        }
        return new PaymentIntentEntity(
                record.get(PAYMENT_INTENTS.ID),
                record.get(PAYMENT_INTENTS.PAYMENT_NO),
                record.get(PAYMENT_INTENTS.ORDER_ID),
                record.get(PAYMENT_INTENTS.ACCOUNT_ID),
                record.get(PAYMENT_INTENTS.PROVIDER),
                record.get(PAYMENT_INTENTS.PROVIDER_PAYMENT_REF),
                PostgresJson.modelEnum(PaymentIntentStatus.class, record.get(PAYMENT_INTENTS.STATUS)),
                record.get(PAYMENT_INTENTS.AMOUNT_MINOR),
                record.get(PAYMENT_INTENTS.CURRENCY),
                record.get(PAYMENT_INTENTS.CALLBACK_TOKEN),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.AUTHORIZED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CAPTURED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.REFUNDED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CANCELLED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.DISPUTED_AT)),
                PostgresJson.map(record.get(PAYMENT_INTENTS.METADATA)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.CREATED_AT)),
                PostgresJson.instant(record.get(PAYMENT_INTENTS.UPDATED_AT)));
    }
}
