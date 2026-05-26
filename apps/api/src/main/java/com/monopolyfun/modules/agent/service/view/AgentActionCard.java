package com.monopolyfun.modules.agent.service.view;

import java.util.Map;

public record AgentActionCard(
        String id,
        String label,
        String plainInstruction,
        String nextExpected,
        String importance,
        AgentApiOperation apiOperation,
        Map<String, Object> inputHints,
        Map<String, Object> inputTemplate,
        Map<String, Object> inputSchema,
        AgentTurnPointer nextTurn,
        String riskCategory,
        boolean requiresApproval,
        String approvalReason,
        Map<String, Object> approvalEvidenceSchema
) {
}
