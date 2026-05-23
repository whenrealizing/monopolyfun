package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkipProjectCandidateWindowRequest(
        @NotEmpty List<@Size(max = 160) String> skippedCandidateIds,
        @Size(max = 80) String reasonCode,
        @Size(max = 2000) String reason,
        @Min(1) @Max(1440) Integer ttlMinutes
) {
}
