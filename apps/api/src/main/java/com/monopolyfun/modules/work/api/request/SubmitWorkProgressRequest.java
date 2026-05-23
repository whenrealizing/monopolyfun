package com.monopolyfun.modules.work.api.request;

import com.monopolyfun.modules.order.domain.ProofLink;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record SubmitWorkProgressRequest(
        @NotBlank String actorAccountId,
        @NotBlank String stepTitle,
        @NotBlank String summary,
        Map<String, Object> progressPayload,
        List<String> evidenceRefs,
        List<ProofLink> links,
        List<String> artifacts,
        String executionMode,
        String agentSessionId,
        String agentRuntime
) {
}
