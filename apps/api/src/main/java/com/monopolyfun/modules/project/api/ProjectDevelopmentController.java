package com.monopolyfun.modules.project.api;

import com.monopolyfun.modules.project.api.request.ProjectPrCiEventRequest;
import com.monopolyfun.modules.project.api.request.ProjectRepoBindingRequest;
import com.monopolyfun.modules.project.api.request.ReviewProjectResultCandidateRequest;
import com.monopolyfun.modules.project.api.request.SkipProjectCandidateWindowRequest;
import com.monopolyfun.modules.project.api.request.SupportProjectResultCandidateRequest;
import com.monopolyfun.modules.project.service.ProjectDevelopmentService;
import com.monopolyfun.modules.project.service.view.ProjectPrCiStatusView;
import com.monopolyfun.modules.project.service.view.ProjectRepoBindingView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidatePageView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateView;
import com.monopolyfun.modules.project.service.view.ProjectResultCandidateWindowView;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectNo}/development")
public class ProjectDevelopmentController {
    private final ProjectDevelopmentService developmentService;
    private final CurrentAccountAccess currentAccountAccess;

    public ProjectDevelopmentController(ProjectDevelopmentService developmentService, CurrentAccountAccess currentAccountAccess) {
        this.developmentService = developmentService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping("/repo-bindings")
    public List<ProjectRepoBindingView> listRepoBindings(@PathVariable String projectNo) {
        return developmentService.listRepoBindings(projectNo, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/repo-bindings")
    public ProjectRepoBindingView bindRepo(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectRepoBindingRequest request) {
        return developmentService.bindRepo(projectNo, request, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/pr-ci")
    public ProjectPrCiStatusView getPrCiStatus(@PathVariable String projectNo) {
        return developmentService.getStatus(projectNo, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/candidates")
    public ProjectResultCandidatePageView listResultCandidates(
            @PathVariable String projectNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return developmentService.listResultCandidatePage(projectNo, status, limit, cursor, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/candidates/next")
    public ProjectResultCandidateWindowView nextResultCandidateWindow(
            @PathVariable String projectNo,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String after) {
        return developmentService.nextCandidateWindow(projectNo, limit, after, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/candidates/window-skip")
    public ProjectResultCandidateWindowView skipResultCandidateWindow(
            @PathVariable String projectNo,
            @Valid @RequestBody SkipProjectCandidateWindowRequest request) {
        return developmentService.skipCandidateWindow(projectNo, request, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/candidates/{taskId}/support")
    public ProjectResultCandidateView supportResultCandidate(
            @PathVariable String projectNo,
            @PathVariable String taskId,
            @Valid @RequestBody SupportProjectResultCandidateRequest request) {
        return developmentService.supportCandidate(projectNo, taskId, request, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/candidates/{taskId}/final-review")
    public ProjectResultCandidateView finalReviewResultCandidate(
            @PathVariable String projectNo,
            @PathVariable String taskId,
            @Valid @RequestBody ReviewProjectResultCandidateRequest request) {
        return developmentService.finalReviewCandidate(projectNo, taskId, request, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/pr-ci/events")
    public ProjectPrCiStatusView ingestPrCiEvent(
            @PathVariable String projectNo,
            @Valid @RequestBody ProjectPrCiEventRequest request) {
        return developmentService.ingestEvent(projectNo, request, currentAccountAccess.requireAccountId());
    }
}
