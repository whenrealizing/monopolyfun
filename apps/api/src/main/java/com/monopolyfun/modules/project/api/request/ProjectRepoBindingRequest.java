package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRepoBindingRequest(
        @Size(max = 40) String provider,
        @NotBlank @Size(max = 500) String repoUrl,
        @NotBlank @Size(max = 120) String repoOwner,
        @NotBlank @Size(max = 120) String repoName,
        @Size(max = 120) String defaultBranch,
        @Size(max = 120) String installationId
) {
}
