package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateIdentityDisplaySkinRequest(
        @NotBlank String source,
        String certifierId
) {
}
