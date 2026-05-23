package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;

public record ClaimProjectOwnerRequest(
        @NotBlank String actorAccountId,
        String reason,
        String plan
) {
}
