package com.monopolyfun.modules.work.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record RequestWorkHelpRequest(
        @NotBlank String actorAccountId,
        @NotBlank String reason,
        String title,
        List<String> evidenceRefs,
        Map<String, Object> helpPayload
) {
}
