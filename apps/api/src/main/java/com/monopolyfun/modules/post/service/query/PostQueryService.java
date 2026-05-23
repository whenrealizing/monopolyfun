package com.monopolyfun.modules.post.service.query;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.domain.OfferEntity;
import com.monopolyfun.modules.post.domain.RequestEntity;
import com.monopolyfun.modules.post.infra.OfferRepository;
import com.monopolyfun.modules.post.infra.RequestPostRepository;
import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.modules.post.service.view.RequestView;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import com.monopolyfun.modules.project.service.view.ProjectView;
import com.monopolyfun.platform.agent.openapi.AgentCapabilityResolver;
import com.monopolyfun.platform.agent.openapi.AgentResourceKeyFactory;
import com.monopolyfun.shared.pagination.PageInfo;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PostQueryService {
    private final OfferRepository offerRepository;
    private final RequestPostRepository requestPostRepository;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final RootProjectService rootProjectService;
    private final CurrentAccountAccess currentAccountAccess;
    private final AgentCapabilityResolver agentCapabilityResolver;
    private final AgentResourceKeyFactory agentResourceKeyFactory;

    public PostQueryService(
            OfferRepository offerRepository,
            RequestPostRepository requestPostRepository,
            ProjectRepository projectRepository,
            AccountRepository accountRepository,
            OrganizationAuthorityService organizationAuthorityService,
            RootProjectService rootProjectService,
            CurrentAccountAccess currentAccountAccess,
            AgentCapabilityResolver agentCapabilityResolver,
            AgentResourceKeyFactory agentResourceKeyFactory) {
        this.offerRepository = offerRepository;
        this.requestPostRepository = requestPostRepository;
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
        this.organizationAuthorityService = organizationAuthorityService;
        this.rootProjectService = rootProjectService;
        this.currentAccountAccess = currentAccountAccess;
        this.agentCapabilityResolver = agentCapabilityResolver;
        this.agentResourceKeyFactory = agentResourceKeyFactory;
    }

    public PageResult<OfferView> listOffers(String status, String q, String sort, PageQuery pageQuery) {
        return listOffers(status, q, sort, pageQuery, false);
    }

    public PageResult<OfferView> listOffers(String status, String q, String sort, PageQuery pageQuery, boolean includeAgent) {
        // 中文注释：公开列表统一返回 PageResult，前端只认 cursor 这一种翻页契约。
        var page = offerRepository.findPublic(status, q, sort, pageQuery);
        Map<String, String> actorHandles = accountHandles(page.items().stream().map(OfferEntity::actorAccountId).toList());
        return mapPage(page, page.items().stream()
                .map(offer -> offerView(offer, actorHandles.get(offer.actorAccountId()), includeAgent))
                .toList());
    }

    public OfferView getOffer(String offerId) {
        return getOffer(offerId, false);
    }

    public OfferView getOffer(String offerId, boolean includeAgent) {
        return offerRepository.findByOfferNo(offerId)
                .map(offer -> offerView(offer, ownerHandle(offer.actorAccountId()), includeAgent))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
    }

    public PageResult<RequestView> listRequests(String status, String q, String sort, PageQuery pageQuery) {
        return listRequests(status, q, sort, pageQuery, false);
    }

    public PageResult<RequestView> listRequests(String status, String q, String sort, PageQuery pageQuery, boolean includeAgent) {
        var page = requestPostRepository.findPublic(status, q, sort, pageQuery);
        Map<String, String> actorHandles = accountHandles(page.items().stream().map(RequestEntity::actorAccountId).toList());
        return mapPage(page, page.items().stream()
                .map(request -> requestView(request, actorHandles.get(request.actorAccountId()), includeAgent))
                .toList());
    }

    public RequestView getRequest(String requestId) {
        return getRequest(requestId, false);
    }

    public RequestView getRequest(String requestId, boolean includeAgent) {
        return requestPostRepository.findByRequestNo(requestId)
                .map(request -> requestView(request, ownerHandle(request.actorAccountId()), includeAgent))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    public PageResult<ProjectView> listProjects(String status, String q, String sort, PageQuery pageQuery) {
        return listProjects(status, q, sort, pageQuery, false);
    }

    public PageResult<ProjectView> listProjects(String status, String q, String sort, PageQuery pageQuery, boolean includeAgent) {
        var page = projectRepository.findPublicChildren(status, q, sort, pageQuery);
        var projects = page.items();
        Map<String, List<com.monopolyfun.modules.project.domain.ProjectRoleEntity>> rolesByProject =
                organizationAuthorityService.listProjectRolesByProjectIds(projects.stream().map(ProjectEntity::id).toList());
        Map<String, String> ownerHandles = ownerHandles(projects);
        Map<String, String> roleHandles = roleHandles(rolesByProject);
        List<ProjectView> items = projects.stream()
                .map(project -> com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProject(
                        project,
                        rolesByProject.getOrDefault(project.id(), List.of()),
                        ownerHandles.get(project.ownerAccountId()),
                        roleHandles))
                .map(view -> includeAgent ? projectViewWithAgent(projects.stream()
                        .filter(project -> project.projectNo().equals(view.projectNo()))
                        .findFirst()
                        .orElse(null), view) : view)
                .toList();
        return mapPage(page, items);
    }

    public ProjectView getProject(String projectId) {
        return getProject(projectId, false);
    }

    public ProjectView getProject(String projectId, boolean includeAgent) {
        return findProjectByPublicNo(projectId)
                .map(project -> {
                    // 中文注释：公开项目读取使用纯读角色快照，角色初始化统一留在项目写入链路。
                    List<com.monopolyfun.modules.project.domain.ProjectRoleEntity> roles = organizationAuthorityService
                            .listProjectRolesByProjectIds(List.of(project.id()))
                            .getOrDefault(project.id(), List.of());
                    ProjectView view = com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProject(
                            project,
                            roles,
                            ownerHandle(project.ownerAccountId()),
                            roleHandles(Map.of(project.id(), roles)));
                    return includeAgent ? projectViewWithAgent(project, view) : view;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private Optional<ProjectEntity> findProjectByPublicNo(String projectNo) {
        if (RootProjectService.ROOT_PROJECT_NO.equalsIgnoreCase(projectNo)) {
            // 中文注释：monopolyfun 是 Root Project 的公开编号，读取时直接落到唯一 root 记录。
            return projectRepository.findRootProject();
        }
        return projectRepository.findByProjectNo(projectNo);
    }

    public List<ProjectView> projectViews(List<ProjectEntity> projects) {
        Map<String, List<com.monopolyfun.modules.project.domain.ProjectRoleEntity>> rolesByProject =
                organizationAuthorityService.listProjectRolesByProjectIds(projects.stream().map(ProjectEntity::id).toList());
        Map<String, String> ownerHandles = ownerHandles(projects);
        Map<String, String> roleHandles = roleHandles(rolesByProject);
        return projects.stream()
                .map(project -> com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProject(
                        project,
                        rolesByProject.getOrDefault(project.id(), List.of()),
                        ownerHandles.get(project.ownerAccountId()),
                        roleHandles))
                .toList();
    }

    public ProjectView getRootProject() {
        return getRootProject(false);
    }

    public ProjectView getRootProject(boolean includeAgent) {
        // 中文注释：Root Project 是系统任务的公开读取面，查询入口顺手补齐 bootstrap 数据。
        var root = rootProjectService.ensureRootProject(null);
        List<com.monopolyfun.modules.project.domain.ProjectRoleEntity> roles = organizationAuthorityService.listProjectRoles(root.id());
        ProjectView view = com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProject(
                root,
                roles,
                ownerHandle(root.ownerAccountId()),
                roleHandles(Map.of(root.id(), roles)));
        return includeAgent ? projectViewWithAgent(root, view) : view;
    }

    private Map<String, String> ownerHandles(List<ProjectEntity> projects) {
        if (projects == null || projects.isEmpty()) {
            return Map.of();
        }
        return accountHandles(projects.stream().map(ProjectEntity::ownerAccountId).distinct().toList());
    }

    private String ownerHandle(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        return accountRepository.findById(accountId).map(AccountEntity::handle).orElse(null);
    }

    private OfferView offerView(OfferEntity offer, String actorHandle, boolean includeAgent) {
        OfferView view = com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicOffer(offer, actorHandle);
        if (!includeAgent) {
            return view;
        }
        // 中文注释：agent 扩展字段只在显式请求时计算，普通 UI 列表保持轻量响应。
        String accountId = currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse(null);
        return view.withAgentState(
                agentResourceKeyFactory.offer(offer.offerNo()),
                agentCapabilityResolver.offerCapabilities(offer, accountId),
                agentCapabilityResolver.offerBlockedCapabilities(offer, accountId));
    }

    private RequestView requestView(RequestEntity request, String actorHandle, boolean includeAgent) {
        RequestView view = com.monopolyfun.modules.post.service.mapper.PostViewMapper.publicRequest(request, actorHandle);
        if (!includeAgent) {
            return view;
        }
        // 中文注释：Request 与 Offer 共享 post owner 能力模型，agent 读取时才补充动作状态。
        String accountId = currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse(null);
        return view.withAgentState(
                agentResourceKeyFactory.request(request.requestNo()),
                agentCapabilityResolver.requestCapabilities(request, accountId),
                agentCapabilityResolver.requestBlockedCapabilities(request, accountId));
    }

    private ProjectView projectViewWithAgent(ProjectEntity project, ProjectView view) {
        if (project == null || view == null) {
            return view;
        }
        String accountId = currentAccountAccess.current().map(com.monopolyfun.shared.security.CurrentAccount::accountId).orElse(null);
        boolean rootProject = rootProject(project);
        boolean canCreateItem = accountId != null && (rootProject
                ? organizationAuthorityService.hasProjectCapability(accountId, project.id(), ProjectCapability.MARKET_QUALITY_MANAGE)
                : project.status() == com.monopolyfun.modules.project.domain.ProjectStatus.ACTIVE);
        boolean canAssignRole = rootProject && accountId != null && organizationAuthorityService.hasProjectCapability(accountId, project.id(), ProjectCapability.ROLE_ASSIGN);
        return view.withAgentState(
                agentResourceKeyFactory.project(project.projectNo()),
                agentCapabilityResolver.projectCapabilities(project, canCreateItem, canAssignRole, accountId),
                agentCapabilityResolver.projectBlockedCapabilities(project, canCreateItem, canAssignRole, accountId));
    }

    private Map<String, String> roleHandles(Map<String, List<com.monopolyfun.modules.project.domain.ProjectRoleEntity>> rolesByProject) {
        if (rolesByProject == null || rolesByProject.isEmpty()) {
            return Map.of();
        }
        return accountHandles(rolesByProject.values().stream()
                .flatMap(List::stream)
                .map(role -> role.accountId())
                .toList());
    }

    private boolean rootProject(ProjectEntity project) {
        return project != null
                && (com.monopolyfun.modules.project.service.RootProjectService.ROOT_PROJECT_ID.equals(project.id())
                || com.monopolyfun.modules.project.service.RootProjectService.ROOT_PROJECT_NO.equals(project.projectNo()));
    }

    private Map<String, String> accountHandles(List<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findByIds(accountIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList()).stream()
                .collect(Collectors.toMap(AccountEntity::id, AccountEntity::handle));
    }

    private <T, U> PageResult<U> mapPage(PageResult<T> page, List<U> items) {
        PageInfo pageInfo = page.pageInfo();
        return new PageResult<>(items, pageInfo);
    }
}
