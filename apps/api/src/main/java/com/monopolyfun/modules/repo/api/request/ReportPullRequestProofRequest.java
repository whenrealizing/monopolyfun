package com.monopolyfun.modules.repo.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportPullRequestProofRequest(
        @NotBlank @Size(max = 500) String prUrl,
        @NotBlank @Size(max = 120) String headCommit,
        @Size(max = 1000) String diffSummary
) {
}
