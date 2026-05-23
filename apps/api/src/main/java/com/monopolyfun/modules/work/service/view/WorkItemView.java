package com.monopolyfun.modules.work.service.view;

import java.util.List;
import java.util.Map;

public record WorkItemView(
        String id,
        String itemNo,
        String title,
        String goal,
        String status,
        String sourceType,
        String sourceId,
        String urgency,
        String requiredRole,
        String requiredCapability,
        String claimExpiresAt,
        List<String> acceptanceCriteria,
        List<String> inputRefs,
        Map<String, Object> outputSchema,
        List<WorkItemActionView> actions,
        String updatedAt
) {
}
