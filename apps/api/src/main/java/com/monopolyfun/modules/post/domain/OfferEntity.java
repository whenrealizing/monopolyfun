package com.monopolyfun.modules.post.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record OfferEntity(
        String id,
        String offerNo,
        String actorAccountId,
        String title,
        String description,
        String deliveryStandard,
        BigDecimal priceAmount,
        String currency,
        String paymentMethod,
        String paymentProfile,
        String paymentNetwork,
        String paymentAsset,
        String paymentRecipient,
        InventoryPolicy inventoryPolicy,
        Integer stockTotal,
        int stockSold,
        OfferStatus status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
