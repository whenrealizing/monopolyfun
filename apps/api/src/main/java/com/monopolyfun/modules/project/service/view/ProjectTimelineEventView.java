package com.monopolyfun.modules.project.service.view;

import java.time.Instant;
import java.util.Map;

public record ProjectTimelineEventView(
        String id,
        String type,
        String title,
        String summary,
        String actorAccountId,
        String subjectType,
        String subjectId,
        Instant createdAt,
        Map<String, Object> payload
) {
}
