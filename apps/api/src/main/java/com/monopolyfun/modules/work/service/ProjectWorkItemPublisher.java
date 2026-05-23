package com.monopolyfun.modules.work.service;

import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectCiCheckEntity;
import com.monopolyfun.modules.project.domain.ProjectPrLinkEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import com.monopolyfun.modules.projectmemory.domain.ProjectMemorySyncEventEntity;
import com.monopolyfun.modules.share.domain.ShareReleaseRequestEntity;
import com.monopolyfun.modules.work.domain.WorkItemEntity;
import com.monopolyfun.modules.work.infra.WorkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProjectWorkItemPublisher {
    private static final List<ProjectRoleCode> DEVELOPMENT_ROLES = List.of(
            ProjectRoleCode.SYSTEM_CTO,
            ProjectRoleCode.SYSTEM_CEO);
    private static final List<ProjectRoleCode> MEMORY_ROLES = List.of(
            ProjectRoleCode.SYSTEM_CEO,
            ProjectRoleCode.SYSTEM_CTO);
    private final WorkRepository workRepository;

    public ProjectWorkItemPublisher(WorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    public void publishShareRelease(ShareReleaseRequestEntity request, List<ProjectRoleEntity> roles, Instant now) {
        workRepository.closeOpenItemsBySource("share_release_request", request.id(), "share_release_projection_refreshed");
        if (request.isResolved()) {
            return;
        }
        // 中文注释：shares 审批从 release request 事实直接投影，审批角色变化后以最新席位重建待办。
        for (ProjectRoleCode roleCode : missingRoles(request.requiredRoleCodes(), request.approvedRoleCodes())) {
            roleAccountId(roles, roleCode).ifPresent(accountId -> workRepository.upsertItem(new WorkItemEntity(
                    "wi-" + accountId + "-wb-share-release-" + request.id(),
                    "wb-share-release-" + request.id(),
                    "share_release_request",
                    request.id(),
                    accountId,
                    "审批平台股份发放",
                    request.amount() + " shares 等待 " + roleLabel(roleCode) + " 审批",
                    List.of(),
                    List.of("share_release_request:" + request.id()),
                    Map.of("action", "share_release_approval"),
                    roleCode.code(),
                    ProjectCapability.SETTLEMENT_MANAGE.code(),
                    "attention",
                    "ready",
                    null,
                    now,
                    now,
                    now)));
        }
    }

    public void publishCiCheck(ProjectCiCheckEntity check, List<ProjectRoleEntity> roles, Instant now) {
        workRepository.closeOpenItemsBySource("project_ci_check", check.id(), "project_ci_projection_refreshed");
        if (!isFailedCheck(check)) {
            return;
        }
        publishForRoles(
                roles,
                DEVELOPMENT_ROLES,
                "wb-project-ci-failed-" + check.id(),
                "project_ci_check",
                check.id(),
                "修复项目 CI",
                "项目 PR 的 CI 检查失败，需要生成修复任务或重新提交 proof。",
                List.of("失败 check 已定位", "生成修复任务或更新 PR", "修复后重新读回 CI 状态"),
                Map.of("action", "ci_failed", "checkId", check.id(), "projectId", check.projectId(), "prNumber", check.prNumber() == null ? "" : check.prNumber(), "checkName", check.checkName()),
                ProjectRoleCode.SYSTEM_CTO.code(),
                ProjectCapability.PROOF_TECH_REVIEW.code(),
                "urgent",
                now);
    }

    public void publishPrLink(ProjectPrLinkEntity link, List<ProjectRoleEntity> roles, Instant now) {
        workRepository.closeOpenItemsBySource("project_pr", link.id(), "project_pr_projection_refreshed");
        if (!List.of("open", "ready", "checks_passed").contains(link.state())) {
            return;
        }
        publishForRoles(
                roles,
                DEVELOPMENT_ROLES,
                "wb-project-pr-ready-" + link.id(),
                "project_pr",
                link.id(),
                "验收项目 PR",
                "项目 PR 已进入可验收状态，需要提交 proof 草案或进入项目验收。",
                List.of("PR 属于项目仓库", "CI 已通过或有明确状态", "proof 绑定 validation task 后再验收"),
                Map.of(
                        "action", "pr_ready_to_accept",
                        "prLinkId", link.id(),
                        "projectId", link.projectId(),
                        "validationTaskId", link.validationTaskId() == null ? "" : link.validationTaskId(),
                        "prUrl", link.prUrl(),
                        "headSha", link.headSha() == null ? "" : link.headSha(),
                        "state", link.state()),
                ProjectRoleCode.SYSTEM_CTO.code(),
                ProjectCapability.PROOF_TECH_REVIEW.code(),
                "attention",
                now);
    }

    public void publishProjectMemorySourceReview(ProjectMemorySyncEventEntity event, List<ProjectRoleEntity> roles, Instant now) {
        workRepository.closeOpenItemsBySource("project_memory", event.id(), "project_memory_projection_refreshed");
        if (!"source_review_ready".equals(event.eventType()) || !"pending".equals(event.status())) {
            return;
        }
        // 中文注释：Project memory source review 由事件写入点直接投影，团队提交 memory 后通过事件状态关闭待办。
        publishForRoles(
                roles,
                MEMORY_ROLES,
                "wb-project-memory-source-" + event.id(),
                "project_memory",
                event.id(),
                "复盘项目 Source",
                "项目运行产生了新的 proof / PR / CI source，需要平台维护 memory 后再进入 Agent Context。",
                List.of("核对 source 原始事实", "填写或编辑平台 memory", "绑定 sourceRefs 后提交审批"),
                Map.of("action", "source_review_ready", "actionId", "review_project_sources", "eventId", event.id(), "projectId", event.projectId(), "payload", event.payload()),
                ProjectRoleCode.SYSTEM_CEO.code(),
                ProjectCapability.PROJECT_MANAGE.code(),
                "attention",
                now);
    }

    public void closeProjectMemorySourceReview(String eventId) {
        workRepository.closeOpenItemsBySource("project_memory", eventId, "project_memory_source_reviewed");
    }

    private void publishForRoles(
            List<ProjectRoleEntity> roles,
            List<ProjectRoleCode> eligibleRoles,
            String itemNo,
            String sourceType,
            String sourceId,
            String title,
            String goal,
            List<String> acceptanceCriteria,
            Map<String, Object> outputSchema,
            String requiredRole,
            String requiredCapability,
            String urgency,
            Instant now) {
        roles.stream()
                .filter(role -> eligibleRoles.contains(role.roleCode()))
                .map(ProjectRoleEntity::accountId)
                .filter(accountId -> accountId != null && !accountId.isBlank())
                .distinct()
                .forEach(accountId -> workRepository.upsertItem(new WorkItemEntity(
                        "wi-" + accountId + "-" + itemNo,
                        itemNo,
                        sourceType,
                        sourceId,
                        accountId,
                        title,
                        goal,
                        acceptanceCriteria,
                        List.of(sourceType + ":" + sourceId),
                        outputSchema,
                        requiredRole,
                        requiredCapability,
                        urgency,
                        "ready",
                        null,
                        now,
                        now,
                        now)));
    }

    private java.util.Optional<String> roleAccountId(List<ProjectRoleEntity> roles, ProjectRoleCode roleCode) {
        return roles.stream()
                .filter(role -> role.roleCode() == roleCode)
                .map(ProjectRoleEntity::accountId)
                .filter(accountId -> accountId != null && !accountId.isBlank())
                .findFirst();
    }

    private String roleLabel(ProjectRoleCode roleCode) {
        // 中文注释：待办描述使用维护席位名称，保留底层 roleCode 作为机器可读权限字段。
        return switch (roleCode) {
            case SYSTEM_CEO -> "协议维护";
            case SYSTEM_CTO -> "技术维护";
            case SYSTEM_CFO -> "结算维护";
        };
    }

    private List<ProjectRoleCode> missingRoles(List<ProjectRoleCode> requiredRoles, List<ProjectRoleCode> approvedRoles) {
        List<ProjectRoleCode> approved = approvedRoles == null ? List.of() : approvedRoles;
        return (requiredRoles == null ? List.<ProjectRoleCode>of() : requiredRoles).stream()
                .filter(role -> !approved.contains(role))
                .toList();
    }

    private boolean isFailedCheck(ProjectCiCheckEntity check) {
        String state = check.conclusion() == null || check.conclusion().isBlank() ? check.status() : check.conclusion();
        return List.of("failure", "failed", "cancelled", "timed_out").contains(state == null ? "" : state.toLowerCase(java.util.Locale.ROOT));
    }
}
