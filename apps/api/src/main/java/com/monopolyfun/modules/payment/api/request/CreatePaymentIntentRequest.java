package com.monopolyfun.modules.payment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreatePaymentIntentRequest(
        @NotBlank String accountId,
        @Size(max = 120) String payer,
        Map<String, Object> paymentPayload,
        Boolean syncSettle,
        Map<String, Object> reconciliation
) {
}
