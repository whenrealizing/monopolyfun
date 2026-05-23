package com.monopolyfun.modules.initiative.api.request;

import jakarta.validation.constraints.NotBlank;

public record ExecuteProposalRequest(
        @NotBlank String actorAccountId
) {
}
