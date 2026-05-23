package com.monopolyfun.modules.initiative.api;

import com.monopolyfun.modules.initiative.api.request.ApproveProposalRequest;
import com.monopolyfun.modules.initiative.api.request.CreateMandateRequest;
import com.monopolyfun.modules.initiative.api.request.CreateProposalRequest;
import com.monopolyfun.modules.initiative.api.request.DiscoverOpportunitiesRequest;
import com.monopolyfun.modules.initiative.api.request.ExecuteProposalRequest;
import com.monopolyfun.modules.initiative.domain.AgentMandateEntity;
import com.monopolyfun.modules.initiative.domain.ProjectInitiativeRecommendationEntity;
import com.monopolyfun.modules.initiative.service.InitiativeService;
import com.monopolyfun.modules.initiative.service.view.InitiativeProjectionView;
import com.monopolyfun.shared.command.CommandReceipt;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/initiative")
public class InitiativeController {
    private final InitiativeService initiativeService;

    public InitiativeController(InitiativeService initiativeService) {
        this.initiativeService = initiativeService;
    }

    @GetMapping
    public InitiativeProjectionView getInitiative() {
        return initiativeService.currentProjection();
    }

    @PostMapping("/mandates")
    public AgentMandateEntity createMandate(@Valid @RequestBody CreateMandateRequest request) {
        return initiativeService.createMandate(request);
    }

    @PostMapping("/proposals")
    public CommandReceipt createProposal(@Valid @RequestBody CreateProposalRequest request) {
        return initiativeService.createProposal(request);
    }

    @PostMapping("/opportunities")
    public CommandReceipt discoverOpportunity(@Valid @RequestBody DiscoverOpportunitiesRequest request) {
        return initiativeService.discoverOpportunity(request);
    }

    @PostMapping("/proposals/{proposalNo}/approve")
    public CommandReceipt approveProposal(@PathVariable String proposalNo, @Valid @RequestBody ApproveProposalRequest request) {
        return initiativeService.approveProposal(proposalNo, request);
    }

    @PostMapping("/proposals/{proposalNo}/execute")
    public CommandReceipt executeProposal(@PathVariable String proposalNo, @Valid @RequestBody ExecuteProposalRequest request) {
        return initiativeService.executeProposal(proposalNo, request);
    }

    @PostMapping("/projects/{projectNo}/recommendations/generate")
    public List<ProjectInitiativeRecommendationEntity> generateProjectRecommendations(@PathVariable String projectNo) {
        return initiativeService.generateProjectRecommendations(projectNo);
    }
}
