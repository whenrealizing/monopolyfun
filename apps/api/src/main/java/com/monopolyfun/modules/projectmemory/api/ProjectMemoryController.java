package com.monopolyfun.modules.projectmemory.api;

import com.monopolyfun.modules.projectmemory.api.request.ProjectMemoryEntryRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemoryRepoSyncRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemoryReviewRequest;
import com.monopolyfun.modules.projectmemory.api.request.ProjectMemorySourceRequest;
import com.monopolyfun.modules.projectmemory.service.ProjectMemoryService;
import com.monopolyfun.modules.projectmemory.service.view.ProjectAgentContextView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryEntryView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryOverviewView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemoryRootView;
import com.monopolyfun.modules.projectmemory.service.view.ProjectMemorySourceView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects/{projectNo}")
public class ProjectMemoryController {
    private final ProjectMemoryService memoryService;
    private final CurrentAccountAccess currentAccountAccess;

    public ProjectMemoryController(ProjectMemoryService memoryService, CurrentAccountAccess currentAccountAccess) {
        this.memoryService = memoryService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping("/memory")
    public ProjectMemoryOverviewView overview(@PathVariable String projectNo) {
        return memoryService.overview(projectNo, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/memory/repo-sync")
    public ProjectMemoryRootView syncRepo(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectMemoryRepoSyncRequest request) {
        return memoryService.syncRepo(projectNo, request, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/memory/sources")
    public List<ProjectMemorySourceView> sources(@PathVariable String projectNo) {
        return memoryService.overview(projectNo, currentAccountAccess.requireAccountId()).sources();
    }

    @PostMapping("/memory/sources")
    public ProjectMemorySourceView createSource(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectMemorySourceRequest request) {
        return memoryService.createSource(projectNo, request, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/memory/entries")
    public List<ProjectMemoryEntryView> entries(@PathVariable String projectNo) {
        return memoryService.overview(projectNo, currentAccountAccess.requireAccountId()).entries();
    }

    @PostMapping("/memory/entries")
    public ProjectMemoryEntryView createEntry(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectMemoryEntryRequest request) {
        return memoryService.createEntry(projectNo, request, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/memory/entries/{memoryId}/approve")
    public ProjectMemoryEntryView approveEntry(
            @PathVariable String projectNo,
            @PathVariable String memoryId,
            @RequestBody(required = false) ProjectMemoryReviewRequest request) {
        return memoryService.approveEntry(projectNo, memoryId, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/memory/entries/{memoryId}/supersede")
    public ProjectMemoryEntryView supersedeEntry(
            @PathVariable String projectNo,
            @PathVariable String memoryId,
            @RequestBody(required = false) ProjectMemoryReviewRequest request) {
        return memoryService.supersedeEntry(projectNo, memoryId, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/agent-context")
    public ProjectAgentContextView agentContext(@PathVariable String projectNo) {
        return memoryService.agentContext(projectNo, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/memory/source-contract")
    public Map<String, Object> sourceContract(@PathVariable String projectNo) {
        return memoryService.sourceContract(projectNo, currentAccountAccess.requireAccountId());
    }
}
