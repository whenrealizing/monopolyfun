package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
        @NotBlank(message = "auth.handle.required")
        @Size(min = 3, max = 20, message = "auth.handle.invalid_length")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "auth.handle.invalid_pattern")
        String handle,
        @NotBlank(message = "auth.password.required")
        @Size(min = 8, max = 120, message = "auth.password.invalid_length")
        String password
) {
}
