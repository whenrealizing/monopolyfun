package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.NotBlank;

public record ClaimWorkThreadRequest(
        @NotBlank String actorAccountId,
        String runtime
) {
}
