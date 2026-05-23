package com.monopolyfun.modules.digitalinventory.domain;

import java.time.Instant;
import java.util.Map;

public record DigitalDeliveryEntity(
        String id,
        String orderId,
        String inventoryItemId,
        String status,
        Map<String, Object> deliverySnapshot,
        String errorMessage,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_FAILED = "failed";
}
