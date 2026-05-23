package com.monopolyfun.modules.delivery.domain;

import java.time.Instant;
import java.util.Map;

public record DeliveryAttemptEntity(
        String id,
        String orderId,
        String paymentIntentId,
        String provider,
        String providerOrderId,
        String status,
        String idempotencyKey,
        Map<String, Object> requestPayload,
        Map<String, Object> receiptPayload,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";
}
