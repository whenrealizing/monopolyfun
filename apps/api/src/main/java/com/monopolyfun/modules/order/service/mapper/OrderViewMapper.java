package com.monopolyfun.modules.order.service.mapper;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.domain.OrderProgressUpdateEntity;
import com.monopolyfun.modules.order.domain.ProofEntity;
import com.monopolyfun.modules.order.service.view.OrderEventView;
import com.monopolyfun.modules.order.service.view.OrderSummary;
import com.monopolyfun.modules.order.service.view.ProgressUpdateView;
import com.monopolyfun.modules.order.service.view.ProofSummary;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OrderViewMapper {
    private OrderViewMapper() {
    }

    public static OrderSummary order(OrderEntity order) {
        return order(order, null);
    }

    public static OrderSummary order(OrderEntity order, String accountId) {
        return order(order, accountId, order == null ? null : order.roleFor(accountId));
    }

    public static OrderSummary order(OrderEntity order, String accountId, String currentAccountRole) {
        if (order == null) return null;
        Map<String, Object> metadata = order.metadata() == null ? Map.of() : order.metadata();
        Map<String, Object> settlementSnapshot = order.settlementSnapshot() == null ? Map.of() : order.settlementSnapshot();
        // 中文注释：订单摘要是前端主读模型，枚举统一在这里转成稳定小写字符串。
        return new OrderSummary(order.id(), order.orderNo(), orderDisplayName(order), order.postKind().name().toLowerCase(Locale.ROOT), order.postId(), order.parentOrderId(),
                order.status().name().toLowerCase(Locale.ROOT), order.displayPhase(), order.claimedByAccountId(),
                order.buyerAccountId(), order.sellerAccountId(), order.fulfillerAccountId(), order.acceptorAccountId(),
                order.roleModelVersion(), currentAccountRole, order.submittedByAccountId(),
                order.acceptedByAccountId(), order.reviewerAccountId(), order.reviewDueAt(), order.proofId(), order.settlementType().name().toLowerCase(Locale.ROOT), order.effectiveSettlementAmount(),
                stringValue(metadata.get("itemId")),
                stringValue(metadata.get("fulfillmentMode")),
                stringValue(metadata.get("deliveryMode")),
                stringValue(metadata.get("deliverySource")),
                stringValue(metadata.get("buyerNote")),
                stringValue(settlementSnapshot.get("paymentMethod")),
                metadata.get("deliveryPayload"),
                deliveryReceiptView(order),
                stringValue(metadata.get("agentRuntimeId")),
                intValue(metadata.get("reservedShares")),
                instantValue(metadata.get("lockExpiresAt")),
                instantValue(metadata.get("lastProgressAt")),
                instantValue(metadata.get("nextProgressDueAt")),
                intValue(metadata.get("progressCount")) == null ? 0 : intValue(metadata.get("progressCount")),
                order.disputeReason(), order.reviewPostId(),
                order.disputeOpenedByAccountId(),
                order.disputeOpenedFromStatus() == null ? null : order.disputeOpenedFromStatus().name().toLowerCase(Locale.ROOT),
                order.disputeOpenedFromWindowStatus(),
                order.disputeOpenedFromWindowExpiresAt(),
                order.disputeOpenedAt(),
                order.disputeCancelledByAccountId(),
                order.disputeCancelledAt(),
                order.disputeCancelReason(),
                order.backofficeOverrideDecision() == null ? null : order.backofficeOverrideDecision().name().toLowerCase(),
                order.backofficeOverrideReason(),
                order.challengeNonce(),
                order.settlementFrozen(),
                order.acceptanceCriteriaSnapshot(),
                order.proofSpecSnapshot(),
                order.settlementSpecSnapshot(),
                order.reviewStatus(),
                order.disputeWindowStatus(),
                order.disputeWindowExpiresAt(),
                order.finalizedAt(),
                order.createdAt(), order.updatedAt());
    }

    public static ProgressUpdateView progress(OrderProgressUpdateEntity update) {
        if (update == null) return null;
        return new ProgressUpdateView(
                update.id(),
                update.orderId(),
                update.stepIndex(),
                update.stepTitle(),
                update.summary(),
                update.links(),
                update.artifacts(),
                update.executionMode() == null ? null : update.executionMode().name().toLowerCase(Locale.ROOT),
                update.agentRuntime(),
                update.createdAt());
    }

    private static Object deliveryReceiptView(OrderEntity order) {
        Map<String, Object> metadata = order.metadata() == null ? Map.of() : order.metadata();
        Map<String, Object> deliverySnapshot = order.deliverySnapshot() == null ? Map.of() : order.deliverySnapshot();
        Object rawReceipt = metadata.get("deliveryReceipt");
        LinkedHashMap<String, Object> receipt = new LinkedHashMap<>();
        if (rawReceipt instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key instanceof String textKey) {
                    receipt.put(textKey, value);
                }
            });
        }
        putIfMissing(receipt, "summary", deliverySnapshot.get("deliverySummary"));
        putIfMissing(receipt, "submittedAt", metadata.get("deliveryCompletedAt"));
        return receipt.isEmpty() ? rawReceipt : receipt;
    }

    private static void putIfMissing(Map<String, Object> target, String key, Object value) {
        if (target.containsKey(key) || value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    public static ProofSummary proof(ProofEntity proof) {
        if (proof == null) return null;
        return new ProofSummary(
                proof.id(),
                proof.orderId(),
                proof.kind(),
                proof.parentOrderId(),
                proof.submittedByAccountId(),
                proof.summary(),
                proof.links(),
                proof.artifacts(),
                proof.proofPayload(),
                proof.executionMode(),
                proof.agentSessionId(),
                proof.agentRuntime(),
                proof.decision(),
                proof.evidenceRefs(),
                proof.contentHashes(),
                proof.criteriaRefs(),
                proof.visibility(),
                proof.executionTraceRef(),
                proof.createdAt());
    }

    public static OrderEventView event(OrderEventEntity event) {
        if (event == null) return null;
        return new OrderEventView(event.id(), event.eventType(), event.actorAccountId(), event.payload(), event.createdAt());
    }

    private static String orderDisplayName(OrderEntity order) {
        // 中文注释：订单列表标题优先使用成交快照，保证用户看到业务对象名称。
        Map<String, Object> deliverySnapshot = order.deliverySnapshot() == null ? Map.of() : order.deliverySnapshot();
        String snapshotTitle = stringValue(deliverySnapshot.get("title"));
        if (snapshotTitle != null && !snapshotTitle.isBlank()) return snapshotTitle;
        Map<String, Object> metadata = order.metadata() == null ? Map.of() : order.metadata();
        String metadataTitle = stringValue(metadata.get("title"));
        if (metadataTitle != null && !metadataTitle.isBlank()) return metadataTitle;
        return order.orderNo();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static java.time.Instant instantValue(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant instant) return instant;
        try {
            return java.time.Instant.parse(String.valueOf(value));
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }
}
