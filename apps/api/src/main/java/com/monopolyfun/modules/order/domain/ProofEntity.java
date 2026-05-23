package com.monopolyfun.modules.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProofEntity(
        String id,
        String orderId,
        ProofKind kind,
        String parentOrderId,
        String submittedByAccountId,
        String summary,
        List<ProofLink> links,
        List<String> artifacts,
        Map<String, Object> proofPayload,
        ExecutionMode executionMode,
        String agentSessionId,
        String agentRuntime,
        ReviewDecision decision,
        List<String> evidenceRefs,
        List<String> contentHashes,
        List<String> criteriaRefs,
        String visibility,
        String executionTraceRef,
        Instant createdAt
) {
}
