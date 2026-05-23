package com.monopolyfun.modules.order.api.request;

import jakarta.validation.constraints.NotBlank;

public record AppealOrderRequest(
        @NotBlank String actorAccountId,
        @NotBlank String reason
) {
}
