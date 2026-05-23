package com.monopolyfun.modules.payment.domain;

import java.time.Instant;
import java.util.Map;

public record PaymentProviderEventEntity(
        String id,
        String provider,
        String providerEventId,
        String paymentIntentId,
        String providerPaymentRef,
        String txHash,
        String status,
        Map<String, Object> payload,
        Instant createdAt
) {
}
