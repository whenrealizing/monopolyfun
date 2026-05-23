package com.monopolyfun.modules.work.api.request;

import jakarta.validation.constraints.NotBlank;

public record CloseWorkRunRequest(
        @NotBlank String actorAccountId,
        @NotBlank String reason
) {
}
