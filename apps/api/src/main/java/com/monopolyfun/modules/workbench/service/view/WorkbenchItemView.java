package com.monopolyfun.modules.workbench.service.view;

import java.util.List;
import java.util.Map;

public record WorkbenchItemView(
        String id,
        String title,
        String description,
        String lane,
        String urgency,
        String reason,
        String category,
        List<String> filterTags,
        String roleBucket,
        String domain,
        String actionKind,
        String targetHref,
        List<Map<String, String>> summaryFacts,
        boolean canDismiss,
        WorkbenchItemTargetView target,
        List<WorkbenchItemActionView> actions,
        List<String> requiredInputs,
        List<String> acceptanceCriteria,
        Map<String, Object> nextAction,
        String requiredCapability,
        String requiredRoleCode,
        String updatedAt
) {
}
