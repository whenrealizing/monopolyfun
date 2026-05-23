package com.monopolyfun.modules.order.api.request;

import jakarta.validation.constraints.NotBlank;

public record AcceptOrderRequest(
        @NotBlank String acceptedByAccountId,
        String note
) {
}
