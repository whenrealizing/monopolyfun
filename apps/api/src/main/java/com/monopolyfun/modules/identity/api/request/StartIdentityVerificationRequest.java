package com.monopolyfun.modules.identity.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StartIdentityVerificationRequest(
        @NotBlank String certifierId,
        Map<String, Object> input
) {
}
