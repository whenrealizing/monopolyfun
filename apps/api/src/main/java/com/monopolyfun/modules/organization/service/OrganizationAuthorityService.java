package com.monopolyfun.modules.organization.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.post.domain.PostKind;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.OrganizationEventEntity;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import com.monopolyfun.modules.project.infra.MarketRepository;
import com.monopolyfun.modules.project.infra.OrganizationEventRepository;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class OrganizationAuthorityService {
    private static final String AUTHORITY_ACTION = "use";
    private static final String SYSTEM_DOMAIN = "system";
    private static final String CASBIN_MODEL = """
            [request_definition]
            r = sub, dom, obj, act
            
            [policy_definition]
            p = sub, dom, obj, act
            
            [role_definition]
            g = _, _, _
            
            [policy_effect]
            e = some(where (p.eft == allow))
            
            [matchers]
            m = g(r.sub, p.sub, r.dom) && r.dom == p.dom && r.obj == p.obj && r.act == p.act
            """;

    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final MarketRepository marketRepository;
    private final ProjectRoleRepository projectRoleRepository;
    private final OrganizationEventRepository organizationEventRepository;
    private final RootProjectService rootProjectService;
    private final WorkRepository workRepository;

    public OrganizationAuthorityService(
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            MarketRepository marketRepository,
            ProjectRoleRepository projectRoleRepository,
            OrganizationEventRepository organizationEventRepository,
            RootProjectService rootProjectService,
            WorkRepository workRepository) {
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.marketRepository = marketRepository;
        this.projectRoleRepository = projectRoleRepository;
        this.organizationEventRepository = organizationEventRepository;
        this.rootProjectService = rootProjectService;
        this.workRepository = workRepository;
    }

    public static Set<ProjectCapability> capabilitiesForRole(ProjectRoleCode roleCode) {
        return ProjectRoleCapabilityPolicy.capabilitiesByRole().getOrDefault(roleCode, Set.of());
    }

    public void initializeProjectRoles(String projectId, String ownerAccountId, Instant now) {
        ProjectEntity project = ensureProject(projectId);
        if (!rootProject(project)) {
            return;
        }
        // 中文注释：Root Project 保留维护席位，普通 Project 通过协议事实开放参与。
        projectRoleRepository.initializeProjectRoles(projectId, ownerAccountId, now);
        recordEvent(projectId, ownerAccountId, "project_roles_initialized", Map.of("systemCeoAccountId", ownerAccountId));
        createRoleTask(ensureProject(projectId), ProjectRoleCode.SYSTEM_CEO, ownerAccountId);
    }

    public ProjectAuthorityContext getProjectAuthorityContext(String accountId, String projectId) {
        ProjectEntity project = ensureProject(projectId);
        if (!rootProject(project)) {
            Set<ProjectCapability> capabilities = EnumSet.noneOf(ProjectCapability.class);
            for (ProjectCapability capability : ProjectCapability.values()) {
                if (openProjectCapability(accountId, project, capability)) {
                    capabilities.add(capability);
                }
            }
            return new ProjectAuthorityContext(accountId, projectId, List.of(), Set.copyOf(capabilities));
        }
        List<ProjectRoleEntity> assignedRoles = projectRoleRepository.findAssignedRoles(projectId, accountId);
        Set<ProjectCapability> capabilities = EnumSet.noneOf(ProjectCapability.class);
        Enforcer enforcer = enforcerFor(projectId, projectDomain(projectId));
        for (ProjectCapability capability : ProjectCapability.values()) {
            if (enforcer.enforce(accountId, projectDomain(projectId), capability.code(), AUTHORITY_ACTION)) {
                capabilities.add(capability);
            }
        }
        return new ProjectAuthorityContext(
                accountId,
                projectId,
                assignedRoles.stream().map(ProjectRoleEntity::roleCode).toList(),
                Set.copyOf(capabilities));
    }

    public boolean hasProjectCapability(String accountId, String projectId, ProjectCapability capability) {
        if (accountId == null || accountId.isBlank() || capability == null) {
            return false;
        }
        ProjectEntity project = ensureProject(projectId);
        if (!rootProject(project)) {
            return openProjectCapability(accountId, project, capability);
        }
        return enforcerFor(projectId, projectDomain(projectId))
                .enforce(accountId, projectDomain(projectId), capability.code(), AUTHORITY_ACTION);
    }

    public List<ProjectRoleCode> assignedRoleCodes(String projectId, String accountId) {
        if (projectId == null || projectId.isBlank() || accountId == null || accountId.isBlank()) {
            return List.of();
        }
        if (projectRepository.findById(projectId).map(project -> !rootProject(project)).orElse(true)) {
            return List.of();
        }
        // 中文注释：资源能力扩展只需要账号当前职位快照，避免重复暴露 Casbin 内部规则。
        return projectRoleRepository.findAssignedRoles(projectId, accountId).stream()
                .map(ProjectRoleEntity::roleCode)
                .distinct()
                .toList();
    }

    public void requireProjectCapability(String accountId, String projectId, ProjectCapability capability) {
        if (!hasProjectCapability(accountId, projectId, capability)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project capability required: " + capability.code());
        }
    }

    public ProjectAuthorityContext getSystemAuthorityContext(String accountId) {
        ProjectEntity rootProject = rootProjectService.ensureRootProject(null);
        return getProjectAuthorityContext(accountId, rootProject.id());
    }

    public boolean hasSystemCapability(String accountId, ProjectCapability capability) {
        if (accountId == null || accountId.isBlank() || capability == null) {
            return false;
        }
        // 中文注释：系统能力来自 Root Project 角色，避免账号全局等级形成第二条权限链。
        ProjectEntity rootProject = rootProjectService.ensureRootProject(null);
        return enforcerFor(rootProject.id(), SYSTEM_DOMAIN)
                .enforce(accountId, SYSTEM_DOMAIN, capability.code(), AUTHORITY_ACTION);
    }

    public void requireSystemCapability(String accountId, ProjectCapability capability) {
        if (!hasSystemCapability(accountId, capability)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System capability required: " + capability.code());
        }
    }

    public boolean canReviewOrder(String accountId, OrderEntity order) {
        // 中文注释：普通 Project 的 review 走开放协议能力，Root/非项目订单走维护席位能力。
        if (order.postKind() == PostKind.PROJECT) {
            return hasProjectCapability(accountId, order.postId(), ProjectCapability.ORDER_REVIEW);
        }
        return hasReviewerRole(accountId, rootProjectService.ensureRootProject(null).id());
    }

    public boolean canReviewAnyOrder(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        // 中文注释：Workbench 只需要判断是否可能承接 review，具体订单仍由 canReviewOrder 二次过滤。
        return projectRoleRepository.findAssignedRolesByAccountId(accountId).stream()
                .map(ProjectRoleEntity::roleCode)
                .anyMatch(ProjectRoleCode::singleSeat);
    }

    public boolean canResolveOrderDispute(String accountId, OrderEntity order) {
        if (order.postKind() == PostKind.PROJECT) {
            return hasProjectCapability(accountId, order.postId(), ProjectCapability.ORDER_DISPUTE_RESOLVE);
        }
        return hasSystemCapability(accountId, ProjectCapability.ORDER_DISPUTE_RESOLVE);
    }

    public boolean canReviewUpload(String accountId, OrderEntity order) {
        if (order.postKind() == PostKind.PROJECT) {
            return hasProjectCapability(accountId, order.postId(), ProjectCapability.PROOF_TECH_REVIEW)
                    || hasProjectCapability(accountId, order.postId(), ProjectCapability.ORDER_REVIEW);
        }
        return hasSystemCapability(accountId, ProjectCapability.UPLOAD_REVIEW);
    }

    public List<AccountEntity> listReviewerCandidates(OrderEntity order) {
        MarketEntity market = marketRepository.findById(order.marketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Market not found"));
        String projectId = order.postKind() == PostKind.PROJECT ? order.postId() : rootProjectService.ensureRootProject(null).id();
        List<String> candidateIds = projectRoleRepository.findByProjectId(projectId).stream()
                .filter(role -> role.roleCode().singleSeat())
                .map(ProjectRoleEntity::accountId)
                .distinct()
                .toList();
        // 中文注释：评审候选从项目角色事实反查账号，避免为了筛选少量候选扫描整个账号表。
        return accountRepository.findByIds(candidateIds).stream()
                .filter(account -> reviewerEligibleForOrder(account, order, market))
                .sorted(Comparator.comparing(AccountEntity::id))
                .toList();
    }

    public void requireReviewerCandidate(OrderEntity order, String reviewerAccountId) {
        boolean eligible = listReviewerCandidates(order).stream()
                .anyMatch(account -> account.id().equals(reviewerAccountId));
        if (!eligible) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reviewer candidate authority required");
        }
    }

    public List<ProjectRoleEntity> listProjectRoles(String projectId) {
        ProjectEntity project = ensureProject(projectId);
        if (!rootProject(project)) {
            return List.of();
        }
        List<ProjectRoleEntity> roles = projectRoleRepository.findByProjectId(projectId);
        if (!roles.isEmpty()) {
            return roles;
        }
        projectRoleRepository.initializeProjectRoles(projectId, project.ownerAccountId(), Instant.now());
        return projectRoleRepository.findByProjectId(projectId);
    }

    public Map<String, List<ProjectRoleEntity>> listProjectRolesByProjectIds(List<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        List<String> rootProjectIds = projectIds.stream()
                .filter(projectId -> projectRepository.findById(projectId).map(this::rootProject).orElse(false))
                .toList();
        if (rootProjectIds.isEmpty()) {
            return Map.of();
        }
        // 中文注释：批量 read model 只返回 Root Project 维护席位，普通 Project 不再暴露角色表。
        return projectRoleRepository.findByProjectIds(rootProjectIds);
    }

    public ProjectRoleEntity assignProjectRole(
            String projectId,
            ProjectRoleCode roleCode,
            String accountId,
            String actorAccountId) {
        requireProjectCapability(actorAccountId, projectId, ProjectCapability.ROLE_ASSIGN);
        AccountEntity assignee = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee account not found"));
        ProjectRoleEntity role = projectRoleRepository.assignRole(projectId, roleCode, assignee.id(), actorAccountId);
        recordEvent(projectId, actorAccountId, "project_role_assigned", Map.of(
                "roleCode", roleCode.name().toLowerCase(),
                "accountId", assignee.id()));
        createRoleTask(ensureProject(projectId), roleCode, assignee.id());
        return role;
    }

    public ProjectRoleEntity acceptProjectRoleInvite(
            String projectId,
            ProjectRoleCode roleCode,
            String accountId,
            String invitedByAccountId) {
        ProjectEntity project = ensureProject(projectId);
        AccountEntity assignee = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitee account not found"));
        // 中文注释：workbench 邀请本身就是授权凭据，接受邀请只校验被邀请账号，避免普通成员需要先拥有分配权限。
        ProjectRoleEntity role = projectRoleRepository.assignRole(projectId, roleCode, assignee.id(), invitedByAccountId);
        recordEvent(projectId, assignee.id(), "project_role_invite_accepted", Map.of(
                "roleCode", roleCode.name().toLowerCase(),
                "accountId", assignee.id(),
                "invitedByAccountId", invitedByAccountId));
        createRoleTask(project, roleCode, assignee.id());
        return role;
    }

    public ProjectRoleEntity vacateProjectRole(String projectId, ProjectRoleCode roleCode, String accountId, String actorAccountId) {
        requireProjectCapability(actorAccountId, projectId, ProjectCapability.ROLE_VACATE);
        ProjectRoleEntity role = projectRoleRepository.vacateRole(projectId, roleCode, accountId, actorAccountId);
        recordEvent(projectId, actorAccountId, "project_role_vacated", Map.of(
                "roleCode", roleCode.name().toLowerCase(),
                "accountId", accountId));
        return role;
    }

    private void createRoleTask(ProjectEntity project, ProjectRoleCode roleCode, String accountId) {
        ProjectRoleTaskCatalog.RoleTaskSpec spec = ProjectRoleTaskCatalog.spec(roleCode);
        Instant now = Instant.now();
        WorkItemEntity item = new WorkItemEntity(
                "wi-project-role-" + project.projectNo().toLowerCase() + "-" + roleCode.code() + "-" + safeKey(accountId),
                "wi-project-role-" + project.projectNo().toLowerCase() + "-" + roleCode.code() + "-" + safeKey(accountId),
                "project_role_task",
                project.projectNo(),
                accountId,
                spec.title().formatted(project.title()),
                spec.goal().formatted(project.title()),
                spec.acceptanceCriteria(),
                List.of("project:" + project.projectNo(), "project_role:" + roleCode.code()),
                Map.of(
                        "action", "project_role_task",
                        "projectNo", project.projectNo(),
                        "projectId", project.id(),
                        "roleCode", roleCode.code(),
                        "taskKind", spec.taskKind()),
                roleCode.code(),
                spec.primaryCapability().code(),
                "attention",
                "ready",
                null,
                now,
                now,
                now);
        // 中文注释：每个维护席位都获得一个角色任务，让 Root Project 授权有明确下一步。
        workRepository.upsertItem(item);
    }

    private String safeKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private Enforcer enforcerFor(String projectId, String domain) {
        Model model = Model.newModelFromString(CASBIN_MODEL);
        Enforcer enforcer = new Enforcer(model);
        enforcer.enableAutoSave(false);
        for (Map.Entry<ProjectRoleCode, Set<ProjectCapability>> entry : ProjectRoleCapabilityPolicy.capabilitiesByRole().entrySet()) {
            for (ProjectCapability capability : entry.getValue()) {
                enforcer.addPolicy(entry.getKey().code(), domain, capability.code(), AUTHORITY_ACTION);
            }
        }
        for (ProjectRoleEntity role : projectRoleRepository.findByProjectId(projectId)) {
            enforcer.addGroupingPolicy(role.accountId(), role.roleCode().code(), domain);
        }
        // 中文注释：每次从 project_roles 重建 Casbin 链接，保证授权判定只依赖项目角色事实。
        enforcer.buildRoleLinks();
        return enforcer;
    }

    private String projectDomain(String projectId) {
        // 中文注释：权限判定会出现在只读聚合查询里，这里只读 root 快照，避免 GET 路径触发 bootstrap 写入。
        boolean rootProject = projectRepository.findRootProject()
                .map(ProjectEntity::id)
                .filter(projectId::equals)
                .isPresent();
        return rootProject ? SYSTEM_DOMAIN : "project:" + projectId;
    }

    private boolean rootProject(ProjectEntity project) {
        return project != null
                && (RootProjectService.ROOT_PROJECT_ID.equals(project.id())
                || RootProjectService.ROOT_PROJECT_NO.equals(project.projectNo()));
    }

    private boolean openProjectCapability(String accountId, ProjectEntity project, ProjectCapability capability) {
        // 中文注释：普通 Project 的能力来自开放市场协议，角色和后台维护能力集中到 Root Project。
        return switch (capability) {
            case PROJECT_PARTICIPATE, MARKET_QUALITY_MANAGE, ORDER_REVIEW, PROOF_TECH_REVIEW, UPLOAD_REVIEW -> true;
            case PROJECT_MANAGE -> project.ownerAccountId().equals(accountId);
            default -> false;
        };
    }

    private boolean reviewerEligibleForOrder(AccountEntity account, OrderEntity order, MarketEntity market) {
        if (RootProjectService.ROOT_FALLBACK_ACCOUNT_ID.equals(account.id())) {
            return false;
        }
        if (order.hasParticipant(account.id()) || account.id().equals(market.leadAccountId())) {
            return false;
        }
        // 中文注释：Root Project reviewer 候选只认维护席位，普通 Project 通过开放协议能力判断。
        if (order.postKind() == PostKind.PROJECT) {
            return hasProjectCapability(account.id(), order.postId(), ProjectCapability.ORDER_REVIEW);
        }
        return hasReviewerRole(account.id(), rootProjectService.ensureRootProject(null).id());
    }

    private boolean hasReviewerRole(String accountId, String projectId) {
        if (accountId == null || accountId.isBlank() || projectId == null || projectId.isBlank()) {
            return false;
        }
        return projectRoleRepository.findAssignedRoles(projectId, accountId).stream()
                .map(ProjectRoleEntity::roleCode)
                .anyMatch(ProjectRoleCode::singleSeat);
    }

    private ProjectEntity ensureProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private void recordEvent(String projectId, String actorAccountId, String eventType, Map<String, Object> payload) {
        organizationEventRepository.save(new OrganizationEventEntity(
                "orgevt-" + UUID.randomUUID(),
                projectId,
                actorAccountId,
                eventType,
                payload,
                Instant.now()));
    }

}
