package com.monopolyfun.modules.order.api.request;

import com.monopolyfun.modules.order.domain.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BackofficeOverrideReviewRequest(
        @NotBlank String actorAccountId,
        @NotNull ReviewDecision decision,
        @NotBlank String reason
) {
}
