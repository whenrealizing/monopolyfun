package com.monopolyfun.modules.project.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ProjectAgentActionRequest(
        @NotBlank @Size(max = 120) String actionType,
        @Size(max = 180) String cardId,
        Map<String, Object> payload
) {
}
