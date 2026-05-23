package com.monopolyfun.modules.work.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ReviseWorkReceiptRequest(
        @NotBlank String reviewerAccountId,
        @NotBlank String reason,
        List<String> evidenceRefs
) {
}
