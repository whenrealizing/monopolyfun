package com.monopolyfun.modules.workthread.api;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.workthread.api.request.ClaimDistributionRequest;
import com.monopolyfun.modules.workthread.api.request.ClaimWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.CreateDistributionBatchRequest;
import com.monopolyfun.modules.workthread.api.request.CreateWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.ReviewWorkThreadRequest;
import com.monopolyfun.modules.workthread.api.request.SubmitWorkThreadResultRequest;
import com.monopolyfun.modules.workthread.api.request.UpsertProjectRevenueAddressRequest;
import com.monopolyfun.modules.workthread.service.RevenueDistributionService;
import com.monopolyfun.modules.workthread.service.WorkThreadService;
import com.monopolyfun.modules.workthread.service.view.ContributionRewardView;
import com.monopolyfun.modules.workthread.service.view.DistributionBatchView;
import com.monopolyfun.modules.workthread.service.view.DistributionClaimView;
import com.monopolyfun.modules.workthread.service.view.ProjectRevenueAddressView;
import com.monopolyfun.modules.workthread.service.view.WorkResultView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadOverviewView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadPacketView;
import com.monopolyfun.modules.workthread.service.view.WorkThreadView;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WorkThreadController {
    private final WorkThreadService workThreadService;
    private final RevenueDistributionService revenueDistributionService;
    private final CurrentAccountAccess currentAccountAccess;

    public WorkThreadController(
            WorkThreadService workThreadService,
            RevenueDistributionService revenueDistributionService,
            CurrentAccountAccess currentAccountAccess) {
        this.workThreadService = workThreadService;
        this.revenueDistributionService = revenueDistributionService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @PostMapping("/projects/{projectId}/work-threads")
    public WorkThreadView createWorkThread(@PathVariable String projectId, @Valid @RequestBody CreateWorkThreadRequest request) {
        return workThreadService.create(projectId, request);
    }

    @GetMapping("/projects/{projectId}/work-threads")
    public List<WorkThreadView> listWorkThreads(@PathVariable String projectId) {
        return workThreadService.list(projectId);
    }

    @GetMapping("/projects/{projectId}/workroom")
    public WorkThreadOverviewView getWorkroom(@PathVariable String projectId) {
        return workThreadService.overview(projectId);
    }

    @GetMapping("/work-threads/{threadId}/packet")
    public WorkThreadPacketView getPacket(@PathVariable String threadId) {
        return workThreadService.packet(threadId);
    }

    @PostMapping("/work-threads/{threadId}/claim")
    public CommandReceipt claimWorkThread(@PathVariable String threadId, @Valid @RequestBody ClaimWorkThreadRequest request) {
        return workThreadService.claim(threadId, request);
    }

    @PostMapping("/work-threads/{threadId}/result")
    public WorkResultView submitResult(@PathVariable String threadId, @Valid @RequestBody SubmitWorkThreadResultRequest request) {
        return workThreadService.submitResult(threadId, request);
    }

    @PostMapping("/work-threads/{threadId}/review")
    public CommandReceipt review(@PathVariable String threadId, @Valid @RequestBody ReviewWorkThreadRequest request) {
        return workThreadService.review(threadId, request);
    }

    @PostMapping("/projects/{projectId}/revenue-address")
    public ProjectRevenueAddressView upsertRevenueAddress(@PathVariable String projectId, @Valid @RequestBody UpsertProjectRevenueAddressRequest request) {
        ProjectEntity project = workThreadService.requireProject(projectId);
        return revenueDistributionService.upsertAddress(project, request);
    }

    @PostMapping("/projects/{projectId}/distributions")
    public DistributionBatchView createDistribution(@PathVariable String projectId, @Valid @RequestBody CreateDistributionBatchRequest request) {
        ProjectEntity project = workThreadService.requireProject(projectId);
        return revenueDistributionService.createBatch(project, request);
    }

    @GetMapping("/projects/{projectId}/rewards/me")
    public ContributionRewardView myRewards(@PathVariable String projectId) {
        ProjectEntity project = workThreadService.requireProject(projectId);
        return revenueDistributionService.rewards(project, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/projects/{projectId}/distributions/{period}/claim")
    public DistributionClaimView claimDistribution(
            @PathVariable String projectId,
            @PathVariable String period,
            @Valid @RequestBody ClaimDistributionRequest request) {
        ProjectEntity project = workThreadService.requireProject(projectId);
        return revenueDistributionService.claim(project, period, request);
    }
}
