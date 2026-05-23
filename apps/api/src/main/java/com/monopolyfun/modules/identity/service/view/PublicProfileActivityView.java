package com.monopolyfun.modules.identity.service.view;

import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.modules.post.service.view.RequestView;
import com.monopolyfun.modules.project.service.view.ProjectView;

import java.util.List;

public record PublicProfileActivityView(
        List<OfferView> offers,
        List<RequestView> requests,
        List<ProjectView> projects
) {
}
