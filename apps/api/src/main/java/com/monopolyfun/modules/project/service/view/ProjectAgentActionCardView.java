package com.monopolyfun.modules.project.service.view;

import java.util.List;
import java.util.Map;

public record ProjectAgentActionCardView(
        String cardId,
        String type,
        String title,
        String packId,
        Map<String, Object> context,
        List<String> requiredFields
) {
}
