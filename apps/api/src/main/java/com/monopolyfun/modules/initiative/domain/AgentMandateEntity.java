package com.monopolyfun.modules.initiative.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AgentMandateEntity(
        String id,
        String mandateNo,
        String accountId,
        String goal,
        List<String> scope,
        Map<String, Object> budget,
        Map<String, Object> riskPolicy,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentMandateEntity {
        scope = scope == null ? List.of() : List.copyOf(scope);
        budget = budget == null ? Map.of() : Map.copyOf(budget);
        riskPolicy = riskPolicy == null ? Map.of() : Map.copyOf(riskPolicy);
    }
}
