package com.monopolyfun.modules.digitalinventory.service;

import com.monopolyfun.modules.digitalinventory.api.request.UploadDigitalInventoryRequest;
import com.monopolyfun.modules.digitalinventory.api.response.DigitalDeliveryRevealView;
import com.monopolyfun.modules.digitalinventory.api.response.DigitalInventorySummaryView;
import com.monopolyfun.modules.digitalinventory.api.response.DigitalInventoryUploadResponse;
import com.monopolyfun.modules.digitalinventory.domain.DigitalDeliveryEntity;
import com.monopolyfun.modules.digitalinventory.domain.DigitalInventoryItemEntity;
import com.monopolyfun.modules.digitalinventory.infra.DigitalDeliveryRepository;
import com.monopolyfun.modules.digitalinventory.infra.DigitalInventoryRepository;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.service.PostItemSupport;
import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DigitalInventoryService {
    private final DigitalInventoryRepository inventoryRepository;
    private final DigitalDeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final DigitalInventoryCrypto crypto;
    private final PostItemWorkspaceQueryService postItemWorkspaceQueryService;
    private final CurrentAccountAccess currentAccountAccess;

    public DigitalInventoryService(
            DigitalInventoryRepository inventoryRepository,
            DigitalDeliveryRepository deliveryRepository,
            OrderRepository orderRepository,
            DigitalInventoryCrypto crypto,
            PostItemWorkspaceQueryService postItemWorkspaceQueryService,
            CurrentAccountAccess currentAccountAccess) {
        this.inventoryRepository = inventoryRepository;
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.crypto = crypto;
        this.postItemWorkspaceQueryService = postItemWorkspaceQueryService;
        this.currentAccountAccess = currentAccountAccess;
    }

    public DigitalInventoryUploadResponse upload(String itemId, UploadDigitalInventoryRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        ListingEntity listing = requireStockListingOwnedBy(itemId, request.actorAccountId());
        Instant now = Instant.now();
        List<DigitalInventoryItemEntity> items = normalizedPayloads(request).stream()
                .map(payload -> new DigitalInventoryItemEntity(
                        "dinv-" + UUID.randomUUID(),
                        listing.id(),
                        crypto.encrypt(payload),
                        preview(payload),
                        crypto.hash(payload),
                        DigitalInventoryItemEntity.STATUS_AVAILABLE,
                        null,
                        null,
                        request.actorAccountId(),
                        now,
                        now))
                .toList();
        List<DigitalInventoryItemEntity> saved = inventoryRepository.saveAll(items);
        return new DigitalInventoryUploadResponse(
                listing.id(),
                saved.size(),
                saved.stream().map(DigitalInventoryItemEntity::id).toList(),
                summary(listing.id(), request.actorAccountId()));
    }

    public DigitalInventorySummaryView summary(String itemId, String actorAccountId) {
        currentAccountAccess.requireSameAccount(actorAccountId);
        ListingEntity listing = requireStockListingOwnedBy(itemId, actorAccountId);
        Map<String, Integer> counts = inventoryRepository.countByListingId(listing.id());
        int available = counts.getOrDefault(DigitalInventoryItemEntity.STATUS_AVAILABLE, 0);
        int reserved = counts.getOrDefault(DigitalInventoryItemEntity.STATUS_RESERVED, 0);
        int delivered = counts.getOrDefault(DigitalInventoryItemEntity.STATUS_DELIVERED, 0);
        int voided = counts.getOrDefault(DigitalInventoryItemEntity.STATUS_VOIDED, 0);
        return new DigitalInventorySummaryView(listing.id(), available, reserved, delivered, voided, available + reserved + delivered + voided);
    }

    public DigitalInventoryItemEntity reserveForOrder(String listingId, String orderId, String actorAccountId, Instant now) {
        return inventoryRepository.reserveAvailable(listingId, orderId, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Digital inventory is sold out"));
    }

    public DigitalDeliveryRevealView revealDeliveredPayload(String orderNo) {
        String accountId = currentAccountAccess.requireAccountId();
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .or(() -> orderRepository.findById(orderNo))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!canRevealDeliveredPayload(order, accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order recipient required");
        }
        DigitalDeliveryEntity delivery = deliveryRepository.findByOrderId(order.id())
                .filter(item -> DigitalDeliveryEntity.STATUS_DELIVERED.equals(item.status()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Digital delivery not found"));
        DigitalInventoryItemEntity inventoryItem = inventoryRepository.findDeliveredByOrderId(order.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivered digital inventory not found"));
        // 中文注释：订单与交付快照只保存 preview，买家进入 reveal API 时才按参与方权限即时解密明文。
        return new DigitalDeliveryRevealView(
                order.orderNo(),
                inventoryItem.id(),
                crypto.decrypt(inventoryItem.encryptedPayload()),
                inventoryItem.payloadPreview(),
                delivery.deliveredAt());
    }

    private boolean canRevealDeliveredPayload(OrderEntity order, String accountId) {
        return accountId != null
                && (accountId.equals(order.buyerAccountId()) || accountId.equals(order.acceptorAccountId()));
    }

    private ListingEntity requireStockListingOwnedBy(String itemId, String actorAccountId) {
        ListingEntity listing = postItemWorkspaceQueryService.requirePostItem(itemId);
        if (!actorAccountId.equals(listing.openedByAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only item owner can manage digital inventory");
        }
        if (!PostItemSupport.isStockFulfillment(listing.metadata())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Digital inventory requires stock_fulfillment mode");
        }
        return listing;
    }

    private List<String> normalizedPayloads(UploadDigitalInventoryRequest request) {
        LinkedHashSet<String> payloads = new LinkedHashSet<>();
        for (UploadDigitalInventoryRequest.UploadItem item : request.items()) {
            String payload = item.payload() == null ? "" : item.payload().trim();
            if (!payload.isBlank()) {
                payloads.add(payload);
            }
        }
        if (payloads.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digital inventory payload is required");
        }
        return List.copyOf(payloads);
    }

    private String preview(String payload) {
        String text = payload.trim();
        if (text.length() <= 8) {
            return "****";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }
}
