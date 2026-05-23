package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteProjectRoleRequest(
        @NotBlank String accountId,
        @Size(max = 500) String message
) {
}
