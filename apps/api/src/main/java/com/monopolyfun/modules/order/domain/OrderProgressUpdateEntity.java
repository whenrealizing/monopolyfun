package com.monopolyfun.modules.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrderProgressUpdateEntity(
        String id,
        String orderId,
        String listingId,
        int stepIndex,
        String stepTitle,
        String summary,
        List<ProofLink> links,
        List<String> artifacts,
        Map<String, Object> progressPayload,
        String submittedByAccountId,
        ExecutionMode executionMode,
        String agentSessionId,
        String agentRuntime,
        Instant createdAt
) {
}
