package com.monopolyfun.modules.initiative.infra;

import com.monopolyfun.modules.initiative.domain.AgentActionProposalEntity;
import com.monopolyfun.modules.initiative.domain.AgentActionRunEntity;
import com.monopolyfun.modules.initiative.domain.AgentMandateEntity;
import com.monopolyfun.modules.initiative.domain.AgentOpportunityEntity;
import com.monopolyfun.modules.initiative.domain.ProjectInitiativeRecommendationEntity;

import java.util.List;
import java.util.Optional;

public interface InitiativeRepository {
    AgentMandateEntity saveMandate(AgentMandateEntity mandate);

    List<AgentMandateEntity> findMandatesByAccountId(String accountId);

    Optional<AgentMandateEntity> findMandateByNo(String mandateNo);

    AgentOpportunityEntity saveOpportunity(AgentOpportunityEntity opportunity);

    List<AgentOpportunityEntity> findOpportunitiesByMandateId(String mandateId);

    Optional<AgentOpportunityEntity> findOpportunityById(String opportunityId);

    Optional<AgentOpportunityEntity> findOpportunityByNo(String opportunityNo);

    AgentActionProposalEntity saveProposal(AgentActionProposalEntity proposal);

    List<AgentActionProposalEntity> findProposalsByMandateId(String mandateId);

    Optional<AgentActionProposalEntity> findProposalByNo(String proposalNo);

    AgentActionRunEntity saveActionRun(AgentActionRunEntity run);

    List<AgentActionRunEntity> findActionRunsByProposalId(String proposalId);

    ProjectInitiativeRecommendationEntity saveProjectRecommendation(ProjectInitiativeRecommendationEntity recommendation);

    List<ProjectInitiativeRecommendationEntity> findProjectRecommendationsByProjectId(String projectId);

    List<ProjectInitiativeRecommendationEntity> findProjectRecommendationsByAccountId(String accountId);

    Optional<ProjectInitiativeRecommendationEntity> findProjectRecommendationByNo(String recommendationNo);
}
