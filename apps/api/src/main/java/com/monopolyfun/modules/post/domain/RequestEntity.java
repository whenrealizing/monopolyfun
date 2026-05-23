package com.monopolyfun.modules.post.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record RequestEntity(
        String id,
        String requestNo,
        String actorAccountId,
        String title,
        String description,
        String deliveryStandard,
        BigDecimal budgetAmount,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient,
        InventoryPolicy inventoryPolicy,
        Integer stockTotal,
        int stockFilled,
        RequestStatus status,
        Instant deadlineAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
