package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ProjectPrCiEventRequest(
        @NotBlank @Size(max = 40) String eventType,
        @Size(max = 80) String validationTaskId,
        @Size(max = 500) String repoUrl,
        Integer prNumber,
        @Size(max = 500) String prUrl,
        @Size(max = 80) String headSha,
        @Size(max = 120) String baseBranch,
        @Size(max = 120) String branchName,
        @Size(max = 40) String state,
        @Size(max = 160) String checkName,
        @Size(max = 40) String status,
        @Size(max = 40) String conclusion,
        @Size(max = 500) String detailsUrl,
        Map<String, Object> payload
) {
}
