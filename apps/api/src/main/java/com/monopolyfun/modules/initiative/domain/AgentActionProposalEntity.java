package com.monopolyfun.modules.initiative.domain;

import java.time.Instant;
import java.util.Map;

public record AgentActionProposalEntity(
        String id,
        String proposalNo,
        String opportunityId,
        String mandateId,
        String actionId,
        String reason,
        String risk,
        Map<String, Object> input,
        String expectedOutcome,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentActionProposalEntity {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
