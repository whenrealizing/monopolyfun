package com.monopolyfun.modules.initiative.api.request;

import jakarta.validation.constraints.NotBlank;

public record ApproveProposalRequest(
        @NotBlank String actorAccountId,
        String note
) {
}
