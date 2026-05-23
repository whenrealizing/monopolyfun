package com.monopolyfun.modules.work.api.request;

import jakarta.validation.constraints.NotBlank;

public record ClaimWorkItemRequest(
        @NotBlank String actorAccountId,
        String executionMode
) {
}
