package com.monopolyfun.modules.digitalinventory.domain;

import java.time.Instant;

public record DigitalInventoryItemEntity(
        String id,
        String listingId,
        String encryptedPayload,
        String payloadPreview,
        String payloadHash,
        String status,
        String reservedOrderId,
        String deliveredOrderId,
        String createdByAccountId,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_RESERVED = "reserved";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_VOIDED = "voided";

    public DigitalInventoryItemEntity withReserved(String orderId, Instant now) {
        return new DigitalInventoryItemEntity(
                id,
                listingId,
                encryptedPayload,
                payloadPreview,
                payloadHash,
                STATUS_RESERVED,
                orderId,
                deliveredOrderId,
                createdByAccountId,
                createdAt,
                now);
    }

    public DigitalInventoryItemEntity withDelivered(String orderId, Instant now) {
        return new DigitalInventoryItemEntity(
                id,
                listingId,
                encryptedPayload,
                payloadPreview,
                payloadHash,
                STATUS_DELIVERED,
                reservedOrderId,
                orderId,
                createdByAccountId,
                createdAt,
                now);
    }
}
