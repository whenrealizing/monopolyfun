package com.monopolyfun.modules.order.api.request;

import jakarta.validation.constraints.NotBlank;

public record AssignReviewerRequest(
        @NotBlank String actorAccountId,
        @NotBlank String reviewerAccountId,
        String reviewDueAt
) {
}
