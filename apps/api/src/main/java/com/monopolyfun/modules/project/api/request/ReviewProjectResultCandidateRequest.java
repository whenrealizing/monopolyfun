package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewProjectResultCandidateRequest(
        Integer prNumber,
        @NotBlank @Size(max = 40) String decision,
        @Size(max = 2000) String reason
) {
}
