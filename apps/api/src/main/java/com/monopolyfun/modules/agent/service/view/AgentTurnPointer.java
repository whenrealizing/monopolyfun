package com.monopolyfun.modules.agent.service.view;

import com.monopolyfun.modules.agent.api.request.AgentSubject;

import java.util.Map;

public record AgentTurnPointer(
        String intent,
        String scene,
        AgentSubject subject,
        String actionId,
        Map<String, Object> input
) {
}
