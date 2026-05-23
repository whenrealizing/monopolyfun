package com.monopolyfun.modules.initiative.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record DiscoverOpportunitiesRequest(
        @NotBlank String mandateNo,
        String targetType,
        String targetId,
        String reason,
        String suggestedAction,
        Map<String, Object> signal
) {
}
