package com.monopolyfun.modules.order.api.request;

import com.monopolyfun.modules.order.domain.ExecutionMode;
import com.monopolyfun.modules.order.domain.ProofLink;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SubmitProgressRequest(
        @NotBlank String submittedByAccountId,
        @NotBlank @Size(max = 120) String stepTitle,
        @NotBlank @Size(max = 500) String summary,
        @Size(max = 20) List<ProofLink> links,
        @Size(max = 20) List<@Size(max = 500) String> artifacts,
        Map<String, Object> progressPayload,
        @NotNull ExecutionMode executionMode,
        @Size(max = 120) String agentSessionId,
        @Size(max = 120) String agentRuntime
) {
}
