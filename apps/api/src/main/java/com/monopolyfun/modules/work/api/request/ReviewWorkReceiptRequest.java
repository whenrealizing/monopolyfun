package com.monopolyfun.modules.work.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ReviewWorkReceiptRequest(
        @NotBlank String reviewerAccountId,
        @NotBlank String decision,
        @NotBlank String reason,
        List<String> evidenceRefs
) {
}
