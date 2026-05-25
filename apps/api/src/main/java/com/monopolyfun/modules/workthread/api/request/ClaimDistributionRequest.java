package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.NotBlank;

public record ClaimDistributionRequest(
        @NotBlank String actorAccountId,
        @NotBlank String walletAddress,
        String txHash
) {
}
