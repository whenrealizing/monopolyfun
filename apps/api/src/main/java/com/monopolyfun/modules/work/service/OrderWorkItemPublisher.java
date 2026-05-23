package com.monopolyfun.modules.work.service;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.modules.share.domain.SettlementType;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderWorkItemPublisher {
    private static final Duration CLAIM_LEASE = Duration.ofHours(3);

    private final WorkRepository workRepository;

    public OrderWorkItemPublisher(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public void publishClaimedOrder(OrderEntity order, Instant now) {
        if (order.settlementType() == SettlementType.MONEY) {
            publishMoneyPayment(order, now);
            return;
        }
        publishDeliveryIfReady(order, now, "ready");
    }

    public void publishPaymentCaptured(OrderEntity order, Instant now) {
        closeMoneyPayment(order, now);
        publishDeliveryIfReady(order, now, "ready");
    }

    private void publishMoneyPayment(OrderEntity order, Instant now) {
        String itemNo = "wb-money-payment-" + order.orderNo();
        // 中文注释：现金订单在 claim 阶段直接写 WorkItem，Workbench 投影读取当前事实，后续支付完成会关闭该待办。
        workRepository.upsertItem(new WorkItemEntity(
                "wi-" + order.buyerAccountId() + "-" + itemNo,
                itemNo,
                "order",
                order.orderNo(),
                order.buyerAccountId(),
                "去付款",
                "请完成付款。",
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                orderOutputSchema(order, "complete_money_payment", Map.of("paymentIntent", "object", "orderBinding", "object")),
                OrderEntity.ROLE_PAYER,
                null,
                "urgent",
                "ready",
                null,
                now,
                now,
                now));
    }

    private void closeMoneyPayment(OrderEntity order, Instant now) {
        for (WorkItemEntity item : workRepository.findItemsBySource("order", order.orderNo())) {
            if (!"complete_money_payment".equals(item.outputSchema().get("action")) || List.of("closed", "accepted").contains(item.status())) {
                continue;
            }
            workRepository.saveItem(new WorkItemEntity(
                    item.id(),
                    item.itemNo(),
                    item.sourceType(),
                    item.sourceId(),
                    item.accountId(),
                    item.title(),
                    item.goal(),
                    item.acceptanceCriteria(),
                    item.inputRefs(),
                    item.outputSchema(),
                    item.requiredRole(),
                    item.requiredCapability(),
                    item.urgency(),
                    "closed",
                    null,
                    item.readyAt(),
                    item.createdAt(),
                    now));
        }
    }

    private void publishDeliveryIfReady(OrderEntity order, Instant now, String status) {
        if (order.status() != OrderStatus.CLAIMED || !isReviewedDeliveryOrder(order)) {
            return;
        }
        String itemNo = "wb-delivery-result-" + order.orderNo();
        // 中文注释：交付待办在订单或支付事实变更时直接生成，避免 Workbench 依赖旧 source provider 定时补齐。
        workRepository.upsertItem(new WorkItemEntity(
                "wi-" + order.fulfillerAccountId() + "-" + itemNo,
                itemNo,
                "order",
                order.orderNo(),
                order.fulfillerAccountId(),
                "revision_requested".equals(status) ? "重新交付" : "去交付",
                "revision_requested".equals(status) ? "请按要求修改后重新提交。" : "请提交交付内容。",
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                orderOutputSchema(order, "delivery_result_due", Map.of("summary", "string", "links", "ProofLink[]", "artifacts", "string[]")),
                OrderEntity.ROLE_FULFILLER,
                null,
                "urgent",
                status,
                "claimed".equals(status) ? now.plus(CLAIM_LEASE) : null,
                now,
                now,
                now));
    }

    public void publishLeadReview(OrderEntity order, Instant now) {
        String itemNo = "wb-lead-review-" + order.orderNo();
        WorkItemEntity item = new WorkItemEntity(
                "wi-" + order.acceptorAccountId() + "-" + itemNo,
                itemNo,
                "order",
                order.orderNo(),
                order.acceptorAccountId(),
                "去验收",
                "请确认交付内容是否符合要求。",
                order.acceptanceCriteriaSnapshot(),
                List.of("order:" + order.orderNo()),
                orderOutputSchema(order, "lead_accept_or_dispute", Map.of("decision", "accepted|revision_requested|disputed")),
                OrderEntity.ROLE_PAYER,
                null,
                "urgent",
                "submitted",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(item);
        WorkRunEntity run = workRepository.findRunByItemId(item.id())
                .orElseGet(() -> new WorkRunEntity("wr-" + UUID.randomUUID(), "wr-" + itemNo, item.id(), order.acceptorAccountId(), "submitted", "manual", now, now, null, now));
        workRepository.saveRun(new WorkRunEntity(run.id(), run.runNo(), run.workItemId(), run.actorAccountId(), "submitted", run.executionMode(), run.startedAt(), now, null, now));
        workRepository.saveReceipt(new WorkReceiptEntity(
                "wrc-" + UUID.randomUUID(),
                "wrc-" + itemNo + "-" + now.toEpochMilli(),
                run.id(),
                "订单交付结果等待验收",
                Map.of("source", "order_delivery", "orderNo", order.orderNo()),
                List.of("order:" + order.orderNo()),
                List.of("review:" + itemNo),
                List.of(),
                now));
    }

    public void publishRevision(OrderEntity order, Instant now) {
        publishDeliveryIfReady(order, now, "revision_requested");
    }

    private Map<String, Object> orderOutputSchema(OrderEntity order, String action, Map<String, Object> extras) {
        LinkedHashMap<String, Object> schema = new LinkedHashMap<>();
        schema.put("action", action);
        schema.put("orderNo", order.orderNo());
        putIfPresent(schema, "itemTitle", orderDisplayName(order));
        if (order.settlementAmount() != null) {
            schema.put("amount", order.settlementAmount().stripTrailingZeros().toPlainString());
        }
        putIfPresent(schema, "currency", firstText(
                order.settlementSnapshot() == null ? null : order.settlementSnapshot().get("currency"),
                order.settlementSnapshot() == null ? null : order.settlementSnapshot().get("paymentAsset")));
        schema.putAll(extras);
        return Map.copyOf(schema);
    }

    private String orderDisplayName(OrderEntity order) {
        return firstText(
                order.deliverySnapshot() == null ? null : order.deliverySnapshot().get("title"),
                order.metadata() == null ? null : order.metadata().get("title"));
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private boolean isReviewedDeliveryOrder(OrderEntity order) {
        return order.kind() != ListingKind.REVIEW
                && !"instant_fulfillment".equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")))
                && !"stock_fulfillment".equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")));
    }
}
