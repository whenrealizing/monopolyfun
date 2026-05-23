package com.monopolyfun.modules.project.service.view;

import java.util.List;

public record ProjectResultCandidateWindowView(
        List<ProjectResultCandidateWindowItemView> current,
        String nextCursor,
        ProjectCandidateBlockedSummaryView blockedSummary,
        ProjectCandidateHistorySummaryView historySummary
) {
}
