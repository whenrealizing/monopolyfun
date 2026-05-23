package com.monopolyfun.modules.delivery.domain;

import java.util.Map;

public record DeliveryRequest(
        String orderId,
        String orderNo,
        String paymentIntentId,
        String accountId,
        String provider,
        Map<String, Object> input,
        Map<String, Object> settlementSnapshot,
        String idempotencyKey
) {
}
