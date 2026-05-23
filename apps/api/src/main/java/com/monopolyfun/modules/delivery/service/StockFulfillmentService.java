package com.monopolyfun.modules.delivery.service;

import com.monopolyfun.modules.digitalinventory.domain.DigitalDeliveryEntity;
import com.monopolyfun.modules.digitalinventory.domain.DigitalInventoryItemEntity;
import com.monopolyfun.modules.digitalinventory.infra.DigitalDeliveryRepository;
import com.monopolyfun.modules.digitalinventory.infra.DigitalInventoryRepository;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.domain.OrderEventEntity;
import com.monopolyfun.modules.order.domain.OrderStatus;
import com.monopolyfun.modules.order.infra.OrderEventRepository;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.platform.lifecycle.LifecycleContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StockFulfillmentService {
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final DigitalInventoryRepository inventoryRepository;
    private final DigitalDeliveryRepository deliveryRepository;

    public StockFulfillmentService(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            DigitalInventoryRepository inventoryRepository,
            DigitalDeliveryRepository deliveryRepository) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.inventoryRepository = inventoryRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public OrderEntity tryDeliverAfterPayment(OrderEntity order, PaymentIntentEntity paymentIntent) {
        if (!isStockFulfillment(order)) {
            return order;
        }
        if (paymentIntent.status() != PaymentIntentStatus.CAPTURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock fulfillment requires captured payment");
        }
        if (order.status() != OrderStatus.CLAIMED) {
            return order;
        }
        return deliver(order, paymentIntent);
    }

    public boolean isStockFulfillment(OrderEntity order) {
        return PostItemSupport.DELIVERY_MODE_STOCK.equalsIgnoreCase(String.valueOf(order.metadata().get("deliveryMode")));
    }

    private OrderEntity deliver(OrderEntity order, PaymentIntentEntity paymentIntent) {
        DigitalDeliveryEntity existing = deliveryRepository.findByOrderId(order.id()).orElse(null);
        if (existing != null && DigitalDeliveryEntity.STATUS_DELIVERED.equals(existing.status())) {
            return orderRepository.findById(order.id()).orElse(order);
        }
        DigitalInventoryItemEntity inventoryItem = inventoryRepository.findReservedByOrderId(order.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Reserved digital inventory is required"));
        Instant now = Instant.now();
        Map<String, Object> receipt = Map.of(
                "mode", PostItemSupport.DELIVERY_MODE_STOCK,
                "inventoryItemId", inventoryItem.id(),
                "payloadPreview", inventoryItem.payloadPreview(),
                "deliveredAt", now.toString());
        LinkedHashMap<String, Object> deliverySnapshot = new LinkedHashMap<>(order.deliverySnapshot());
        deliverySnapshot.put("deliverySummary", "数字库存已自动发货");
        // 中文注释：订单快照只保留 preview 与 reveal 标记，明文通过受控 API 即时解密给订单参与方。
        deliverySnapshot.put("deliveryPayload", Map.of(
                "payloadPreview", inventoryItem.payloadPreview(),
                "revealPath", "/api/v1/orders/" + order.orderNo() + "/digital-delivery"));
        deliverySnapshot.put("deliveryReceipt", receipt);
        deliverySnapshot.put("deliveryMode", PostItemSupport.DELIVERY_MODE_STOCK);
        deliverySnapshot.put("deliverySource", "platform_inventory");
        deliverySnapshot.put("deliveredAt", now.toString());
        deliverySnapshot.put("inventoryItemId", inventoryItem.id());

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(order.metadata());
        metadata.put("deliveryReceipt", receipt);
        metadata.put("deliveryCompletedAt", now.toString());
        metadata.put("stockFulfillmentStatus", "delivered");
        metadata.put("inventoryItemId", inventoryItem.id());

        var delivered = order.submitDeliveryResult(
                paymentIntent.accountId(),
                Map.copyOf(deliverySnapshot),
                Map.copyOf(metadata),
                new LifecycleContext(paymentIntent.accountId(), "stock-fulfillment", now, Map.of("inventoryItemId", inventoryItem.id())));
        orderRepository.save(delivered.entity());
        inventoryRepository.save(inventoryItem.withDelivered(order.id(), now));
        deliveryRepository.save(new DigitalDeliveryEntity(
                existing == null ? "ddlv-" + UUID.randomUUID() : existing.id(),
                order.id(),
                inventoryItem.id(),
                DigitalDeliveryEntity.STATUS_DELIVERED,
                Map.copyOf(deliverySnapshot),
                null,
                now,
                existing == null ? now : existing.createdAt(),
                now));
        saveEvent(order.id(), "stock_fulfillment_delivered", paymentIntent.accountId(), Map.of(
                "inventoryItemId", inventoryItem.id(),
                "paymentIntentId", paymentIntent.id()));
        return delivered.entity();
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
