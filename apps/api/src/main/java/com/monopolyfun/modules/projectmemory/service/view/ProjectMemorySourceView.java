package com.monopolyfun.modules.projectmemory.service.view;

import java.util.Map;

public record ProjectMemorySourceView(
        String id,
        String sourceId,
        String kind,
        String path,
        String sha256,
        String visibility,
        String provider,
        String externalUrl,
        String syncStatus,
        Map<String, Object> metadata,
        String createdAt
) {
}
