package com.monopolyfun.modules.initiative.service;

import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.initiative.api.request.ApproveProposalRequest;
import com.monopolyfun.modules.initiative.api.request.CreateMandateRequest;
import com.monopolyfun.modules.initiative.api.request.CreateProposalRequest;
import com.monopolyfun.modules.initiative.api.request.DiscoverOpportunitiesRequest;
import com.monopolyfun.modules.initiative.api.request.ExecuteProposalRequest;
import com.monopolyfun.modules.initiative.domain.AgentActionProposalEntity;
import com.monopolyfun.modules.initiative.domain.AgentActionRunEntity;
import com.monopolyfun.modules.initiative.domain.AgentMandateEntity;
import com.monopolyfun.modules.initiative.domain.AgentOpportunityEntity;
import com.monopolyfun.modules.initiative.domain.ProjectInitiativeRecommendationEntity;
import com.monopolyfun.modules.initiative.infra.InitiativeRepository;
import com.monopolyfun.modules.initiative.service.view.InitiativeProjectionView;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class InitiativeService {
    private static final String PROJECT_INITIATIVE_SOURCE = "project_initiative";
    private static final List<ProjectRoleCode> DEFAULT_PROJECT_ROLE_RECOMMENDATIONS = List.of(
            ProjectRoleCode.SYSTEM_CTO,
            ProjectRoleCode.SYSTEM_CFO);
    private final CurrentAccountAccess currentAccountAccess;
    private final AccountRepository accountRepository;
    private final InitiativeRepository initiativeRepository;
    private final WorkRepository workRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationAuthorityService organizationAuthorityService;

    public InitiativeService(
            CurrentAccountAccess currentAccountAccess,
            AccountRepository accountRepository,
            InitiativeRepository initiativeRepository,
            WorkRepository workRepository,
            ProjectRepository projectRepository,
            OrganizationAuthorityService organizationAuthorityService) {
        this.currentAccountAccess = currentAccountAccess;
        this.accountRepository = accountRepository;
        this.initiativeRepository = initiativeRepository;
        this.workRepository = workRepository;
        this.projectRepository = projectRepository;
        this.organizationAuthorityService = organizationAuthorityService;
    }

    public InitiativeProjectionView currentProjection() {
        String accountId = requireAccountId();
        List<AgentMandateEntity> mandates = initiativeRepository.findMandatesByAccountId(accountId);
        List<AgentOpportunityEntity> opportunities = mandates.stream()
                .flatMap(mandate -> initiativeRepository.findOpportunitiesByMandateId(mandate.id()).stream())
                .toList();
        List<AgentActionProposalEntity> proposals = mandates.stream()
                .flatMap(mandate -> initiativeRepository.findProposalsByMandateId(mandate.id()).stream())
                .toList();
        List<AgentActionRunEntity> runs = proposals.stream()
                .flatMap(proposal -> initiativeRepository.findActionRunsByProposalId(proposal.id()).stream())
                .toList();
        List<ProjectInitiativeRecommendationEntity> projectRecommendations =
                initiativeRepository.findProjectRecommendationsByAccountId(accountId);
        return new InitiativeProjectionView(mandates, opportunities, runs, proposals, projectRecommendations);
    }

    public List<ProjectInitiativeRecommendationEntity> generateProjectRecommendations(String projectNoOrId) {
        ProjectEntity project = requireProject(projectNoOrId);
        List<ProjectRoleEntity> roles = organizationAuthorityService.listProjectRoles(project.id());
        Set<ProjectRoleCode> occupiedRoles = roles.stream()
                .filter(role -> role.accountId() != null && !role.accountId().isBlank())
                .map(ProjectRoleEntity::roleCode)
                .collect(java.util.stream.Collectors.toSet());
        for (ProjectRoleCode roleCode : DEFAULT_PROJECT_ROLE_RECOMMENDATIONS) {
            if (!occupiedRoles.contains(roleCode)) {
                // 中文注释：项目推荐按下一步最小动作推进，避免一次性把所有角色和工程任务刷进 owner workbench。
                return List.of(createProjectRecommendation(project, "role", roleCode.code(), roleCode, titleForRole(project, roleCode), reasonForRole(roleCode), "recommend_role_candidate"));
            }
        }
        if (hasRepo(project)) {
            return List.of(createProjectRecommendation(
                    project,
                    "item",
                    "workflow_health",
                    null,
                    "为 " + project.title() + " 创建 workflow health 检查任务",
                    "项目已经绑定仓库，需要持续检查 CI、workflow 和 release 状态。",
                    "create_project_item"));
        }
        return List.of();
    }

    public ProjectInitiativeRecommendationEntity requireProjectRecommendation(String recommendationNo) {
        if (recommendationNo == null || recommendationNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recommendationNo is required");
        }
        // 中文注释：Workbench 只携带 sourceId，执行低风险推荐动作时按推荐编号回读完整项目与角色上下文。
        ProjectInitiativeRecommendationEntity recommendation = initiativeRepository.findProjectRecommendationByNo(recommendationNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project recommendation not found"));
        currentAccountAccess.requireSameAccount(recommendation.accountId());
        return recommendation;
    }

    public ProjectInitiativeRecommendationEntity closeProjectRecommendation(
            ProjectInitiativeRecommendationEntity recommendation,
            String status,
            String reason) {
        Instant now = Instant.now();
        // 中文注释：角色推荐完成后同步关闭来源 workbench 项，长驻 OpenClaw 轮询会自然转向下一条待办。
        workRepository.closeOpenItemsBySource(PROJECT_INITIATIVE_SOURCE, recommendation.recommendationNo(), reason);
        return initiativeRepository.saveProjectRecommendation(new ProjectInitiativeRecommendationEntity(
                recommendation.id(),
                recommendation.recommendationNo(),
                recommendation.accountId(),
                recommendation.projectId(),
                recommendation.projectNo(),
                recommendation.recommendationType(),
                recommendation.targetKey(),
                recommendation.targetRoleCode(),
                recommendation.title(),
                recommendation.reason(),
                recommendation.suggestedAction(),
                recommendation.input(),
                status,
                recommendation.workItemId(),
                recommendation.createdAt(),
                now));
    }

    public AgentMandateEntity createMandate(CreateMandateRequest request) {
        validateMandate(request);
        String accountId = requireAccountId();
        Instant now = Instant.now();
        return initiativeRepository.saveMandate(new AgentMandateEntity(
                "md-" + UUID.randomUUID(),
                "md-" + now.toEpochMilli(),
                accountId,
                request.goal().trim(),
                request.scope(),
                request.budget(),
                request.riskPolicy(),
                "active",
                now,
                now));
    }

    public CommandReceipt createProposal(CreateProposalRequest request) {
        validateProposal(request);
        AgentMandateEntity mandate = initiativeRepository.findMandateByNo(request.mandateNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mandate not found"));
        currentAccountAccess.requireSameAccount(mandate.accountId());
        Instant now = Instant.now();
        String risk = normalizeRisk(request.risk());
        AgentOpportunityEntity opportunity = opportunity(request.opportunityNo(), mandate.id());
        boolean autoApproved = "low".equals(risk) && autoApproved(mandate, request.actionId());
        AgentActionProposalEntity proposal = initiativeRepository.saveProposal(new AgentActionProposalEntity(
                "ap-" + UUID.randomUUID(),
                "ap-" + now.toEpochMilli(),
                opportunity == null ? null : opportunity.id(),
                mandate.id(),
                request.actionId(),
                request.reason().trim(),
                risk,
                request.input(),
                request.expectedOutcome().trim(),
                autoApproved ? "approved" : "pending",
                now,
                now));
        if (opportunity != null) {
            initiativeRepository.saveOpportunity(withOpportunityStatus(opportunity, "proposed", now));
        }
        Map<String, Object> payload = autoApproved ? executeProposal(mandate, withProposalStatus(proposal, "approved", now)) : Map.of("proposalNo", proposal.proposalNo());
        return new CommandReceipt("cmd-" + UUID.randomUUID(), "agent_action_proposal", proposal.proposalNo(), autoApproved ? "executed" : "pending", payload, mandate.accountId(), "initiative-" + UUID.randomUUID(), null, Instant.now());
    }

    public CommandReceipt discoverOpportunity(DiscoverOpportunitiesRequest request) {
        AgentMandateEntity mandate = initiativeRepository.findMandateByNo(request.mandateNo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mandate not found"));
        currentAccountAccess.requireSameAccount(mandate.accountId());
        RequestPayloadLimits.requireTextLength("targetType", request.targetType(), 120);
        RequestPayloadLimits.requireTextLength("targetId", request.targetId(), 160);
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        RequestPayloadLimits.requireTextLength("suggestedAction", request.suggestedAction(), 120);
        RequestPayloadLimits.requireMapShape("signal", request.signal(), 6, 80, 2000);
        Instant now = Instant.now();
        String action = blank(request.suggestedAction()) ? "create_work_item" : request.suggestedAction().trim();
        AgentOpportunityEntity opportunity = initiativeRepository.saveOpportunity(new AgentOpportunityEntity(
                "ao-" + UUID.randomUUID(),
                "ao-" + now.toEpochMilli(),
                mandate.id(),
                "agent_signal",
                blank(request.reason()) ? mandate.goal() : request.reason().trim(),
                blank(request.targetType()) ? "mandate" : request.targetType().trim(),
                blank(request.targetId()) ? mandate.mandateNo() : request.targetId().trim(),
                action,
                "open",
                now,
                now));
        return new CommandReceipt("cmd-" + UUID.randomUUID(), "agent_opportunity", opportunity.opportunityNo(), opportunity.status(), Map.of("opportunityNo", opportunity.opportunityNo(), "suggestedAction", action), mandate.accountId(), "initiative-" + UUID.randomUUID(), null, now);
    }

    public CommandReceipt approveProposal(String proposalNo, ApproveProposalRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        AgentActionProposalEntity proposal = initiativeRepository.findProposalByNo(proposalNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        AgentMandateEntity mandate = mandateById(proposal.mandateId());
        currentAccountAccess.requireSameAccount(mandate.accountId());
        if (!"pending".equals(proposal.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending proposals can be approved");
        }
        AgentActionProposalEntity approved = initiativeRepository.saveProposal(withProposalStatus(proposal, "approved", Instant.now()));
        return new CommandReceipt("cmd-" + UUID.randomUUID(), "agent_action_proposal", approved.proposalNo(), approved.status(), Map.of("proposalNo", approved.proposalNo(), "note", request.note() == null ? "" : request.note()), mandate.accountId(), "initiative-" + UUID.randomUUID(), null, Instant.now());
    }

    public CommandReceipt executeProposal(String proposalNo, ExecuteProposalRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        AgentActionProposalEntity proposal = initiativeRepository.findProposalByNo(proposalNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found"));
        AgentMandateEntity mandate = mandateById(proposal.mandateId());
        currentAccountAccess.requireSameAccount(mandate.accountId());
        if (!"approved".equals(proposal.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal must be approved before execution");
        }
        Map<String, Object> payload = executeProposal(mandate, proposal);
        return new CommandReceipt("cmd-" + UUID.randomUUID(), "agent_action_run", proposal.proposalNo(), "executed", payload, mandate.accountId(), "initiative-" + UUID.randomUUID(), null, Instant.now());
    }

    private Map<String, Object> executeProposal(AgentMandateEntity mandate, AgentActionProposalEntity proposal) {
        if (!List.of("create_work_item", "request_help", "summarize_project_status").contains(proposal.actionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal action is not executable: " + proposal.actionId());
        }
        Instant now = Instant.now();
        String title = stringInput(proposal.input(), "title", proposal.reason());
        WorkItemEntity item = new WorkItemEntity(
                "wi-" + UUID.randomUUID(),
                "wi-initiative-" + now.toEpochMilli(),
                "initiative",
                proposal.proposalNo(),
                mandate.accountId(),
                title,
                proposal.expectedOutcome(),
                stringListInput(proposal.input(), "acceptanceCriteria"),
                List.of("mandate:" + mandate.mandateNo(), "proposal:" + proposal.proposalNo()),
                Map.of("summary", "string", "evidenceRefs", "string[]"),
                "worker",
                null,
                "attention",
                "ready",
                null,
                now,
                now,
                now);
        workRepository.upsertItem(item);
        initiativeRepository.saveActionRun(new AgentActionRunEntity(
                "ar-" + UUID.randomUUID(),
                "ar-" + now.toEpochMilli(),
                proposal.id(),
                "succeeded",
                item.id(),
                Map.of("workItemNo", item.itemNo()),
                null,
                now,
                now));
        initiativeRepository.saveProposal(withProposalStatus(proposal, "executed", now));
        // 中文注释：Proposal 已绑定 opportunity_id，执行落库时按绑定关系推进机会状态，避免依赖输入快照中的冗余编号。
        initiativeRepository.findOpportunityById(proposal.opportunityId())
                .ifPresent(opportunity -> initiativeRepository.saveOpportunity(withOpportunityStatus(opportunity, "executed", now)));
        return Map.of("proposalNo", proposal.proposalNo(), "workItemNo", item.itemNo());
    }

    private ProjectInitiativeRecommendationEntity createProjectRecommendation(
            ProjectEntity project,
            String type,
            String targetKey,
            ProjectRoleCode roleCode,
            String title,
            String reason,
            String suggestedAction) {
        Instant now = Instant.now();
        String normalizedType = normalizeKey(type);
        String normalizedTarget = normalizeKey(targetKey);
        String recommendationNo = "pir-" + project.projectNo().toLowerCase(Locale.ROOT) + "-" + normalizedType + "-" + normalizedTarget;
        Map<String, Object> input = projectRecommendationInput(project, normalizedType, normalizedTarget, roleCode, suggestedAction);
        ProjectInitiativeRecommendationEntity recommendation = new ProjectInitiativeRecommendationEntity(
                "pir-" + UUID.randomUUID(),
                recommendationNo,
                project.ownerAccountId(),
                project.id(),
                project.projectNo(),
                normalizedType,
                normalizedTarget,
                roleCode == null ? null : roleCode.code(),
                title,
                reason,
                suggestedAction,
                input,
                "open",
                null,
                now,
                now);
        ProjectInitiativeRecommendationEntity saved = initiativeRepository.saveProjectRecommendation(recommendation);
        WorkItemEntity workItem = projectRecommendationWorkItem(saved, input, now);
        workRepository.upsertItem(workItem);
        return initiativeRepository.saveProjectRecommendation(new ProjectInitiativeRecommendationEntity(
                saved.id(),
                saved.recommendationNo(),
                saved.accountId(),
                saved.projectId(),
                saved.projectNo(),
                saved.recommendationType(),
                saved.targetKey(),
                saved.targetRoleCode(),
                saved.title(),
                saved.reason(),
                saved.suggestedAction(),
                saved.input(),
                saved.status(),
                workItem.id(),
                saved.createdAt(),
                now));
    }

    private WorkItemEntity projectRecommendationWorkItem(ProjectInitiativeRecommendationEntity recommendation, Map<String, Object> input, Instant now) {
        return new WorkItemEntity(
                "wi-" + recommendation.recommendationNo(),
                "wi-" + recommendation.recommendationNo(),
                PROJECT_INITIATIVE_SOURCE,
                recommendation.recommendationNo(),
                recommendation.accountId(),
                recommendation.title(),
                recommendation.reason(),
                List.of("确认推荐是否适合当前 project", "执行后回到 project 或 workbench 验证状态"),
                List.of("project:" + recommendation.projectNo(), "project_initiative:" + recommendation.recommendationNo()),
                Map.of(
                        "action", "project_initiative_recommendation",
                        "projectNo", recommendation.projectNo(),
                        "recommendationType", recommendation.recommendationType(),
                        "targetRoleCode", recommendation.targetRoleCode() == null ? "" : recommendation.targetRoleCode(),
                        "suggestedAction", recommendation.suggestedAction(),
                        "input", input),
                recommendation.targetRoleCode() == null ? "project" : recommendation.targetRoleCode(),
                null,
                "attention",
                "ready",
                null,
                now,
                now,
                now);
    }

    private Map<String, Object> projectRecommendationInput(ProjectEntity project, String type, String targetKey, ProjectRoleCode roleCode, String suggestedAction) {
        java.util.LinkedHashMap<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("projectId", project.id());
        input.put("projectNo", project.projectNo());
        input.put("projectTitle", project.title());
        input.put("recommendationType", type);
        input.put("targetKey", targetKey);
        input.put("suggestedAction", suggestedAction);
        if (roleCode != null) {
            input.put("roleCode", roleCode.code());
        }
        return Map.copyOf(input);
    }

    private ProjectEntity requireProject(String projectNoOrId) {
        if (projectNoOrId == null || projectNoOrId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectNo is required");
        }
        return projectRepository.findByProjectNo(projectNoOrId)
                .or(() -> projectRepository.findById(projectNoOrId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private boolean hasRepo(ProjectEntity project) {
        Object links = project.metadata() == null ? null : project.metadata().get("referenceLinks");
        return links instanceof List<?> list && !list.isEmpty();
    }

    private String titleForRole(ProjectEntity project, ProjectRoleCode roleCode) {
        return "为 " + project.title() + " 推荐 " + roleCode.code() + " 候选人";
    }

    private String reasonForRole(ProjectRoleCode roleCode) {
        return switch (roleCode) {
            case SYSTEM_CTO -> "Root Project 需要技术维护处理 PR review、代码交付质量和 runtime 相关任务。";
            case SYSTEM_CFO -> "Root Project 需要结算维护处理 payment 和 shares 发放审批。";
            case SYSTEM_CEO -> "Root Project 协议维护负责规则、权限和最终协调。";
        };
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project recommendation key is required");
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private void validateMandate(CreateMandateRequest request) {
        RequestPayloadLimits.requireTextLength("goal", request.goal(), 500);
        RequestPayloadLimits.requireStringList("scope", request.scope(), 20, 120);
        RequestPayloadLimits.requireMapShape("budget", request.budget(), 3, 20, 500);
        RequestPayloadLimits.requireMapShape("riskPolicy", request.riskPolicy(), 4, 40, 500);
    }

    private void validateProposal(CreateProposalRequest request) {
        RequestPayloadLimits.requireTextLength("actionId", request.actionId(), 120);
        RequestPayloadLimits.requireTextLength("reason", request.reason(), 500);
        RequestPayloadLimits.requireTextLength("expectedOutcome", request.expectedOutcome(), 500);
        RequestPayloadLimits.requireMapShape("input", request.input(), 4, 60, 1000);
    }

    private boolean autoApproved(AgentMandateEntity mandate, String actionId) {
        Object value = mandate.riskPolicy().get("autoApprove");
        return value instanceof List<?> list && list.stream().anyMatch(actionId::equals);
    }

    private AgentOpportunityEntity opportunity(String opportunityNo, String mandateId) {
        if (blank(opportunityNo)) {
            return null;
        }
        AgentOpportunityEntity opportunity = initiativeRepository.findOpportunityByNo(opportunityNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunity not found"));
        if (!mandateId.equals(opportunity.mandateId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Opportunity belongs to another mandate");
        }
        return opportunity;
    }

    private AgentMandateEntity mandateById(String mandateId) {
        return initiativeRepository.findMandatesByAccountId(requireAccountId()).stream()
                .filter(mandate -> mandate.id().equals(mandateId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mandate not found"));
    }

    private AgentActionProposalEntity withProposalStatus(AgentActionProposalEntity proposal, String status, Instant now) {
        return new AgentActionProposalEntity(
                proposal.id(),
                proposal.proposalNo(),
                proposal.opportunityId(),
                proposal.mandateId(),
                proposal.actionId(),
                proposal.reason(),
                proposal.risk(),
                proposal.input(),
                proposal.expectedOutcome(),
                status,
                proposal.createdAt(),
                now);
    }

    private AgentOpportunityEntity withOpportunityStatus(AgentOpportunityEntity opportunity, String status, Instant now) {
        return new AgentOpportunityEntity(
                opportunity.id(),
                opportunity.opportunityNo(),
                opportunity.mandateId(),
                opportunity.type(),
                opportunity.reason(),
                opportunity.targetType(),
                opportunity.targetId(),
                opportunity.suggestedAction(),
                status,
                opportunity.createdAt(),
                now);
    }

    private String requireAccountId() {
        String accountId = currentAccountAccess.requireAccountId();
        accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        return accountId;
    }

    private String normalizeRisk(String value) {
        String risk = value == null ? "" : value.trim().toLowerCase();
        if (!List.of("low", "high").contains(risk)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "risk must be low or high");
        }
        return risk;
    }

    private String stringInput(Map<String, Object> input, String key, String defaultValue) {
        Object value = input == null ? null : input.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : defaultValue;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> stringListInput(Map<String, Object> input, String key) {
        Object value = input == null ? null : input.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
