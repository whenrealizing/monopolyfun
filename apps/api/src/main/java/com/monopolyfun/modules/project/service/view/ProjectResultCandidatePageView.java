package com.monopolyfun.modules.project.service.view;

import java.util.List;

public record ProjectResultCandidatePageView(
        List<ProjectResultCandidateView> items,
        String nextCursor,
        ProjectResultCandidateSummaryView summary
) {
}
