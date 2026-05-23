package com.monopolyfun.modules.order.api.request;

import com.monopolyfun.modules.order.domain.ExecutionMode;
import com.monopolyfun.modules.order.domain.ProofLink;
import com.monopolyfun.modules.order.domain.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SubmitProofRequest(
        @NotBlank String submittedByAccountId,
        @NotBlank @Size(max = 500) String summary,
        @Size(max = 20) List<ProofLink> links,
        @Size(max = 20) List<@Size(max = 500) String> artifacts,
        Map<String, Object> proofPayload,
        @NotNull ExecutionMode executionMode,
        @Size(max = 120) String agentSessionId,
        @Size(max = 120) String agentRuntime,
        ReviewDecision decision,
        @Size(max = 20) List<@Size(max = 500) String> evidenceRefs,
        @Size(max = 20) List<@Size(max = 128) String> contentHashes,
        @Size(max = 20) List<@Size(max = 500) String> criteriaRefs,
        @Size(max = 40) String visibility,
        @Size(max = 500) String executionTraceRef
) {
}
