package com.monopolyfun.modules.project.service;

import com.monopolyfun.modules.identity.domain.AccountEntity;
import com.monopolyfun.modules.identity.infra.AccountRepository;
import com.monopolyfun.modules.organization.service.OrganizationAuthorityService;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.infra.ProjectRepository;
import com.monopolyfun.modules.project.service.view.ProjectRoleInviteView;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import com.monopolyfun.shared.command.CommandReceipt;
import com.monopolyfun.shared.validation.RequestPayloadLimits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ProjectRoleInviteService {
    public static final String SOURCE_TYPE = "project_role_invite";
    public static final String ACTION_ACCEPT = "accept_project_invite";
    public static final String ACTION_DECLINE = "decline_project_invite";

    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final WorkRepository workRepository;
    private final OrganizationAuthorityService organizationAuthorityService;

    public ProjectRoleInviteService(
            ProjectRepository projectRepository,
            AccountRepository accountRepository,
            WorkRepository workRepository,
            OrganizationAuthorityService organizationAuthorityService) {
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
        this.workRepository = workRepository;
        this.organizationAuthorityService = organizationAuthorityService;
    }

    public ProjectRoleInviteView invite(String projectNo, ProjectRoleCode roleCode, String accountId, String invitedByAccountId, String message) {
        ProjectEntity project = requireProject(projectNo);
        organizationAuthorityService.requireProjectCapability(invitedByAccountId, project.id(), ProjectCapability.ROLE_ASSIGN);
        AccountEntity account = resolveInviteeAccount(accountId);
        RequestPayloadLimits.requireTextLength("message", message, 500);
        Instant now = Instant.now();
        WorkItemEntity item = new WorkItemEntity(
                inviteWorkItemId(project, roleCode, account.id()),
                inviteWorkItemNo(project, roleCode, account.id()),
                SOURCE_TYPE,
                inviteSourceId(project, roleCode, account.id()),
                account.id(),
                "接受 " + project.title() + " 的 " + roleCode.code() + " 邀请",
                inviteGoal(project, roleCode, message),
                List.of("确认项目与角色匹配", "接受后读取项目 roles 验证职务已生效", "接受后进入该角色的项目任务"),
                List.of("project:" + project.projectNo(), "project_role:" + roleCode.code(), "invited_by:" + invitedByAccountId),
                Map.of(
                        "action", SOURCE_TYPE,
                        "projectId", project.id(),
                        "projectNo", project.projectNo(),
                        "roleCode", roleCode.code(),
                        "invitedByAccountId", invitedByAccountId,
                        "acceptAction", ACTION_ACCEPT,
                        "declineAction", ACTION_DECLINE),
                roleCode.code(),
                ProjectCapability.PROJECT_PARTICIPATE.code(),
                "attention",
                "ready",
                null,
                now,
                now,
                now);
        // 中文注释：邀请以 WorkItem 进入被邀请人的 workbench，个人 agent 只需轮询当前账号即可看到并接受。
        workRepository.upsertItem(item);
        return new ProjectRoleInviteView(project.projectNo(), roleCode, account.id(), invitedByAccountId, item.itemNo(), "ready", now);
    }

    private AccountEntity resolveInviteeAccount(String accountRef) {
        String normalized = accountRef == null ? "" : accountRef.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitee account not found");
        }
        return accountRepository.findById(normalized)
                .or(() -> accountRepository.findByHandle(normalized))
                .or(() -> accountRepository.findByHandle(normalized.replaceFirst("^@+", "")))
                .or(() -> accountRepository.findByHandle("@" + normalized.replaceFirst("^@+", "")))
                // 中文注释：项目邀请允许 agent 使用用户可见 handle，API 在写入前统一收敛到内部 accountId。
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitee account not found"));
    }

    public CommandReceipt accept(String workItemNoOrId, String actorAccountId) {
        WorkItemEntity item = requireInviteItem(workItemNoOrId, actorAccountId);
        ProjectEntity project = requireProject(requiredOutputString(item, "projectNo"));
        ProjectRoleCode roleCode = ProjectRoleCode.fromCode(requiredOutputString(item, "roleCode"));
        String invitedBy = requiredOutputString(item, "invitedByAccountId");
        var role = organizationAuthorityService.acceptProjectRoleInvite(project.id(), roleCode, actorAccountId, invitedBy);
        workRepository.closeOpenItemsBySource(SOURCE_TYPE, item.sourceId(), "project_invite_accepted");
        return new CommandReceipt(
                "cmd-" + UUID.randomUUID(),
                "project_role_invite",
                item.itemNo(),
                "accepted",
                Map.of(
                        "projectNo", project.projectNo(),
                        "roleCode", roleCode.code(),
                        "accountId", actorAccountId,
                        "projectRole", Map.of("projectId", role.projectId(), "roleCode", role.roleCode().code(), "accountId", role.accountId())),
                actorAccountId,
                "project-role-invite-" + UUID.randomUUID(),
                null,
                Instant.now());
    }

    public CommandReceipt decline(String workItemNoOrId, String actorAccountId) {
        WorkItemEntity item = requireInviteItem(workItemNoOrId, actorAccountId);
        workRepository.closeOpenItemsBySource(SOURCE_TYPE, item.sourceId(), "project_invite_declined");
        return new CommandReceipt(
                "cmd-" + UUID.randomUUID(),
                "project_role_invite",
                item.itemNo(),
                "declined",
                Map.of("workItemNo", item.itemNo(), "projectNo", requiredOutputString(item, "projectNo"), "roleCode", requiredOutputString(item, "roleCode")),
                actorAccountId,
                "project-role-invite-" + UUID.randomUUID(),
                null,
                Instant.now());
    }

    private WorkItemEntity requireInviteItem(String workItemNoOrId, String actorAccountId) {
        WorkItemEntity item = workRepository.findItemByNoOrId(workItemNoOrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project role invite not found"));
        if (!SOURCE_TYPE.equals(item.sourceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workbench item is not a project role invite");
        }
        if (!actorAccountId.equals(item.accountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the invited account can handle this invite");
        }
        return item;
    }

    private ProjectEntity requireProject(String projectNoOrId) {
        return projectRepository.findByProjectNo(projectNoOrId)
                .or(() -> projectRepository.findById(projectNoOrId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private String requiredOutputString(WorkItemEntity item, String key) {
        Object value = item.outputSchema().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Project role invite is missing " + key);
    }

    private String inviteGoal(ProjectEntity project, ProjectRoleCode roleCode, String message) {
        String base = "你被邀请加入 " + project.title() + "，担任 " + roleLabel(roleCode) + "。接受后系统会写入维护席位，并生成该授权的初始任务。";
        return message == null || message.isBlank() ? base : base + " 邀请说明：" + message.trim();
    }

    private String roleLabel(ProjectRoleCode roleCode) {
        // 中文注释：邀请正文面向用户展示维护席位名称，底层 work item 仍保留 roleCode 便于动作执行。
        return switch (roleCode) {
            case SYSTEM_CEO -> "协议维护";
            case SYSTEM_CTO -> "技术维护";
            case SYSTEM_CFO -> "结算维护";
        };
    }

    private String inviteSourceId(ProjectEntity project, ProjectRoleCode roleCode, String accountId) {
        return project.projectNo() + ":" + roleCode.code() + ":" + accountId;
    }

    private String inviteWorkItemNo(ProjectEntity project, ProjectRoleCode roleCode, String accountId) {
        return "wi-invite-" + project.projectNo().toLowerCase() + "-" + roleCode.code() + "-" + safeKey(accountId);
    }

    private String inviteWorkItemId(ProjectEntity project, ProjectRoleCode roleCode, String accountId) {
        return "wi-" + inviteWorkItemNo(project, roleCode, accountId);
    }

    private String safeKey(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
