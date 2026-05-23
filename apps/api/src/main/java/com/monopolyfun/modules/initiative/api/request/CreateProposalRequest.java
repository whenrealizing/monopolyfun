package com.monopolyfun.modules.initiative.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record CreateProposalRequest(
        String opportunityNo,
        @NotBlank String mandateNo,
        @NotBlank String actionId,
        @NotBlank String reason,
        @NotBlank String risk,
        Map<String, Object> input,
        @NotBlank String expectedOutcome
) {
}
