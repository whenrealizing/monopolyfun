package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String handle,
        @NotBlank String password
) {
}
