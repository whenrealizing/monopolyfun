package com.monopolyfun.modules.post.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClosePostRequest(
        @NotBlank String actorAccountId,
        @Size(max = 500) String reason
) {
}
