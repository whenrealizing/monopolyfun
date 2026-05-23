package com.monopolyfun.modules.payment.domain;

import java.time.Instant;
import java.util.Map;

public record PaymentIntentEntity(
        String id,
        String paymentNo,
        String orderId,
        String accountId,
        String provider,
        String providerPaymentRef,
        PaymentIntentStatus status,
        int amountMinor,
        String currency,
        String callbackToken,
        Instant authorizedAt,
        Instant capturedAt,
        Instant refundedAt,
        Instant cancelledAt,
        Instant disputedAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public PaymentIntentEntity withStatus(
            PaymentIntentStatus nextStatus,
            String nextProviderPaymentRef,
            Instant statusAt,
            Map<String, Object> nextMetadata) {
        return new PaymentIntentEntity(
                id,
                paymentNo,
                orderId,
                accountId,
                provider,
                nextProviderPaymentRef == null || nextProviderPaymentRef.isBlank() ? providerPaymentRef : nextProviderPaymentRef,
                nextStatus,
                amountMinor,
                currency,
                callbackToken,
                nextStatus == PaymentIntentStatus.AUTHORIZED ? statusAt : authorizedAt,
                nextStatus == PaymentIntentStatus.CAPTURED ? statusAt : capturedAt,
                nextStatus == PaymentIntentStatus.REFUNDED ? statusAt : refundedAt,
                nextStatus == PaymentIntentStatus.CANCELLED ? statusAt : cancelledAt,
                nextStatus == PaymentIntentStatus.DISPUTED ? statusAt : disputedAt,
                nextMetadata == null ? metadata : nextMetadata,
                createdAt,
                statusAt);
    }
}
