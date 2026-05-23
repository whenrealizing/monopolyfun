package com.monopolyfun.modules.payment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OkxA2aCallbackRequest(
        @NotBlank String paymentId,
        @NotBlank String intentId,
        @NotBlank String orderNo,
        @NotNull Integer amountMinor,
        @NotBlank String currency,
        @NotBlank String recipient,
        String payer,
        @NotBlank String txHash,
        @NotBlank String status,
        String callbackEventId,
        String network,
        String chainReceiptStatus,
        Integer transferLogCount,
        String evidencePath
) {
}
