package com.monopolyfun.modules.repo.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinalizeRepoProofRequest(
        @NotBlank @Size(max = 500) String summary,
        @NotEmpty @Size(max = 20) List<@Size(max = 500) String> artifacts,
        @Size(max = 20) List<@Size(max = 500) String> criteriaRefs,
        @Size(max = 20) List<@Size(max = 500) String> evidenceRefs
) {
}
