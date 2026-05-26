package com.monopolyfun.modules.agent.api.request;

import java.util.Map;

public record AgentTurnRequest(
        String intent,
        String turnId,
        String scene,
        AgentSubject subject,
        String actionId,
        Map<String, Object> input
) {
}
