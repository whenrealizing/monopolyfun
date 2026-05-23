package com.monopolyfun.modules.project.service.view;

import java.util.List;
import java.util.Map;

public record ProjectAgentInboxView(
        Map<String, Object> project,
        Map<String, Object> agentState,
        List<ProjectAgentActionCardView> cards
) {
}
