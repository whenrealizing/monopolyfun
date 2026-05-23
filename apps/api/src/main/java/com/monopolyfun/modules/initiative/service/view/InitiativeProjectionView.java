package com.monopolyfun.modules.initiative.service.view;

import com.monopolyfun.modules.initiative.domain.AgentActionProposalEntity;
import com.monopolyfun.modules.initiative.domain.AgentActionRunEntity;
import com.monopolyfun.modules.initiative.domain.AgentMandateEntity;
import com.monopolyfun.modules.initiative.domain.AgentOpportunityEntity;
import com.monopolyfun.modules.initiative.domain.ProjectInitiativeRecommendationEntity;

import java.util.List;

public record InitiativeProjectionView(
        List<AgentMandateEntity> mandates,
        List<AgentOpportunityEntity> opportunities,
        List<AgentActionRunEntity> actionRuns,
        List<AgentActionProposalEntity> proposals,
        List<ProjectInitiativeRecommendationEntity> projectRecommendations
) {
}
