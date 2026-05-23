package com.monopolyfun.modules.settlement.domain;

import java.time.Instant;
import java.util.Map;

public record SettlementEventEntity(
        String id,
        String orderId,
        String paymentIntentId,
        String eventType,
        String idempotencyKey,
        Integer amountMinor,
        String currency,
        String actorAccountId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
