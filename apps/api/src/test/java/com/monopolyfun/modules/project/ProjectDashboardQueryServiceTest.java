package com.monopolyfun;

import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.post.service.query.PostItemWorkspaceQueryService;
import com.monopolyfun.modules.post.service.view.PostWorkspaceView;
import com.monopolyfun.modules.project.service.ProjectCommercializationService;
import com.monopolyfun.modules.project.service.ProjectDashboardQueryService;
import com.monopolyfun.modules.project.service.ProjectDevelopmentService;
import com.monopolyfun.modules.project.service.view.ProjectCommercializationView;
import com.monopolyfun.modules.projectmemory.service.ProjectMemoryService;
import com.monopolyfun.shared.security.CurrentAccount;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ProjectDashboardQueryServiceTest {
    @BeforeEach
    void resetSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedDashboardMarksPrivateSectionsForbidden() {
        ProjectDashboardQueryService service = service(null, null);

        var view = service.getDashboard("MF260519PRJ000001X");

        assertEquals("forbidden", view.repoBindings().status());
        assertEquals("auth_required", view.repoBindings().errorCode());
        assertEquals(List.of(), view.repoBindings().data());
        assertEquals("visible", view.commercialization().status());
    }

    @Test
    void sectionMapsForbiddenAndMissingErrorsWithoutHidingReason() {
        ProjectDevelopmentService developmentService = Mockito.mock(ProjectDevelopmentService.class);
        ProjectMemoryService memoryService = Mockito.mock(ProjectMemoryService.class);
        when(developmentService.listRepoBindings("MF260519PRJ000001X", "acct-founder"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden"));
        when(developmentService.getStatus("MF260519PRJ000001X", "acct-founder"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentAccount("acct-founder", "@founder", "Founder"),
                null,
                List.of()));

        var view = service(developmentService, memoryService).getDashboard("MF260519PRJ000001X");

        assertEquals("forbidden", view.repoBindings().status());
        assertEquals("403 FORBIDDEN", view.repoBindings().errorCode());
        assertEquals("missing", view.prCiStatus().status());
        assertEquals("404 NOT_FOUND", view.prCiStatus().errorCode());
    }

    private ProjectDashboardQueryService service(ProjectDevelopmentService developmentService, ProjectMemoryService memoryService) {
        PostItemWorkspaceQueryService workspaceQueryService = Mockito.mock(PostItemWorkspaceQueryService.class);
        ProjectDevelopmentService effectiveDevelopment = developmentService == null ? Mockito.mock(ProjectDevelopmentService.class) : developmentService;
        ProjectMemoryService effectiveMemory = memoryService == null ? Mockito.mock(ProjectMemoryService.class) : memoryService;
        ProjectCommercializationService commercializationService = Mockito.mock(ProjectCommercializationService.class);
        when(workspaceQueryService.getWorkspace("MF260519PRJ000001X", false))
                .thenReturn(new PostWorkspaceView(PostKind.PROJECT, null, null, null, List.of(), Map.of()));
        when(commercializationService.getCommercialization("MF260519PRJ000001X"))
                .thenReturn(new ProjectCommercializationView(
                        "MF260519PRJ000001X",
                        "project-1",
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of()));
        return new ProjectDashboardQueryService(
                workspaceQueryService,
                effectiveDevelopment,
                effectiveMemory,
                commercializationService,
                new CurrentAccountAccess());
    }
}
