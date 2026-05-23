package com.monopolyfun.modules.delivery.service;

import com.monopolyfun.modules.delivery.domain.DeliveryAttemptEntity;
import com.monopolyfun.modules.delivery.domain.DeliveryReceipt;
import com.monopolyfun.modules.delivery.domain.DeliveryRequest;
import com.monopolyfun.modules.delivery.infra.DeliveryAttemptRepository;
import com.monopolyfun.modules.identity.service.security.RiskEventService;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.payment.infra.PaymentIntentRepository;
import com.monopolyfun.modules.settlement.service.SettlementEventService;
import com.monopolyfun.modules.work.domain.WorkEventEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.domain.WorkReceiptEntity;
import com.monopolyfun.modules.work.domain.WorkRunEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InstantFulfillmentService {
    public static final String DELIVERY_MODE_INSTANT = "instant_fulfillment";
    private static final String DEFAULT_PROVIDER = "phone_recharge";

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryProviderRegistry deliveryProviderRegistry;
    private final SettlementEventService settlementEventService;
    private final RiskEventService riskEventService;
    private final WorkRepository workRepository;
    private final StockFulfillmentService stockFulfillmentService;

    public InstantFulfillmentService(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            PaymentIntentRepository paymentIntentRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            DeliveryProviderRegistry deliveryProviderRegistry,
            SettlementEventService settlementEventService,
            RiskEventService riskEventService,
            WorkRepository workRepository,
            StockFulfillmentService stockFulfillmentService) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryProviderRegistry = deliveryProviderRegistry;
        this.settlementEventService = settlementEventService;
        this.riskEventService = riskEventService;
        this.workRepository = workRepository;
        this.stockFulfillmentService = stockFulfillmentService;
    }

    @Transactional
    public OrderEntity tryDeliverAfterPayment(OrderEntity order, PaymentIntentEntity paymentIntent) {
        if (stockFulfillmentService.isStockFulfillment(order)) {
            return stockFulfillmentService.tryDeliverAfterPayment(order, paymentIntent);
        }
        if (!isInstantFulfillment(order)) {
            return order;
        }
        if (paymentIntent.status() != PaymentIntentStatus.CAPTURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instant fulfillment requires captured payment");
        }
        if (order.status() != OrderStatus.CLAIMED) {
            return order;
        }
        return deliver(order, paymentIntent, "payment_captured");
    }

    @Transactional
    public OrderEntity retry(String orderNo, String actorAccountId) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!isInstantFulfillment(order)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not instant fulfillment");
        }
        PaymentIntentEntity paymentIntent = latestCapturedPayment(order);
        if (paymentIntent == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instant fulfillment retry requires captured payment");
        }
        if (!actorAccountId.equals(order.buyerAccountId()) && !actorAccountId.equals(order.fulfillerAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order participant required");
        }
        return deliver(order, paymentIntent, "operator_retry");
    }

    private OrderEntity deliver(OrderEntity order, PaymentIntentEntity paymentIntent, String source) {
        String providerCode = providerCode(order);
        String idempotencyKey = "instant_fulfillment:%s:%s:%s".formatted(order.orderNo(), paymentIntent.id(), providerCode);
        var existing = deliveryAttemptRepository.findByProviderIdempotencyKey(providerCode, idempotencyKey).orElse(null);
        if (existing != null && DeliveryAttemptEntity.STATUS_SUCCEEDED.equals(existing.status())) {
            riskEventService.record("instant_fulfillment_duplicate_blocked", "order", order.id(), paymentIntent.accountId(), "medium", "Instant fulfillment duplicate blocked", Map.of("attemptId", existing.id()));
            OrderEntity latest = orderRepository.findById(order.id()).orElse(order);
            return latest.status() == OrderStatus.CLAIMED ? finalizeInstantFulfillment(latest, paymentIntent, existing, receiptFromAttempt(existing)) : latest;
        }
        Map<String, Object> input = deliveryInput(order);
        DeliveryRequest request = new DeliveryRequest(order.id(), order.orderNo(), paymentIntent.id(), paymentIntent.accountId(), providerCode, input, order.settlementSnapshot(), idempotencyKey);
        Instant now = Instant.now();
        DeliveryAttemptEntity pending = deliveryAttemptRepository.save(new DeliveryAttemptEntity(
                existing == null ? "delivery-attempt-" + UUID.randomUUID() : existing.id(),
                order.id(),
                paymentIntent.id(),
                providerCode,
                null,
                DeliveryAttemptEntity.STATUS_PENDING,
                idempotencyKey,
                Map.of("input", input, "source", source),
                Map.of(),
                null,
                existing == null ? now : existing.createdAt(),
                now));
        settlementEventService.recordOnce(order.id(), paymentIntent.id(), "delivery_attempted", idempotencyKey, paymentIntent.amountMinor(), paymentIntent.currency(), paymentIntent.accountId(), Map.of("provider", providerCode, "attemptId", pending.id()));
        DeliveryReceipt receipt;
        try {
            receipt = deliveryProviderRegistry.requireProvider(providerCode).deliver(request);
        } catch (RuntimeException exception) {
            DeliveryAttemptEntity failed = deliveryAttemptRepository.save(new DeliveryAttemptEntity(
                    pending.id(),
                    order.id(),
                    paymentIntent.id(),
                    providerCode,
                    null,
                    DeliveryAttemptEntity.STATUS_FAILED,
                    idempotencyKey,
                    pending.requestPayload(),
                    Map.of(),
                    exception.getMessage(),
                    pending.createdAt(),
                    Instant.now()));
            settlementEventService.recordOnce(order.id(), paymentIntent.id(), "delivery_failed", idempotencyKey, paymentIntent.amountMinor(), paymentIntent.currency(), paymentIntent.accountId(), Map.of("provider", providerCode, "attemptId", failed.id(), "error", exception.getMessage()));
            riskEventService.record("instant_fulfillment_failed", "order", order.id(), paymentIntent.accountId(), "high", "Instant fulfillment provider failed", Map.of("attemptId", failed.id(), "provider", providerCode, "error", exception.getMessage()));
            return orderRepository.findById(order.id()).orElse(order);
        }
        DeliveryAttemptEntity succeeded = deliveryAttemptRepository.save(new DeliveryAttemptEntity(
                pending.id(),
                order.id(),
                paymentIntent.id(),
                providerCode,
                receipt.providerOrderId(),
                DeliveryAttemptEntity.STATUS_SUCCEEDED,
                idempotencyKey,
                pending.requestPayload(),
                receiptPayload(receipt),
                null,
                pending.createdAt(),
                Instant.now()));
        OrderEntity finalized = finalizeInstantFulfillment(order, paymentIntent, succeeded, receipt);
        saveInstantFulfillmentWorkReceipt(finalized, paymentIntent, succeeded, receipt);
        settlementEventService.recordOnce(order.id(), paymentIntent.id(), "delivery_succeeded", idempotencyKey, paymentIntent.amountMinor(), paymentIntent.currency(), paymentIntent.accountId(), Map.of("provider", providerCode, "attemptId", succeeded.id(), "providerOrderId", receipt.providerOrderId()));
        return finalized;
    }

    private void saveInstantFulfillmentWorkReceipt(
            OrderEntity order,
            PaymentIntentEntity paymentIntent,
            DeliveryAttemptEntity attempt,
            DeliveryReceipt receipt) {
        Instant now = Instant.now();
        String itemNo = "work-instant-fulfillment-" + order.orderNo();
        String itemId = "wi-" + itemNo;
        WorkItemEntity item = new WorkItemEntity(
                itemId,
                itemNo,
                "order",
                order.orderNo(),
                paymentIntent.accountId(),
                "自动执行直接发货",
                "支付捕获后由自动发货 provider 完成交付。",
                List.of("provider 回执已生成", "订单已完成验收"),
                List.of("order:" + order.orderNo(), "payment_intent:" + paymentIntent.id(), "delivery_attempt:" + attempt.id()),
                Map.of("action", "instant_fulfillment", "provider", attempt.provider()),
                "automation",
                null,
                "system",
                "accepted",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(item);
        WorkRunEntity run = workRepository.saveRun(new WorkRunEntity(
                "wr-" + UUID.randomUUID(),
                "wr-" + itemNo,
                itemId,
                paymentIntent.accountId(),
                "accepted",
                "agent",
                attempt.createdAt(),
                now,
                now,
                now));
        WorkReceiptEntity workReceipt = workRepository.saveReceipt(new WorkReceiptEntity(
                "wrc-" + UUID.randomUUID(),
                "wrc-" + itemNo + "-" + now.toEpochMilli(),
                run.id(),
                "直接发货 provider 已返回成功回执",
                Map.of(
                        "orderNo", order.orderNo(),
                        "deliveryAttemptId", attempt.id(),
                        "provider", attempt.provider(),
                        "receipt", receiptPayload(receipt)),
                List.of("delivery_attempt:" + attempt.id(), "provider_order:" + receipt.providerOrderId()),
                List.of("order:" + order.orderNo(), "payment_intent:" + paymentIntent.id()),
                List.of(),
                now));
        // 中文注释：自动发货也落 Work 事件，后续排查能从 WorkRun 回到 provider attempt。
        workRepository.saveEvent(new WorkEventEntity(
                "we-" + UUID.randomUUID(),
                "work_item",
                itemId,
                paymentIntent.accountId(),
                "work_receipt_submitted",
                "instant_fulfillment",
                Map.of("orderNo", order.orderNo(), "paymentIntentId", paymentIntent.id(), "attemptId", attempt.id()),
                Map.of("runNo", run.runNo(), "receiptNo", workReceipt.receiptNo(), "providerOrderId", receipt.providerOrderId()),
                workReceipt.id(),
                now));
    }

    private OrderEntity finalizeInstantFulfillment(OrderEntity order, PaymentIntentEntity paymentIntent, DeliveryAttemptEntity attempt, DeliveryReceipt receipt) {
        Instant now = Instant.now();
        LinkedHashMap<String, Object> deliverySnapshot = new LinkedHashMap<>(order.deliverySnapshot());
        deliverySnapshot.put("deliverySummary", "直接发货已完成");
        deliverySnapshot.put("deliveryReceipt", receiptPayload(receipt));
        deliverySnapshot.put("deliveryAttemptId", attempt.id());
        deliverySnapshot.put("deliveryProvider", receipt.providerCode());
        deliverySnapshot.put("deliveredAt", receipt.deliveredAt().toString());
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(order.metadata());
        metadata.put("deliveryReceipt", receiptPayload(receipt));
        metadata.put("deliveryAttemptId", attempt.id());
        metadata.put("deliveryCompletedAt", now.toString());
        metadata.put("instantFulfillmentStatus", "succeeded");
        var delivered = order.submitDeliveryResult(paymentIntent.accountId(), deliverySnapshot, metadata,
                new LifecycleContext(paymentIntent.accountId(), "instant-fulfillment", now, Map.of("deliveryMode", DELIVERY_MODE_INSTANT)));
        var accepted = delivered.entity().finalizeAccepted(paymentIntent.accountId(), "instant_fulfillment_succeeded",
                new LifecycleContext(paymentIntent.accountId(), "instant-fulfillment", now, Map.of("attemptId", attempt.id(), "providerOrderId", receipt.providerOrderId())));
        orderRepository.save(accepted.entity());
        saveEvent(order.id(), "instant_fulfillment_completed", paymentIntent.accountId(), Map.of("attemptId", attempt.id(), "providerOrderId", receipt.providerOrderId()));
        saveEvent(order.id(), "order_finalized", paymentIntent.accountId(), Map.of("reason", "instant_fulfillment_succeeded"));
        return accepted.entity();
    }

    private PaymentIntentEntity latestCapturedPayment(OrderEntity order) {
        return paymentIntentRepository.findByOrderId(order.id())
                .filter(intent -> intent.status() == PaymentIntentStatus.CAPTURED)
                .orElse(null);
    }

    private boolean isInstantFulfillment(OrderEntity order) {
        return DELIVERY_MODE_INSTANT.equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")));
    }

    private String providerCode(OrderEntity order) {
        Object provider = order.metadata().get("deliveryProvider");
        if (provider == null) {
            provider = order.deliverySnapshot().get("deliveryProvider");
        }
        String text = provider == null ? "" : String.valueOf(provider).trim();
        return text.isBlank() ? DEFAULT_PROVIDER : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deliveryInput(OrderEntity order) {
        Object input = order.metadata().get("deliveryInput");
        if (input instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> cleaned = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    cleaned.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return cleaned;
        }
        return Map.of();
    }

    private Map<String, Object> receiptPayload(DeliveryReceipt receipt) {
        return Map.of(
                "providerCode", receipt.providerCode(),
                "providerOrderId", receipt.providerOrderId(),
                "status", receipt.status(),
                "deliveredAt", receipt.deliveredAt().toString(),
                "rawReceipt", receipt.rawReceipt());
    }

    private DeliveryReceipt receiptFromAttempt(DeliveryAttemptEntity attempt) {
        Map<String, Object> receiptPayload = attempt.receiptPayload() == null ? Map.of() : attempt.receiptPayload();
        String providerOrderId = attempt.providerOrderId() == null || attempt.providerOrderId().isBlank()
                ? String.valueOf(receiptPayload.getOrDefault("providerOrderId", attempt.id()))
                : attempt.providerOrderId();
        // 中文注释：幂等恢复只读取本地回执快照，用原 provider 单号补齐订单终态。
        return new DeliveryReceipt(
                attempt.provider(),
                providerOrderId,
                String.valueOf(receiptPayload.getOrDefault("status", "DELIVERED")),
                attempt.updatedAt(),
                receiptPayload);
    }

    private void saveEvent(String orderId, String eventType, String actorAccountId, Map<String, Object> payload) {
        orderEventRepository.save(new OrderEventEntity(
                "event-" + UUID.randomUUID(),
                orderId,
                eventType,
                actorAccountId,
                payload == null ? Map.of() : Map.copyOf(payload),
                Instant.now()));
    }
}
