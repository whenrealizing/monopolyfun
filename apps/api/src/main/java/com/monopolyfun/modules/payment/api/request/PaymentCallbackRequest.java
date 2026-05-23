package com.monopolyfun.modules.payment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
        @NotBlank String intentId,
        @NotBlank String callbackToken,
        @NotBlank String providerPaymentRef,
        @NotBlank String status,
        @NotNull Integer amountMinor
) {
}
