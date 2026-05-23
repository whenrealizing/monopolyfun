package com.monopolyfun.modules.repo.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvisionProjectRepoRequest(
        @NotBlank @Size(max = 2000) String goal,
        @Size(max = 80) String titleHint
) {
}
