package com.monopolyfun.modules.projectmemory.service.view;

import java.util.Map;

public record ProjectMemorySyncEventView(
        String id,
        String eventType,
        String status,
        String message,
        Map<String, Object> payload,
        String createdAt
) {
}
