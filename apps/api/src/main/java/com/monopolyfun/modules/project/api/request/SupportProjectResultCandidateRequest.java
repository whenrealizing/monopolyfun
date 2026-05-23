package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.Size;

public record SupportProjectResultCandidateRequest(
        Integer prNumber,
        @Size(max = 1000) String reason
) {
}
