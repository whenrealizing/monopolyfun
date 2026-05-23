package com.monopolyfun.modules.payment.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.monopolyfun.modules.payment.domain.PaymentIntentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PaymentIntentView(
        String id,
        String paymentNo,
        String orderId,
        String accountId,
        String provider,
        String providerPaymentRef,
        PaymentIntentStatus status,
        int amountMinor,
        String currency,
        Instant authorizedAt,
        Instant capturedAt,
        Instant refundedAt,
        Instant cancelledAt,
        Instant disputedAt,
        Map<String, Object> metadata,
        PaymentBindingView binding,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String resourceKey,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> capabilities,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> blockedCapabilities
) {
    public PaymentIntentView(
            String id,
            String paymentNo,
            String orderId,
            String accountId,
            String provider,
            String providerPaymentRef,
            PaymentIntentStatus status,
            int amountMinor,
            String currency,
            Instant authorizedAt,
            Instant capturedAt,
            Instant refundedAt,
            Instant cancelledAt,
            Instant disputedAt,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                paymentNo,
                orderId,
                accountId,
                provider,
                providerPaymentRef,
                status,
                amountMinor,
                currency,
                authorizedAt,
                capturedAt,
                refundedAt,
                cancelledAt,
                disputedAt,
                metadata,
                null,
                createdAt,
                updatedAt);
    }

    public PaymentIntentView(
            String id,
            String paymentNo,
            String orderId,
            String accountId,
            String provider,
            String providerPaymentRef,
            PaymentIntentStatus status,
            int amountMinor,
            String currency,
            Instant authorizedAt,
            Instant capturedAt,
            Instant refundedAt,
            Instant cancelledAt,
            Instant disputedAt,
            Map<String, Object> metadata,
            PaymentBindingView binding,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                paymentNo,
                orderId,
                accountId,
                provider,
                providerPaymentRef,
                status,
                amountMinor,
                currency,
                authorizedAt,
                capturedAt,
                refundedAt,
                cancelledAt,
                disputedAt,
                metadata,
                binding,
                createdAt,
                updatedAt,
                null,
                List.of(),
                List.of());
    }

    public PaymentIntentView {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        blockedCapabilities = blockedCapabilities == null ? List.of() : List.copyOf(blockedCapabilities);
    }

    public PaymentIntentView withAgentState(
            String resourceKey,
            List<String> capabilities,
            List<Map<String, Object>> blockedCapabilities) {
        return new PaymentIntentView(
                id,
                paymentNo,
                orderId,
                accountId,
                provider,
                providerPaymentRef,
                status,
                amountMinor,
                currency,
                authorizedAt,
                capturedAt,
                refundedAt,
                cancelledAt,
                disputedAt,
                metadata,
                binding,
                createdAt,
                updatedAt,
                resourceKey,
                capabilities,
                blockedCapabilities);
    }
}
