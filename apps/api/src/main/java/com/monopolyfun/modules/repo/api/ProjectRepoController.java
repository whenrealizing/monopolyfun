package com.monopolyfun.modules.repo.api;

import com.monopolyfun.modules.repo.api.request.CreateRepoDeliverySessionRequest;
import com.monopolyfun.modules.repo.api.request.FinalizeRepoProofRequest;
import com.monopolyfun.modules.repo.api.request.ProvisionProjectRepoRequest;
import com.monopolyfun.modules.repo.api.request.ReportPullRequestProofRequest;
import com.monopolyfun.modules.repo.api.response.ProjectRepoProvisionResponse;
import com.monopolyfun.modules.repo.api.response.RepoDeliverySessionResponse;
import com.monopolyfun.modules.repo.service.RepoDeliveryService;
import com.monopolyfun.modules.repo.service.RepoProvisionService;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectRepoController {
    private final CurrentAccountAccess currentAccountAccess;
    private final RepoProvisionService repoProvisionService;
    private final RepoDeliveryService repoDeliveryService;

    public ProjectRepoController(
            CurrentAccountAccess currentAccountAccess,
            RepoProvisionService repoProvisionService,
            RepoDeliveryService repoDeliveryService) {
        this.currentAccountAccess = currentAccountAccess;
        this.repoProvisionService = repoProvisionService;
        this.repoDeliveryService = repoDeliveryService;
    }

    @PostMapping("/api/v1/project-repos/provision")
    public ProjectRepoProvisionResponse provisionProjectRepo(@Valid @RequestBody ProvisionProjectRepoRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        // 中文注释：前端和 OpenClaw 都走这一个建仓库入口，后续 project publish 直接复用返回的 repoUrl。
        return repoProvisionService.provisionPublicProjectRepo(actorAccountId, request.titleHint(), request.goal());
    }

    @Operation(operationId = "createRepoDeliverySession")
    @PostMapping("/api/v1/work/repo-delivery-sessions")
    public RepoDeliverySessionResponse createRepoDeliverySession(@Valid @RequestBody CreateRepoDeliverySessionRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        return repoDeliveryService.createDeliverySession(actorAccountId, request.orderNo(), request.projectNo(), request.runtime());
    }

    @Operation(operationId = "reportPullRequest")
    @PostMapping("/api/v1/work/repo-delivery-sessions/{sessionId}/report-pr")
    public RepoDeliverySessionResponse reportPullRequest(
            @PathVariable String sessionId,
            @Valid @RequestBody ReportPullRequestProofRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        return repoDeliveryService.reportPullRequest(actorAccountId, sessionId, request);
    }

    @Operation(operationId = "finalizeRepoProof")
    @PostMapping("/api/v1/work/repo-delivery-sessions/{sessionId}/finalize-proof")
    public CommandReceipt finalizeProof(
            @PathVariable String sessionId,
            @Valid @RequestBody FinalizeRepoProofRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        return repoDeliveryService.finalizeProof(actorAccountId, sessionId, request);
    }
}
