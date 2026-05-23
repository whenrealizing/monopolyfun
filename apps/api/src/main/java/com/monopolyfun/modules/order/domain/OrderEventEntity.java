package com.monopolyfun.modules.order.domain;

import java.time.Instant;
import java.util.Map;

public record OrderEventEntity(
        String id,
        String orderId,
        String eventType,
        String actorAccountId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
