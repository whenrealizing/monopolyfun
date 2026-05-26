package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertProjectRevenueAddressRequest(
        @NotBlank String actorAccountId,
        @NotBlank String chainId,
        @NotBlank String contractAddress,
        @NotBlank String tokenAddress
) {
}
