package com.monopolyfun.modules.post.service.view;

import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.project.service.view.MarketSummary;
import com.monopolyfun.modules.share.service.view.ProjectSharesView;

import java.util.List;
import java.util.Map;

public record PostWorkspaceView(
        PostKind postKind,
        Object post,
        MarketSummary market,
        ProjectSharesView shares,
        List<PostItemView> items,
        Map<String, Long> itemCounts
) {
}
