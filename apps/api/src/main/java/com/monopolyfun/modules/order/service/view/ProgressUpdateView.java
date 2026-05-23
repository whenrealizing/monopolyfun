package com.monopolyfun.modules.order.service.view;

import com.monopolyfun.modules.order.domain.ProofLink;

import java.time.Instant;
import java.util.List;

public record ProgressUpdateView(
        String id,
        String orderId,
        int stepIndex,
        String stepTitle,
        String summary,
        List<ProofLink> links,
        List<String> artifacts,
        String executionMode,
        String agentRuntime,
        Instant createdAt
) {
}
