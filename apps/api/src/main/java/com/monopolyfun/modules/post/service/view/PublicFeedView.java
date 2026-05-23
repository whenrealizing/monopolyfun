package com.monopolyfun.modules.post.service.view;

import com.monopolyfun.modules.identity.service.view.PublicAccountSummary;
import com.monopolyfun.modules.project.service.view.ProjectView;

import java.util.List;
import java.util.Map;

public record PublicFeedView(
        Map<String, PublicAccountSummary> accountsById,
        List<OfferView> offers,
        List<RequestView> requests,
        List<ProjectView> projects,
        ProjectView rootProject,
        Map<String, Long> counts
) {
}
