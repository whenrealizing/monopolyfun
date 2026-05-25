package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateDistributionBatchRequest(
        @NotBlank String actorAccountId,
        @NotBlank String period,
        @Min(0) int totalRevenueMinor
) {
}
