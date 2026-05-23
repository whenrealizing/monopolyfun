package com.monopolyfun.modules.projectmemory.domain;

import java.time.Instant;
import java.util.Map;

public record ProjectMemorySourceEntity(
        String id,
        String projectId,
        String rootId,
        String sourceId,
        String kind,
        String path,
        String sha256,
        String visibility,
        String provider,
        String externalUrl,
        String externalFileId,
        String externalRevisionId,
        Long externalSize,
        String syncStatus,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
