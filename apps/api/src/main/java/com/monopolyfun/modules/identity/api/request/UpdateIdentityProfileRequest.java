package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateIdentityProfileRequest(
        @NotBlank(message = "identity.profile.display_name.required")
        @Size(max = 60, message = "identity.profile.display_name.invalid_length")
        String displayName,
        @Size(max = 240, message = "identity.profile.agent_summary.invalid_length")
        String agentSummary,
        @Size(max = 500, message = "identity.profile.avatar_url.invalid_length")
        String avatarUrl
) {
}
