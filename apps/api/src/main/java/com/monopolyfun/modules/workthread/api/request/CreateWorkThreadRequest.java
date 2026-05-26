package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateWorkThreadRequest(
        @NotBlank String actorAccountId,
        @NotBlank String title,
        @NotBlank String goal,
        List<String> deliverables,
        List<String> acceptanceCriteria,
        @Min(0) @Max(10000) int taskValue,
        @Min(0) Integer bountyAmountMinor,
        String bountyToken,
        String repoRef,
        String issueUrl,
        String reviewerAccountId
) {
}
