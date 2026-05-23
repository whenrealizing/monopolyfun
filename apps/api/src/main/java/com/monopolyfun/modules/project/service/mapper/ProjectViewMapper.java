package com.monopolyfun.modules.project.service.mapper;

import com.monopolyfun.modules.identity.service.PublicIdentityRefs;
import com.monopolyfun.modules.organization.service.ProjectAuthorityContext;
import com.monopolyfun.modules.project.domain.MarketEntity;
import com.monopolyfun.modules.project.domain.ProjectCapability;
import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.project.domain.ProjectRoleEntity;
import com.monopolyfun.modules.project.service.view.MarketSummary;
import com.monopolyfun.modules.project.service.view.ProjectAuthorityContextView;
import com.monopolyfun.modules.project.service.view.ProjectRoleView;
import com.monopolyfun.modules.project.service.view.ProjectView;

import java.util.List;
import java.util.Map;

public final class ProjectViewMapper {
    private ProjectViewMapper() {
    }

    public static MarketSummary market(MarketEntity market) {
        if (market == null) return null;
        return new MarketSummary(
                market.id(),
                market.name(),
                market.summary(),
                market.listingGoal(),
                market.leadAccountId(),
                market.settlementType(),
                market.nextCurveSlot(),
                market.status(),
                market.sourceRef(),
                market.surfaceUrl());
    }

    public static ProjectView project(ProjectEntity project) {
        return project(project, List.of(), null);
    }

    public static ProjectView project(ProjectEntity project, List<ProjectRoleView> roles) {
        return project(project, roles, null);
    }

    public static ProjectView project(ProjectEntity project, List<ProjectRoleView> roles, String ownerHandle) {
        if (project == null) return null;
        Map<String, Object> metadata = project.metadata() == null ? Map.of() : project.metadata();
        return new ProjectView(
                project.id(),
                project.projectNo(),
                project.ownerAccountId(),
                ownerHandle,
                project.projectLevel().code(),
                project.parentProjectId(),
                project.title(),
                project.summary(),
                project.oneSentence(),
                stringValue(metadata.get("description")),
                stringValue(metadata.get("goal")),
                stringValue(metadata.get("deliverables")),
                stringValue(metadata.get("joinGuide")),
                stringValue(metadata.get("ownerIntro")),
                stringList(metadata.get("referenceLinks")),
                metadataText(metadata, "maintenanceMode", "repo_first"),
                stringValue(metadata.get("repoProvider")),
                stringValue(metadata.get("repoOwner")),
                stringValue(metadata.get("repoName")),
                stringList(metadata.get("defaultMaintenanceCommands")),
                objectMap(metadata.get("maintenancePlaybook")),
                project.inventoryPolicy().name().toLowerCase(),
                project.stockTotal(),
                project.stockSold(),
                roles == null ? List.of() : roles,
                project.status().name().toLowerCase(),
                metadataText(metadata, "tradeStatus", "open"),
                metadataText(metadata, "visibility", "market_public"),
                project.createdAt(),
                project.updatedAt());
    }

    public static ProjectView publicProject(
            ProjectEntity project,
            List<ProjectRoleEntity> roles,
            String ownerHandle,
            Map<String, String> accountHandlesById) {
        // 中文注释：公开项目面保留职位和成员绑定，治理链路细节继续留在私有接口。
        if (project == null) return null;
        Map<String, Object> metadata = project.metadata() == null ? Map.of() : project.metadata();
        return new ProjectView(
                null,
                project.projectNo(),
                null,
                ownerHandle,
                project.projectLevel().code(),
                null,
                project.title(),
                project.summary(),
                project.oneSentence(),
                stringValue(metadata.get("description")),
                stringValue(metadata.get("goal")),
                stringValue(metadata.get("deliverables")),
                stringValue(metadata.get("joinGuide")),
                stringValue(metadata.get("ownerIntro")),
                stringList(metadata.get("referenceLinks")),
                metadataText(metadata, "maintenanceMode", "repo_first"),
                stringValue(metadata.get("repoProvider")),
                stringValue(metadata.get("repoOwner")),
                stringValue(metadata.get("repoName")),
                stringList(metadata.get("defaultMaintenanceCommands")),
                objectMap(metadata.get("maintenancePlaybook")),
                project.inventoryPolicy().name().toLowerCase(),
                project.stockTotal(),
                project.stockSold(),
                publicProjectRoles(roles, accountHandlesById),
                project.status().name().toLowerCase(),
                metadataText(metadata, "tradeStatus", "open"),
                metadataText(metadata, "visibility", "market_public"),
                project.createdAt(),
                project.updatedAt());
    }

    public static ProjectRoleView projectRole(ProjectRoleEntity role) {
        if (role == null) return null;
        return new ProjectRoleView(
                role.projectId(),
                role.roleCode(),
                role.accountId(),
                role.assignedByAccountId(),
                role.assignedAt(),
                role.updatedAt());
    }

    public static List<ProjectRoleView> publicProjectRoles(List<ProjectRoleEntity> roles, Map<String, String> accountHandlesById) {
        if (roles == null || roles.isEmpty()) return List.of();
        // 中文注释：公开角色面保留职位和成员绑定，隐藏指派链和时间线，避免暴露治理操作细节。
        return roles.stream()
                .filter(role -> role != null)
                .map(role -> new ProjectRoleView(
                        null,
                        role.roleCode(),
                        PublicIdentityRefs.accountId(accountHandlesById == null ? null : accountHandlesById.get(role.accountId())),
                        null,
                        null,
                        null))
                .toList();
    }

    public static List<ProjectRoleView> publicProjectRoles(List<ProjectRoleEntity> roles) {
        return publicProjectRoles(roles, Map.of());
    }

    public static ProjectAuthorityContextView projectAuthorityContext(ProjectAuthorityContext context) {
        if (context == null) return null;
        return new ProjectAuthorityContextView(
                context.accountId(),
                context.projectId(),
                context.roleCodes(),
                context.capabilities().stream()
                        .map(ProjectCapability::code)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String metadataText(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) return fallback;
        Object value = metadata.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream()
                .map(item -> item == null ? "" : String.valueOf(item).trim())
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        return map.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new));
    }
}
