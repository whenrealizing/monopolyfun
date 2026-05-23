package com.monopolyfun.modules.project.service.view;

import java.time.Instant;

public record ProjectCandidateHistorySummaryView(
        int mergedMainline,
        Instant latestMergedAt
) {
}
