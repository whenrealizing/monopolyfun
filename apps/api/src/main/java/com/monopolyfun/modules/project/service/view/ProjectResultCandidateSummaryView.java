package com.monopolyfun.modules.project.service.view;

public record ProjectResultCandidateSummaryView(
        int candidateReady,
        int finalReviewRequired,
        int integrationChecking,
        int integrationBlocked,
        int mergedMainline
) {
}
