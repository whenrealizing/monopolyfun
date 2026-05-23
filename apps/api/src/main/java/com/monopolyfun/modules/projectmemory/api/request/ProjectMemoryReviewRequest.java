package com.monopolyfun.modules.projectmemory.api.request;

import jakarta.validation.constraints.Size;

public record ProjectMemoryReviewRequest(
        @Size(max = 1000) String note
) {
}
