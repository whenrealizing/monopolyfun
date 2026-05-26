package com.monopolyfun.modules.agent.service.view;

import java.util.Map;

public record AgentTurnProjection(
        Map<String, Object> summary,
        Map<String, Object> refs,
        Map<String, Object> counts,
        Map<String, Object> current
) {
}
