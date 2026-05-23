package com.monopolyfun.modules.order.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DisputeOrderRequest(
        @NotBlank String actorAccountId,
        @NotBlank String reason,
        List<String> evidenceRefs
) {
}
