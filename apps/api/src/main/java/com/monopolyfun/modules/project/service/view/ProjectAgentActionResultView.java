package com.monopolyfun.modules.project.service.view;

import java.util.Map;

public record ProjectAgentActionResultView(
        boolean accepted,
        String actionType,
        String packId,
        String status,
        Map<String, Object> result,
        ProjectAgentInboxView inbox
) {
}
