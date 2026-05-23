package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.modules.post.service.view.PostWorkspaceView;
import com.monopolyfun.modules.project.service.view.DashboardSectionView;
import com.monopolyfun.modules.project.service.view.ProjectDashboardView;
import com.monopolyfun.modules.projectmemory.service.ProjectMemoryService;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProjectDashboardQueryService {
    private final PostItemWorkspaceQueryService workspaceQueryService;
    private final ProjectDevelopmentService developmentService;
    private final ProjectMemoryService memoryService;
    private final ProjectCommercializationService commercializationService;
    private final CurrentAccountAccess currentAccountAccess;

    public ProjectDashboardQueryService(
            PostItemWorkspaceQueryService workspaceQueryService,
            ProjectDevelopmentService developmentService,
            ProjectMemoryService memoryService,
            ProjectCommercializationService commercializationService,
            CurrentAccountAccess currentAccountAccess) {
        this.workspaceQueryService = workspaceQueryService;
        this.developmentService = developmentService;
        this.memoryService = memoryService;
        this.commercializationService = commercializationService;
        this.currentAccountAccess = currentAccountAccess;
    }

    public ProjectDashboardView getDashboard(String projectNo) {
        PostWorkspaceView workspace = workspaceQueryService.getWorkspace(projectNo, false);
        String actorAccountId = currentAccountAccess.current()
                .map(com.monopolyfun.shared.security.CurrentAccount::accountId)
                .orElse(null);
        if (actorAccountId == null) {
            return new ProjectDashboardView(
                    workspace,
                    DashboardSectionView.unavailable("forbidden", "auth_required", List.of()),
                    DashboardSectionView.unavailable("forbidden", "auth_required", null),
                    DashboardSectionView.unavailable("forbidden", "auth_required", null),
                    DashboardSectionView.unavailable("forbidden", "auth_required", null),
                    DashboardSectionView.visible(commercializationService.getCommercialization(projectNo)));
        }
        // 中文注释：dashboard 聚合返回 section 状态，避免权限或状态错误被误读为空数据。
        return new ProjectDashboardView(
                workspace,
                section(() -> developmentService.listRepoBindings(projectNo, actorAccountId), List.of()),
                section(() -> developmentService.getStatus(projectNo, actorAccountId), null),
                section(() -> memoryService.overview(projectNo, actorAccountId), null),
                section(() -> memoryService.agentContext(projectNo, actorAccountId), null),
                section(() -> commercializationService.getCommercialization(projectNo), null));
    }

    private <T> DashboardSectionView<T> section(CheckedSupplier<T> supplier, T fallback) {
        try {
            return DashboardSectionView.visible(supplier.get());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                return DashboardSectionView.unavailable(sectionStatus(exception), exception.getStatusCode().toString(), fallback);
            }
            throw exception;
        }
    }

    private String sectionStatus(ResponseStatusException exception) {
        if (exception.getStatusCode().value() == 403) {
            return "forbidden";
        }
        if (exception.getStatusCode().value() == 404) {
            return "missing";
        }
        return "unavailable";
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }
}
