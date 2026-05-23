package com.monopolyfun.modules.projectmemory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProjectMemoryEntryEntity(
        String id,
        String projectId,
        String rootId,
        String memoryId,
        String kind,
        String content,
        List<String> sourceRefs,
        BigDecimal confidence,
        String visibility,
        String riskLevel,
        List<String> retrievalTags,
        List<String> supersedes,
        String originEventType,
        String originEventId,
        String maintenanceReason,
        Instant validFrom,
        Instant expiresAt,
        Instant lastUsedAt,
        String status,
        String createdByAccountId,
        String approvedByAccountId,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
