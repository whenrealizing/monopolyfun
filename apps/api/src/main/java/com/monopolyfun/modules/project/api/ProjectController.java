package com.monopolyfun.modules.project.api;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.post.api.request.CreatePostItemRequest;
import com.monopolyfun.modules.post.service.command.PostCommandService;
import com.monopolyfun.modules.post.service.command.PostItemCommandService;
import com.monopolyfun.modules.post.service.query.PostQueryService;
import com.monopolyfun.modules.post.service.view.PostItemView;
import com.monopolyfun.modules.project.api.request.AssignProjectRoleRequest;
import com.monopolyfun.modules.project.api.request.ClaimProjectOwnerRequest;
import com.monopolyfun.modules.project.api.request.CreateProjectItemRequest;
import com.monopolyfun.modules.project.api.request.InviteProjectRoleRequest;
import com.monopolyfun.modules.project.api.request.PublishProjectRequest;
import com.monopolyfun.modules.project.api.request.UpdateProjectPostRequest;
import com.monopolyfun.modules.project.api.response.ProjectCreateResponse;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.ProjectCommercializationService;
import com.monopolyfun.modules.project.service.ProjectDashboardQueryService;
import com.monopolyfun.modules.project.service.ProjectLifecycleService;
import com.monopolyfun.modules.project.service.ProjectRoleInviteService;
import com.monopolyfun.modules.project.service.ProjectTimelineQueryService;
import com.monopolyfun.modules.project.service.view.ProjectAuthorityContextView;
import com.monopolyfun.modules.project.service.view.ProjectCommercializationView;
import com.monopolyfun.modules.project.service.view.ProjectDashboardView;
import com.monopolyfun.modules.project.service.view.ProjectRoleInviteView;
import com.monopolyfun.modules.project.service.view.ProjectRoleView;
import com.monopolyfun.modules.project.service.view.ProjectTimelineEventView;
import com.monopolyfun.modules.project.service.view.ProjectView;
import com.monopolyfun.shared.pagination.PageQuery;
import com.monopolyfun.shared.pagination.PageResult;
import com.monopolyfun.shared.security.CurrentAccountAccess;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final PostQueryService postQueryService;
    private final PostCommandService postCommandService;
    private final ProjectLifecycleService projectLifecycleService;
    private final ProjectCommercializationService projectCommercializationService;
    private final ProjectDashboardQueryService projectDashboardQueryService;
    private final ProjectTimelineQueryService projectTimelineQueryService;
    private final PostItemCommandService postItemCommandService;
    private final CurrentAccountAccess currentAccountAccess;
    private final OrganizationAuthorityService organizationAuthorityService;
    private final ProjectRoleInviteService projectRoleInviteService;
    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;

    public ProjectController(
            PostQueryService postQueryService,
            PostCommandService postCommandService,
            ProjectLifecycleService projectLifecycleService,
            ProjectCommercializationService projectCommercializationService,
            ProjectDashboardQueryService projectDashboardQueryService,
            ProjectTimelineQueryService projectTimelineQueryService,
            PostItemCommandService postItemCommandService,
            CurrentAccountAccess currentAccountAccess,
            OrganizationAuthorityService organizationAuthorityService,
            ProjectRoleInviteService projectRoleInviteService,
            ProjectRepository projectRepository,
            AccountRepository accountRepository) {
        this.postQueryService = postQueryService;
        this.postCommandService = postCommandService;
        this.projectLifecycleService = projectLifecycleService;
        this.projectCommercializationService = projectCommercializationService;
        this.projectDashboardQueryService = projectDashboardQueryService;
        this.projectTimelineQueryService = projectTimelineQueryService;
        this.postItemCommandService = postItemCommandService;
        this.currentAccountAccess = currentAccountAccess;
        this.organizationAuthorityService = organizationAuthorityService;
        this.projectRoleInviteService = projectRoleInviteService;
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public PageResult<ProjectView> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.listProjects(status, q, sort, PageQuery.of(limit, cursor), includeAgent);
    }

    @GetMapping("/root")
    public ProjectView getRootProject(@RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.getRootProject(includeAgent);
    }

    @GetMapping("/{projectNo}")
    public ProjectView getProject(
            @PathVariable String projectNo,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postQueryService.getProject(projectNo, includeAgent);
    }

    @GetMapping("/{projectNo}/dashboard")
    public ProjectDashboardView getProjectDashboard(@PathVariable String projectNo) {
        return projectDashboardQueryService.getDashboard(projectNo);
    }

    @GetMapping("/{projectNo}/commercialization")
    public ProjectCommercializationView getProjectCommercialization(@PathVariable String projectNo) {
        return projectCommercializationService.getCommercialization(projectNo);
    }

    @GetMapping("/{projectNo}/timeline")
    public List<ProjectTimelineEventView> getProjectTimeline(@PathVariable String projectNo) {
        return projectTimelineQueryService.getTimeline(requireProjectId(projectNo));
    }

    @PostMapping
    public ProjectCreateResponse publishProject(
            @Valid @RequestBody PublishProjectRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        return postCommandService.createProject(request, includeAgent);
    }

    @PostMapping("/{projectNo}/items")
    @Operation(summary = "创建项目任务项", description = "Project 任务项使用专用合同。shares 定价、agentInstruction 和 quantity 由系统统一处理。")
    public PostItemView createProjectItem(
            @PathVariable String projectNo,
            @Valid @RequestBody CreateProjectItemRequest request,
            @RequestParam(defaultValue = "false") boolean includeAgent) {
        // 中文注释：Project 公开任务入口使用专用合同，避免 amount、quantity、agentInstruction 等非项目字段继续污染文档和 agent 请求。
        return postItemCommandService.createItem(projectNo, new CreatePostItemRequest(
                request.actorAccountId(),
                request.name(),
                request.description(),
                request.deliveryStandard(),
                request.acceptanceCriteria(),
                null,
                request.difficultyScore(),
                1,
                null,
                request.itemType(),
                request.mode()), includeAgent);
    }

    @PatchMapping("/{projectNo}")
    public ProjectView updateProject(@PathVariable String projectNo, @Valid @RequestBody UpdateProjectPostRequest request) {
        return postCommandService.updateProject(projectNo, request);
    }

    @PostMapping("/{projectNo}/owner-claim")
    public ProjectView claimProjectOwner(
            @PathVariable String projectNo,
            @Valid @RequestBody ClaimProjectOwnerRequest request) {
        currentAccountAccess.requireSameAccount(request.actorAccountId());
        String projectId = requireProjectId(projectNo);
        var project = projectLifecycleService.claimOwner(projectId, request.actorAccountId(), request.reason(), request.plan());
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.project(project, organizationAuthorityService.listProjectRoles(project.id()).stream()
                .map(com.monopolyfun.modules.project.service.mapper.ProjectViewMapper::projectRole)
                .toList());
    }

    @GetMapping("/{projectNo}/roles")
    public List<ProjectRoleView> listProjectRoles(@PathVariable String projectNo) {
        String projectId = requireProjectId(projectNo);
        // 中文注释：公开角色列表只读取当前事实，避免 GET 请求为缺失角色补写初始化数据。
        List<com.monopolyfun.modules.project.domain.ProjectRoleEntity> roles = organizationAuthorityService
                .listProjectRolesByProjectIds(List.of(projectId))
                .getOrDefault(projectId, List.of());
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.publicProjectRoles(
                roles,
                accountRepository.findByIds(roles.stream()
                                .map(role -> role.accountId())
                                .filter(accountId -> accountId != null && !accountId.isBlank())
                                .distinct()
                                .toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(AccountEntity::id, AccountEntity::handle)));
    }

    @PostMapping("/{projectNo}/roles/{roleCode}/assign")
    public ProjectRoleView assignProjectRole(
            @PathVariable String projectNo,
            @PathVariable String roleCode,
            @Valid @RequestBody AssignProjectRoleRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        String projectId = requireProjectId(projectNo);
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.projectRole(organizationAuthorityService.assignProjectRole(projectId, parseRoleCode(roleCode), request.accountId(), actorAccountId));
    }

    @PostMapping("/{projectNo}/roles/{roleCode}/invite")
    public ProjectRoleInviteView inviteProjectRole(
            @PathVariable String projectNo,
            @PathVariable String roleCode,
            @Valid @RequestBody InviteProjectRoleRequest request) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        // 中文注释：邀请先进入对方 workbench，接受后才写 project_roles，避免后台替用户静默入职。
        return projectRoleInviteService.invite(projectNo, parseRoleCode(roleCode), request.accountId(), actorAccountId, request.message());
    }

    @PostMapping("/{projectNo}/roles/{roleCode}/{accountId}/vacate")
    public ProjectRoleView vacateProjectRole(
            @PathVariable String projectNo,
            @PathVariable String roleCode,
            @PathVariable String accountId) {
        String actorAccountId = currentAccountAccess.requireAccountId();
        String projectId = requireProjectId(projectNo);
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.projectRole(organizationAuthorityService.vacateProjectRole(projectId, parseRoleCode(roleCode), accountId, actorAccountId));
    }

    @GetMapping("/{projectNo}/authority/me")
    public ProjectAuthorityContextView getMyProjectAuthority(@PathVariable String projectNo) {
        String accountId = currentAccountAccess.requireAccountId();
        String projectId = requireProjectId(projectNo);
        return com.monopolyfun.modules.project.service.mapper.ProjectViewMapper.projectAuthorityContext(organizationAuthorityService.getProjectAuthorityContext(accountId, projectId));
    }

    private String requireProjectId(String projectNo) {
        if (projectNo == null || projectNo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "project business number is required");
        }
        // 中文注释：Project 公开控制面使用 projectNo，controller 统一翻译，领域服务继续使用内部主键。
        return projectRepository.findByProjectNo(projectNo.trim())
                .map(project -> project.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectRoleCode parseRoleCode(String value) {
        try {
            return ProjectRoleCode.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
