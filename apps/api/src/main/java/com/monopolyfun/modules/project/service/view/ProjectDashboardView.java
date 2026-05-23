package com.monopolyfun.modules.project.service.view;

import com.monopolyfun.modules.post.service.view.PostWorkspaceView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectAgentContextView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryOverviewView;

import java.util.List;

public record ProjectDashboardView(
        PostWorkspaceView workspace,
        DashboardSectionView<List<ProjectRepoBindingView>> repoBindings,
        DashboardSectionView<ProjectPrCiStatusView> prCiStatus,
        DashboardSectionView<ProjectMemoryOverviewView> projectMemory,
        DashboardSectionView<ProjectAgentContextView> agentContext,
        DashboardSectionView<ProjectCommercializationView> commercialization
) {
}
