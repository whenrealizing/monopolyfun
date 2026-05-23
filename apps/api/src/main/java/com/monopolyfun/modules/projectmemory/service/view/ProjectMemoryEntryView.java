package com.monopolyfun.modules.projectmemory.service.view;

import java.math.BigDecimal;
import java.util.List;

public record ProjectMemoryEntryView(
        String id,
        String memoryId,
        String kind,
        String content,
        List<String> sourceRefs,
        BigDecimal confidence,
        String visibility,
        String riskLevel,
        List<String> retrievalTags,
        List<String> supersedes,
        String status,
        String createdByAccountId,
        String approvedByAccountId,
        String approvedAt,
        String updatedAt
) {
}
