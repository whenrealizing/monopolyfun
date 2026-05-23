package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;

public record AssignProjectRoleRequest(
        @NotBlank String accountId
) {
}
