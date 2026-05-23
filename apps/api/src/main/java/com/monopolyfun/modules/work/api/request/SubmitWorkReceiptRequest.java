package com.monopolyfun.modules.work.api.request;

import com.monopolyfun.modules.order.domain.ProofLink;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record SubmitWorkReceiptRequest(
        @NotBlank String actorAccountId,
        @NotBlank String summary,
        Map<String, Object> output,
        Map<String, Object> sourceReceipt,
        List<String> evidenceRefs,
        List<String> traceRefs,
        List<String> contentHashes,
        List<ProofLink> links,
        List<String> artifacts,
        String agentRuntime
) {
}
