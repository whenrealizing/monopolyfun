package com.monopolyfun.modules.project.api;

import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateFeedbackRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateLaunchRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateProofRequestRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.CreateTaskRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.FeedbackView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.LaunchView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofRequestView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ProofView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ResolveFeedbackRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ReviewProofRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.ReviewQueueItemView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.RewardView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.SettleLaunchRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.SubmitProofRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.TaskView;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolDtos.UpdateLaunchRequest;
import com.monopolyfun.modules.project.protocol.ProjectValidationProtocolService;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectNo}")
public class ProjectValidationProtocolController {
    private final ProjectValidationProtocolService protocolService;
    private final CurrentAccountAccess currentAccountAccess;

    public ProjectValidationProtocolController(
            ProjectValidationProtocolService protocolService,
            CurrentAccountAccess currentAccountAccess) {
        this.protocolService = protocolService;
        this.currentAccountAccess = currentAccountAccess;
    }

    @GetMapping("/launches")
    public List<LaunchView> listLaunches(@PathVariable String projectNo) {
        return protocolService.listLaunches(projectNo);
    }

    @PostMapping("/launches")
    public LaunchView createLaunch(@PathVariable String projectNo, @Valid @RequestBody CreateLaunchRequest request) {
        return protocolService.createLaunch(projectNo, currentAccountAccess.requireAccountId(), request);
    }

    @PatchMapping("/launches/{launchId}")
    public LaunchView updateLaunch(
            @PathVariable String projectNo,
            @PathVariable String launchId,
            @Valid @RequestBody UpdateLaunchRequest request) {
        return protocolService.updateLaunch(projectNo, launchId, currentAccountAccess.requireAccountId(), request);
    }

    @PostMapping("/launches/{launchId}/publish")
    public LaunchView publishLaunch(@PathVariable String projectNo, @PathVariable String launchId) {
        return protocolService.publishLaunch(projectNo, launchId, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/launches/{launchId}/settle")
    public LaunchView settleLaunch(
            @PathVariable String projectNo,
            @PathVariable String launchId,
            @Valid @RequestBody SettleLaunchRequest request) {
        return protocolService.settleLaunch(projectNo, launchId, currentAccountAccess.requireAccountId(), request);
    }

    @GetMapping("/launches/{launchId}/proof-requests")
    public List<ProofRequestView> listProofRequests(@PathVariable String projectNo, @PathVariable String launchId) {
        return protocolService.listProofRequests(projectNo, launchId);
    }

    @PostMapping("/launches/{launchId}/proof-requests")
    public ProofRequestView createProofRequest(
            @PathVariable String projectNo,
            @PathVariable String launchId,
            @Valid @RequestBody CreateProofRequestRequest request) {
        return protocolService.createProofRequest(projectNo, launchId, currentAccountAccess.requireAccountId(), request);
    }

    @GetMapping("/launches/{launchId}/tasks")
    public List<TaskView> listLaunchTasks(@PathVariable String projectNo, @PathVariable String launchId) {
        return protocolService.listTasks(projectNo, launchId);
    }

    @PostMapping("/launches/{launchId}/tasks")
    public TaskView createTask(
            @PathVariable String projectNo,
            @PathVariable String launchId,
            @Valid @RequestBody CreateTaskRequest request) {
        return protocolService.createTask(projectNo, launchId, currentAccountAccess.requireAccountId(), request);
    }

    @PostMapping("/tasks/{taskId}/claim")
    public TaskView claimTask(@PathVariable String projectNo, @PathVariable String taskId) {
        return protocolService.claimTask(projectNo, taskId, currentAccountAccess.requireAccountId());
    }

    @PostMapping("/tasks/{taskId}/proof")
    public ProofView submitProof(
            @PathVariable String projectNo,
            @PathVariable String taskId,
            @Valid @RequestBody SubmitProofRequest request) {
        return protocolService.submitProof(projectNo, taskId, currentAccountAccess.requireAccountId(), request);
    }

    @GetMapping("/launches/{launchId}/proofs")
    public List<ProofView> listLaunchProofs(@PathVariable String projectNo, @PathVariable String launchId) {
        return protocolService.listProofs(projectNo, launchId);
    }

    @PostMapping("/proofs/{proofId}/review")
    public ProofView reviewProof(
            @PathVariable String projectNo,
            @PathVariable String proofId,
            @Valid @RequestBody ReviewProofRequest request) {
        return protocolService.reviewProof(projectNo, proofId, currentAccountAccess.requireAccountId(), request);
    }

    @GetMapping("/review-queue")
    public List<ReviewQueueItemView> listReviewQueue(@PathVariable String projectNo) {
        return protocolService.listReviewQueue(projectNo, currentAccountAccess.requireAccountId());
    }

    @GetMapping("/validation-feedback")
    public List<FeedbackView> listFeedback(@PathVariable String projectNo) {
        return protocolService.listFeedback(projectNo);
    }

    @PostMapping("/validation-feedback")
    public FeedbackView createFeedback(
            @PathVariable String projectNo,
            @Valid @RequestBody CreateFeedbackRequest request) {
        return protocolService.createFeedback(projectNo, currentAccountAccess.requireAccountId(), request);
    }

    @PostMapping("/validation-feedback/{feedbackId}/resolve")
    public FeedbackView resolveFeedback(
            @PathVariable String projectNo,
            @PathVariable String feedbackId,
            @Valid @RequestBody ResolveFeedbackRequest request) {
        return protocolService.resolveFeedback(projectNo, feedbackId, currentAccountAccess.requireAccountId(), request);
    }

    @GetMapping("/rewards")
    public List<RewardView> listRewards(@PathVariable String projectNo) {
        return protocolService.listRewards(projectNo);
    }
}
