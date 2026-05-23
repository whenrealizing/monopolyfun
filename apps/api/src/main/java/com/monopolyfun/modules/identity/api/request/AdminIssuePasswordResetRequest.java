package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record AdminIssuePasswordResetRequest(
        @NotBlank String handle
) {
}
