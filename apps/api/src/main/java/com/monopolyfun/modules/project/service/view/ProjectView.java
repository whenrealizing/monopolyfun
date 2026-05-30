package com.monopolyfun.modules.project.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectView(
        // 项目内部 id，私有读取面用于稳定前端 key 与权限回读。
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String id,
        // 用户可见项目编号。
        String projectNo,
        // 发起人账号 id，私有读取面用于权限判断。
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String ownerAccountId,
        // 发起人的公开 handle。
        String ownerHandle,
        // root 或 child，前端据此区分系统任务面与普通项目面。
        String projectLevel,
        // child 项目的父 root project id，私有读取面用于层级判断。
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String parentProjectId,
        // 详情页和列表页展示标题。
        String title,
        // 对外摘要。
        String summary,
        // 一句话项目描述。
        String oneSentence,
        // 项目详情正文。
        String description,
        // 项目目标。
        String goal,
        // 项目自己定义的交付物说明。
        String deliverables,
        // 项目自己定义的加入方式说明。
        String joinGuide,
        // 发起人介绍。
        String ownerIntro,
        // 外部资源链接列表。
        List<String> referenceLinks,
        // 仓库维护模式，repo_first 表示后续任务围绕仓库证据推进。
        String maintenanceMode,
        // 仓库平台，例如 forgejo。
        String repoProvider,
        // 仓库 owner。
        String repoOwner,
        // 仓库名称。
        String repoName,
        // 默认维护命令，供 agent 和页面共用。
        List<String> defaultMaintenanceCommands,
        // 维护协议，描述任务类型和证据要求。
        Map<String, Object> maintenancePlaybook,
        // 库存策略。
        String inventoryPolicy,
        // 项目可同时开放的参与席位上限。
        Integer stockTotal,
        // 已占用席位数量。
        int stockSold,
        // 项目系统维护职位，5 个固定席位允许空缺。
        List<ProjectRoleView> roles,
        // 项目状态。
        String status,
        // 市场交易状态。
        String tradeStatus,
        // 市场可见性。
        String visibility,
        // 创建时间。
        Instant createdAt,
        // 最近更新时间。
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String resourceKey,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<String> capabilities,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> blockedCapabilities
) {
    public ProjectView(
            String id,
            String projectNo,
            String ownerAccountId,
            String ownerHandle,
            String projectLevel,
            String parentProjectId,
            String title,
            String summary,
            String oneSentence,
            String description,
            String goal,
            String deliverables,
            String joinGuide,
            String ownerIntro,
            List<String> referenceLinks,
            String maintenanceMode,
            String repoProvider,
            String repoOwner,
            String repoName,
            List<String> defaultMaintenanceCommands,
            Map<String, Object> maintenancePlaybook,
            String inventoryPolicy,
            Integer stockTotal,
            int stockSold,
            List<ProjectRoleView> roles,
            String status,
            String tradeStatus,
            String visibility,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                projectNo,
                ownerAccountId,
                ownerHandle,
                projectLevel,
                parentProjectId,
                title,
                summary,
                oneSentence,
                description,
                goal,
                deliverables,
                joinGuide,
                ownerIntro,
                referenceLinks,
                maintenanceMode,
                repoProvider,
                repoOwner,
                repoName,
                defaultMaintenanceCommands,
                maintenancePlaybook,
                inventoryPolicy,
                stockTotal,
                stockSold,
                roles,
                status,
                tradeStatus,
                visibility,
                createdAt,
                updatedAt,
                null,
                List.of(),
                List.of());
    }

    public ProjectView {
        referenceLinks = referenceLinks == null ? List.of() : List.copyOf(referenceLinks);
        defaultMaintenanceCommands = defaultMaintenanceCommands == null ? List.of() : List.copyOf(defaultMaintenanceCommands);
        maintenancePlaybook = maintenancePlaybook == null ? Map.of() : Map.copyOf(maintenancePlaybook);
        roles = roles == null ? List.of() : List.copyOf(roles);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        blockedCapabilities = blockedCapabilities == null ? List.of() : List.copyOf(blockedCapabilities);
    }

    public ProjectView withAgentState(
            String resourceKey,
            List<String> capabilities,
            List<Map<String, Object>> blockedCapabilities) {
        return new ProjectView(
                id,
                projectNo,
                ownerAccountId,
                ownerHandle,
                projectLevel,
                parentProjectId,
                title,
                summary,
                oneSentence,
                description,
                goal,
                deliverables,
                joinGuide,
                ownerIntro,
                referenceLinks,
                maintenanceMode,
                repoProvider,
                repoOwner,
                repoName,
                defaultMaintenanceCommands,
                maintenancePlaybook,
                inventoryPolicy,
                stockTotal,
                stockSold,
                roles,
                status,
                tradeStatus,
                visibility,
                createdAt,
                updatedAt,
                resourceKey,
                capabilities,
                blockedCapabilities);
    }
}
