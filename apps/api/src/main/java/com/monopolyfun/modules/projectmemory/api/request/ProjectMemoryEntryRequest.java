package com.monopolyfun.modules.projectmemory.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProjectMemoryEntryRequest(
        @Size(max = 80) String memoryId,
        @NotBlank @Size(max = 40) String kind,
        @NotBlank @Size(max = 1000) String content,
        List<String> sourceRefs,
        BigDecimal confidence,
        @Size(max = 40) String visibility,
        @Size(max = 40) String riskLevel,
        List<String> retrievalTags,
        List<String> supersedes,
        @Size(max = 80) String originEventType,
        @Size(max = 120) String originEventId,
        @Size(max = 200) String maintenanceReason
) {
}
