package com.monopolyfun.modules.risk.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ManualRiskActionRequest(
        @NotBlank @Size(max = 500) String reason,
        @Positive Integer freezeHours
) {
}
