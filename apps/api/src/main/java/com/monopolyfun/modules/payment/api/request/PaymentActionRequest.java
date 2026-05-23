package com.monopolyfun.modules.payment.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentActionRequest(
        @NotBlank String actorAccountId,
        @Size(max = 500) String reason,
        @Size(max = 128) String refundTxHash
) {
}
