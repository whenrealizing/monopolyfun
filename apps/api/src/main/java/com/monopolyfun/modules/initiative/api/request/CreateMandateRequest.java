package com.monopolyfun.modules.initiative.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record CreateMandateRequest(
        @NotBlank String goal,
        List<String> scope,
        Map<String, Object> budget,
        Map<String, Object> riskPolicy
) {
}
