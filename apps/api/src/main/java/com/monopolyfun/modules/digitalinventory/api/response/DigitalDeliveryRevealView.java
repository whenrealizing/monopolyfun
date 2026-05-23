package com.monopolyfun.modules.digitalinventory.api.response;

import java.time.Instant;

public record DigitalDeliveryRevealView(
        String orderNo,
        String inventoryItemId,
        String payload,
        String payloadPreview,
        Instant deliveredAt
) {
}
