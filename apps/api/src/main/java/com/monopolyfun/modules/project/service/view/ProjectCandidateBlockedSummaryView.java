package com.monopolyfun.modules.project.service.view;

import java.util.List;
import java.util.Map;

public record ProjectCandidateBlockedSummaryView(
        int integrationChecking,
        int integrationBlocked,
        List<Map<String, Object>> topReasons
) {
}
