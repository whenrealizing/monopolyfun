package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectPostRequest(
        @NotBlank String actorAccountId,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 2000) String goal,
        @Size(max = 2000) String ownerIntro
) {
}
