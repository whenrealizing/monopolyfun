package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, max = 120) String newPassword
) {
}
