package com.monopolyfun.modules.order.service.view;

import java.time.Instant;
import java.util.Map;

public record OrderEventView(
        String id,
        String eventType,
        String actorAccountId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
