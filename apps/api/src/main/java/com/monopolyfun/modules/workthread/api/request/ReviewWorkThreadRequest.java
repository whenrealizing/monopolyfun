package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.NotBlank;

public record ReviewWorkThreadRequest(
        @NotBlank String reviewerAccountId,
        @NotBlank String decision,
        @NotBlank String reason
) {
}
