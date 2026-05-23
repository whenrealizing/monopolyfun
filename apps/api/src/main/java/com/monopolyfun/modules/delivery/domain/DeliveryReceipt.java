package com.monopolyfun.modules.delivery.domain;

import java.time.Instant;
import java.util.Map;

public record DeliveryReceipt(
        String providerCode,
        String providerOrderId,
        String status,
        Instant deliveredAt,
        Map<String, Object> rawReceipt
) {
}
